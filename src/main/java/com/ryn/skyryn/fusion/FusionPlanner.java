package com.ryn.skyryn.fusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.AttributeLevels;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.data.ShardProgress;

public class FusionPlanner {
	static final double INF = Double.MAX_VALUE;
	private static final int CRAFT_LIMIT = 64;
	private static final int MAX_QTY = 2_000_000;

	public static class Step {
		public final String output;
		public int fusions;
		public int outputAmount;
		public final Map<String, Integer> inputs = new LinkedHashMap<>();
		public boolean selfFuse;
		Step(String output) { this.output = output; }
	}

	public static class Plan {
		public final String target;
		public final int amount;
		public final boolean ironman;
		public final List<Step> steps = new ArrayList<>();
		public final Map<String, Integer> farm = new LinkedHashMap<>();
		public double total;
		public boolean hasUnfarmable;
		public boolean hasBuy;

		Plan(String target, int amount, boolean ironman) {
			this.target = target;
			this.amount = amount;
			this.ironman = ironman;
		}
	}

	private static double baseCost(String s, boolean ironman) {
		ShardDb.Shard sh = ShardDb.shard(s);
		if (sh == null) return INF;
		if (ironman) {
			double r = effectiveRate(sh);
			return r > 0 ? 1.0 / r : INF;
		}
		return FusionCalculator.unitBuyPrice(s);
	}

	private static final Set<String> NO_FORTUNE = Set.of(
			"C19", "U4", "U16", "U28", "R24", "R25", "R27", "R60", "R64", "L4", "L15", "L30", "L33", "L48", "L51");
	private static final Set<String> WOODEN_BAIT = Set.of("R29", "L23", "R59", "R23", "R49");
	private static final Set<String> BLACK_HOLE = Set.of(
			"L47", "L27", "L26", "L17", "E33", "E29", "E20", "E18", "E17", "E14",
			"R56", "R49", "R42", "R39", "R38", "R36", "R31", "R21", "R18", "R6",
			"U38", "U36", "U33", "U32", "U30", "U29", "U27", "U18", "U15", "U12",
			"C36", "C33", "C30", "C27", "C21", "C20", "C15", "C14", "C12", "C9", "C8");
	private static final Set<String> BLACK_HOLE_PYTHON = Set.of(
			"E33", "E18", "R39", "R36", "R31", "R6",
			"U38", "U36", "U33", "U32", "U18", "U15", "U12",
			"C36", "C33", "C30", "C21", "C15", "C12", "C9");

	private static double effectiveRate(ShardDb.Shard sh) {
		double rate = sh.rate;
		if (rate <= 0) return 0;
		String id = sh.id == null ? "" : sh.id.toUpperCase();

		if (RynConfig.excludeChameleon && id.equals("L4")) return 0;
		if (RynConfig.excludeWoodenBait && WOODEN_BAIT.contains(id)) rate *= id.equals("L23") ? 0.10 : 0.05;
		if (RynConfig.frogPet) rate *= 1.1;

		if (NO_FORTUNE.contains(id)) return rate;

		double tiamat = 1 + 0.05 * lvl("tiamat");
		double seaSerpent = 1 + (0.02 * lvl("sea serpent")) * tiamat;
		double python = (0.05 * lvl("python")) * seaSerpent;
		double kingCobra = (0.01 * lvl("king cobra")) * seaSerpent;

		double eff = RynConfig.hunterFortune + rarityFortune(sh.rarity);
		if (BLACK_HOLE.contains(id)) {
			if (BLACK_HOLE_PYTHON.contains(id)) rate *= 1 + python;
			eff *= 1 + kingCobra;
		}
		return rate * (1 + eff / 100.0);
	}

	public static double rateOf(String key) {
		ShardDb.Shard s = ShardDb.shard(key);
		return s == null ? 0 : effectiveRate(s);
	}

	private static double rarityFortune(String rarity) {
		String r = rarity == null ? "" : rarity.toLowerCase();
		return switch (r) {
			case "common" -> 2.0 * lvl("newt");
			case "uncommon" -> 2.0 * lvl("salamander");
			case "rare" -> lvl("lizard king");
			case "epic" -> lvl("leviathan");
			default -> 0;
		};
	}

	private static int lvl(String key) {
		int l = ShardProgress.displayLevel(key);
		return l < 0 ? 0 : l;
	}

	private static long fortuneSig() {
		long s = Math.round(RynConfig.hunterFortune * 100);
		s = s * 31 + lvl("newt") + lvl("salamander") + lvl("lizard king") + lvl("leviathan");
		s = s * 31 + lvl("tiamat") + lvl("sea serpent") + lvl("python") + lvl("king cobra");
		s = s * 31 + (RynConfig.excludeChameleon ? 1 : 0) + (RynConfig.excludeWoodenBait ? 2 : 0)
				+ (RynConfig.frogPet ? 4 : 0);
		return s;
	}

	private static class Costs {
		final Map<String, Double> base = new HashMap<>();
		final Map<String, Double> craft = new HashMap<>();
		final Map<String, ShardDb.Recipe> best = new HashMap<>();

		double u(String s) {
			return Math.min(base.getOrDefault(s, INF), craft.getOrDefault(s, INF));
		}
		boolean shouldCraft(String s) {
			return craft.getOrDefault(s, INF) < base.getOrDefault(s, INF);
		}
	}

	private static Costs cachedIron, cachedMoney;
	private static int cachedCroco = -1, cachedBz = -1;
	private static boolean cachedInsta;
	private static long cachedFortune = Long.MIN_VALUE;

