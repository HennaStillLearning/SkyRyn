package com.ryn.skyryn.fusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;

public class FusionCalculator {
	private static double sellTax() {
		return RynConfig.bazaarTaxPercent() / 100.0;
	}

	public static class Step {
		public final String output;
		public int outputAmount;
		public int fusions;
		public final Map<String, Integer> inputs = new LinkedHashMap<>();
		public boolean selfFuse;
		public Step(String output) { this.output = output; }
	}

	public static class Result {
		public final Map<String, Integer> shoppingList;
		public final List<Step> steps;
		public final double totalCost;
		public final double sellRevenue;
		public final double profit;
		public final int produced;
		public Result(Map<String, Integer> shoppingList, List<Step> steps,
					  double totalCost, double sellRevenue, double profit, int produced) {
			this.shoppingList = shoppingList;
			this.steps = steps;
			this.totalCost = totalCost;
			this.sellRevenue = sellRevenue;
			this.profit = profit;
			this.produced = produced;
		}
	}

	private static class Costs {
		final Map<String, Double> buy = new HashMap<>();
		final Map<String, Double> craft = new HashMap<>();
		final Map<String, ShardDb.Recipe> best = new HashMap<>();

		double unit(String s) {
			return Math.min(buy.getOrDefault(s, Double.MAX_VALUE),
					craft.getOrDefault(s, Double.MAX_VALUE));
		}
		boolean shouldCraft(String s) {
			return craft.getOrDefault(s, Double.MAX_VALUE)
					< buy.getOrDefault(s, Double.MAX_VALUE);
		}
	}

	static double effectiveQty(ShardDb.Recipe r) {
		ShardDb.Shard a = ShardDb.shard(r.a);
		ShardDb.Shard b = ShardDb.shard(r.b);
		boolean reptile = (a != null && a.reptile) || (b != null && b.reptile);
		return reptile ? r.qty * RynConfig.crocodileMultiplier() : r.qty;
	}

	public static double unitCraftCost(String shard) {
		if (shard == null) return Double.MAX_VALUE;
		return costs().craft.getOrDefault(shard.toLowerCase(), Double.MAX_VALUE);
	}

	public static double unitSellRevenue(String shard) {
		return shard == null ? 0 : sellPrice(shard.toLowerCase());
	}

	public static boolean topReptile(String shard) {
		if (shard == null) return false;
		ShardDb.Recipe r = costs().best.get(shard.toLowerCase());
		if (r == null) return false;
		ShardDb.Shard a = ShardDb.shard(r.a), b = ShardDb.shard(r.b);
		return (a != null && a.reptile) || (b != null && b.reptile);
	}

	public static double unitBuyPrice(String shard) {
		return buyPrice(shard == null ? null : shard.toLowerCase());
	}

	private static double buyPrice(String shard) {
		String id = ShardDb.bazaarId(shard);
		if (id == null) return Double.MAX_VALUE;
		BazaarPrices.Price p = BazaarPrices.get(id);
		if (p == null) return Double.MAX_VALUE;
		double price = RynConfig.useInstaBuy ? p.instaBuy : p.sellOffer;
		return price > 0 ? price : Double.MAX_VALUE;
	}

	private static double sellPrice(String shard) {
		String id = ShardDb.bazaarId(shard);
		if (id == null) return 0;
		BazaarPrices.Price p = BazaarPrices.get(id);
		if (p == null || p.instaBuy <= 0) return 0;
		return p.instaBuy * (1 - sellTax());
	}

	private static Costs cached = null;
	private static int cachedVersion = -1;
	private static boolean cachedInstaBuy = false;
	private static int cachedCrocodile = -1;

	private static Costs costs() {
		int v = BazaarPrices.version();
		if (cached != null && cachedVersion == v
				&& cachedInstaBuy == RynConfig.useInstaBuy
				&& cachedCrocodile == RynConfig.crocodileLevel) {
			return cached;
		}
		cached = solve();
		cachedVersion = v;
		cachedInstaBuy = RynConfig.useInstaBuy;
		cachedCrocodile = RynConfig.crocodileLevel;
		return cached;
	}

