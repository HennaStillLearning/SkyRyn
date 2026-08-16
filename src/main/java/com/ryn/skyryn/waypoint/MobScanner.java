package com.ryn.skyryn.waypoint;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;

/**
 * Сканер мобов — как сканер floor drop, только по существам.
 *
 * Наводишься на моба (или просто стоишь рядом) и пишешь {@code /srmob <имя>}: мод
 * смотрит, что там за существо, запоминает его приметы (имя над мобом и тип) и
 * сразу включает ему подсветку контуром. Так можно добавить моба, которого в списке
 * ещё нет, не трогая код.
 *
 * Кастомный моб сервера — это обычно НЕ одна сущность. Ванильное тело (у Flitter это
 * летучая мышь) часто невидимо и служит только хитбоксом, картинку рисует отдельный
 * display, имя висит стойкой. Поэтому сканер печатает всю связку разом, помечая, кто
 * невидим и кто на ком едет: по этому списку видно, что именно обводить контуром.
 *
 * Команды:
 *   /srmob auto           — автосбор: ходишь, каждый НОВЫЙ моб сам пишется в
 *                           config/skyryn-scan.txt
 *   /srmob                — что за моб под прицелом (ничего не сохраняет)
 *   /srmob &lt;имя&gt;    — запомнить моба под прицелом и подсветить его
 *   /srmob list           — список своих мобов
 *   /srmob remove &lt;имя&gt; — убрать своего моба
 */
public class MobScanner {

	private static final String PREFIX = "§5§l[§dSkyRyn§5§l]§r ";
	/** Дальность прицела и радиус поиска ближайшего моба. */
	private static final double REACH = 24.0, NEAR = 8.0;

