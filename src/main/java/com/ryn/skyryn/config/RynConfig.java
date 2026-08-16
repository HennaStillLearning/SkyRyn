package com.ryn.skyryn.config;

/**
 * Настройки мода. Правятся через /sr (экран YACL)
 * и сохраняются в .minecraft/config/skyryn.json.
 */
public class RynConfig {

	// ===== Язык интерфейса =====
	/** Язык мода: "en" (база по умолчанию) или "ru". Тумблер в /sr. */
	public static String lang = "en";

	public static boolean isRu() { return "ru".equals(lang); }

	/** Экран /sr shards: false — вид иконок скиллов (по умолчанию), true — плоская сетка. Запоминается между заходами. */
	public static boolean shardsFlatView = false;

	/** Последние открытые шарды (ключи), самый свежий первым. Показываются в хабе. */
	public static final java.util.List<String> recentShards = new java.util.ArrayList<>();
	public static final int RECENT_MAX = 3;

	/** Отметить шард как недавно открытый (двигает в начало, ограничивает RECENT_MAX). */
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

	/** Переключает язык по кругу (en <-> ru). */
	public static void toggleLang() { lang = isRu() ? "en" : "ru"; }

	// ===== Модули =====
	/** Панель калькулятора в окне Fusion Box. */
	public static boolean calculatorEnabled = true;
	/** Панель «Что сфьюзить» справа от окна Fusion Box. */
	public static boolean boxBoardEnabled = true;
	/** Подсветка в /hb и Fusion Box шардов, участвующих в текущем фьюзе калькулятора. */
	public static boolean highlightFuseInputs = true;
	/**
	 * Что делать с серверным ресурспаком Hypixel. Режима два, третьего («грузить как
	 * есть») в тумблере нет: за обычное поведение игры отвечает выключенный мод.
	 *
	 * {@link #PACK_HYBRID} — пак грузится, но серверный вид остаётся только у вещей
	 * НА БАЗЕ БУМАГИ: у бумаги своего вида нет, а мечам, броне и инструментам есть куда
	 * вернуться (MissingItemModelMixin). Заодно прячем файлы пака, перекрывающие ванильные
	 * текстуры и звуки (ServerPackFilterMixin + FilteredServerPack).
	 *
	 * {@link #PACK_OFF} — пак не применяется вовсе. Серверу отвечаем «принял и загрузил»
	 * (иначе на SkyBlock кикает), а обработку отменяем (ResourcePackBypassMixin). Вещи на
	 * базе бумаги в этом режиме бумагой и выглядят.
	 */
	public static final int PACK_HYBRID = 1, PACK_OFF = 2;
	public static int packMode = PACK_HYBRID;
	/** Позиция панели-гайда (перетаскивается за шапку). -1 = авто (у правого края). */
	public static int boxGuideX = -1;
	public static int boxGuideY = -1;
	/** Размер слота панели-гайда (px). Пользователь крутит кнопкой в шапке. */
	public static int boxGuideSlot = 26;
	/** Exclude-тумблеры как у SkyShards: не строить пути через Chameleon / Wooden Bait. */
	public static boolean excludeChameleon = false;
	public static boolean excludeWoodenBait = false;
	/** Frog Pet: +10% к скорости добычи (ironman-фортуна). */
	public static boolean frogPet = false;
	/** Трекер фьюзов (фьюзы/час, Fusion XP, заработок). */
	public static boolean fusionTrackerEnabled = true;
	/** Трекер хантинга — пока не реализован, задел на будущее. */
	public static boolean huntingTrackerEnabled = false;

	// ===== Калькулятор =====
	/** Считать по insta-buy (true) или по buy-offer (false). */
	public static boolean useInstaBuy = true;
	/** Подсказка с количеством на табличке ввода при заказе на базаре. */
	public static boolean bazaarHintEnabled = true;

	/** Уровень перка Bazaar Flipper: 0, 1 или 2. Максимум — 2. */
	public static int bazaarFlipperLevel = 0;

	public static final int BAZAAR_FLIPPER_MAX = 2;

	/** Налог при продаже, %. Каждый тир Bazaar Flipper снимает 0.125% от базовых 1.25%. */
	public static float bazaarTaxPercent() {
		int lvl = Math.max(0, Math.min(BAZAAR_FLIPPER_MAX, bazaarFlipperLevel));
		return 1.25f - 0.125f * lvl;
	}

