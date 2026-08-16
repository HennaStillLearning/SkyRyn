package com.ryn.skyryn.fusion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;

/**
 * Рейтинг шардов по выгодности фьюжена.
 *
 * Сортировать по профиту ЗА ШТУКУ бессмысленно: наверху окажется шард с профитом
 * 10M, которого рынок съедает две штуки в день — деньги в нём заморожены на недели.
 * Поэтому основная сортировка — профит В ДЕНЬ:
 *
 *     профит/день = профит за штуку * спрос в день
 *
 * Спрос берём из buyMovingWeek (сколько игроки выкупили за неделю) — это та же
 * цифра, что игра показывает в /bz как "insta-buys in 7d". Продаём мы через
 * sell offer, и его должен кто-то выкупить, поэтому важен именно спрос,
 * а не глубина стакана.
 */
public class FusionTop {

	public static class Entry {
		public final String shard;
		public final double unitCost;
		public final double unitRevenue;
		public final double unitProfit;
		public final double demandPerDay;
		public final double profitPerDay;
		public final BazaarPrices.Warning warning;
		/** Путь тратит reptile-шарды → цифры зависят от уровня Crocodile (настройка). */
		public boolean reptile;

		// ===== Для XP-режима: считается на партии, а не на штуку =====
		/** Сколько фьюзов уйдёт на партию, включая промежуточные. */
		public int batchFusions;
		/** XP за всю ветку партии. */
		public double batchXp;
		/** Вложить в партию. */
		public double batchCost;
		/** Сколько вернётся, если ПРОДАТЬ МГНОВЕННО, честно по стакану заявок. */
		public double batchInstaSell;
		/** Итог: >0 заработал, <0 потерял. */
		public double batchNet;
		/** Сколько штук из партии реально удастся продать мгновенно. */
		public int batchSellable;
		/**
		 * На сколько просядет цена, если слить партию мгновенно: 0.11 = на 11% ниже
		 * верхней заявки. Заявок по верхней цене обычно на десятки штук, и партия
		 * съезжает вниз по стакану — из-за этого Mimic из «прибыльного» становится
		 * убыточным.
		 */
		public double batchSlippage;
		/** Сколько штук реально выйдет из партии. */
		public int batchProduced;

		Entry(String shard, double unitCost, double unitRevenue, double demandPerDay,
			  BazaarPrices.Warning warning) {
			this.shard = shard;
			this.unitCost = unitCost;
			this.unitRevenue = unitRevenue;
			this.unitProfit = unitRevenue - unitCost;
			this.demandPerDay = demandPerDay;
			this.profitPerDay = this.unitProfit * demandPerDay;
			this.warning = warning;
		}

		/** Цена одной тысячи XP. Отрицательная — значит фармишь XP в плюс. */
		public double costPer1kXp() {
			return batchXp > 0 ? -batchNet / (batchXp / 1000.0) : Double.MAX_VALUE;
		}
	}

	public enum Sort {
		PER_DAY("profit/day", "профит/день", false),
		PER_UNIT("profit/pc", "профит/шт", false),
		DEMAND("demand", "спрос", false),
		XP_CHEAP("cheapest XP", "дешевле XP", true),
		XP_MOST("most XP", "больше всего XP", true);

		private final String labelEn, labelRu;
		/** Сортировка из вкладки XP top. */
		public final boolean xp;
		Sort(String labelEn, String labelRu, boolean xp) {
			this.labelEn = labelEn;
			this.labelRu = labelRu;
			this.xp = xp;
		}

		public String label() { return Lang.tr(labelEn, labelRu); }
	}

