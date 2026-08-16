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

/**
 * Оптимизатор пути фьюза — порт модели SkyShards.
 *
 * У каждого шарда одна «цена за штуку», и это либо ДЕНЬГИ (цена базара), либо
 * ВРЕМЯ (1/rate — часов на шард прямой добычи). Флаг ironman переключает:
 *   - money   → минимизируем монеты (как {@link FusionCalculator});
 *   - ironman → минимизируем суммарное ВРЕМЯ фарма (базара нет).
 * Оптимизатор один: та же релаксация цен, что в FusionCalculator, но стоимость
 * базового шарда зависит от режима.
 *
 * Бокс на путь НЕ влияет: показываем полный оптимальный путь и ВСЕ материалы —
 * что из них уже лежит в Hunting Box, игрок видит по зелёной подсветке слотов.
 * «Дефицит» = базовые шарды, которые надо нафармить (ironman) или купить (money).
 *
 * v1-упрощения: craftPenalty (время самого фьюза) = 0 — считаем только время
 * добычи; общий ресурс между ветками дерева расходуется жадно в порядке DFS
 * (без treeDemand-резерва SkyShards — добавим, если всплывут перекосы).
 */
public class FusionPlanner {

	static final double INF = Double.MAX_VALUE;
	private static final int CRAFT_LIMIT = 64;
	/** Потолок количества: глубокие цепочки фьюза раздуваются в 5× на уровень и
	 *  переполняют int (отрицательные/гигантские числа). Выше — путь абсурден,
	 *  считаем шард недостижимым по этой ветке. */
	private static final int MAX_QTY = 2_000_000;

	/** Один шаг фьюза (что во что). */
	public static class Step {
		public final String output;
		public int fusions;
		public int outputAmount;
		public final Map<String, Integer> inputs = new LinkedHashMap<>();
		public boolean selfFuse;
		Step(String output) { this.output = output; }
	}

	/** Итог планирования цели. */
	public static class Plan {
		public final String target;
		public final int amount;              // сколько шардов цели просили
		public final boolean ironman;
		public final List<Step> steps = new ArrayList<>();
		/** Базовые шарды, которые надо нафармить/купить. */
		public final Map<String, Integer> farm = new LinkedHashMap<>();
		/** Время (часов, ironman) или монеты (money) по ФАРМИМЫМ шардам. */
		public double total;
		/** true — в дефиците есть шарды без rate/цены (боссы/данж/Кудра): время неполное. */
		public boolean hasUnfarmable;
		/** true — в дефиците есть покупные у NPC (Kirara/Agatha): их НЕ фармят, а покупают. */
		public boolean hasBuy;

		Plan(String target, int amount, boolean ironman) {
			this.target = target;
			this.amount = amount;
			this.ironman = ironman;
		}
	}

	// ===== Модель стоимости =====

	/** Цена одной штуки базового шарда: время 1/rate (ironman) либо цена базара. */
	private static double baseCost(String s, boolean ironman) {
		ShardDb.Shard sh = ShardDb.shard(s);
		if (sh == null) return INF;
		if (ironman) {
			// ВАЖНО: покупные шарды тут НЕ удешевляем — иначе оптимизатор проложит
			// все пути через них (цена ≈ 0), и время схлопнется у всего. Покупку
			// учитываем только на ПОКАЗЕ (исключаем из фарм-времени + метка 🛒).
			double r = effectiveRate(sh);
			return r > 0 ? 1.0 / r : INF;   // только фьюз, если напрямую не фармится
		}
		return FusionCalculator.unitBuyPrice(s);                 // INF, если цены нет
	}

	// ===== Таблицы SkyShards (по id: C/U/R/E/L) =====

