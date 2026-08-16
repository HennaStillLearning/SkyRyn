package com.ryn.skyryn.fusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;

/**
 * Оптимизатор: ищет самый дешёвый способ получить шард по ценам базара.
 *
 * Алгоритм — итеративная релаксация, а не рекурсия. Для каждого шарда держим
 * цену за штуку и повторяем проход, пока цены падают:
 *
 *     craft[s] = min по рецептам от 5*(unit[a] + unit[b]) / qty
 *     unit[s]  = min(купить на базаре, craft[s])
 *
 * Почему не рекурсия: в графе рецептов есть циклы (шард A может участвовать
 * в получении B, и наоборот), а рецептов после импорта будет ~88 тысяч пар.
 * Наивный обход там либо зациклится, либо повесит игру. Релаксация же просто
 * сходится: цены только убывают и ограничены снизу.
 *
 * Почему сходится и почему выбранный путь — дерево без циклов: фьюз всегда
 * съедает 10 шардов и отдаёт не больше 2, поэтому craft[s] >= 2.5*(unit[a]+unit[b]),
 * то есть полученный шард всегда дороже своих входов. Значит по выбранным
 * рецептам цена строго убывает вниз — цикл невозможен.
 */
public class FusionCalculator {

	/** Доля налога при продаже (0.0125 = 1.25%). Настраивается — см. Bazaar Flipper. */
	private static double sellTax() {
		return RynConfig.bazaarTaxPercent() / 100.0;
	}

	/**
	 * Один шаг фьюжена: что во что фьюзить.
	 * Например: 1000x Chill + 1000x Pest -> 400x Praying Mantis (200 фьюзов).
	 */
	public static class Step {
		public final String output;
		public int outputAmount;
		public int fusions;
		public final Map<String, Integer> inputs = new LinkedHashMap<>();
		/** Рецепт a+b из ОДНОГО шарда (напр. Sun Fish + Sun Fish): в inputs он
		 *  схлопнут в один ключ, но показывать надо двумя слагаемыми. */
		public boolean selfFuse;
		public Step(String output) { this.output = output; }
	}

	/** Результат расчёта для панели. */
	public static class Result {
		public final Map<String, Integer> shoppingList; // что купить на базаре
		public final List<Step> steps;                  // шаги фьюжена по порядку
		public final double totalCost;
		public final double sellRevenue;                // после налога
		public final double profit;
		/** Сколько штук РЕАЛЬНО выйдет: фьюз даёт по qty, на нечётном заказе будет больше. */
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

	/** Посчитанные цены за штуку + выбранный рецепт для каждого шарда. */
	private static class Costs {
		final Map<String, Double> buy = new HashMap<>();   // цена на базаре
		final Map<String, Double> craft = new HashMap<>(); // цена сфьюзить
		final Map<String, ShardDb.Recipe> best = new HashMap<>();

		double unit(String s) {
			return Math.min(buy.getOrDefault(s, Double.MAX_VALUE),
					craft.getOrDefault(s, Double.MAX_VALUE));
		}
		/** Выгоднее сфьюзить, чем купить готовым. */
		boolean shouldCraft(String s) {
			return craft.getOrDefault(s, Double.MAX_VALUE)
					< buy.getOrDefault(s, Double.MAX_VALUE);
		}
	}

	/**
	 * Сколько шардов реально выходит за фьюз с учётом Crocodile.
	 *
	 * Рецепт считается Reptile-овским, если ХОТЯ БЫ ОДИН ВХОД из семейства Reptile
	 * (так это и определено у skyshards). При Crocodile 0 множитель = 1.
	 */
	static double effectiveQty(ShardDb.Recipe r) {
		ShardDb.Shard a = ShardDb.shard(r.a);
		ShardDb.Shard b = ShardDb.shard(r.b);
		boolean reptile = (a != null && a.reptile) || (b != null && b.reptile);
		return reptile ? r.qty * RynConfig.crocodileMultiplier() : r.qty;
	}

	/**
	 * Себестоимость одной штуки по дешёвому пути. Берём прямо из таблицы релаксации,
	 * поэтому без искажения от округления числа фьюзов вверх — в отличие от
	 * calculate(shard, N)/N на маленьком N.
	 * MAX_VALUE, если сфьюзить нельзя.
	 */
	public static double unitCraftCost(String shard) {
		if (shard == null) return Double.MAX_VALUE;
		return costs().craft.getOrDefault(shard.toLowerCase(), Double.MAX_VALUE);
	}

	/** Выручка с продажи одной штуки после налога. */
	public static double unitSellRevenue(String shard) {
		return shard == null ? 0 : sellPrice(shard.toLowerCase());
	}

	/** Топовый рецепт шарда тратит reptile-вход → выход множится на Crocodile (стоимость зависит от уровня). */
	public static boolean topReptile(String shard) {
		if (shard == null) return false;
		ShardDb.Recipe r = costs().best.get(shard.toLowerCase());
		if (r == null) return false;
		ShardDb.Shard a = ShardDb.shard(r.a), b = ShardDb.shard(r.b);
		return (a != null && a.reptile) || (b != null && b.reptile);
	}

	/** Цена покупки одной штуки — для показа в панели. MAX_VALUE, если цены нет. */
	public static double unitBuyPrice(String shard) {
		return buyPrice(shard == null ? null : shard.toLowerCase());
	}

