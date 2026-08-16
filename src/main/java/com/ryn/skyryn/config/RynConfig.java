package com.ryn.skyryn.config;

public class RynConfig {
	public static String lang = "en";

	public static boolean isRu() { return "ru".equals(lang); }

	public static boolean shardsFlatView = false;

	public static final java.util.List<String> recentShards = new java.util.ArrayList<>();
	public static final int RECENT_MAX = 3;

	public static void pushRecent(String key) {
		if (key == null || key.isBlank()) return;
		String k = key.toLowerCase();
		recentShards.remove(k);
		recentShards.add(0, k);
		while (recentShards.size() > RECENT_MAX) recentShards.remove(recentShards.size() - 1);
	}

	public static void removeRecent(String key) {
		if (key != null) recentShards.remove(key.toLowerCase());
	}

	public static void toggleLang() { lang = isRu() ? "en" : "ru"; }

	public static boolean calculatorEnabled = true;
	public static boolean boxBoardEnabled = true;
	public static boolean highlightFuseInputs = true;
	public static final int PACK_HYBRID = 1, PACK_OFF = 2;
	public static int packMode = PACK_HYBRID;
	public static int boxGuideX = -1;
	public static int boxGuideY = -1;
	public static int boxGuideSlot = 26;
	public static boolean excludeChameleon = false;
	public static boolean excludeWoodenBait = false;
	public static boolean frogPet = false;
	public static boolean fusionTrackerEnabled = true;
	public static boolean huntingTrackerEnabled = false;

	public static boolean useInstaBuy = true;
	public static boolean bazaarHintEnabled = true;

	public static int bazaarFlipperLevel = 0;

	public static final int BAZAAR_FLIPPER_MAX = 2;

	public static float bazaarTaxPercent() {
		int lvl = Math.max(0, Math.min(BAZAAR_FLIPPER_MAX, bazaarFlipperLevel));
		return 1.25f - 0.125f * lvl;
	}

	public static int crocodileLevel = 0;

	public static final int CROCODILE_MAX = 10;

	public static double crocodileMultiplier() {
		int lvl = Math.max(0, Math.min(CROCODILE_MAX, crocodileLevel));
		return 1.0 + (2.0 * lvl) / 100.0;
	}

	public static void cycleCrocodile() {
		crocodileLevel = (crocodileLevel + 1) % (CROCODILE_MAX + 1);
	}

	public static float huntingWisdom = 0f;

	public static float hunterFortune = 0f;

	public static int huntHudX = 4;
	public static int huntHudY = 4;
	public static float huntHudScale = 1.0f;
	public static int huntTrackerMode = 0;

	public static int safariHudX = 4;
	public static int safariHudY = 60;
	public static float safariHudScale = 1.0f;

	public static final java.util.Map<String, Integer> colors = new java.util.HashMap<>();
	public static int color(String key, int def) { return colors.getOrDefault(key, def); }
	public static void setColor(String key, int argb) { colors.put(key, argb); }

	public static final java.util.Map<String, Boolean> flags = new java.util.HashMap<>();
	public static boolean flag(String key, boolean def) { return flags.getOrDefault(key, def); }
	public static void setFlag(String key, boolean v) { flags.put(key, v); }
	public static final java.util.Map<String, Integer> ints = new java.util.HashMap<>();
	public static int getInt(String key, int def) { return ints.getOrDefault(key, def); }
	public static void setInt(String key, int v) { ints.put(key, v); }
	public static final java.util.Map<String, String> texts = new java.util.HashMap<>();
	public static String getText(String key, String def) {
		String s = texts.get(key);
		return s == null || s.isBlank() ? def : s;
	}
	public static void setText(String key, String v) {
		if (v == null || v.isBlank()) texts.remove(key); else texts.put(key, v);
	}

	public static final java.util.Map<String, Integer> lifeShards = new java.util.HashMap<>();
	public static final java.util.Map<String, Integer> lifeCaptures = new java.util.HashMap<>();
	public static long lifeEssence = 0, lifeTimeMs = 0, lifeHuntXp = 0;
	public static int lifeRuns = 0;
	public static boolean safariSellOffer = true;
	public static boolean huntInstaSell = true;

	public static boolean huntCountLootShare = true;
	public static boolean huntCountSalt = true;
	public static boolean huntCountCharm = true;
	public static boolean huntCountNaga = true;

	public static int huntIdleSeconds = 60;

	public static boolean huntShowSources = false;

	public static boolean huntPauseAnnounce = true;

	public static boolean ironman = false;

	public static boolean mvpPlus = false;

	public static boolean preferNucleus = false;

	public static boolean routeBeam = true;

	public static boolean bestiaryHints = false;
	public static boolean seaGuideHints = false;

	public static final java.util.Set<String> scrolls = new java.util.LinkedHashSet<>();

	public static boolean hasScroll(String scroll) {
		return scroll != null && scrolls.contains(scroll);
	}

	public static void setScroll(String scroll, boolean on) {
		if (scroll == null || scroll.isBlank()) return;
		if (on) scrolls.add(scroll); else scrolls.remove(scroll);
	}