	private static Costs costs(boolean ironman) {
		int cro = RynConfig.crocodileLevel;
		if (cro != cachedCroco) { cachedIron = null; cachedMoney = null; cachedCroco = cro; }
		long fsig = fortuneSig();
		if (fsig != cachedFortune) { cachedIron = null; cachedFortune = fsig; }
		if (ironman) {
			if (cachedIron == null) cachedIron = solve(true);
			return cachedIron;
		}
		int bz = BazaarPrices.version();
		if (bz != cachedBz || RynConfig.useInstaBuy != cachedInsta) {
			cachedMoney = null; cachedBz = bz; cachedInsta = RynConfig.useInstaBuy;
		}
		if (cachedMoney == null) cachedMoney = solve(false);
		return cachedMoney;
	}

	private static Costs solve(boolean ironman) {
		Costs c = new Costs();
		for (String s : ShardDb.allShards()) c.base.put(s, baseCost(s, ironman));

		int maxPasses = ShardDb.allShards().size() + 1;
		for (int pass = 0; pass < maxPasses; pass++) {
			boolean changed = false;
			for (String out : ShardDb.allCraftable()) {
				for (ShardDb.Recipe r : ShardDb.recipesFor(out)) {
					double ua = c.u(r.a), ub = c.u(r.b);
					if (ua >= INF || ub >= INF) continue;
					double cost = (ShardDb.fuseAmount(r.a) * ua + ShardDb.fuseAmount(r.b) * ub)
							/ FusionCalculator.effectiveQty(r);
					if (cost < c.craft.getOrDefault(out, INF) - 1e-9) {
						c.craft.put(out, cost);
						c.best.put(out, r);
						changed = true;
					}
				}
			}
			if (!changed) break;
		}
		return c;
	}

	public static Plan plan(String target, int amount, boolean ironman) {
		String key = target == null ? null : target.toLowerCase();
		Costs c = costs(ironman);
		Plan p = new Plan(key, amount, ironman);
		if (key == null || amount <= 0) return p;

		Map<String, Step> steps = new LinkedHashMap<>();
		produce(c, key, amount, p, steps, 0);
		p.steps.addAll(steps.values());
		finalizeTotals(p, ironman);
		return p;
	}

	private static void finalizeTotals(Plan p, boolean ironman) {
		p.total = 0;
		for (Map.Entry<String, Integer> e : p.farm.entrySet()) {
			boolean buyable = ironman && com.ryn.skyryn.data.ShardInfo.hasPurchase(e.getKey());
			if (buyable) p.hasBuy = true;
			double bc = baseCost(e.getKey(), ironman);
			if (bc >= INF) { if (!buyable) p.hasUnfarmable = true; }
			else p.total += bc * e.getValue();
		}
	}

	public static String[] topInputs(Plan p) {
		for (Step s : p.steps) {
			if (!s.output.equals(p.target)) continue;
			java.util.List<String> in = new java.util.ArrayList<>(s.inputs.keySet());
			if (in.size() == 1) return new String[] { in.get(0), in.get(0) };
			if (in.size() >= 2) return new String[] { in.get(0), in.get(1) };
		}
		return null;
	}

	public static Plan planForLevel(String target, int targetLevel, boolean ironman) {
		ShardDb.Shard s = ShardDb.shard(target);
		int cur = Math.max(0, ShardProgress.displayLevel(target));
		int need = s == null ? 0 : fuseNeed(target, s.rarity, cur, targetLevel);
		return plan(target, need, ironman);
	}

	private static int fuseNeed(String target, String rarity, int cur, int targetLevel) {
		return shardsForLevels(rarity, cur, targetLevel);
	}

	private static void produce(Costs c, String shard, int need, Plan plan,
								Map<String, Step> steps, int depth) {
		if (need <= 0) return;
		if (depth > CRAFT_LIMIT || need > MAX_QTY) {
			plan.farm.merge(shard, Math.min(need, MAX_QTY), Integer::sum);
			plan.hasUnfarmable = true;
			return;
		}

		ShardDb.Recipe r = c.best.get(shard);
		if (r == null || !c.shouldCraft(shard)) {
			plan.farm.merge(shard, need, Integer::sum);
			return;
		}

		double q = FusionCalculator.effectiveQty(r);
		int fusions = (int) Math.ceil(need / q);
		long perAL = (long) fusions * ShardDb.fuseAmount(r.a);
		long perBL = (long) fusions * ShardDb.fuseAmount(r.b);
		if (perAL > MAX_QTY || perBL > MAX_QTY) {
			plan.farm.merge(shard, Math.min(need, MAX_QTY), Integer::sum);
			plan.hasUnfarmable = true;
			return;
		}
		int perA = (int) perAL, perB = (int) perBL;

		produce(c, r.a, perA, plan, steps, depth + 1);
		produce(c, r.b, perB, plan, steps, depth + 1);

		Step st = steps.computeIfAbsent(shard, Step::new);
		st.fusions += fusions;
		st.outputAmount += (int) Math.floor(fusions * q);
		st.inputs.merge(r.firstClick(), r.firstClick().equals(r.a) ? perA : perB, Integer::sum);
		st.inputs.merge(r.secondClick(), r.secondClick().equals(r.a) ? perA : perB, Integer::sum);
		if (r.a.equals(r.b)) st.selfFuse = true;
	}

	public static int shardsForLevels(String rarity, int from, int to) {
		int sum = 0;
		for (int l = from; l < to; l++) {
			int cost = AttributeLevels.nextLevelCost(rarity, l);
			if (cost <= 0) break;
			sum += cost;
		}
		return sum;
	}

	public static double totalOf(Plan p) { return p.total; }
}
