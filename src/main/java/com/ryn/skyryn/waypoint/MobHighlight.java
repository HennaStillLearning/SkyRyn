package com.ryn.skyryn.waypoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

import com.ryn.skyryn.config.RynConfig;

/**
 * Подсветка мобов контуром.
 *
 * Обводится силуэт самого моба — рисует это игра (миксины MinecraftGlowMixin /
 * EntityTeamColorMixin), поэтому работает и с Sodium.
 *
 * СКВОЗЬ СТЕНЫ НЕ ПОДСВЕЧИВАЕМ — это воллхак и он запрещён. Ванильное свечение само
 * по себе просвечивает блоки, поэтому включается оно только тем мобам, которых видно
 * от камеры ({@link #inSight}); зашёл за камень — погасло. Луч бьётся в несколько точек
 * хитбокса: моба, торчащего из листвы наполовину, видно — значит подсвечиваем.
 *
 * КОГО именно обводим — см. {@link #resolve}. У серверных мобов ванильная сущность
 * часто невидима (у Flitter это Bat), а картинку рисует отдельный display, который на
 * ней «едет». Обводить надо display: контур невидимой мыши висел бы поверх криттера и
 * формой был бы мышью.
 *
 * ЗОНА проверяется только у безымянных мобов, которых иначе не различить: Cinderbat
 * (Crimson Isle) и Bloodbat (Critter Safari, биом Haunted) — обе ванильные летучие
 * мыши, отличает их только место. У криттеров сафари зона пустая: имя над мобом
 * уникально. У мобов, снятых сканером, зоны нет вовсе.
 *
 * Остров/подзону берём из скорборда, а вот БИОМОВ сафари в скорборде нет — их границы
 * размечаются вручную командой /srbiome, поэтому биом спрашиваем у {@link SafariBiomes}.
 *
 * Какие мобы подсвечивать — выбирает игрок (тумблеры в /sr); набор в
 * {@link RynConfig#highlightMobs}, мастер-тумблер {@link RynConfig#mobHighlightEnabled}.
 * Свои мобы из конфига (customMobs) подсвечиваются так же.
 */
public class MobHighlight {

	/**
	 * Определение подсвечиваемого моба: ключ (тумблер), подпись, цвет, ключ ЗОНЫ
	 * ("" — любая) и проверка типа энтити.
	 */
	public record MobDef(String key, String label, int color, String zone, Predicate<Entity> typeMatch, String group) { }

	/** Биомы Critter Safari — их списки живут в разделе Critter Safari, а не в «Подсветке». */
	public static final List<String> GROUPS = List.of("Cavern", "Forest", "Haunted", "Icy");
	/** Группа Torrhus Canyon — свой раздел настроек. «Other» — всё, что вне этих мест. */
	public static final String TORRHUS = "Torrhus Canyon", OTHER = "Other",
			GALATEA = "Galatea", SAFARI_NPC = "Safari NPC";

	/**
	 * Мобы, сгруппированные по биому. Безымянные (Bloodbat, шалкеры) — по типу энтити
	 * плюс зона, криттеры сафари — по имени над мобом.
	 */
	public static final List<MobDef> MOBS = build();