	/** Шарды без бонуса фортуны (боссы/спец): rate не множится на фортуну. */
	private static final Set<String> NO_FORTUNE = Set.of(
			"C19", "U4", "U16", "U28", "R24", "R25", "R27", "R60", "R64", "L4", "L15", "L30", "L33", "L48", "L51");
	/** Wooden Bait-шарды: при excludeWoodenBait rate срезается (L23 → 10%, прочие → 5%). */
	private static final Set<String> WOODEN_BAIT = Set.of("R29", "L23", "R59", "R23", "R49");
	/** Black Hole-шарды: effFortune ×(1+kingCobra); у отмеченных true ещё rate ×(1+python). */
	private static final Set<String> BLACK_HOLE = Set.of(
			"L47", "L27", "L26", "L17", "E33", "E29", "E20", "E18", "E17", "E14",
			"R56", "R49", "R42", "R39", "R38", "R36", "R31", "R21", "R18", "R6",
			"U38", "U36", "U33", "U32", "U30", "U29", "U27", "U18", "U15", "U12",
			"C36", "C33", "C30", "C27", "C21", "C20", "C15", "C14", "C12", "C9", "C8");
	private static final Set<String> BLACK_HOLE_PYTHON = Set.of(
			"E33", "E18", "R39", "R36", "R31", "R6",
			"U38", "U36", "U33", "U32", "U18", "U15", "U12",
			"C36", "C33", "C30", "C21", "C15", "C12", "C9");

