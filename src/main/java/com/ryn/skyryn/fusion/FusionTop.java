package com.ryn.skyryn.fusion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;

public class FusionTop {
	public static class Entry {
		public final String shard;
		public final double unitCost;
		public final double unitRevenue;
		public final double unitProfit;
		public final double demandPerDay;
		public final double profitPerDay;
		public final BazaarPrices.Warning warning;
		public boolean reptile;

		public int batchFusions;
		public double batchXp;
		public double batchCost;
		public double batchInstaSell;
		public double batchNet;
		public int batchSellable;
		public double batchSlippage;
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
		public final boolean xp;
		Sort(String labelEn, String labelRu, boolean xp) {
			this.labelEn = labelEn;
			this.labelRu = labelRu;
			this.xp = xp;
		}

		public String label() { return Lang.tr(labelEn, labelRu); }
	}

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

		if (sell[1] > 0 && p.highestBuyOrder > 0) {
			double avg = sell[0] / sell[1];
			e.batchSlippage = Math.max(0, 1 - avg / p.highestBuyOrder);
		}
	}

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

			if (revenue <= 0 && !w.isBad()) continue;

			Entry e = new Entry(shard, cost, revenue, p.demandPerDay(), w);
			ShardDb.Shard sh = ShardDb.shard(shard);
			e.reptile = FusionCalculator.topReptile(shard) || (sh != null && sh.reptile);
			if (hideWarned && w.isBad()) continue;
			if (!sort.xp && profitableOnly && e.unitProfit <= 0) continue;
			out.add(e);
		}

		if (sort.xp) {
			for (Entry e : out) fillBatch(e, instaSell);
			if (sort == Sort.XP_MOST) {
				out.sort(Comparator.comparingDouble((Entry e) -> e.batchXp).reversed());
			} else {
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