	private static List<MobDef> build() {
		List<MobDef> m = new java.util.ArrayList<>();
		// --- Torrhus Canyon (полный список криттеров с вики; морские существа не в счёт) ---
		m.add(new MobDef("hideonsun", "§eHideonsun§r", 0xFFFFD24A, "torrhus",
				e -> e instanceof net.minecraft.world.entity.monster.Shulker, TORRHUS));
		// Grizzly и Parched на вики-странице не значатся: Grizzly выходит из подарков
		// с деревьев, Parched — с honeycomb-дерева. Мобы настоящие, из списка не убирать.
		for (String k : new String[]{ "beeheemoth", "blue jay", "bunbun", "drybark", "dustybit", "ember",
				"firefox", "goldolot", "grizzly", "groundhog", "hivethief", "honeybuzz", "mountain goat",
				"pangolin", "parched", "pollendart", "puck", "sepialot", "solar", "timil", "water snake" })
			m.add(crit(k, 0xFFE0C060, TORRHUS));
		// Hunter-NPC: отдельный тумблер. Это игрок-сущность, а не моб, поэтому и правило
		// своё — иначе NPC не подсветить вовсе: обычных игроков подсветка не трогает.
		m.add(new MobDef("hunter", "§eHunter NPC§r", 0xFFFFD24A, "",
				e -> e instanceof net.minecraft.world.entity.player.Player
						&& e.getName().getString().toLowerCase().startsWith("hunter "), SAFARI_NPC));
		// --- Прочее: не Torrhus и не сафари, но подсветка та же ---
		m.add(new MobDef("cinderbat", "§6Cinderbat§r", 0xFFFF7A33, "crimson",
				e -> e instanceof net.minecraft.world.entity.ambient.Bat, OTHER));
		m.add(new MobDef("hideonleaf", "§aHideonleaf§r", 0xFF5FD68A, "galatea",
				e -> e instanceof net.minecraft.world.entity.monster.Shulker, GALATEA));
		// Murkbat — ванильная летучая мышь с нейтмегом (снято сканером: minecraft:bat,
		// хитбокс 1.50×2.70). Ловится по имени, так что с Cinderbat и Bloodbat не путается.
		m.add(crit("murkbat", 0xFF3FD0C0, GALATEA));
		// Honeyhog сняты автосканом на Galatea.
		m.add(crit("honeyhog", 0xFFE0C060, GALATEA));
		m.add(crit("stag beetle", 0xFFB08040, GALATEA));
		m.add(crit("honeymite", 0xFFE0C060, GALATEA));
		m.add(crit("woodlouse", 0xFFB08040, GALATEA));
		// Hewver — ванильный sniffer с нейтмегом «[Lv69] ⓪ Hewver» (снято /srmob).
		// Имени на самом мобе нет, только на стойке рядом — ловится тем же nearNamed.
		m.add(crit("hewver", 0xFF7FD060, GALATEA));
		// Invisibug: тела у него нет вовсе. Сканер показал одну стойку-МАРКЕР
		// (хитбокс 0.00×0.00, невидимая, без имени, без предмета, рядом никого) —
		// картинку жука рисуют частицы, а не модель. Поэтому и по имени его не найти,
		// и контур ему не сделать: обводить нечего. Рисуем боксом (см. boxed).
		m.add(new MobDef("invisibug", "§bInvisibug§r", 0xFF9BE0FF, "galatea",
				MobHighlight::isInvisibugMarker, GALATEA));
		// --- Cavern ---
		// Scrappy и Rockmite в списке не значились — попались автосканом в Cavern.
		for (String k : new String[]{ "cavernfish", "flitter", "driftling", "chuckwalla",
				"snoozle", "gemzie", "scrappy", "rockmite" })
			m.add(crit(k, 0xFF55E0FF, "Cavern"));
		// Shyworm — червяк из СЕГМЕНТОВ: связка из зомби и семи невидимых слизней, на
		// каждом сегменте стойка с предметом. Когда он зарылся, нейтмега у него нет
		// вовсе (снято сканером: «name nearby: —»), поэтому одного имени мало —
		// добавляем примету по самой связке.
		// Зона пустая, как у всех криттеров: строку сафари скорборд отдаёт не всегда,
		// и проверка зоны глушит подсветку целиком.
		m.add(new MobDef("shyworm", "§fShyworm§r", 0xFF55E0FF, "",
				e -> nearNamed(e, "shyworm") || isSlimeStack(e), "Cavern"));
		// --- Forest (+ Hideonfloor — безымянный шалкер, не фиолетовый) ---
		m.add(new MobDef("hideonfloor", "§bHideonfloor§r", 0xFF4AC0E0, "critter safari",
				e -> e instanceof net.minecraft.world.entity.monster.Shulker && !isPurpleShulker(e), "Forest"));
		for (String k : new String[]{ "foxtrot", "bluebird", "honeybug", "treefrog", "woodchucker", "fluffling", "parakeet", "macaw" })
			m.add(crit(k, 0xFF50E070, "Forest"));
		// --- Haunted (+ Bloodbat безымянный Bat, кроме Flitter; Solsnatcher по имени) ---
		m.add(new MobDef("bloodbat", "§cBloodbat§r", 0xFFFF4A4A, "critter safari",
				e -> e instanceof net.minecraft.world.entity.ambient.Bat && !nearNamed(e, "flitter")
						&& inBiome(e, "haunted"), "Haunted"));
		// Duplico прикидывается блоками особняка (книжная полка и два тёмных блока), и
		// пока он замаскирован, нейтмега у него НЕТ — по имени его не найти, а именно
		// тогда он и нужен. Ловим по его interaction-сущности, но ОБЯЗАТЕЛЬНО по размеру:
		// interaction у Hypixel общий для половины криттеров (Shyworm, Chuckwalla,
		// Flitter, Driftling), и по одному типу под Duplico попали бы все подряд.
		// Хитбокс 1.10×1.10 — только у него, остальные 0.37…0.75 (снято автосканом).
		m.add(new MobDef("duplico", "§5Duplico§r", 0xFFB060FF, "",
				e -> (typeIs(e, "interaction") && isDuplicoBox(e)) || nearNamed(e, "duplico"), "Haunted"));
		// Hideonwall — фиолетовый шалкер в стене. Ловим по имени, а не по цвету шалкера:
		// цвет нужен лишь затем, чтобы отсечь его у Hideonfloor.
		for (String k : new String[]{ "areita", "gazer", "litterbug", "hideyho", "solsnatcher",
				"gimmiegold", "hideonwall" })
			m.add(crit(k, 0xFFB060FF, "Haunted"));
		// --- Icy ---
		for (String k : new String[]{ "strongarm", "tepid", "polaris", "shuddersquid", "billygoat",
				"mantis shrimp", "nozzlenose", "troodon" })
			m.add(crit(k, 0xFF9BE0FF, "Icy"));
		return List.copyOf(m);
	}

