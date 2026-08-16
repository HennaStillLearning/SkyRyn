package com.ryn.skyryn.fusion;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.waypoint.SkyBlockCheck;

/**
 * Трекер фьюзов и Fusion XP.
 *
 * Сообщения приходят с цветовыми кодами (§x), поэтому чистим их перед разбором.
 *
 * Фьюз (чат, overlay=false): "FUSION! You obtained <Name> Shard x<N>!"
 * XP (action bar, overlay=true): "+<XP> Hunting (...)"
 *
 * ВАЖНО: action bar дублирует одно и то же XP-сообщение несколько раз подряд
 * (обновляется каждый тик). Поэтому Fusion XP засчитываем ТОЛЬКО ОДИН РАЗ
 * на каждый фьюз: после FUSION! ждём первое +Hunting и берём его, дубли игнорим.
 */
public class FusionTracker {

	// ===== Всего за всё время (переживает перезаход) =====
	public static long totalFusions = 0;
	public static long totalShardsObtained = 0;
	public static double totalFusionXp = 0;
	public static double totalSpent = 0;
	public static double totalEarned = 0;

	/** Профит за всё время. */
	public static double totalProfit() {
		return totalEarned - totalSpent;
	}

	public static long sessionFusions = 0;
	public static long sessionShardsObtained = 0;
	public static double sessionFusionXp = 0;
	/** Сколько ЦЕЛЕВЫХ шардов сфьюжено (промежуточные не в счёт). */
	public static long sessionTargetCrafted = 0;
	/** Затраты на материалы для целевых шардов (по дешёвому пути). */
	public static double sessionSpent = 0;
	/** Выручка от продажи целевых шардов (после налога 1.25%). */
	public static double sessionRevenue = 0;
	public static long sessionStart = System.currentTimeMillis();

	/** Профит = выручка − затраты. Промежуточные фьюзы сюда не попадают. */
	public static double sessionProfit() {
		return sessionRevenue - sessionSpent;
	}

	// Ждём ли мы XP за только что случившийся фьюз (чтобы взять только первый).
	private static boolean awaitingFusionXp = false;

	/** Ждём ли XP за фьюз. Трекер охоты по этому флагу не забирает чужой XP. */
	public static boolean isAwaitingFusionXp() { return awaitingFusionXp; }
	// Текст последнего засчитанного XP-сообщения — чтобы отсеять дубли.
	private static String lastXpText = "";

	/**
	 * Hypixel пишет фьюз двумя разными способами:
	 *   "FUSION! You obtained Firefly Shard x2!"      — когда даёт несколько
	 *   "FUSION! You obtained a Cocoaleech Shard!"    — когда даёт один (артикль a/an, без x)
	 * Раньше ловили только первый вариант — поэтому шарды с выходом 1 за фьюз
	 * (Cocoaleech, Sphinx, King Minos) не считались вообще, вместе с их XP.
	 */
	private static final Pattern FUSION_MSG =
			Pattern.compile("FUSION! You obtained (?:an?\\s+)?(.+?) Shard(?:\\s*x(\\d+))?!");
	private static final Pattern XP_MSG =
			Pattern.compile("\\+([\\d,.]+)\\s+Hunting");

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!SkyBlockCheck.onSkyBlock()) return; // вне скайблока фьюзов не бывает
			String raw = message.getString();
			if (raw == null) return;
			String text = stripColors(raw); // убираем §-коды

			if (overlay) {
				detectXp(text);
			} else {
				detectFusion(text);
			}
		});
	}

	/** Убирает цветовые коды вида §x. */
	private static String stripColors(String s) {
		return s.replaceAll("\u00a7.", "");
	}

	private static void detectFusion(String text) {
		Matcher m = FUSION_MSG.matcher(text);
		if (!m.find()) return;

		String shardName = m.group(1).trim();
		// Группа с количеством отсутствует в варианте "a Cocoaleech Shard!" — там 1 штука.
		String countGroup = m.group(2);
		int obtained = 1;
		if (countGroup != null) {
			try { obtained = Integer.parseInt(countGroup); } catch (NumberFormatException ignored) { }
		}

		totalFusions++;
		sessionFusions++;
		sessionShardsObtained += obtained;
		totalShardsObtained += obtained;

		// Ждём XP за этот фьюз (возьмём первое +Hunting).
		awaitingFusionXp = true;
		lastXpText = ""; // сброс, чтобы одинаковый XP-текст нового фьюза засчитался

		// Профит считаем ТОЛЬКО по целевому шарду из калькулятора.
		//
		// Промежуточные фьюзы (Pest+Chill -> Praying Mantis) намеренно не считаем:
		// их стоимость уже сидит внутри себестоимости целевого шарда. Иначе один и
		// тот же вложенный шард засчитался бы дважды — и профит оказался бы завышен.
		String key = shardName.toLowerCase();
		String target = FusionState.currentShard;
		if (target != null && key.equals(target.toLowerCase())) {
			double[] econ = FusionCalculator.unitEconomics(target, FusionState.currentAmount);
			if (econ != null) {
				sessionSpent += econ[0] * obtained;   // купил материалы
				sessionRevenue += econ[1] * obtained; // продал готовый
				sessionTargetCrafted += obtained;
				totalSpent += econ[0] * obtained;
				totalEarned += econ[1] * obtained;
			}
		}
		// НЕ сохраняем конфиг здесь — синхронная запись файла при каждом фьюзе
		// закрывает открытое меню Fusion Box. Totals сохранятся при выходе/ресете.
	}

	private static void detectXp(String text) {
		Matcher m = XP_MSG.matcher(text);
		if (!m.find()) return;

		// Дубль того же самого сообщения — игнорируем.
		if (text.equals(lastXpText)) return;
		lastXpText = text;

		// XP засчитываем как Fusion XP только если ждём его после фьюза.
		if (!awaitingFusionXp) return;
		awaitingFusionXp = false; // берём только первое XP-сообщение на фьюз

		double xp;
		try {
			xp = Double.parseDouble(m.group(1).replace(",", ""));
		} catch (Exception e) {
			return;
		}
		sessionFusionXp += xp;
		totalFusionXp += xp;
	}

	private static double perHour(double value) {
		long elapsed = System.currentTimeMillis() - sessionStart;
		if (elapsed < 1000) return 0;
		return value / (elapsed / 3_600_000.0);
	}

	public static double fusionsPerHour()  { return perHour(sessionFusions); }
	public static double shardsPerHour()   { return perHour(sessionShardsObtained); }
	public static double fusionXpPerHour() { return perHour(sessionFusionXp); }
	public static double profitPerHour()   { return perHour(sessionProfit()); }

	public static void resetSession() {
		sessionFusions = 0;
		sessionShardsObtained = 0;
		sessionFusionXp = 0;
		sessionTargetCrafted = 0;
		sessionSpent = 0;
		sessionRevenue = 0;
		sessionStart = System.currentTimeMillis();
		awaitingFusionXp = false;
	}

	public static void resetAll() {
		totalFusions = 0;
		totalShardsObtained = 0;
		totalFusionXp = 0;
		totalSpent = 0;
		totalEarned = 0;
		resetSession();
		ConfigManager.save();
	}
}
