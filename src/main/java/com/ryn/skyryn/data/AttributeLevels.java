package com.ryn.skyryn.data;

import java.util.Map;

/**
 * Уровни аттрибутов: сколько шардов нужно вложить, чтобы поднять уровень.
 *
 * Числа — факты об игре. Суммы сходятся с тем, что игрок видит в интерфейсе:
 * common 96, uncommon 64, rare 48, epic 32, legendary 24 шарда до 10 уровня.
 *
 * Чем реже шард, тем меньше их нужно — legendary качается 24 штуками,
 * а common требует 96.
 */
public class AttributeLevels {

	public static final int MAX_LEVEL = 10;

	/** Сколько шардов нужно ДОПОЛНИТЕЛЬНО на каждый уровень (1..10). */
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

	/** Сколько шардов нужно всего до 10 уровня: common 96 … legendary 24. */
	public static int totalForMax(String rarity) {
		int[] t = table(rarity);
		if (t == null) return 0;
		int sum = 0;
		for (int v : t) sum += v;
		return sum;
	}

	/** Сколько нужно накопить суммарно, чтобы иметь уровень level (1..10). */
	public static int cumulativeFor(String rarity, int level) {
		int[] t = table(rarity);
		if (t == null || level <= 0) return 0;
		int sum = 0;
		for (int i = 0; i < Math.min(level, t.length); i++) sum += t[i];
		return sum;
	}

	/** Какой уровень даёт вложенное количество шардов. */
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

	/** Сколько ещё шардов до следующего уровня. 0 — уже максимум. */
	public static int toNextLevel(String rarity, int fused) {
		int lvl = levelFor(rarity, fused);
		if (lvl >= MAX_LEVEL) return 0;
		return cumulativeFor(rarity, lvl + 1) - fused;
	}

	// ===== Считаем от УРОВНЯ =====
	// Уровень мы читаем из Attribute Menu, а сколько шардов уже вложено — нет.
	// Поэтому нужны функции, которым хватает одного уровня.

	/** Сколько шардов с уровня level на level+1. 0 — уже максимум. */
	public static int nextLevelCost(String rarity, int level) {
		int[] t = table(rarity);
		if (t == null || level < 0 || level >= t.length) return 0;
		return t[level];
	}

	/** Сколько шардов с уровня level до 10. 0 — уже максимум. */
	public static int toMax(String rarity, int level) {
		int[] t = table(rarity);
		if (t == null || level >= t.length) return 0;
		int sum = 0;
		for (int i = Math.max(0, level); i < t.length; i++) sum += t[i];
		return sum;
	}
}