	/**
	 * Скорость добычи с учётом Hunter Fortune и аттрибутов (полная модель SkyShards):
	 *   effFortune = hunterFortune + бонус_редкости (common 2×Newt · uncommon 2×Salamander
	 *                · rare Lizard King · epic Leviathan · legendary 0)
	 *   Black Hole-шарды: effFortune ×(1+kingCobra), у python-отмеченных rate ×(1+python)
	 *   Frog Pet: rate ×1.1
	 *   rate = base × (1 + effFortune/100)
	 * Множители: tiamat=1+5%×Tiamat; seaSerpent=1+(2%×SeaSerpent)×tiamat;
	 *   python=(5%×Python)×seaSerpent; kingCobra=(1%×KingCobra)×seaSerpent.
	 * Exclude: Chameleon(L4)→0; Wooden Bait→срез rate. NO_FORTUNE → без фортуны.
	 */
	private static double effectiveRate(ShardDb.Shard sh) {
		double rate = sh.rate;
		if (rate <= 0) return 0;
		String id = sh.id == null ? "" : sh.id.toUpperCase();

		if (RynConfig.excludeChameleon && id.equals("L4")) return 0;
		if (RynConfig.excludeWoodenBait && WOODEN_BAIT.contains(id)) rate *= id.equals("L23") ? 0.10 : 0.05;
		if (RynConfig.frogPet) rate *= 1.1;

		if (NO_FORTUNE.contains(id)) return rate;   // фортуна не действует

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

	/**
	 * Эффективный rate шарда (шардов/час с фортуной) — для сверки со SkyShards.
	 * Ставишь те же уровни аттрибутов и hunterFortune, сравниваешь с их числами
	 * (напр. Glacite Walker должен быть ~3710 при fortune 122, max stats).
	 */
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

	/** Подпись фортуны/эксклюдов для кеша: меняется — пересчитываем ironman-стоимости. */
	private static long fortuneSig() {
		long s = Math.round(RynConfig.hunterFortune * 100);
		s = s * 31 + lvl("newt") + lvl("salamander") + lvl("lizard king") + lvl("leviathan");
		s = s * 31 + lvl("tiamat") + lvl("sea serpent") + lvl("python") + lvl("king cobra");
		s = s * 31 + (RynConfig.excludeChameleon ? 1 : 0) + (RynConfig.excludeWoodenBait ? 2 : 0)
				+ (RynConfig.frogPet ? 4 : 0);
		return s;
	}

	/** Посчитанные цены за штуку + лучший рецепт (релаксация как в FusionCalculator). */
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

	// Кеш решения по режиму: релаксация дорогая, а plan() зовётся пачками.
	// Инвалидируем по Crocodile/базару/фортуне.
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

	// ===== Планирование =====

	/** Строит план: получить {@code amount} шардов цели по оптимальному пути. */
	public static Plan plan(String target, int amount, boolean ironman) {
		String key = target == null ? null : target.toLowerCase();
		Costs c = costs(ironman);
		Plan p = new Plan(key, amount, ironman);
		if (key == null || amount <= 0) return p;

		// Бокс НЕ вычитаем: показываем полный оптимальный путь и все материалы.
		// Что лежит в боксе — игрок видит по зелёной подсветке слотов.
		Map<String, Step> steps = new LinkedHashMap<>();
		produce(c, key, amount, p, steps, 0);
		p.steps.addAll(steps.values());
		finalizeTotals(p, ironman);
		return p;
	}

	/** Считает итог по фармимому дефициту (общее для обычного плана и альтернатив). */
	private static void finalizeTotals(Plan p, boolean ironman) {
		// Боссовые (rate/цены нет) не рушат весь план в INF — hasUnfarmable, видно в дефиците.
		// Покупные — считаем как обычно, но помечаем hasBuy («можно купить у NPC»).
		p.total = 0;
		for (Map.Entry<String, Integer> e : p.farm.entrySet()) {
			boolean buyable = ironman && com.ryn.skyryn.data.ShardInfo.hasPurchase(e.getKey());
			if (buyable) p.hasBuy = true;
			double bc = baseCost(e.getKey(), ironman);
			if (bc >= INF) { if (!buyable) p.hasUnfarmable = true; }
			else p.total += bc * e.getValue();
		}
	}

	/** Верхний рецепт плана (вход цели): {a,b}, либо null если фьюза нет. */
	public static String[] topInputs(Plan p) {
		for (Step s : p.steps) {
			if (!s.output.equals(p.target)) continue;
			java.util.List<String> in = new java.util.ArrayList<>(s.inputs.keySet());
			if (in.size() == 1) return new String[] { in.get(0), in.get(0) };
			if (in.size() >= 2) return new String[] { in.get(0), in.get(1) };
		}
		return null;
	}

	/** План поднять аттрибут цели до уровня {@code targetLevel} с текущего. */
	public static Plan planForLevel(String target, int targetLevel, boolean ironman) {
		ShardDb.Shard s = ShardDb.shard(target);
		int cur = Math.max(0, ShardProgress.displayLevel(target));
		int need = s == null ? 0 : fuseNeed(target, s.rarity, cur, targetLevel);
		return plan(target, need, ironman);
	}

	/** Сколько шардов цели надо СФЬЮЗИТЬ до уровня (полная потребность по таблице). */
	private static int fuseNeed(String target, String rarity, int cur, int targetLevel) {
		return shardsForLevels(rarity, cur, targetLevel);
	}

	private static void produce(Costs c, String shard, int need, Plan plan,
								Map<String, Step> steps, int depth) {
		if (need <= 0) return;
		if (depth > CRAFT_LIMIT || need > MAX_QTY) {
			// Слишком глубоко/раздулось — не углубляемся, помечаем недостижимым.
			plan.farm.merge(shard, Math.min(need, MAX_QTY), Integer::sum);
			plan.hasUnfarmable = true;
			return;
		}

		// Единый ОПТИМАЛЬНЫЙ путь (как SkyShards): самый дешёвый рецепт по релаксации
		// (ironman → время, non-ironman → монеты). Бокс на выбор рецепта НЕ влияет.
		ShardDb.Recipe r = c.best.get(shard);
		if (r == null || !c.shouldCraft(shard)) {
			plan.farm.merge(shard, need, Integer::sum);
			return;
		}

		double q = FusionCalculator.effectiveQty(r);
		int fusions = (int) Math.ceil(need / q);
		long perAL = (long) fusions * ShardDb.fuseAmount(r.a);   // вход по своему fuse_amount
		long perBL = (long) fusions * ShardDb.fuseAmount(r.b);
		if (perAL > MAX_QTY || perBL > MAX_QTY) {
			plan.farm.merge(shard, Math.min(need, MAX_QTY), Integer::sum);
			plan.hasUnfarmable = true;
			return;
		}
		int perA = (int) perAL, perB = (int) perBL;

		// Сначала дети — их шаги встанут в списке раньше родителя.
		produce(c, r.a, perA, plan, steps, depth + 1);
		produce(c, r.b, perB, plan, steps, depth + 1);

		Step st = steps.computeIfAbsent(shard, Step::new);
		st.fusions += fusions;
		st.outputAmount += (int) Math.floor(fusions * q);
		st.inputs.merge(r.a, perA, Integer::sum);
		st.inputs.merge(r.b, perB, Integer::sum);
		if (r.a.equals(r.b)) st.selfFuse = true;
	}

	// ===== Помощники по уровням =====

	/** Сколько шардов цели нужно, чтобы поднять аттрибут с {@code from} до {@code to}. */
	public static int shardsForLevels(String rarity, int from, int to) {
		int sum = 0;
		for (int l = from; l < to; l++) {
			int cost = AttributeLevels.nextLevelCost(rarity, l);
			if (cost <= 0) break;
			sum += cost;
		}
		return sum;
	}

	/** Время фарма в часах (ironman-план). Для money вернёт монеты (это и есть total). */
	public static double totalOf(Plan p) { return p.total; }
}