	/**
	 * Уровень шарда Crocodile (Pure Reptile). Даёт +2% за уровень к ШАНСУ удвоить
	 * выход Reptile-фьюза — не гарантию.
	 *
	 * В расчёте это корректно как матожидание: qty*(1-p) + 2*qty*p = qty*(1+p).
	 * То есть цифры верны в среднем на длинной дистанции, а на отдельном фьюзе
	 * ты либо получишь удвоение, либо нет.
	 */
	public static int crocodileLevel = 0;

	public static final int CROCODILE_MAX = 10;

	/** Средний множитель выхода Reptile-фьюзов. */
	public static double crocodileMultiplier() {
		int lvl = Math.max(0, Math.min(CROCODILE_MAX, crocodileLevel));
		return 1.0 + (2.0 * lvl) / 100.0;
	}

	/** Крутит уровень Crocodile по кругу — для тумблера в панели. */
	public static void cycleCrocodile() {
		crocodileLevel = (crocodileLevel + 1) % (CROCODILE_MAX + 1);
	}

	/**
	 * Hunting Wisdom в процентах — множитель XP на Hypixel.
	 * XP за фьюз = база(редкость) * (1 + wisdom/100). Смотри своё значение в игре.
	 */
	public static float huntingWisdom = 0f;

	/**
	 * Hunter Fortune — среднее число шардов за поимку = 1 + fortune/100.
	 * Подхватывается сам из SkyBlock Menu -> Your Stats Breakdown.
	 */
	public static float hunterFortune = 0f;


	// ===== Трекер охоты =====
	/** Позиция плашки на экране и её масштаб. */
	public static int huntHudX = 4;
	public static int huntHudY = 4;
	public static float huntHudScale = 1.0f;
	/** Что показывает плашка: 0 = сессия, 1 = всего, 2 = в час. */
	public static int huntTrackerMode = 0;

	// ===== Плашка Critter Safari (позиция/масштаб, перетаскивается в /sr hud) =====
	public static int safariHudX = 4;
	public static int safariHudY = 60;
	public static float safariHudScale = 1.0f;

	// ===== Пользовательские цвета (маркеры, хайлайт мобов, плашка). Ключ→ARGB. =====
	public static final java.util.Map<String, Integer> colors = new java.util.HashMap<>();
	public static int color(String key, int def) { return colors.getOrDefault(key, def); }
	public static void setColor(String key, int argb) { colors.put(key, argb); }

	// ===== Ключ→булев (тумблеры по-биомно) и ключ→int (режимы: off/off-after/on). =====
	public static final java.util.Map<String, Boolean> flags = new java.util.HashMap<>();
	public static boolean flag(String key, boolean def) { return flags.getOrDefault(key, def); }
	public static void setFlag(String key, boolean v) { flags.put(key, v); }
	public static final java.util.Map<String, Integer> ints = new java.util.HashMap<>();
	public static int getInt(String key, int def) { return ints.getOrDefault(key, def); }
	public static void setInt(String key, int v) { ints.put(key, v); }
	/** Ключ→строка: свой текст анонсов и прочее, что игрок вписывает руками. */
	public static final java.util.Map<String, String> texts = new java.util.HashMap<>();
	public static String getText(String key, String def) {
		String s = texts.get(key);
		return s == null || s.isBlank() ? def : s;
	}
	public static void setText(String key, String v) {
		if (v == null || v.isBlank()) texts.remove(key); else texts.put(key, v);
	}

	// ===== Critter Safari: пожизненные тоталы (персистентно). Ключ шарда/имя моба → кол-во. =====
	public static final java.util.Map<String, Integer> lifeShards = new java.util.HashMap<>();
	public static final java.util.Map<String, Integer> lifeCaptures = new java.util.HashMap<>();
	public static long lifeEssence = 0, lifeTimeMs = 0, lifeHuntXp = 0;
	public static int lifeRuns = 0;
	/** Профит сафари по цене Sell Offer (true, реально получишь ордером) или Instant Sell (false). */
	public static boolean safariSellOffer = true;
	/** Считать пойманное по мгновенной продаже (true) или через sell offer. */
	public static boolean huntInstaSell = true;

	// Какие источники учитывать. Ловля через lasso/сеть/black hole пишет одинаковое
	// "You caught" и не отключается — это основа трекера.
	public static boolean huntCountLootShare = true;
	public static boolean huntCountSalt = true;
	public static boolean huntCountCharm = true;
	public static boolean huntCountNaga = true;