	/** Цена покупки одной штуки на базаре с учётом тумблера insta-buy/buy-offer. */
	private static double buyPrice(String shard) {
		String id = ShardDb.bazaarId(shard);
		if (id == null) return Double.MAX_VALUE;
		BazaarPrices.Price p = BazaarPrices.get(id);
		if (p == null) return Double.MAX_VALUE;
		double price = RynConfig.useInstaBuy ? p.instaBuy : p.sellOffer;
		return price > 0 ? price : Double.MAX_VALUE;
	}

	/** Цена продажи одной штуки (sell offer = buyPrice в API) за вычетом налога. */
	private static double sellPrice(String shard) {
		String id = ShardDb.bazaarId(shard);
		if (id == null) return 0;
		BazaarPrices.Price p = BazaarPrices.get(id);
		if (p == null || p.instaBuy <= 0) return 0;
		return p.instaBuy * (1 - sellTax());
	}

	// Кеш решения: релаксация по всему графу — дорогая штука, а панель зовёт
	// calculate() каждый кадр. Пересчитываем только когда обновились цены
	// или переключили insta-buy/buy-offer.
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

	/** Считает цены за штуку для всех шардов. */
	private static Costs solve() {
		Costs c = new Costs();
		for (String s : ShardDb.allShards()) {
			c.buy.put(s, buyPrice(s));
		}

		// Гоняем проходы, пока хоть одна цена падает. Больше, чем шардов,
		// проходов не нужно — как в Беллмане-Форде.
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

	/**
	 * Раскладывает потребность в шарде на покупки и шаги фьюжена.
	 * {@code forcedTop} — если задан, верхний фьюз (только на этом шаге) идёт по
	 * нему, а не по лучшему рецепту; глубже снова берём оптимальный путь.
	 */
	private static void expand(Costs c, String shard, int amount, boolean forceCraft,
							   Map<String, Integer> buys, Map<String, Step> steps, int depth,
							   ShardDb.Recipe forcedTop) {
		if (amount <= 0) return;
		if (depth > 64) { // страховка от кривых данных
			buys.merge(shard, amount, Integer::sum);
			return;
		}

		ShardDb.Recipe r = forcedTop != null ? forcedTop : c.best.get(shard);
		// Покупаем, если рецепта нет или купить дешевле. Для целевого шарда
		// покупку не рассматриваем: мы его фьюзим ради профита, а не покупаем.
		if (r == null || (!forceCraft && !c.shouldCraft(shard))) {
			buys.merge(shard, amount, Integer::sum);
			return;
		}

		double qty = effectiveQty(r);
		int fusions = (int) Math.ceil(amount / qty);
		int needA = fusions * ShardDb.fuseAmount(r.a);   // каждый вход по своему fuse_amount
		int needB = fusions * ShardDb.fuseAmount(r.b);

		// Сначала дети — тогда их шаги встанут в списке раньше нашего.
		// Форс только на верхнем шаге; ниже — снова оптимальный путь.
		expand(c, r.a, needA, false, buys, steps, depth + 1, null);
		expand(c, r.b, needB, false, buys, steps, depth + 1, null);

		Step step = steps.computeIfAbsent(shard, Step::new);
		step.fusions += fusions;
		step.outputAmount += (int) Math.floor(fusions * qty);
		step.inputs.merge(r.a, needA, Integer::sum);
		step.inputs.merge(r.b, needB, Integer::sum);
		if (r.a.equals(r.b)) step.selfFuse = true;   // Sun Fish + Sun Fish и т.п.
	}

	/** Ищет рецепт цели с входами {a,b} (в любом порядке). null — нет такого. */
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

	/** Мемо расчётов в пределах одного снимка цен/настроек. */
	private static final Map<String, Result> memo = new HashMap<>();
	private static String memoSig = null;

	/**
	 * {@code topA}+{@code topB} — форсированный верхний рецепт цели (как в плашке
	 * BoxBoard), либо null. Стоимость ПОЛНАЯ (бокс не вычитаем — профит честный;
	 * что уже лежит в Hunting Box, игрок видит по зелёной подсветке слотов).
	 *
	 * Мемоизировано: /sr top зовёт calculate() на ВСЕ 321 шард (и повторно при
	 * смене сортировки/фильтра), а страница шарда — каждый кадр. Построение дерева
	 * дорогое, поэтому в пределах снимка цен возвращаем готовый результат. Result
	 * везде читается только на чтение — кэшировать безопасно.
	 */
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

		// Продаём то, что РЕАЛЬНО выйдет, а не то, что попросили.
		// Фьюз даёт qty штук за раз (у firefly 2), поэтому на заказ в 1 штуку
		// придётся сделать фьюз и получить 2. Раньше мы платили за материалы
		// на 2, а выручку считали за 1 — и профит скакал от чётности заказа.
		Step target = steps.get(key);
		int produced = target != null ? target.outputAmount : amount;
		double revenue = sellPrice(key) * produced;
		double profit = (totalCost == Double.MAX_VALUE) ? 0 : revenue - totalCost;

		return new Result(buys, new ArrayList<>(steps.values()), totalCost, revenue, profit, produced);
	}

	/**
	 * Себестоимость и выручка ОДНОГО шарда — для трекера. {@code [unitCost, unitRevenue]},
	 * либо null, если цен нет и посчитать нельзя.
	 *
	 * Считаем на ПЛАНОВОЙ партии (то, что введено в калькулятор), а не на маленькой:
	 * рецепты округляют число фьюзов вверх, и на партии из 2 штук лишние шарды
	 * (сфьюзил 6 промежуточных, нужно 5) задирают себестоимость почти на 20%.
	 */
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