	/**
	 * Досчитывает партию для XP-режима.
	 *
	 * Выручку берём по РЕАЛЬНОМУ стакану заявок, а не по верхней цене: у Mimic
	 * заявок по верхней всего 45 штук, и продажа сотни съезжает на 11% вниз —
	 * «прибыль» превращается в убыток. По верхней цене мы бы этого не увидели.
	 */
	private static void fillBatch(Entry e, boolean instaSell) {
		int batch = RynConfig.batchOf(e.shard);
		FusionCalculator.Result r = FusionCalculator.calculate(e.shard, batch);
		e.batchFusions = FusionXp.fusionsInBranch(r.steps);
		e.batchXp = FusionXp.forBranch(r.steps);
		e.batchCost = r.totalCost;
		e.batchProduced = r.produced;

		String id = ShardDb.bazaarId(e.shard);
		BazaarPrices.Price p = id == null ? null : BazaarPrices.get(id);
		if (p == null) { e.batchNet = -e.batchCost; return; }

		if (!instaSell) {
			// Через sell offer: продаём по цене нижнего оффера и ждём покупателя.
			// Стакан заявок тут ни при чём — глубина не важна, важна честность цены,
			// а за неё отвечает флажок манипуляции.
			e.batchInstaSell = e.unitRevenue * r.produced;
			e.batchSellable = r.produced;
			e.batchSlippage = 0;
			e.batchNet = e.batchInstaSell - e.batchCost;
			return;
		}

		double[] sell = p.instaSellRevenue(r.produced);
		double tax = RynConfig.bazaarTaxPercent() / 100.0;
		e.batchInstaSell = sell[0] * (1 - tax);
		e.batchSellable = (int) sell[1];
		e.batchNet = e.batchInstaSell - e.batchCost;

		// Насколько средняя цена продажи хуже верхней заявки
		if (sell[1] > 0 && p.highestBuyOrder > 0) {
			double avg = sell[0] / sell[1];
			e.batchSlippage = Math.max(0, 1 - avg / p.highestBuyOrder);
		}
	}

	/** Считает рейтинг. Дёшево: таблица цен уже посчитана и закеширована. */
	public static List<Entry> build(Sort sort, boolean profitableOnly, boolean hideWarned, boolean instaSell) {
		List<Entry> out = new ArrayList<>();
		if (!BazaarPrices.isLoaded() || !ShardDb.isLoaded()) return out;

		for (String shard : ShardDb.allCraftable()) {
			double cost = FusionCalculator.unitCraftCost(shard);
			if (!Double.isFinite(cost) || cost == Double.MAX_VALUE) continue;

			String id = ShardDb.bazaarId(shard);
			BazaarPrices.Price p = id == null ? null : BazaarPrices.get(id);
			if (p == null) continue;

			double revenue = FusionCalculator.unitSellRevenue(shard);
			BazaarPrices.Warning w = p.warning();

			// Цены нет вообще — сравнивать не с чем, но и молча прятать нечестно:
			// показываем с пометкой, если фильтр не включён.
			if (revenue <= 0 && !w.isBad()) continue;

			Entry e = new Entry(shard, cost, revenue, p.demandPerDay(), w);
			ShardDb.Shard sh = ShardDb.shard(shard);
			e.reptile = FusionCalculator.topReptile(shard) || (sh != null && sh.reptile);
			if (hideWarned && w.isBad()) continue;
			// В XP-режиме «только плюсовые» не применяем: там весь смысл в том,
			// чтобы увидеть, где потери минимальны — включая убыточные.
			if (!sort.xp && profitableOnly && e.unitProfit <= 0) continue;
			out.add(e);
		}

		if (sort.xp) {
			// Во вкладке XP считаем партию целиком: сколько фьюзов, сколько XP,
			// и что будет, если сразу слить результат обратно на базар.
			for (Entry e : out) fillBatch(e, instaSell);
			if (sort == Sort.XP_MOST) {
				out.sort(Comparator.comparingDouble((Entry e) -> e.batchXp).reversed());
			} else {
				// Дешевле XP — выше. Отрицательная цена = фармишь в плюс.
				out.sort(Comparator.comparingDouble(Entry::costPer1kXp));
			}
			return out;
		}

		Comparator<Entry> cmp = switch (sort) {
			case PER_UNIT -> Comparator.comparingDouble((Entry e) -> e.unitProfit);
			case DEMAND -> Comparator.comparingDouble((Entry e) -> e.demandPerDay);
			default -> Comparator.comparingDouble((Entry e) -> e.profitPerDay);
		};
		out.sort(cmp.reversed());
		return out;
	}
}