	/**
	 * Через сколько СЕКУНД без шардов трекер встаёт на паузу.
	 * Фармил-фармил, отошёл — счётчик «в час» замирает, а не занижается.
	 * 0 = не вставать на паузу.
	 */
	public static int huntIdleSeconds = 60;

	/** Показывать разбивку по источникам. */
	/**
	 * Разбивка добычи по источникам. ВЫКЛ: после обновы Naga и Salts как источников
	 * больше нет, остались поимки, лутшейр и чарм — разбивать нечего, и тумблера в
	 * настройках у неё тоже больше нет.
	 */
	public static boolean huntShowSources = false;

	/** Надпись по центру экрана, когда трекер встал на паузу и когда продолжил. */
	public static boolean huntPauseAnnounce = true;

	/**
	 * Ironman-профиль: базара нет, всё добывается руками.
	 *
	 * Пока это только галочка — на расчёты она НЕ влияет. Смысл появится, когда
	 * сделаем путь без базара: там вместо цен нужно показывать, что и сколько
	 * фармить самому.
	 */
	public static boolean ironman = false;

	/**
	 * Ранг MVP+: даёт свитки быстрого варпа к отдельным спотам.
	 *
	 * Отмечается вручную — наличие ранга и свитков клиент не видит. Выключен —
	 * везде обычные варпы.
	 */
	public static boolean mvpPlus = false;

	/**
	 * Crystal Hollows: слать /warp nucleus вместо /warp crystals. Оба бесплатны
	 * и ведут в одну локацию — это просто выбор точки спавна, не MVP+.
	 */
	public static boolean preferNucleus = false;

	/**
	 * Рисовать рут-луч (линию от прицела к споту) при отслеживании. На долгом
	 * фарме мельтешит — можно выключить, останется только box с дистанцией.
	 */
	public static boolean routeBeam = true;

	/**
	 * Кликабельные подсказки-команды на имени моба в гайде. Не всем нужны и чуть
	 * перегружают страницу, поэтому по отдельному тумблеру и по умолчанию выкл.
	 */
	public static boolean bestiaryHints = false;
	public static boolean seaGuideHints = false;

	/**
	 * Какие свитки варпа у игрока есть. У MVP+ может не быть всех, поэтому
	 * отмечаются по отдельности. Нет свитка — ближний варп не предлагаем.
	 */
	public static final java.util.Set<String> scrolls = new java.util.LinkedHashSet<>();

	public static boolean hasScroll(String scroll) {
		return scroll != null && scrolls.contains(scroll);
	}

	public static void setScroll(String scroll, boolean on) {
		if (scroll == null || scroll.isBlank()) return;
		if (on) scrolls.add(scroll); else scrolls.remove(scroll);
	}

	/** Подсветка мобов: мастер-тумблер + набор выбранных типов (ключи из MobHighlight.MOBS). */
	public static boolean mobHighlightEnabled = false;
	public static final java.util.Set<String> highlightMobs = new java.util.LinkedHashSet<>();
	/** Подсветка floor drop — рамка на блоке под дропнутым шардом (работает везде). */
	public static boolean floorDropHighlight = false;
	/** Foraging: оповещение при срабатывании Woodpecker (мгновенный слом дерева). */
	public static boolean woodpeckerAlert = true;
	/** Foraging: оповещение при срабатывании Timber. */
	public static boolean timberAlert = true;
	/** Foraging: оповещение при срабатывании Petalfall. */
	public static boolean petalfallAlert = true;
	/** Foraging: таймеры Critter по Honeycomb (плашка + алерт за 5 сек). */
	public static boolean critterTimer = true;
	/** Critter Safari: трекер захода (плашка со статой). */
	public static boolean safariTracker = true;
	/** Critter Safari: пати-функции (ответы на #команды, Doomspiral-оповещения). ВЫКЛ по умолчанию. */
	public static boolean safariParty = false;
	/** Critter Safari: держишь quest item → подсветка места применения (бокс+луч). */
	public static boolean questHighlight = true;
	/** Critter Safari: показывать Hunting Hotspot на экране (плашка + вспышка). */
	public static boolean hotspotAnnounce = true;
	/**
	 * Critter Safari, соло-режим: всё, что мод обычно пишет в пати-чат (боссы, врата,
	 * биомы, итоги захода), показывается тебе — крупно по центру экрана и строкой
	 * в своём чате. На сервер ничего не уходит.
	 */
	public static boolean safariSolo = false;
	/** Critter Safari: писать тайминги призыва/победы Doomspiral и Wumpa от момента входа. */
	public static boolean safariTimings = true;
	/** Personal Best (мс) по времени полной победы: -1 — ещё нет рекорда. */
	public static long doomPbMs = -1;
	public static long wumpaPbMs = -1;
	/** Critter Safari: слать в пати «Gate open!» при открытии камеры с самоцветами. */
	public static boolean gateAnnounce = true;
	/** Critter Safari: всегда-видимые маркеры-голограммы ключевых точек. */
	public static boolean lmWumpa = true, lmDoom = true, lmGate = true, lmBirdfeeder = true;
	/** Critter Safari: слать в пати «Вошёл в <биом>» при пересечении границы биома. */
	public static boolean biomeEnterMsg = true;
	/** Critter Safari: предупреждать в пати, если 2+ игроков в одном биоме (дюп). */
	public static boolean dupeWarn = true;
	/** Дюп-предупреждение включая Forest (иначе в Forest не анонсим — там нормально быть вместе). */
	public static boolean dupeWarnForest = false;

