package com.ryn.skyryn.fusion;

import java.util.List;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;

/**
 * Hunting XP за фьюзы.
 *
 * Формула выведена из наблюдений в игре и сошлась до десятых на всех редкостях
 * при Hunting Wisdom 39.5%:
 *
 *     Chill      (common)     75 * 1.395 =  104.6   — в игре 104.6
 *     Termite    (uncommon)  150 * 1.395 =  209.2   — в игре 209.2
 *     Cropeetle  (rare)      300 * 1.395 =  418.5   — в игре 418.5
 *     Cocoaleech (legendary)1000 * 1.395 = 1395.0   — в игре 1395
 *
 * То есть XP = база(редкость) * (1 + wisdom/100), база кратна 75/150/300/500/1000.
 *
 * Важно: XP даётся ЗА ФЬЮЗ, а не за штуку. Фьюз с выходом x2 даёт столько же XP,
 * сколько с выходом x1 — это видно по «Termite Shard x2» с теми же 209.2.
 * Поэтому в ветке считаем именно число фьюзов на каждом шаге, включая промежуточные.
 */
public class FusionXp {

	/** База XP за один фьюз по редкости выходного шарда, без учёта wisdom. */
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

	/** Множитель от Hunting Wisdom: это процент, 39.5 -> 1.395. */
	public static double wisdomMultiplier() {
		return 1.0 + Math.max(0, RynConfig.huntingWisdom) / 100.0;
	}

	/** XP за один фьюз шарда с учётом твоего wisdom. */
	public static double perFusion(String shard) {
		return baseXp(ShardDb.rarity(shard)) * wisdomMultiplier();
	}

	/**
	 * XP за всю ветку: суммируем по шагам число фьюзов на XP за редкость выхода
	 * этого шага. Промежуточные фьюзы тоже дают XP, и их обычно большинство.
	 */
	public static double forBranch(List<FusionCalculator.Step> steps) {
		double total = 0;
		for (FusionCalculator.Step s : steps) {
			total += s.fusions * perFusion(s.output);
		}
		return total;
	}

	/** Сколько всего фьюзов в ветке. */
	public static int fusionsInBranch(List<FusionCalculator.Step> steps) {
		int n = 0;
		for (FusionCalculator.Step s : steps) n += s.fusions;
		return n;
	}
}