	public static boolean mobHighlightEnabled = false;
	public static final java.util.Set<String> highlightMobs = new java.util.LinkedHashSet<>();
	public static boolean floorDropHighlight = false;
	public static boolean woodpeckerAlert = true;
	public static boolean timberAlert = true;
	public static boolean petalfallAlert = true;
	public static boolean critterTimer = true;
	public static boolean safariTracker = true;
	public static boolean safariParty = false;
	public static boolean questHighlight = true;
	public static boolean hotspotAnnounce = true;
	public static boolean safariSolo = false;
	public static boolean safariTimings = true;
	public static long doomPbMs = -1;
	public static long wumpaPbMs = -1;
	public static boolean gateAnnounce = true;
	public static boolean lmWumpa = true, lmDoom = true, lmGate = true, lmBirdfeeder = true;
	public static boolean biomeEnterMsg = true;
	public static boolean dupeWarn = true;
	public static boolean dupeWarnForest = false;

	public record CustomMob(String key, String label, String entityType, String namePart, int color) { }

	public static final java.util.List<CustomMob> customMobs = new java.util.ArrayList<>();

	public static CustomMob customMob(String key) {
		if (key == null) return null;
		for (CustomMob c : customMobs) if (c.key().equalsIgnoreCase(key)) return c;
		return null;
	}

	public static void putCustomMob(CustomMob m) {
		if (m == null) return;
		customMobs.removeIf(c -> c.key().equalsIgnoreCase(m.key()));
		customMobs.add(m);
	}

	public static boolean removeCustomMob(String key) {
		return key != null && customMobs.removeIf(c -> c.key().equalsIgnoreCase(key));
	}

	public static boolean hasHighlightMob(String key) {
		return key != null && highlightMobs.contains(key.toLowerCase());
	}

	public static void setHighlightMob(String key, boolean on) {
		if (key == null || key.isBlank()) return;
		if (on) highlightMobs.add(key.toLowerCase()); else highlightMobs.remove(key.toLowerCase());
	}

	public static String huntTrackerModeName() {
		return switch (huntTrackerMode) {
			case TRACKER_TOTAL -> Lang.tr("total", "всего");
			case TRACKER_PER_HOUR -> Lang.tr("per hour", "в час");
			case TRACKER_TIMER -> Lang.tr("timer", "таймер");
			default -> Lang.tr("session", "сессия");
		};
	}

	private static final java.util.Map<String, Integer> BATCH = new java.util.HashMap<>();

	public static final int DEFAULT_BATCH = 100;

	public static int batchOf(String shard) {
		if (shard == null) return DEFAULT_BATCH;
		return BATCH.getOrDefault(shard.toLowerCase(), DEFAULT_BATCH);
	}

	public static void setBatch(String shard, int amount) {
		if (shard == null) return;
		String k = shard.toLowerCase();
		if (amount <= 0 || amount == DEFAULT_BATCH) BATCH.remove(k);
		else BATCH.put(k, Math.min(amount, 100_000));
	}

	public static java.util.Map<String, Integer> allBatches() { return BATCH; }

	public static void clearBatches() { BATCH.clear(); }

	public static int panelX = -1;
	public static int panelY = -1;
	public static float panelScale = 0.8f;

	public static int trackerMode = 0;

	public static final int TRACKER_SESSION = 0;
	public static final int TRACKER_TOTAL = 1;
	public static final int TRACKER_PER_HOUR = 2;
	public static final int TRACKER_TIMER = 3;

	public static String trackerModeName() {
		return switch (trackerMode) {
			case TRACKER_TOTAL -> Lang.tr("total", "всего");
			case TRACKER_PER_HOUR -> Lang.tr("per hour", "в час");
			default -> Lang.tr("session", "сессия");
		};
	}

	public static void cycleTrackerMode() {
		trackerMode = (trackerMode + 1) % 3;
	}

	public static void resetPanelPosition() {
		panelX = -1;
		panelY = -1;
	}

	public static void allFeaturesOff() {
		calculatorEnabled = boxBoardEnabled = highlightFuseInputs = false;
		fusionTrackerEnabled = huntingTrackerEnabled = false;
		mobHighlightEnabled = floorDropHighlight = false;
		woodpeckerAlert = timberAlert = petalfallAlert = critterTimer = false;
		safariTracker = safariParty = safariSolo = questHighlight = false;
		hotspotAnnounce = safariTimings = gateAnnounce = biomeEnterMsg = false;
		dupeWarn = dupeWarnForest = false;
		lmWumpa = lmDoom = lmGate = lmBirdfeeder = false;
		bestiaryHints = seaGuideHints = bazaarHintEnabled = false;
		highlightMobs.clear();
		for (String k : new String[]{ "hive.timer", "critter.plaque", "sparkling.hl",
				"sparkling.ann", "bells.show", "tiki.on", "tiki.show", "tiki.hint",
				"torrhus.beeheemoth", "tr.sparkling" })
			flags.put(k, false);
		ints.put("trees.mode", 0);
	}
}