	public static void register() {
		ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
			String c = command.trim();
			if (!c.equals("srmob") && !c.startsWith("srmob ")) return true;
			String arg = c.length() > 5 ? c.substring(5).trim() : "";
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> handle(mc, arg));
			return false;
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(MobScanner::autoTick);
	}

	private static void handle(Minecraft mc, String arg) {
		if (arg.equalsIgnoreCase("auto")) { toggleAuto(mc); return; }
		if (arg.equalsIgnoreCase("list")) { list(mc); return; }
		if (arg.toLowerCase().startsWith("remove")) {
			String k = arg.substring(6).trim().toLowerCase();
			boolean removed = RynConfig.removeCustomMob(k);
			RynConfig.setHighlightMob(k, false);
			ConfigManager.save();
			say(mc, removed ? Lang.tr("Removed: ", "Убран: ") + k : Lang.tr("§cNo such mob: ", "§cНет такого моба: ") + k);
			return;
		}

		Entity target = target(mc);
		if (target == null) {
			say(mc, Lang.tr("§cNo mob in sight. Look at it or stand closer.",
					"§cПод прицелом никого. Наведись на моба или подойди ближе."));
			return;
		}

		String type = EntityType.getKey(target.getType()).toString();
		String own = target.getCustomName() != null ? strip(target.getCustomName().getString()) : "";
		String near = nearbyName(target);
		String name = !own.isBlank() ? own : near;

		// Отчёт — всегда, даже без сохранения: видно, по чему моба вообще можно узнать.
		say(mc, Lang.tr("Scanned: ", "Отсканирован: ") + "§f" + (name.isBlank() ? Lang.tr("(no name)", "(без имени)") : name));
		say(mc, "§7  " + Lang.tr("type: ", "тип: ") + "§f" + type
				+ (target.isInvisible() ? " §8(" + Lang.tr("invisible", "невидим") + ")" : ""));
		say(mc, "§7  " + Lang.tr("name on mob: ", "имя на мобе: ") + "§f" + (own.isBlank() ? "—" : own));
		say(mc, "§7  " + Lang.tr("name nearby: ", "имя рядом: ") + "§f" + (near.isBlank() ? "—" : near));
		say(mc, "§7  " + Lang.tr("hitbox: ", "хитбокс: ") + "§f"
				+ String.format(java.util.Locale.US, "%.2f × %.2f", target.getBoundingBox().getXsize(), target.getBoundingBox().getYsize()));
		cluster(mc, target);

		if (arg.isBlank()) {
			say(mc, "§7" + Lang.tr("Save it: ", "Сохранить: ") + "§d/srmob <" + Lang.tr("name", "имя") + ">");
			return;
		}

		String key = arg.toLowerCase().replace(' ', '_');
		// Основная примета — тип существа: у серверных мобов имени часто нет вовсе.
		// Имя, если оно есть, уточняет. По зонам не привязываемся.
		String namePart = pickNamePart(name);
		int color = RynConfig.color("mob." + key, 0xFFFFD24A);
		RynConfig.putCustomMob(new RynConfig.CustomMob(key, "§f" + cap(arg), type, namePart, color));
		RynConfig.setHighlightMob(key, true);
		RynConfig.mobHighlightEnabled = true;
		ConfigManager.save();
		say(mc, Lang.tr("Saved and highlighted: §f", "Сохранён и подсвечен: §f") + cap(arg));
		say(mc, "§7  " + Lang.tr("looks for: ", "ищем по: ") + "§f" + type
				+ (namePart.isBlank() ? "" : Lang.tr(" and name «", " и имени «") + namePart + "»"));
		if (namePart.isBlank())
			say(mc, "§8  " + Lang.tr("No name to go by, so every mob of this type will be outlined.",
					"Имени нет, так что обведётся любой моб этого типа."));
	}

	// ===== Автосбор в файл =====
	// Разбирать мобов по одному через чат неудобно, их десятки. С автосбором
	// достаточно пройтись по биомам: каждый НОВЫЙ моб сам ложится строкой в
	// config/skyryn-scan.txt, и дальше файл читается целиком.

	private static boolean auto = false;
	private static long lastAuto = 0;
	/** Уже записанные приметы «тип|имя» — чтобы файл не рос от одних и тех же мобов. */
	private static final java.util.Set<String> seen = new java.util.LinkedHashSet<>();

	private static java.nio.file.Path scanFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("skyryn-scan.txt");
	}

	private static void toggleAuto(Minecraft mc) {
		auto = !auto;
		if (auto) {
			say(mc, Lang.tr("§aAuto-scan ON.", "§aАвтосбор ВКЛ.") + " "
					+ Lang.tr("Just walk around — every new mob goes into the file.",
							"Просто ходи — каждый новый моб сам попадёт в файл."));
			say(mc, "§7  " + scanFile());
			append("--- " + java.time.LocalDateTime.now().withNano(0) + " ---");
		} else {
			say(mc, Lang.tr("§eAuto-scan OFF. Written: ", "§eАвтосбор ВЫКЛ. Записано: ") + seen.size());
		}
	}

	/** Раз в секунду смотрим мобов вокруг и дописываем в файл тех, кого ещё не видели. */
	private static void autoTick(Minecraft mc) {
		if (!auto || mc.level == null || mc.player == null) return;
		if (System.currentTimeMillis() - lastAuto < 1000) return;
		lastAuto = System.currentTimeMillis();
		AABB area = mc.player.getBoundingBox().inflate(32.0);
		for (Entity e : mc.level.getEntities(mc.player, area)) {
			if (!interesting(e)) continue;
			String type = EntityType.getKey(e.getType()).toString();
			String own = e.getCustomName() != null ? strip(e.getCustomName().getString()) : "";
			String near = nearbyName(e);
			String name = !own.isBlank() ? own : near;
			// Имя с уровнем и хп у каждого моба своё — для приметы чистим цифры,
			// иначе один и тот же вид писался бы десятками строк.
			String sig = type + "|" + name.replaceAll("[0-9,/]+", "").trim();
			if (!seen.add(sig)) continue;
			append(describe(e, type, own, near));
			say(mc, "§7+ §f" + (name.isBlank() ? type : name));
		}
	}

	/** Одна строка файла: всё, по чему моба можно опознать и решить, что обводить. */
	private static String describe(Entity e, String type, String own, String near) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(java.util.Locale.US, "%-28s", type));
		sb.append(" | hitbox ").append(String.format(java.util.Locale.US, "%.2fx%.2f",
				e.getBoundingBox().getXsize(), e.getBoundingBox().getYsize()));
		if (e.isInvisible()) sb.append(" | INVISIBLE");
		sb.append(" | name: ").append(own.isBlank() ? (near.isBlank() ? "-" : near + " (nearby)") : own);
		if (e.getVehicle() != null) sb.append(" | rides ").append(EntityType.getKey(e.getVehicle().getType()));
		java.util.List<String> riders = new java.util.ArrayList<>();
		for (Entity p : e.getPassengers()) riders.add(EntityType.getKey(p.getType()).toString());
		for (Entity o : e.level().getEntities(e, e.getBoundingBox().inflate(2.5, 3.0, 2.5))) {
			if (o instanceof net.minecraft.world.entity.Display) riders.add(EntityType.getKey(o.getType()) + "(near)");
			else if (MobHighlight.isModelStand(o)) riders.add("armor_stand+" + wornItem(o) + "(near)");
		}
		if (!riders.isEmpty()) sb.append(" | carriers: ").append(String.join(", ", riders));
		sb.append(" | at ").append(SkyBlockCheck.currentArea());
		return sb.toString();
	}

	private static void append(String line) {
		try {
			java.nio.file.Files.writeString(scanFile(), line + System.lineSeparator(),
					java.nio.charset.StandardCharsets.UTF_8,
					java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (Exception ex) {
			com.ryn.skyryn.config.SkyLog.d("Не смог записать скан: " + ex);
		}
	}

	/** Размер хитбокса одной строкой: «0.50×1.98». */
	private static String box(Entity e) {
		var bb = e.getBoundingBox();
		return String.format(java.util.Locale.US, "%.2f×%.2f", bb.getXsize(), bb.getYsize());
	}

	/**
	 * Вся связка вокруг цели: кто на ком едет, кто невидим, кто дисплей. Именно
	 * отсюда видно, что обводить: у невидимого тела картинку несёт display.
	 */
	private static void cluster(Minecraft mc, Entity target) {
		java.util.LinkedHashSet<Entity> set = new java.util.LinkedHashSet<>();
		if (target.getVehicle() != null) set.add(target.getVehicle());
		set.addAll(target.getPassengers());
		for (Entity o : target.level().getEntities(target, target.getBoundingBox().inflate(2.0, 3.0, 2.0)))
			set.add(o);
		if (set.isEmpty()) {
			say(mc, "§7  " + Lang.tr("nothing else nearby", "рядом больше никого"));
			return;
		}
		say(mc, "§7  " + Lang.tr("cluster (", "связка (") + set.size() + "):");
		// Носители картинки — вперёд. У Shyworm в связке 14 сущностей, и обрезание
		// списка по восьмой съедало ровно то, ради чего скан и делается.
		java.util.List<Entity> order = new java.util.ArrayList<>(set);
		order.sort((a, b) -> Integer.compare(rank(b), rank(a)));
		int shown = 0;
		for (Entity o : order) {
			if (++shown > 20) { say(mc, "§8    … +" + (order.size() - 20)); break; }
			String nm = o.getCustomName() != null ? strip(o.getCustomName().getString()) : "";
			StringBuilder sb = new StringBuilder("§8    §7" + EntityType.getKey(o.getType()));
			if (!nm.isBlank()) sb.append(" §f«").append(nm).append("§f»");
			if (o.isInvisible()) sb.append(" §8(").append(Lang.tr("invisible", "невидим")).append(")");
			if (o.getVehicle() == target) sb.append(" §8→ ").append(Lang.tr("rides the mob", "едет на мобе"));
			else if (target.getVehicle() == o) sb.append(" §8→ ").append(Lang.tr("the mob rides it", "моб едет на нём"));
			if (o instanceof net.minecraft.world.entity.Display)
				sb.append(" §e← ").append(Lang.tr("the picture", "картинка"));
			else if (MobHighlight.isModelStand(o))
				sb.append(" §e← ").append(Lang.tr("stand with an item", "стойка с предметом"));
			// Хитбокс и высота относительно моба: по ним считается, куда рисовать бокс.
			// Предмет на стойке сидит на голове, то есть ВЫШЕ её точки, и насколько
			// выше — зависит от того, обычная стойка, маленькая или маркер.
			sb.append(" §8").append(box(o))
					.append(" Δy ").append(String.format(java.util.Locale.US, "%+.2f",
							o.getBoundingBox().minY - target.getBoundingBox().minY));
			if (o instanceof net.minecraft.world.entity.decoration.ArmorStand st) {
				if (st.isSmall()) sb.append(" small");
				if (st.isMarker()) sb.append(" marker");
			}
			say(mc, sb.toString());
		}
	}


	/**
	 * Что надето на стойке-модели. Именно предмет — точная примета такого моба:
	 * безымянных невидимых стоек с предметом на Hypixel полно, а вот конкретная
	 * голова или предмет принадлежит одному мобу (нужно для Invisibug и подобных).
	 */
	private static String wornItem(Entity e) {
		if (!(e instanceof net.minecraft.world.entity.LivingEntity le)) return "item";
		for (net.minecraft.world.entity.EquipmentSlot sl : net.minecraft.world.entity.EquipmentSlot.values()) {
			var st = le.getItemBySlot(sl);
			if (st == null || st.isEmpty()) continue;
			return strip(st.getHoverName().getString()) + "/" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem());
		}
		return "item";
	}

	/** Насколько сущность интересна в связке: сначала картинки, потом всё остальное. */
	private static int rank(Entity e) {
		if (e instanceof net.minecraft.world.entity.Display) return 3;
		if (MobHighlight.isModelStand(e)) return 2;
		if (e instanceof net.minecraft.world.entity.decoration.painting.Painting) return 1;
		return 0;
	}

	/**
	 * Ключевое слово имени: берём самое длинное слово (уровни, редкости и значки
	 * отсекаются сами — они короткие или из цифр).
	 */
	private static String pickNamePart(String name) {
		String best = "";
		for (String w : strip(name).toLowerCase().split("[^a-zа-я]+"))
			if (w.length() > best.length()) best = w;
		return best.length() >= 4 ? best : "";
	}

	/** Моб под прицелом; если прицел пуст — ближайший к игроку. */
	private static Entity target(Minecraft mc) {
		if (mc.player == null || mc.level == null) return null;
		Vec3 eye = mc.player.getEyePosition();
		Vec3 dir = mc.player.getViewVector(1f);
		Entity best = null;
		double bestT = Double.MAX_VALUE;
		AABB search = mc.player.getBoundingBox().inflate(REACH);
		for (Entity e : mc.level.getEntities(mc.player, search)) {
			if (!scannable(e)) continue;
			AABB bb = e.getBoundingBox().inflate(0.35);
			var hit = bb.clip(eye, eye.add(dir.scale(REACH)));
			if (hit.isEmpty()) continue;
			double t = hit.get().distanceToSqr(eye);
			if (t < bestT) { bestT = t; best = e; }
		}
		if (best != null) return best;
		// Прицел мимо — берём ближайшего.
		double bd = NEAR * NEAR;
		for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(NEAR))) {
			if (!scannable(e)) continue;
			double d = e.position().distanceToSqr(mc.player.position());
			if (d < bd) { bd = d; best = e; }
		}
		return best;
	}


	/**
	 * Что вообще может попасть под ручной /srmob. Фильтр нарочно шире, чем у
	 * автосбора: ручной скан — это диагностика, и отбрасывать пустые стойки нельзя.
	 * Invisibug как раз ими и оказался — команда его «не видела», хотя он был рядом.
	 */
	private static boolean scannable(Entity e) {
		return !(e instanceof net.minecraft.world.entity.player.Player)
				&& !(e instanceof net.minecraft.world.entity.item.ItemEntity);
	}

	/**
	 * Пустые стойки и дисплеи — это подписи, а не мобы. НО стойка с надетым предметом
	 * — это модель моба (перекати-поле Driftling и подобное): её пропускать нельзя,
	 * иначе такой моб в скан вообще не попадёт.
	 */
	private static boolean interesting(Entity e) {
		if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) return MobHighlight.isModelStand(e);
		return !(e instanceof net.minecraft.world.entity.Display)
				&& !(e instanceof net.minecraft.world.entity.player.Player)
				&& !(e instanceof net.minecraft.world.entity.item.ItemEntity);
	}

	/** Имя с ближайшей стойки-нейтмега над мобом. */
	private static String nearbyName(Entity target) {
		AABB area = target.getBoundingBox().inflate(2.0, 3.0, 2.0);
		String best = "";
		double bd = Double.MAX_VALUE;
		for (Entity o : target.level().getEntities(target, area)) {
			var n = o.getCustomName();
			if (n == null) continue;
			double d = o.position().distanceToSqr(target.position());
			if (d < bd) { bd = d; best = strip(n.getString()); }
		}
		return best;
	}

	private static void list(Minecraft mc) {
		if (RynConfig.customMobs.isEmpty()) {
			say(mc, Lang.tr("No scanned mobs yet. Aim at one and use §d/srmob <name>",
					"Своих мобов пока нет. Наведись и напиши §d/srmob <имя>"));
			return;
		}
		say(mc, Lang.tr("Scanned mobs:", "Свои мобы:"));
		for (RynConfig.CustomMob c : RynConfig.customMobs)
			say(mc, "§7  " + c.key() + " §8— §r" + c.label() + " §8["
					+ (c.namePart().isBlank() ? c.entityType() : c.namePart()) + "] "
					+ (RynConfig.hasHighlightMob(c.key()) ? "§a✔" : "§8✘"));
	}

	private static void say(Minecraft mc, String msg) {
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal(PREFIX + msg));
	}
	private static String strip(String s) { return s == null ? "" : s.replaceAll("§.", "").trim(); }
	private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
