package com.ryn.skyryn.hud;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.fusion.BazaarPrices;
import com.ryn.skyryn.fusion.FusionTracker;
import com.ryn.skyryn.waypoint.SkyBlockCheck;

public class HuntingTracker {
	private static final Pattern CAUGHT =
			Pattern.compile("You caught (?:x(\\d+) )?(?:an?\\s+)?(.+?) Shards?!");

	private static final Pattern LOOT_SHARE =
			Pattern.compile("You received (\\d+) (.+?) Shards? for assisting");

	private static final Pattern CHARMED =
			Pattern.compile("(CHARM|SALT|NAGA)\\s+You charmed an? (.+?) and captured "
					+ "(?:(\\d+) Shards? from it|its Shard)\\.");

	public enum Source {
		CAUGHT(null),
		LOOT_SHARE("loot share"),
		SALT("salt"),
		CHARM("charm"),
		NAGA("naga");

		private final String fixedLabel;
		Source(String fixedLabel) { this.fixedLabel = fixedLabel; }

		public String label() {
			return fixedLabel != null ? fixedLabel : Lang.tr("caught", "поймано");
		}

		public boolean enabled() {
			return switch (this) {
				case LOOT_SHARE -> RynConfig.huntCountLootShare;
				case SALT -> RynConfig.huntCountSalt;
				case CHARM -> RynConfig.huntCountCharm;
				case NAGA -> RynConfig.huntCountNaga;
				default -> true;
			};
		}
	}

	public static final Map<Source, Integer> sessionBySource = new LinkedHashMap<>();
	public static final Map<Source, Integer> totalBySource = new LinkedHashMap<>();

	public static final Map<String, Integer> sessionCaught = new LinkedHashMap<>();
	public static long sessionShards = 0;
	public static double sessionXp = 0;
	public static long sessionStart = System.currentTimeMillis();
	public static long sessionCatches = 0;
	public static final Map<String, Integer> sessionCatchesByShard = new LinkedHashMap<>();

	public static final Map<String, Integer> totalCaught = new LinkedHashMap<>();
	public static long totalShards = 0;
	public static double totalXp = 0;
	public static long totalCatches = 0;
	public static final Map<String, Integer> totalCatchesByShard = new LinkedHashMap<>();

