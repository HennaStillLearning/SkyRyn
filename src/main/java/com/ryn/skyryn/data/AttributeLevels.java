package com.ryn.skyryn.data;

import java.util.Map;

public class AttributeLevels {
	public static final int MAX_LEVEL = 10;

	private static final Map<String, int[]> PER_LEVEL = Map.of(
			"common",    new int[] {1, 3, 5, 6, 7, 8, 10, 14, 18, 24},
			"uncommon",  new int[] {1, 2, 3, 4, 5, 6, 7, 8, 12, 16},
			"rare",      new int[] {1, 2, 3, 3, 4, 4, 5, 6, 8, 12},
			"epic",      new int[] {1, 1, 2, 2, 3, 3, 4, 4, 5, 7},
			"legendary", new int[] {1, 1, 1, 2, 2, 2, 3, 3, 4, 5}
	);

	private static int[] table(String rarity) {
		if (rarity == null) return null;
		return PER_LEVEL.get(rarity.toLowerCase());
	}

	public static int totalForMax(String rarity) {
		int[] t = table(rarity);
		if (t == null) return 0;
		int sum = 0;
		for (int v : t) sum += v;
		return sum;
	}

	public static int cumulativeFor(String rarity, int level) {
		int[] t = table(rarity);
		if (t == null || level <= 0) return 0;
		int sum = 0;
		for (int i = 0; i < Math.min(level, t.length); i++) sum += t[i];
		return sum;
	}

	public static int levelFor(String rarity, int fused) {
		int[] t = table(rarity);
		if (t == null) return 0;
		int cum = 0;
		for (int lvl = 1; lvl <= t.length; lvl++) {
			cum += t[lvl - 1];
			if (fused < cum) return lvl - 1;
		}
		return MAX_LEVEL;
	}

	public static int toNextLevel(String rarity, int fused) {
		int lvl = levelFor(rarity, fused);
		if (lvl >= MAX_LEVEL) return 0;
		return cumulativeFor(rarity, lvl + 1) - fused;
	}

	public static int nextLevelCost(String rarity, int level) {
		int[] t = table(rarity);
		if (t == null || level < 0 || level >= t.length) return 0;
		return t[level];
	}

	public static int toMax(String rarity, int level) {
		int[] t = table(rarity);
		if (t == null || level >= t.length) return 0;
		int sum = 0;
		for (int i = Math.max(0, level); i < t.length; i++) sum += t[i];
		return sum;
	}
}