	/**
	 * Криттер сафари: сначала по ТИПУ существа, потом по имени над мобом. Зона не нужна,
	 * и то и другое уникально.
	 */
	private static MobDef crit(String key, int argb, String group) {
		return new MobDef(key, "§f" + capWords(key), argb, "", e -> typeIs(e, key) || nearNamed(e, key), group);
	}

	/**
	 * Тип существа совпадает с ключом моба. Своих криттеров Hypixel регистрирует
	 * отдельными типами (снято сканером: Parched = «minecraft:parched»), и это примета
	 * надёжнее нейтмега: она есть всегда, читается мгновенно и не зависит от того,
	 * догрузилась ли стойка с именем — вдалеке та отстаёт от моба на 1–2 блока.
	 */
	private static boolean typeIs(Entity e, String key) {
		return EntityType.getKey(e.getType()).toString().endsWith(":" + key.replace(' ', '_'));
	}
	/**
	 * Сегмент Shyworm: невидимый слизень 1.04×1.04, а вокруг ещё такие же. Ничего
	 * другого из невидимых слизней стопкой в Cavern не водится, так что примета точная
	 * и работает, даже когда червяк зарылся и имя пропало.
	 */
	private static boolean isSlimeStack(Entity e) {
		if (!(e instanceof net.minecraft.world.entity.monster.Slime) || !e.isInvisible()) return false;
		if (Math.abs(e.getBoundingBox().getXsize() - 1.04) > 0.1) return false;
		int near = 0;
		for (Entity o : e.level().getEntities(e, e.getBoundingBox().inflate(2.5, 2.0, 2.5)))
			if (o instanceof net.minecraft.world.entity.monster.Slime && o.isInvisible() && ++near >= 2) return true;
		return false;
	}

	/**
	 * Стойка-маркер Invisibug: невидимая, хитбокс 0.00×0.00, без имени и без предмета.
	 *
	 * Такими же маркерами собран Shyworm, но у него вокруг семь стоек с предметами
	 * (сегменты) — по ним и отличаем. Плюс правило привязано к зоне galatea, а Shyworm
	 * живёт в Cavern, так что пересечься им негде.
	 */
	private static boolean isInvisibugMarker(Entity e) {
		if (!(e instanceof net.minecraft.world.entity.decoration.ArmorStand st)) return false;
		if (!st.isInvisible() || st.getCustomName() != null) return false;
		AABB bb = st.getBoundingBox();
		if (bb.getXsize() > 0.02 || bb.getYsize() > 0.02) return false;
		for (net.minecraft.world.entity.EquipmentSlot s : net.minecraft.world.entity.EquipmentSlot.values())
			if (!st.getItemBySlot(s).isEmpty()) return false;
		for (Entity o : e.level().getEntities(e, bb.inflate(3.0, 3.0, 3.0)))
			if (isModelStand(o)) return false;
		return true;
	}

	/** Куб 1.10×1.10 — размер interaction-сущности Duplico и ничей больше. */
	private static boolean isDuplicoBox(Entity e) {
		AABB bb = e.getBoundingBox();
		return Math.abs(bb.getXsize() - 1.10) < 0.06 && Math.abs(bb.getYsize() - 1.10) < 0.06;
	}