	public static String lastCaughtKey = null;

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!RynConfig.huntingTrackerEnabled) return;
			if (!SkyBlockCheck.onSkyBlock()) return;
			String raw = message.getString();
			if (raw == null) return;
			String text = raw.replaceAll("§.", "");

			if (overlay) {
				if (!FusionTracker.isAwaitingFusionXp()) detectHuntXp(text);
			} else {
				detectCatch(text);
			}
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
				.register(mc -> tickTimer());
	}

	private static void detectCatch(String text) {
		Matcher m = CAUGHT.matcher(text);
		if (m.find()) {
			add(m.group(2), parseOr(m.group(1), 1), Source.CAUGHT);
			return;
		}
		m = LOOT_SHARE.matcher(text);
		if (m.find()) {
			add(m.group(2), parseOr(m.group(1), 1), Source.LOOT_SHARE);
			return;
		}
		m = CHARMED.matcher(text);
		if (m.find()) {
			Source src = switch (m.group(1)) {
				case "NAGA" -> Source.NAGA;
				case "CHARM" -> Source.CHARM;
				default -> Source.SALT;
			};
			add(m.group(2), parseOr(m.group(3), 1), src);
		}
	}

	private static int parseOr(String s, int fallback) {
		if (s == null) return fallback;
		try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
	}

	public static long lastCatchAt = 0;
	private static long activeMs = 0;

	private static void add(String name, int n, Source src) {
		if (name == null || n <= 0) return;
		if (!src.enabled()) return;
		String key = name.trim().toLowerCase();
		if (ShardDb.shard(key) == null) return;

		long now = System.currentTimeMillis();
		if (lastCatchAt > 0) {
			long gap = now - lastCatchAt;
			long cap = RynConfig.huntIdleSeconds > 0
					? RynConfig.huntIdleSeconds * 1000L : Long.MAX_VALUE;
			activeMs += Math.min(gap, cap);
		}
		lastCatchAt = now;

		sessionCaught.merge(key, n, Integer::sum);
		totalCaught.merge(key, n, Integer::sum);
		sessionBySource.merge(src, n, Integer::sum);
		totalBySource.merge(src, n, Integer::sum);
		sessionShards += n;
		totalShards += n;
		sessionCatches++;
		totalCatches++;
		sessionCatchesByShard.merge(key, 1, Integer::sum);
		totalCatchesByShard.merge(key, 1, Integer::sum);
		lastCaughtKey = key;

		awaitingHuntXp = true;
		lastXpText = "";
	}

	public static boolean idle() {
		if (RynConfig.huntIdleSeconds <= 0 || lastCatchAt == 0) return false;
		return System.currentTimeMillis() - lastCatchAt > RynConfig.huntIdleSeconds * 1000L;
	}

	private static final Pattern XP_MSG = Pattern.compile("\\+([\\d,.]+)\\s+Hunting");
	private static String lastXpText = "";
	private static boolean awaitingHuntXp = false;

	private static void detectHuntXp(String text) {
		Matcher m = XP_MSG.matcher(text);
		if (!m.find()) return;
		if (text.equals(lastXpText)) return;
		lastXpText = text;
		if (!awaitingHuntXp) return;
		awaitingHuntXp = false;
		try {
			double xp = Double.parseDouble(m.group(1).replace(",", ""));
			sessionXp += xp;
			totalXp += xp;
		} catch (NumberFormatException ignored) { }
	}

	public static double value(Map<String, Integer> caught, boolean instaSell) {
		double total = 0;
		double tax = RynConfig.bazaarTaxPercent() / 100.0;
		for (Map.Entry<String, Integer> e : caught.entrySet()) {
			String id = ShardDb.bazaarId(e.getKey());
			BazaarPrices.Price p = id == null ? null : BazaarPrices.get(id);
			if (p == null) continue;
			if (instaSell) {
				total += p.instaSellRevenue(e.getValue())[0] * (1 - tax);
			} else {
				total += p.instaBuy * e.getValue() * (1 - tax);
			}
		}
		return total;
	}

	private static double perHour(double v) {
		long ms = activeMs;
		if (lastCatchAt > 0 && !idle()) ms += System.currentTimeMillis() - lastCatchAt;
		if (ms < 1000) return 0;
		return v / (ms / 3_600_000.0);
	}

	public static long activeMs() { return activeMs; }

	public static double shardsPerHour() { return perHour(sessionShards); }
	public static double catchesPerHour() { return perHour(sessionCatches); }
	public static double xpPerHour() { return perHour(sessionXp); }
	public static double valuePerHour(boolean instaSell) {
		return perHour(value(sessionCaught, instaSell));
	}

	public static void resetSession() {
		sessionCaught.clear();
		sessionBySource.clear();
		sessionCatchesByShard.clear();
		sessionShards = 0;
		sessionXp = 0;
		sessionCatches = 0;
		sessionStart = System.currentTimeMillis();
		activeMs = 0;
		lastCatchAt = 0;
		if (timerRunning && !timerFrozen) {
			timerShardsAtStart = 0; timerCatchesAtStart = 0; timerXpAtStart = 0;
			timerCaughtAtStart.clear(); timerBySourceAtStart.clear(); timerCatchesByShardAtStart.clear();
		}
	}

	private static final int TIMER_MIN_MINUTES = 1, TIMER_MAX_MINUTES = 180;
	private static int timerMinutes = 10;
	private static boolean timerRunning = false;
	private static long timerStart = 0;
	private static boolean timerFrozen = false;

	private static long timerShardsAtStart, timerCatchesAtStart;
	private static double timerXpAtStart;
	private static Map<String, Integer> timerCaughtAtStart = new LinkedHashMap<>();
	private static Map<Source, Integer> timerBySourceAtStart = new LinkedHashMap<>();
	private static Map<String, Integer> timerCatchesByShardAtStart = new LinkedHashMap<>();

	private static long timerFrozenShards, timerFrozenCatches;
	private static double timerFrozenXp;
	private static Map<String, Integer> timerFrozenCaught = new LinkedHashMap<>();
	private static Map<Source, Integer> timerFrozenBySource = new LinkedHashMap<>();
	private static Map<String, Integer> timerFrozenCatchesByShard = new LinkedHashMap<>();

	public static int timerMinutes() { return timerMinutes; }
	public static boolean timerRunning() { return timerRunning; }
	public static boolean timerFrozen() { return timerFrozen; }

	public static void adjustTimerMinutes(int delta) {
		timerMinutes = Math.max(TIMER_MIN_MINUTES, Math.min(TIMER_MAX_MINUTES, timerMinutes + delta));
	}

	public static void startTimer() {
		timerRunning = true;
		timerFrozen = false;
		timerStart = System.currentTimeMillis();
		timerShardsAtStart = sessionShards;
		timerCatchesAtStart = sessionCatches;
		timerXpAtStart = sessionXp;
		timerCaughtAtStart = new LinkedHashMap<>(sessionCaught);
		timerBySourceAtStart = new LinkedHashMap<>(sessionBySource);
		timerCatchesByShardAtStart = new LinkedHashMap<>(sessionCatchesByShard);
	}

	public static void stopTimer() {
		timerRunning = false;
		timerFrozen = false;
	}

	private static void tickTimer() {
		if (!timerRunning || timerFrozen) return;
		if (System.currentTimeMillis() - timerStart >= timerMinutes * 60_000L) {
			timerFrozen = true;
			timerFrozenShards = sessionShards - timerShardsAtStart;
			timerFrozenCatches = sessionCatches - timerCatchesAtStart;
			timerFrozenXp = sessionXp - timerXpAtStart;
			timerFrozenCaught = diff(sessionCaught, timerCaughtAtStart);
			timerFrozenBySource = diff(sessionBySource, timerBySourceAtStart);
			timerFrozenCatchesByShard = diff(sessionCatchesByShard, timerCatchesByShardAtStart);
		}
	}

	private static <K> Map<K, Integer> diff(Map<K, Integer> current, Map<K, Integer> atStart) {
		Map<K, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<K, Integer> e : current.entrySet()) {
			int d = e.getValue() - atStart.getOrDefault(e.getKey(), 0);
			if (d > 0) out.put(e.getKey(), d);
		}
		return out;
	}

	public static long timerRemainingMs() {
		if (!timerRunning || timerFrozen) return 0;
		return Math.max(0, timerMinutes * 60_000L - (System.currentTimeMillis() - timerStart));
	}

	public static long timerShards() { return timerFrozen ? timerFrozenShards : sessionShards - timerShardsAtStart; }
	public static long timerCatches() { return timerFrozen ? timerFrozenCatches : sessionCatches - timerCatchesAtStart; }
	public static double timerXp() { return timerFrozen ? timerFrozenXp : sessionXp - timerXpAtStart; }
	public static Map<String, Integer> timerCaught() {
		return timerFrozen ? timerFrozenCaught : diff(sessionCaught, timerCaughtAtStart);
	}
	public static Map<Source, Integer> timerBySource() {
		return timerFrozen ? timerFrozenBySource : diff(sessionBySource, timerBySourceAtStart);
	}
	public static Map<String, Integer> timerCatchesByShard() {
		return timerFrozen ? timerFrozenCatchesByShard : diff(sessionCatchesByShard, timerCatchesByShardAtStart);
	}

	public static void resetAll() {
		totalCaught.clear();
		totalBySource.clear();
		totalCatchesByShard.clear();
		totalShards = 0;
		totalXp = 0;
		totalCatches = 0;
		lastCaughtKey = null;
		resetSession();
	}
}