	private static Costs solve() {
		Costs c = new Costs();
		for (String s : ShardDb.allShards()) {
			c.buy.put(s, buyPrice(s));
		}

		int maxPasses = ShardDb.allShards().size() + 1;
		for (int pass = 0; pass < maxPasses; pass++) {
			boolean changed = false;
			for (String out : ShardDb.allCraftable()) {
				for (ShardDb.Recipe r : ShardDb.recipesFor(out)) {
					double ua = c.unit(r.a);
					double ub = c.unit(r.b);
					if (ua == Double.MAX_VALUE || ub == Double.MAX_VALUE) continue;

					double cost = (ShardDb.fuseAmount(r.a) * ua + ShardDb.fuseAmount(r.b) * ub) / effectiveQty(r);
					if (cost < c.craft.getOrDefault(out, Double.MAX_VALUE) - 1e-6) {
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

	private static void expand(Costs c, String shard, int amount, boolean forceCraft,
							   Map<String, Integer> buys, Map<String, Step> steps, int depth,
							   ShardDb.Recipe forcedTop) {
		if (amount <= 0) return;
		if (depth > 64) {
			buys.merge(shard, amount, Integer::sum);
			return;
		}

		ShardDb.Recipe r = forcedTop != null ? forcedTop : c.best.get(shard);
		if (r == null || (!forceCraft && !c.shouldCraft(shard))) {
			buys.merge(shard, amount, Integer::sum);
			return;
		}

		double qty = effectiveQty(r);
		int fusions = (int) Math.ceil(amount / qty);
		int needA = fusions * ShardDb.fuseAmount(r.a);
		int needB = fusions * ShardDb.fuseAmount(r.b);

		expand(c, r.a, needA, false, buys, steps, depth + 1, null);
		expand(c, r.b, needB, false, buys, steps, depth + 1, null);

		Step step = steps.computeIfAbsent(shard, Step::new);
		step.fusions += fusions;
		step.outputAmount += (int) Math.floor(fusions * qty);
		step.inputs.merge(r.firstClick(), r.firstClick().equals(r.a) ? needA : needB, Integer::sum);
		step.inputs.merge(r.secondClick(), r.secondClick().equals(r.a) ? needA : needB, Integer::sum);
		if (r.a.equals(r.b)) step.selfFuse = true;
	}

	private static ShardDb.Recipe resolveTop(String key, String a, String b) {
		if (key == null || a == null || b == null) return null;
		for (ShardDb.Recipe r : ShardDb.recipesFor(key)) {
			if ((r.a.equalsIgnoreCase(a) && r.b.equalsIgnoreCase(b))
					|| (r.a.equalsIgnoreCase(b) && r.b.equalsIgnoreCase(a))) return r;
		}
		return null;
	}

	public static Result calculate(String shard, int amount) {
		return calculate(shard, amount, null, null);
	}

	private static final Map<String, Result> memo = new HashMap<>();
	private static String memoSig = null;

	public static Result calculate(String shard, int amount, String topA, String topB) {
		String sig = BazaarPrices.version() + "|" + RynConfig.crocodileLevel + "|"
				+ RynConfig.useInstaBuy + "|" + RynConfig.bazaarTaxPercent();
		if (!sig.equals(memoSig)) { memo.clear(); memoSig = sig; }
		String mk = shard + "|" + amount + "|" + topA + "|" + topB;
		Result hit = memo.get(mk);
		if (hit != null) return hit;
		Result r = computeResult(shard, amount, topA, topB);
		memo.put(mk, r);
		return r;
	}

	private static Result computeResult(String shard, int amount, String topA, String topB) {
		String key = shard == null ? null : shard.toLowerCase();
		Costs c = costs();
		ShardDb.Recipe forcedTop = resolveTop(key, topA, topB);

		Map<String, Integer> buys = new LinkedHashMap<>();
		Map<String, Step> steps = new LinkedHashMap<>();
		expand(c, key, amount, true, buys, steps, 0, forcedTop);

		double totalCost = 0;
		for (Map.Entry<String, Integer> e : buys.entrySet()) {
			double p = c.buy.getOrDefault(e.getKey(), Double.MAX_VALUE);
			if (p == Double.MAX_VALUE) { totalCost = Double.MAX_VALUE; break; }
			totalCost += p * e.getValue();
		}

		Step target = steps.get(key);
		int produced = target != null ? target.outputAmount : amount;
		double revenue = sellPrice(key) * produced;
		double profit = (totalCost == Double.MAX_VALUE) ? 0 : revenue - totalCost;

		return new Result(buys, new ArrayList<>(steps.values()), totalCost, revenue, profit, produced);
	}

	public static double[] unitEconomics(String shard, int plannedAmount) {
		if (!BazaarPrices.isLoaded()) return null;
		if (plannedAmount <= 0) return null;
		if (!ShardDb.hasRecipe(shard)) return null;

		Result r = calculate(shard, plannedAmount);
		if (!Double.isFinite(r.totalCost) || r.totalCost == Double.MAX_VALUE) return null;
		if (r.sellRevenue <= 0) return null;

		int made = r.produced > 0 ? r.produced : plannedAmount;
		return new double[] { r.totalCost / made, r.sellRevenue / made };
	}
}