	private static String capWords(String s) {
		String[] w = s.split(" ");
		StringBuilder sb = new StringBuilder();
		for (String x : w) { if (sb.length() > 0) sb.append(' '); sb.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1)); }
		return sb.toString();
	}

	/**
	 * Стойка с надетым предметом — это не подпись, а МОДЕЛЬ моба: Hypixel так делает
	 * то, чего в ванили нет (перекати-поле Driftling и подобное). Пустая стойка —
	 * просто нейтмег, её не трогаем.
	 */
	static boolean isModelStand(Entity e) {
		if (!(e instanceof net.minecraft.world.entity.decoration.ArmorStand st)) return false;
		// У стойки-подписи есть имя, у стойки-модели его нет. Без этой проверки обе
		// считаются моделью: подпись стоит к мобу ближе и выигрывает выбор носителя,
		// а рисует она пустоту — контур уезжает на пустую стойку.
		if (st.getCustomName() != null) return false;
		for (net.minecraft.world.entity.EquipmentSlot s : net.minecraft.world.entity.EquipmentSlot.values())
			if (!st.getItemBySlot(s).isEmpty()) return true;
		return false;
	}

	/**
	 * Моб внутри биома сафари. Биом берём под САМИМ мобом (а не под игроком — он может
	 * стоять на границе). Границы сняты через /srbiome; не сняты или точка ничья —
	 * проверку пропускаем, иначе подсветка молчала бы на ровном месте.
	 */
	private static boolean inBiome(Entity e, String biome) {
		if (!SafariBiomes.any()) return true;
		String b = SafariBiomes.biomeAt(e.getX(), e.getZ());
		return b.isEmpty() || b.equalsIgnoreCase(biome);
	}

	/** Шалкер фиолетового цвета (Hideonwall в Haunted-биоме прячется в стене — не подсвечиваем). */
	private static boolean isPurpleShulker(Entity e) {
		if (!(e instanceof net.minecraft.world.entity.monster.Shulker sh)) return false;
		net.minecraft.world.item.DyeColor col = sh.getColor();
		return col == null || col == net.minecraft.world.item.DyeColor.PURPLE;   // null = дефолт-фиолетовый
	}

	/** Есть ли у энтити имя с подстрокой. */
	private static boolean nameContains(Entity e, String sub) {
		var n = e.getCustomName();
		return n != null && n.getString().toLowerCase().contains(sub);
	}

	/**
	 * Имя есть у самого моба ИЛИ у нейтмега рядом. У части мобов Hypixel вешает имя
	 * отдельной невидимой стойкой над головой — по самому мобу их не опознать.
	 * Бокс широкий: вдалеке позиция стойки интерполируется с задержкой и отстаёт
	 * от моба на 1–2 блока.
	 */
	private static boolean nearNamed(Entity e, String sub) {
		if (nameContains(e, sub)) return true;
		for (Entity pass : e.getPassengers()) if (nameContains(pass, sub)) return true;
		// По высоте с запасом: Duplico прикидывается разными мобами, и у высокой
		// маскировки нейтмег висит заметно выше — с трёх блоков его уже не доставали.
		AABB area = e.getBoundingBox().inflate(2.5, 4.0, 2.5);
		for (Entity o : e.level().getEntities(e, area)) if (nameContains(o, sub)) return true;
		return false;
	}

	public static MobDef def(String key) {
		if (key == null) return null;
		for (MobDef d : MOBS) if (d.key().equalsIgnoreCase(key)) return d;
		return null;
	}

	private static final float MAX_DIST = 80f;

	public static boolean enabled() {
		return RynConfig.mobHighlightEnabled && (!RynConfig.highlightMobs.isEmpty() || !RynConfig.customMobs.isEmpty());
	}

	/**
	 * Sparkling-криттер: один на 4096, даёт 10× дроп и Rainbow Feather. Опознаётся по
	 * тегу SPARKLING в имени. Проверяем ПЕРВЫМ и не смотрим на тумблер конкретного
	 * криттера: пропустить такого из-за выключенного Foxtrot — потеря на ровном месте.
	 */
	public static final MobDef SPARKLING = new MobDef("sparkling", "§6Sparkling§r", 0xFFFFA020, "",
			e -> nearNamed(e, "sparkling"), OTHER);

	/** Подходит ли сущность под какого-нибудь включённого моба. */
	private static MobDef match(Entity ent, String island, String area) {
		boolean player = ent instanceof net.minecraft.world.entity.player.Player;
		if (player) return playerMatch(ent);
		if (RynConfig.flag("sparkling.hl", true) && SPARKLING.typeMatch().test(ent)) return SPARKLING;
		for (MobDef d : MOBS) {
			if (!RynConfig.hasHighlightMob(d.key())) continue;
			if (d.key().equals("hunter")) continue;
			String z = d.zone();
			if (!z.isEmpty() && !z.equals(island) && !area.contains(z)) continue;   // не та зона
			if (d.typeMatch().test(ent)) return d;
		}
		return matchCustom(ent);
	}

	/**
	 * Игрока-сущность может подсветить ТОЛЬКО правило Hunter-NPC. Иначе нейтмег
	 * криттера, стоящего рядом с NPC, обводит заодно и самого NPC — тот начинает
	 * светиться цветом соседнего моба.
	 */
	private static MobDef playerMatch(Entity ent) {
		MobDef h = def("hunter");
		if (h == null || !RynConfig.hasHighlightMob("hunter")) return null;
		return h.typeMatch().test(ent) ? h : null;
	}

	/**
	 * Моб, снятый сканером: тип существа (у серверных мобов имени часто нет вовсе)
	 * плюс имя, если оно было. Зоны у своих мобов нет — берём их где угодно.
	 */
	private static MobDef matchCustom(Entity ent) {
		String type = EntityType.getKey(ent.getType()).toString();
		for (RynConfig.CustomMob c : RynConfig.customMobs) {
			if (!RynConfig.hasHighlightMob(c.key())) continue;
			if (!c.entityType().isBlank() && !type.equals(c.entityType())) continue;
			if (!c.namePart().isBlank() && !nearNamed(ent, c.namePart().toLowerCase())) continue;
			if (c.entityType().isBlank() && c.namePart().isBlank()) continue;   // пустое правило ничего не значит
			return new MobDef(c.key(), c.label(), c.color(), "", e -> true, "Custom");
		}
		return null;
	}

	// ===== Кэш совпадений =====
	// Зовётся из миксина для КАЖДОЙ отрисованной сущности каждый кадр, а поиск
	// нейтмега рядом делает запрос по области. Держим ответ 500 мс на энтити.
	private record Cached(long at, MobDef def) { }
	private static final java.util.Map<Integer, Cached> CACHE = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Cached> OUTLINE = new java.util.HashMap<>();
	/** Опознанные мобы: держим имя за сущностью, даже если нейтмег с неё пропал. */
	private static final java.util.Map<Integer, Cached> STICKY = new java.util.HashMap<>();
	private static final long STICKY_MS = 300_000;
	private static long lastSweep = 0;

	/** Логическая сущность моба (может быть невидимой). Стойки-нейтмеги и дисплеи — не мобы. */
	private static MobDef mobOf(Entity ent) {
		if (ent == null) return null;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ent == mc.player) return null;
		// Стойки и дисплеи — это подписи и картинки, а не мобы. Исключение — Invisibug:
		// у него стойка-маркер И ЕСТЬ моб, другого тела у него нет вовсе.
		if (ent instanceof net.minecraft.world.entity.Display) return null;
		if (ent instanceof net.minecraft.world.entity.decoration.ArmorStand && !isInvisibugMarker(ent)) return null;

		long now = System.currentTimeMillis();
		Cached c = CACHE.get(ent.getId());
		if (c != null && now - c.at() < 500) return c.def();
		MobDef d = match(ent, SkyBlockCheck.currentIsland(), SkyBlockCheck.currentArea().toLowerCase());
		// Кого один раз опознали — помним. После неудачной попытки поймать капсулой
		// Hypixel снимает с моба нейтмег (шалкеры прячутся), и по имени он больше не
		// находится: подсветка гасла до самого респавна. Сущность та же — держим за ней
		// прежнее имя, пока она жива.
		if (d != null) STICKY.put(ent.getId(), new Cached(now, d));
		else {
			Cached s = STICKY.get(ent.getId());
			if (s != null && now - s.at() < STICKY_MS) d = s.def();
		}
		CACHE.put(ent.getId(), new Cached(now, d));
		return d;
	}

	/**
	 * Кого обводить. У видимого моба — его самого. У невидимого ванильного тела
	 * контур был бы формой этого тела (мышь вместо Flitter), поэтому его пропускаем
	 * и обводим display, который на нём едет (или стоит вплотную) — именно он и
	 * рисует ту картинку, которую видит игрок.
	 */
	public static MobDef outlineDef(Entity ent) {
		if (ent == null || !enabled()) return null;
		long now = System.currentTimeMillis();
		if (now - lastSweep > 5000) {
			lastSweep = now;
			CACHE.entrySet().removeIf(e -> now - e.getValue().at() > 5000);
			OUTLINE.entrySet().removeIf(e -> now - e.getValue().at() > 5000);
			markerBody.entrySet().removeIf(e -> now - e.getValue() > 5000);
			STICKY.entrySet().removeIf(e -> now - e.getValue().at() > STICKY_MS);
		}
		Cached c = OUTLINE.get(ent.getId());
		if (c != null && now - c.at() < 500) return c.def();
		MobDef d = resolve(ent);
		OUTLINE.put(ent.getId(), new Cached(now, d));
		return d;
	}

	private static MobDef resolve(Entity ent) {
		MobDef d = mobOf(ent);
		if (d != null) {
			// Видимое тело обводим само. У невидимого картинку обычно несёт display или
			// стойка — тогда обводим её, а тело пропускаем. Но если носителя нет вовсе
			// (Shyworm: невидимые слизни и зомби), то обводить больше нечего — берём
			// само тело. Контур невидимой сущности игра рисует, и это лучше, чем
			// ничего: сквозь стены он всё равно не пройдёт, проверка видимости общая.
			if (!ent.isInvisible()) return d;
			// Стойка-МАРКЕР геометрии не даёт вовсе (см. isMarkerStand): рисовать по ней
			// нельзя, поэтому возвращаемся к телу — у него хитбокс на месте. Смотрим, есть
			// ли маркер вообще, а не выбран ли он ближайшим: рядом мог упасть Lasso, и
			// его стойка оказалась бы к телу ближе собственной стойки моба.
			if (hasMarkerCarrier(ent)) { markerBody.put(ent.getId(), System.currentTimeMillis()); return d; }
			return chosenCarrier(ent) == null ? d : null;
		}
		if (!isCarrier(ent) || isMarkerStand(ent)) return null;
		// Носитель картинки: display едет на мобе…
		Entity host = ent.getVehicle();
		if (needsCarrier(host)) { d = mobOf(host); if (d != null) return d; }
		// …либо просто висит на его месте (Hypixel не всегда сажает его пассажиром).
		for (Entity o : ent.level().getEntities(ent, ent.getBoundingBox().inflate(1.5, 2.0, 1.5))) {
			if (!needsCarrier(o)) continue;
			d = mobOf(o);
			if (d == null) continue;
			// У тела со стойкой-маркером подсветку рисуем по нему самому, и никакой
			// носитель ей не нужен. Иначе рядом с таким мобом подсвечивалось всё
			// подряд: паутина на конце брошенного Lasso — это тоже стойка с предметом,
			// и она оказывалась к телу ближе собственной стойки моба.
			if (hasMarkerCarrier(o)) return null;
			// Носителей рядом может быть несколько (картинка + стойка с нейтмегом):
			// обводим только один, иначе за мобом «бегает» второй контур.
			return chosenCarrier(o) == ent ? d : null;
		}
		return null;
	}

	/**
	 * Нужен ли этому телу отдельный носитель картинки. Нужен ТОЛЬКО невидимому: у
	 * видимого моба картинка — он сам.
	 *
	 * Без этой проверки к видимому мобу цепляется всё, что оказалось рядом: паутина на
	 * конце брошенного Lasso — тоже стойка с предметом, и в паре блоков от моба она
	 * сходит за его модель.
	 */
	private static boolean needsCarrier(Entity host) {
		return host != null && host.isInvisible();
	}

	/**
	 * Сущность, которая может нести картинку моба: display или стойка-модель.
	 *
	 * Картины пробовали и убрали: маскировка Hideonwall — это картина на стене, и её
	 * контур было видно с обратной стороны стены. Через стены не подсвечиваем, так
	 * что маскировку не обводим вовсе.
	 */
	private static boolean isCarrier(Entity e) {
		return e instanceof net.minecraft.world.entity.Display || isModelStand(e);
	}

	/**
	 * Стойка-МАРКЕР с предметом: предмет рисуется, а хитбокса у неё нет (0.00×0.00), и
	 * стоит она не там, где моб. У Woodlouse сканер снял её на 1.35 блока НИЖЕ тела —
	 * предмет сидит на голове и оттуда дотягивается до уровня моба.
	 *
	 * Ни рисовать по ней, ни проверять по ней видимость нельзя: точка лежит в полу,
	 * луч от камеры упирается в блок, и подсветка вспыхивала только в прыжке. Контур ей
	 * тоже не идёт — при свечении игра дорисовывает сам скелет стойки. Поэтому у таких
	 * мобов возвращаемся к телу: у него настоящий хитбокс и он там, где моба видно.
	 */
	private static boolean isMarkerStand(Entity e) {
		return isModelStand(e) && e.getBoundingBox().getYsize() < 0.05;
	}

	/** Тела, чью картинку несёт стойка-маркер: их рисуем боксом (id → когда решили). */
	private static final java.util.Map<Integer, Long> markerBody = new java.util.HashMap<>();

	/** Есть ли у этого тела стойка-маркер — тогда подсветка идёт по телу, а не по носителям. */
	private static boolean hasMarkerCarrier(Entity host) {
		for (Entity o : host.level().getEntities(host, host.getBoundingBox().inflate(1.5, 2.0, 1.5)))
			if (isMarkerStand(o)) return true;
		return false;
	}

	/**
	 * Единственный носитель, который обводим у этого тела: ближайший к нему, причём
	 * display важнее стойки — картинку у Hypixel почти всегда рисует именно он.
	 */
	private static Entity chosenCarrier(Entity host) {
		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity o : host.level().getEntities(host, host.getBoundingBox().inflate(1.5, 2.0, 1.5))) {
			if (!isCarrier(o)) continue;
			double score = o.position().distanceToSqr(host.position())
					+ (o instanceof net.minecraft.world.entity.Display ? 0 : 1000);
			if (score < bestScore) { bestScore = score; best = o; }
		}
		return best;
	}

	/**
	 * Светится ли контур прямо сейчас. Для MinecraftGlowMixin.
	 * Обязательно И подсвечен, И на виду — иначе получился бы воллхак.
	 */
	/**
	 * Мобы, которых рисуем БОКСОМ, а не контуром.
	 *
	 * Стойка-модель: игра обводит скелет стойки (руки, штырь, подставку), а видно у неё
	 * только надетый предмет — вместо модели получается длинная палка.
	 * Duplico: он всегда стоит блоком, и контур блока в стене блоков не читается —
	 * боксом его видно, контуром нет.
	 */
	private static boolean boxed(Entity ent, MobDef d) {
		// Стойка-модель: бокс или контур — выбирает игрок (hl.stand, 0 — бокс, 1 — контур).
		// Контур точнее ложится на моба, но игра обводит вместе с предметом и сам скелет
		// стойки, поэтому у мобов с мелкой моделью снизу торчит штырь. Что лучше — зависит
		// от моба, отсюда и переключатель.
		if (isModelStand(ent)) return RynConfig.getInt("hl.stand", 0) == 0;
		if (d == null) return false;
		// Invisibug: модели нет вообще, контуру не за что зацепиться — только бокс.
		if (d.key().equals("duplico") || d.key().equals("invisibug")) return true;
		// Тело, чью картинку несёт стойка-маркер (Woodlouse, Stag Beetle): контур обвёл бы
		// невидимого слизня, а видно там совсем другую модель — честнее бокс по телу.
		Long at = markerBody.get(ent.getId());
		return at != null && System.currentTimeMillis() - at < 2000;
	}

	public static boolean glowing(Entity ent) {
		MobDef d = outlineDef(ent);
		if (d == null || boxed(ent, d)) return false;
		return inSight(ent);
	}

	/** Цвет контура (0 — не подсвечен). Для EntityTeamColorMixin. */
	public static int outlineColor(Entity ent) {
		MobDef d = outlineDef(ent);
		if (d == null || !inSight(ent)) return 0;
		return RynConfig.color("mob." + d.key(), d.color());
	}

	// Видимость проверяем лучом от камеры и держим ответ 200 мс: моб движется,
	// а трассировать его каждый кадр для каждой сущности дорого.
	private record Sight(long at, boolean ok) { }
	private static final java.util.Map<Integer, Sight> SIGHT = new java.util.HashMap<>();

	/** Видно ли моба от камеры (хотя бы частично). */
	public static boolean inSight(Entity ent) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return false;
		long now = System.currentTimeMillis();
		Sight s = SIGHT.get(ent.getId());
		if (s != null && now - s.at() < 200) return s.ok();
		Vec3 eye = mc.gameRenderer.getMainCamera().position();
		boolean ok = ent.position().distanceToSqr(eye) <= MAX_DIST * MAX_DIST && visible(mc.level, eye, ent);
		if (SIGHT.size() > 512) SIGHT.clear();
		SIGHT.put(ent.getId(), new Sight(now, ok));
		return ok;
	}

	// ===== Мир: рамка на блоке под дропом =====


	/** Рисует боксы мобов-стоек и рамки под floor drop. Зовётся из миксина (фаза debug-гизмо). */
	public static void renderWorld(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		// Мобы, чья модель — стойка с предметом: контур им не подходит, красим боксом
		// по верхушке стойки, где и сидит предмет. Правило «только если видно» то же
		// самое, что у контура: сквозь стену бокс не рисуем.
		if (enabled()) {
			VertexConsumer mv = buf.getBuffer(RenderTypes.debugQuads());
			PoseStack.Pose mp = ps.last();
			boolean any = false;
			for (Entity ent : mc.level.entitiesForRendering()) {
				MobDef d = outlineDef(ent);
				if (d == null || !boxed(ent, d)) continue;
				if (ent.position().distanceToSqr(cam) > MAX_DIST * MAX_DIST || !inSight(ent)) continue;
				AABB bb = ent.getBoundingBox();
				double cx = bb.getCenter().x, cz = bb.getCenter().z;
				// У стойки предмет сидит на голове — берём верхушку; у прочих весь хитбокс.
				double top = bb.maxY + 0.1;
				double bot = isModelStand(ent) ? Math.max(bb.minY, top - 1.3) : bb.minY - 0.1;
				// Голый маркер (Invisibug): хитбокса нет, из него вышел бы блин в один
				// пиксель — рисуем куб в его точке, там же, где идут частицы. Стойки-маркеры
				// с предметом сюда не доходят: у них подсветка идёт по телу (см. isMarkerStand).
				if (bb.getYsize() < 0.05) { bot = bb.minY - 0.4; top = bb.minY + 0.6; }
				int col = RynConfig.color("mob." + d.key(), d.color());
				Waypoints.filledBox(mv, mp,
						(float) (cx - 0.55 - cam.x), (float) (bot - cam.y), (float) (cz - 0.55 - cam.z),
						(float) (cx + 0.55 - cam.x), (float) (top - cam.y), (float) (cz + 0.55 - cam.z),
						(col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, 110);
				any = true;
			}
			if (any) buf.endBatch();
		}


		if (!RynConfig.floorDropHighlight) return;

		// Блок под дропом закрашиваем целиком: рамка из рёбер терялась на траве и камне.
		VertexConsumer vc = buf.getBuffer(RenderTypes.debugQuads());
		PoseStack.Pose e = ps.last();

		// Floor drop = КЛАСТЕР item_display у земли: на один дроп их три (подтверждено
		// #scan). Считаем дисплеи по блокам и берём только те блоки, где их больше
		// одного: брошенная капсула — одиночный дисплей, и подсвечивать её не надо.
		java.util.Map<Long, Integer> perBlock = new java.util.HashMap<>();
		for (Entity ent : mc.level.entitiesForRendering()) {
			if (!(ent instanceof net.minecraft.world.entity.Display.ItemDisplay)) continue;
			if (!isFloorDrop(mc.level, ent)) continue;
			Vec3 c = ent.position();
			if (c.distanceTo(cam) > MAX_DIST) continue;
			perBlock.merge(net.minecraft.core.BlockPos.asLong(
					(int) Math.floor(c.x), (int) Math.floor(c.y - 0.1), (int) Math.floor(c.z)), 1, Integer::sum);
		}
		for (var en : perBlock.entrySet()) {
			if (en.getValue() < 2) continue;
			net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.of(en.getKey());
			double bx = bp.getX(), by = bp.getY(), bz = bp.getZ();
			int fc = RynConfig.color("floordrop", 0xFFFFEE55);
			// Грань точно по границе блока мигает (z-fighting с самим блоком) — раздуваем.
			double g = 0.02;
			Waypoints.filledBox(vc, e,
					(float) (bx - g - cam.x), (float) (by - g - cam.y), (float) (bz - g - cam.z),
					(float) (bx + 1 + g - cam.x), (float) (by + 1 + g - cam.y), (float) (bz + 1 + g - cam.z),
					(fc >> 16) & 0xFF, (fc >> 8) & 0xFF, fc & 0xFF, 110);
		}
		buf.endBatch();
	}

	/**
	 * Это правда лежащий на земле дроп, а не картинка моба.
	 *
	 * item_display у Hypixel — не только выпавший шард: тем же дисплеем рисуется
	 * серверная модель криттера (у Flitter из-за этого рядом с мобом летала жёлтая
	 * рамка). Отличаем по трём признакам: дроп лежит НА блоке (под картинкой летящего
	 * моба воздух), ни на ком не едет и рядом с ним нет живого моба.
	 */
	private static boolean isFloorDrop(Level level, Entity ent) {
		if (ent.getVehicle() != null) return false;
		Vec3 c = ent.position();
		net.minecraft.core.BlockPos under = net.minecraft.core.BlockPos.containing(c.x, c.y - 0.1, c.z);
		if (level.getBlockState(under).isAir()) return false;
		AABB near = ent.getBoundingBox().inflate(1.0, 1.5, 1.0);
		for (Entity o : level.getEntities(ent, near))
			if (o instanceof net.minecraft.world.entity.LivingEntity
					&& !(o instanceof net.minecraft.world.entity.decoration.ArmorStand)
					&& !(o instanceof net.minecraft.world.entity.player.Player)) return false;
		return true;
	}

	/**
	 * Видно ли моба от камеры. Бьём в несколько точек хитбокса: если видно хотя бы
	 * кусок (торчит из листвы, выглядывает из-за угла) — считаем, что видно.
	 * Полностью закрытый стеной моб не светится: это и был бы воллхак.
	 */
	private static boolean visible(Level level, Vec3 eye, Entity ent) {
		AABB bb = ent.getBoundingBox();
		Vec3 c = bb.getCenter();
		double ix = Math.min(bb.getXsize(), 1.0) * 0.35, iz = Math.min(bb.getZsize(), 1.0) * 0.35;
		Vec3[] pts = {
				c,
				new Vec3(c.x, bb.maxY - 0.05, c.z), new Vec3(c.x, bb.minY + 0.05, c.z),
				new Vec3(c.x - ix, c.y, c.z - iz), new Vec3(c.x + ix, c.y, c.z + iz),
				new Vec3(c.x - ix, c.y, c.z + iz), new Vec3(c.x + ix, c.y, c.z - iz) };
		// Точки чуть подаём НА камеру: у прижатых к стене сущностей (плоский display,
		// шалкер в стене) центр лежит ровно в блоке, и луч утыкался в саму стену —
		// моб на виду считался закрытым. Сдвиг нарочно мелкий: за стеной до преграды
		// целый блок, так что спрятанного он не выдаёт.
		Vec3 toEye = eye.subtract(c);
		Vec3 nudge = toEye.lengthSqr() > 1.0E-4 ? toEye.normalize().scale(0.15) : Vec3.ZERO;
		for (Vec3 p : pts) if (clear(level, eye, p.add(nudge), ent)) return true;
		return false;
	}

	private static boolean clear(Level level, Vec3 eye, Vec3 target, Entity ent) {
		if (paintingBlocks(level, eye, target, ent)) return false;
		BlockHitResult hit = level.clip(new ClipContext(eye, target,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ent));
		if (hit.getType() == HitResult.Type.MISS) return true;
		// Блок дальше цели — значит цель перед стеной, видим.
		return hit.getLocation().distanceToSqr(eye) >= target.distanceToSqr(eye) - 0.25;
	}

	// Снимок картин вокруг игрока, обновляется в тике.
	private static final java.util.List<AABB> PAINTINGS = new java.util.ArrayList<>();
	private static long lastPaintScan = 0;

	/**
	 * Собирает картины вокруг игрока. Зовётся из тика — и это принципиально.
	 *
	 * Раньше их искали прямо в проверке видимости, то есть в фазе отрисовки, через
	 * getEntitiesOfClass. Эта выборка строит в секции индекс по типу сущности, то есть
	 * МЕНЯЕТ общую структуру, пока рендер её обходит: игра падала с
	 * ArrayIndexOutOfBounds в ClassInstanceMultiMap (краш 15.08.2026, на шалкере).
	 * В фазе отрисовки мир не опрашиваем — берём готовый снимок.
	 */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		long now = System.currentTimeMillis();
		if (now - lastPaintScan < 500) return;
		lastPaintScan = now;
		PAINTINGS.clear();
		if (!RynConfig.mobHighlightEnabled) return;
		AABB near = mc.player.getBoundingBox().inflate(64);
		for (Entity e : mc.level.getEntities(mc.player, near))
			if (e instanceof net.minecraft.world.entity.decoration.painting.Painting)
				PAINTINGS.add(e.getBoundingBox());
	}

	/**
	 * Картина на пути — это тоже преграда.
	 *
	 * Луч видимости проверяет только БЛОКИ, а картина — сущность. Hideonwall сидит в
	 * нише, прикрытой картиной: блоков между ним и камерой нет, и он спокойно светился
	 * сквозь неё. Смотрим, не перекрывает ли картина отрезок «глаз → цель».
	 */
	private static boolean paintingBlocks(Level level, Vec3 eye, Vec3 target, Entity ent) {
		for (AABB bb : PAINTINGS) {
			if (bb.clip(eye, target).isPresent()) return true;
		}
		return false;
	}

}
