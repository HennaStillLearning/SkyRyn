package com.ryn.skyryn.fusion;

import java.util.List;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;

public class FusionXp {
	public static double baseXp(String rarity) {
		if (rarity == null) return 0;
		return switch (rarity.toLowerCase()) {
			case "common" -> 75;
			case "uncommon" -> 150;
			case "rare" -> 300;
			case "epic" -> 500;
			case "legendary" -> 1000;
			default -> 0;
		};
	}

	public static double wisdomMultiplier() {
		return 1.0 + Math.max(0, RynConfig.huntingWisdom) / 100.0;
	}

	public static double perFusion(String shard) {
		return baseXp(ShardDb.rarity(shard)) * wisdomMultiplier();
	}

	public static double forBranch(List<FusionCalculator.Step> steps) {
		double total = 0;
		for (FusionCalculator.Step s : steps) {
			total += s.fusions * perFusion(s.output);
		}
		return total;
	}

	public static int fusionsInBranch(List<FusionCalculator.Step> steps) {
		int n = 0;
		for (FusionCalculator.Step s : steps) n += s.fusions;
		return n;
	}
}
