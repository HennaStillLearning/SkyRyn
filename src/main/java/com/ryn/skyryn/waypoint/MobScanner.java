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

public class MobScanner {
	private static final String PREFIX = "§5§l[§dSkyRyn§5§l]§r ";
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

	private static boolean auto = false;
	private static long lastAuto = 0;
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
			String sig = type + "|" + name.replaceAll("[0-9,/]+", "").trim();
			if (!seen.add(sig)) continue;
			append(describe(e, type, own, near));
			say(mc, "§7+ §f" + (name.isBlank() ? type : name));
		}
	}

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

	private static String box(Entity e) {
		var bb = e.getBoundingBox();
		return String.format(java.util.Locale.US, "%.2f×%.2f", bb.getXsize(), bb.getYsize());
	}

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

	private static String wornItem(Entity e) {
		if (!(e instanceof net.minecraft.world.entity.LivingEntity le)) return "item";
		for (net.minecraft.world.entity.EquipmentSlot sl : net.minecraft.world.entity.EquipmentSlot.values()) {
			var st = le.getItemBySlot(sl);
			if (st == null || st.isEmpty()) continue;
			return strip(st.getHoverName().getString()) + "/" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem());
		}
		return "item";
	}

	private static int rank(Entity e) {
		if (e instanceof net.minecraft.world.entity.Display) return 3;
		if (MobHighlight.isModelStand(e)) return 2;
		if (e instanceof net.minecraft.world.entity.decoration.painting.Painting) return 1;
		return 0;
	}

	private static String pickNamePart(String name) {
		String best = "";
		for (String w : strip(name).toLowerCase().split("[^a-zа-я]+"))
			if (w.length() > best.length()) best = w;
		return best.length() >= 4 ? best : "";
	}

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
		double bd = NEAR * NEAR;
		for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(NEAR))) {
			if (!scannable(e)) continue;
			double d = e.position().distanceToSqr(mc.player.position());
			if (d < bd) { bd = d; best = e; }
		}
		return best;
	}

	private static boolean scannable(Entity e) {
		return !(e instanceof net.minecraft.world.entity.player.Player)
				&& !(e instanceof net.minecraft.world.entity.item.ItemEntity);
	}

	private static boolean interesting(Entity e) {
		if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) return MobHighlight.isModelStand(e);
		return !(e instanceof net.minecraft.world.entity.Display)
				&& !(e instanceof net.minecraft.world.entity.player.Player)
				&& !(e instanceof net.minecraft.world.entity.item.ItemEntity);
	}

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