	/**
	 * Моб, снятый сканером (/srmob) — то, что мод увидел у тебя под прицелом.
	 * entityType — тип существа (у серверных мобов имени часто нет вовсе),
	 * namePart — кусок имени над мобом, если оно есть. Пустое поле = не проверяем.
	 */
	public record CustomMob(String key, String label, String entityType, String namePart, int color) { }

	/** Список снятых сканером мобов. Подсветка у них включается тем же тумблером. */
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

	// ===== Размер партии для оценки вложений =====
	/**
	 * Сколько штук каждого шарда показывать в графе «вложить» в /rftop.
	 * У каждого шарда свой размер: кто-то делает 160 firefly за сессию,
	 * кто-то 64 dragonfly. По умолчанию 100.
	 */
	private static final java.util.Map<String, Integer> BATCH = new java.util.HashMap<>();

	public static final int DEFAULT_BATCH = 100;

	public static int batchOf(String shard) {
		if (shard == null) return DEFAULT_BATCH;
		return BATCH.getOrDefault(shard.toLowerCase(), DEFAULT_BATCH);
	}

	public static void setBatch(String shard, int amount) {
		if (shard == null) return;
		String k = shard.toLowerCase();
		if (amount <= 0 || amount == DEFAULT_BATCH) BATCH.remove(k); // дефолт не храним
		else BATCH.put(k, Math.min(amount, 100_000));
	}

	public static java.util.Map<String, Integer> allBatches() { return BATCH; }

	public static void clearBatches() { BATCH.clear(); }

	// ===== Позиция панели =====
	/**
	 * Координаты левого верхнего угла панели. -1 = авто:
	 * панель встаёт слева от окна Fusion Box.
	 */
	public static int panelX = -1;
	public static int panelY = -1;
	/** Масштаб панели. */
	public static float panelScale = 0.8f;

	// ===== Трекер =====
	/** Что показывает трекер: 0 = сессия, 1 = всего, 2 = в час. */
	public static int trackerMode = 0;

	public static final int TRACKER_SESSION = 0;
	public static final int TRACKER_TOTAL = 1;
	public static final int TRACKER_PER_HOUR = 2;
	/** Только для huntTrackerMode — окно таймера (фьюз-трекер этот режим не использует). */
	public static final int TRACKER_TIMER = 3;

	public static String trackerModeName() {
		return switch (trackerMode) {
			case TRACKER_TOTAL -> Lang.tr("total", "всего");
			case TRACKER_PER_HOUR -> Lang.tr("per hour", "в час");
			default -> Lang.tr("session", "сессия");
		};
	}

	/** Переключает режим трекера по кругу. */
	public static void cycleTrackerMode() {
		trackerMode = (trackerMode + 1) % 3;
	}

	/** Сбрасывает позицию панели на авто. */
	public static void resetPanelPosition() {
		panelX = -1;
		panelY = -1;
	}

	/**
	 * Выключает всё, что мод показывает или пишет. Зовётся один раз при первом
	 * запуске, когда конфига ещё нет: пусть игрок включит то, что ему нужно, а не
	 * разбирает готовую ёлку из плашек и подсветок.
	 *
	 * Тумблеры-флаги живут в общей карте, поэтому достаточно положить в неё false
	 * по префиксам — дефолты в коде их больше не перебьют.
	 */
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
		ints.put("trees.mode", 0);   // метки деревьев — режимом, а не тумблером
	}
}
