package com.ryn.skyryn.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.waypoint.SkyBlockCheck;

public class CritterTimer {
	private static final Pattern LATHER =
			Pattern.compile("lathered the (\\w+) tree with honeycomb", Pattern.CASE_INSENSITIVE);
	private static final Pattern CRITTER_IN =
			Pattern.compile("critter in:\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPAWN =
			Pattern.compile("honey tree!.*\\bhas appeared|\\bfell from the tree", Pattern.CASE_INSENSITIVE);
	private static final Pattern REFILL_IN =
			Pattern.compile("refill in:\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?", Pattern.CASE_INSENSITIVE);
	private static long hiveEndMs = 0;

	private static final long UNSYNCED_LIFE = 600_000;
	private static final long SPAWNED_LIFE = 3 * 3600_000L;

	private static final class Timer {
		final String key;
		String tree;
		BlockPos pos;
		long endMs;
		final long createdAt = now();
		boolean alerted;
		Timer(String key, String tree) { this.key = key; this.tree = tree; }
	}

	private static final Map<String, Timer> timers = new LinkedHashMap<>();
	private static final Map<String, String> knownTrees = new java.util.HashMap<>();
	private static String pendingTree = null;
	private static Vec3 pendingPos = null;
	private static long pendingAt = 0;
	private static long flashAt = -100000;
	private static String flashTree = "";
	private static long lastScan = 0;

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!RynConfig.critterTimer || !SkyBlockCheck.onSkyBlock()) return;
			String s = strip(message.getString());
			if (s == null) return;
			if (overlay) { readTime(s, null, "action bar"); return; }
			Matcher m = LATHER.matcher(s);
			if (m.find()) {
				pendingTree = cap(m.group(1).toLowerCase());
				var pl = Minecraft.getInstance().player;
				pendingPos = pl != null ? pl.position() : null;
				pendingAt = now();
				return;
			}
			if (SPAWN.matcher(s).find()) { removeNearestExpired(); return; }
			readTime(s, null, "чат");
		});

		ClientTickEvents.END_CLIENT_TICK.register(CritterTimer::scanHolograms);

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyryn", "critter-timer"),
				(ctx, tick) -> renderHud(ctx));
	}

	public static void onSubtitle(String raw) {
		if (!RynConfig.critterTimer) return;
		readTime(strip(raw), null, "титр");
	}

	private static Timer readTime(String s, BlockPos pos, String source) {
		if (s == null || s.isEmpty()) return null;
		Matcher m = CRITTER_IN.matcher(s);
		if (!m.find()) return null;
		int mm = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
		int ss = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
		long secs = mm * 60L + ss;
		if (secs <= 0) return null;

		Timer t = pos != null ? timerAt(pos) : pick(secs);
		if (t == null) {
			t = new Timer("loose", pendingTree != null ? pendingTree : "");
			timers.put(t.key, t);
		}
		long end = now() + secs * 1000;
		if (Math.abs(end - t.endMs) > 2000) { t.endMs = end; t.alerted = false; }
		logOnce(source, secs, t.tree);
		return t;
	}

	private static void readHive(String s) {
		Matcher m = REFILL_IN.matcher(s);
		if (!m.find()) return;
		int mm = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
		int ss = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
		long secs = mm * 60L + ss;
		if (secs <= 0) return;
		long end = now() + secs * 1000;
		if (end > hiveEndMs) hiveEndMs = end;
	}

	private static Timer timerAt(BlockPos pos) {
		for (Timer t : timers.values())
			if (t.pos != null && !t.key.equals("loose") && t.pos.distSqr(pos) <= 9) {
				t.pos = pos;
				if (t.tree.isEmpty()) t.tree = nameFor(pos);
				return t;
			}
		String key = key(pos);
		Timer t = timers.get(key);
		if (t == null) {
			t = new Timer(key, nameFor(pos));
			timers.put(key, t);
			timers.remove("loose");
		} else if (t.tree.isEmpty()) {
			t.tree = nameFor(pos);
		}
		t.pos = pos;
		return t;
	}

	private static String nameFor(BlockPos pos) {
		String k = knownTrees.get(key(pos));
		return k != null ? k : "";
	}

	private static void assignPending(java.util.List<Timer> seen) {
		if (pendingTree == null) return;
		if (pendingPos == null || now() - pendingAt > 30_000) { clearPending(); return; }
		Timer best = null; double bd = 16 * 16;
		for (Timer t : seen) {
			if (!t.tree.isEmpty() || t.pos == null) continue;
			double d = pendingPos.distanceToSqr(Vec3.atCenterOf(t.pos));
			if (d < bd) { bd = d; best = t; }
		}
		if (best == null) return;
		best.tree = pendingTree;
		knownTrees.put(best.key, pendingTree);
		clearPending();
	}

	private static void clearPending() { pendingTree = null; pendingPos = null; }

	private static String key(BlockPos pos) { return pos.getX() + ":" + pos.getY() + ":" + pos.getZ(); }

	private static void scanHolograms(Minecraft mc) {
		if (!RynConfig.critterTimer || mc.level == null || mc.player == null) return;
		if (now() - lastScan < 500) return;
		lastScan = now();
		if (!SkyBlockCheck.onSkyBlock()) return;
		timers.values().removeIf(t -> t.endMs > 0
				? now() > t.endMs + SPAWNED_LIFE
				: now() - t.createdAt > UNSYNCED_LIFE);
		Vec3 p = mc.player.position();
		java.util.List<Timer> seen = new java.util.ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			var n = e.getCustomName();
			if (n == null) continue;
			if (e.position().distanceToSqr(p) > 60 * 60) continue;
			String s = strip(n.getString());
			if (s == null) continue;
			if (RynConfig.flag("hive.timer", true) && s.toLowerCase().contains("refill in")) { readHive(s); continue; }
			if (!s.toLowerCase().contains("critter in")) continue;
			Timer t = readTime(s, e.blockPosition(), "голограмма");
			if (t != null) seen.add(t);
		}
		assignPending(seen);
	}

	private static Timer pick(long secs) {
		for (Timer t : timers.values()) if (t.endMs == 0) return t;
		Timer best = null; long bd = Long.MAX_VALUE;
		for (Timer t : timers.values()) {
			long d = Math.abs((t.endMs - now()) / 1000 - secs);
			if (d < bd) { bd = d; best = t; }
		}
		return (best != null && bd <= 120) ? best : null;
	}

	private static void removeNearestExpired() {
		Minecraft mc = Minecraft.getInstance();
		Vec3 p = mc.player != null ? mc.player.position() : null;
		Timer near = null; double nd = Double.MAX_VALUE;
		Timer old = null;
		for (Timer t : timers.values()) {
			if (t.endMs == 0 || t.endMs > now()) continue;
			if (old == null || t.endMs < old.endMs) old = t;
			if (p == null || t.pos == null) continue;
			double d = p.distanceToSqr(Vec3.atCenterOf(t.pos));
			if (d < nd) { nd = d; near = t; }
		}
		Timer best = near != null ? near : old;
		if (best != null) timers.remove(best.key);
	}

	private static long lastLog = 0;
	private static void logOnce(String source, long secs, String tree) {
		if (now() - lastLog < 5000) return;
		lastLog = now();
		com.ryn.skyryn.config.SkyLog.d("Critter: " + secs + "с (" + tree + ") источник — " + source);
	}

	private static final double[][] HONEY_TREES = {
			{ -618, 99, 233 }, { -549, 111, 298 }, { -535, 110, 275 }, { -512, 108, 259 },
			{ -605, 115, 9 }, { -661, 115, -79 }, { -731, 120, 38 },
			{ -611, 90, 32 }, { -610, 99, 94 }, { -717, 100, 38 },
	};

	private static String treeName(int i) { return i < 4 ? "Helix" : i < 7 ? "Fig" : "Mangrove"; }

	private static final org.joml.Matrix4f VP = new org.joml.Matrix4f();
	private static Vec3 camPos = Vec3.ZERO;
	private static boolean haveFrame = false;
	private record TreeLabel(double x, double y, double z, String text) { }
	private static final java.util.List<TreeLabel> treeLabels = new java.util.ArrayList<>();

	public static void captureFrame(Vec3 cam) {
		Minecraft.getInstance().gameRenderer.getMainCamera().getViewRotationProjectionMatrix(VP);
		camPos = cam;
		haveFrame = true;
	}

	private static boolean holdingHoneyPot(Minecraft mc) {
		for (var st : new net.minecraft.world.item.ItemStack[]{
				mc.player.getMainHandItem(), mc.player.getOffhandItem() }) {
			if (st != null && !st.isEmpty()
					&& st.getHoverName().getString().toLowerCase().contains("pot of honeycomb")) return true;
		}
		return false;
	}

	public static void renderWorld(com.mojang.blaze3d.vertex.PoseStack ps,
								   net.minecraft.client.renderer.MultiBufferSource.BufferSource buf, Vec3 cam) {
		treeLabels.clear();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		int mode = RynConfig.getInt("trees.mode", 0);
		if (mode == 0 || (mode == 1 && !holdingHoneyPot(mc))) return;
		String isl = SkyBlockCheck.currentIsland();
		if (!"torrhus".equals(isl) && !"galatea".equals(isl)) return;

		var vc = buf.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.lines());
		var e = ps.last();
		int col = RynConfig.color("mk.tree", 0xFFE0A040);
		int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
		for (int i = 0; i < HONEY_TREES.length; i++) {
			double[] c = HONEY_TREES[i];
			double x = c[0] + 0.5, y = c[1], z = c[2] + 0.5;
			if (mc.player.distanceToSqr(x, y, z) > 200 * 200) continue;
			float px = (float) (x - cam.x), py = (float) (y - cam.y), pz = (float) (z - cam.z);
			vc.addVertex(e, px, py, pz).setNormal(e, 0, 1, 0).setColor(r, g, b, 255).setLineWidth(12f);
			vc.addVertex(e, px, py + 2.5f, pz).setNormal(e, 0, 1, 0).setColor(r, g, b, 255).setLineWidth(12f);
			treeLabels.add(new TreeLabel(x, y + 2.9, z, "§6" + treeName(i) + " Tree"));
		}
		buf.endBatch();
	}

	private static void drawTreeLabels(GuiGraphicsExtractor ctx, Minecraft mc, Font font) {
		if (!haveFrame || treeLabels.isEmpty()) return;
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		for (TreeLabel l : treeLabels) {
			var clip = VP.transform(new org.joml.Vector4f(
					(float) (l.x() - camPos.x), (float) (l.y() - camPos.y), (float) (l.z() - camPos.z), 1f));
			if (clip.w <= 0.05f) continue;
			float nx = clip.x / clip.w, ny = clip.y / clip.w;
			if (nx < -1.1f || nx > 1.1f || ny < -1.1f || ny > 1.1f) continue;
			int px = Math.round((nx * 0.5f + 0.5f) * sw), py = Math.round((1f - (ny * 0.5f + 0.5f)) * sh);
			ctx.text(font, l.text(), px - font.width(l.text()) / 2, py, 0xFFFFFFFF, true);
		}
	}

	private static void renderHud(GuiGraphicsExtractor ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
		drawTreeLabels(ctx, mc, mc.font);
		boolean hive = RynConfig.flag("hive.timer", true) && hiveEndMs > now();
		if (!RynConfig.critterTimer || (timers.isEmpty() && !hive) || !plaqueHere()) return;
		Font font = mc.font;
		if (RynConfig.flag("critter.plaque", true)) drawPlaque(ctx, font);

		long showMs = Announce.showMs(Announce.CRITTER);
		long dt = now() - flashAt;
		if (dt >= 0 && dt < showMs) {
			int a = Math.max(24, 255 - (int) (dt * 255 / showMs));
			String msg = Announce.text(Announce.CRITTER, "{tree}" + Lang.tr(" critter in 5s!", " криттер через 5с!"))
					.replace("{tree}", flashTree);
			Announce.draw(ctx, font, Announce.CRITTER, msg, null, a);
		}
	}

	private static int plaqueW = 90, plaqueH = 30;
	public static int plaqueW() { return plaqueW; }
	public static int plaqueH() { return plaqueH; }
	public static int hudX() { return RynConfig.getInt("critter.x", 4); }
	public static int hudY() {
		int def = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2 - 20;
		return RynConfig.getInt("critter.y", def);
	}
	public static void setHudPos(int x, int y) {
		RynConfig.setInt("critter.x", x);
		RynConfig.setInt("critter.y", y);
	}
	private static float scale() { return scalePct() / 100f; }
	public static int scalePct() { return Math.max(50, Math.min(150, RynConfig.getInt("critter.scale", 100))); }
	public static void setScalePct(int v) { RynConfig.setInt("critter.scale", Math.max(50, Math.min(150, v))); }

	public static void drawPlaque(GuiGraphicsExtractor ctx, Font font) {
		java.util.List<Timer> list = new java.util.ArrayList<>(timers.values());
		list.sort((a, b) -> Long.compare(a.endMs, b.endMs));

		float s = scale();
		ctx.pose().pushMatrix();
		ctx.pose().translate(hudX(), hudY());
		ctx.pose().scale(s, s);

		int y = 0, maxW = font.width(Lang.tr("Critters:", "Криттеры:"));
		ctx.text(font, Lang.tr("§eCritters:", "§eКриттеры:"), 0, y, 0xFFFFFFFF, true);
		y += 11;
		for (Timer t : list) {
			String name = label(t);
			if (t.endMs == 0) {
				String s0 = "§7" + name + " §8— §7?";
				ctx.text(font, s0, 0, y, 0xFFFFFFFF, true);
				maxW = Math.max(maxW, font.width(name + " — ?"));
				y += 10; continue;
			}
			long rem = (t.endMs - now()) / 1000;
			if (rem <= 5 && rem >= 0 && !t.alerted) { t.alerted = true; flashAt = now(); flashTree = name; }
			int col = rem <= 0 ? 0xFF8CE04A : (rem <= 10 ? 0xFFFF5A5A : (rem <= 60 ? 0xFFFFD24A : 0xFF5FD68A));
			String time = rem <= 0 ? Lang.tr("Spawned", "Появился") : fmt(rem);
			ctx.text(font, "§f" + name + " §7— ", 0, y, 0xFFFFFFFF, true);
			ctx.text(font, time, font.width(name + " — "), y, col, true);
			maxW = Math.max(maxW, font.width(name + " — " + time));
			y += 10;
		}
		if (RynConfig.flag("hive.timer", true) && hiveEndMs > now()) {
			long rem = (hiveEndMs - now()) / 1000;
			String time = fmt(rem);
			ctx.text(font, "§6Honeyhive §7— ", 0, y, 0xFFFFFFFF, true);
			ctx.text(font, time, font.width("Honeyhive — "), y, rem <= 60 ? 0xFFFFD24A : 0xFF5FD68A, true);
			maxW = Math.max(maxW, font.width("Honeyhive — " + time));
			y += 10;
		}
		ctx.pose().popMatrix();
		plaqueW = Math.round(maxW * s);
		plaqueH = Math.round(y * s);
	}

	private static boolean plaqueHere() {
		String isl = SkyBlockCheck.currentIsland();
		return switch (RynConfig.getInt("critter.where", 3)) {
			case 0 -> "torrhus".equals(isl);
			case 1 -> "galatea".equals(isl);
			case 2 -> "torrhus".equals(isl) || "galatea".equals(isl);
			default -> true;
		};
	}

	private static String label(Timer t) { return t.tree.isEmpty() ? Lang.tr("Tree", "Дерево") : t.tree; }
	private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
	private static String fmt(long sec) { return String.format("%d:%02d", sec / 60, sec % 60); }
	private static long now() { return System.currentTimeMillis(); }
	private static String strip(String s) { return s == null ? null : s.replaceAll("§.", ""); }
}
