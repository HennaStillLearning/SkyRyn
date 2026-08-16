package com.ryn.skyryn.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.waypoint.SkyBlockCheck;

public class TikiHelper {
	private static BlockPos base = null;
	private static final int[] rot = new int[3];
	private static int steps = 0;
	private static int step = 0;
	private static long lastScan = 0;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TikiHelper::tick);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyryn", "tiki-helper"),
				(ctx, tick) -> render(ctx));
	}

	private static boolean on() {
		return RynConfig.flag("tiki.on", true) && RynConfig.flag("tiki.hint", true);
	}

	private static int plaqueW = 90, plaqueH = 50;
	public static int plaqueW() { return plaqueW; }
	public static int plaqueH() { return plaqueH; }
	public static int hudX() {
		return RynConfig.getInt("tiki.x", Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 + 24);
	}
	public static int hudY() {
		return RynConfig.getInt("tiki.y", Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2 - 24);
	}
	public static void setHudPos(int x, int y) {
		RynConfig.setInt("tiki.x", x);
		RynConfig.setInt("tiki.y", y);
	}

	public static void drawSample(GuiGraphicsExtractor ctx, Font font) {
		int x = hudX(), y = hudY();
		ctx.text(font, "§5§lTiki", x, y, 0xFFFFFFFF, true);
		String[] n = { Lang.tr("top", "верх"), Lang.tr("middle", "центр"), Lang.tr("bottom", "низ") };
		int[] v = { 2, 0, 14 };
		for (int i = 0; i < 3; i++) ctx.text(font, "§7" + n[i] + " §f" + v[i], x, y + 11 + i * 10, 0xFFFFFFFF, true);
		ctx.text(font, "§a" + Lang.tr("LMB", "ЛКМ") + " §f" + n[2] + " §7×2", x, y + 41, 0xFFFFFFFF, true);
	}

	private static void tick(Minecraft mc) {
		if (!on() || mc.level == null || mc.player == null) return;
		if (System.currentTimeMillis() - lastScan < 250) return;
		lastScan = System.currentTimeMillis();
		if (!"torrhus".equals(SkyBlockCheck.currentIsland())) { base = null; return; }

		BlockPos found = nearestTotem(mc);
		if (found == null) { base = null; return; }
		int[] now = new int[3];
		for (int i = 0; i < 3; i++) {
			Integer r = rotationOf(mc.level.getBlockState(found.above(i)));
			if (r == null) { base = null; return; }
			now[i] = r;
		}
		if (found.equals(base)) learnStep(now);
		base = found;
		System.arraycopy(now, 0, rot, 0, 3);
	}

	private static BlockPos nearestTotem(Minecraft mc) {
		BlockPos me = mc.player.blockPosition();
		BlockPos best = null;
		double bd = Double.MAX_VALUE;
		for (BlockPos bp : BlockPos.betweenClosed(me.offset(-6, -4, -6), me.offset(6, 4, 6))) {
			if (!isHead(mc.level.getBlockState(bp))) continue;
			if (isHead(mc.level.getBlockState(bp.below()))) continue;
			if (!isHead(mc.level.getBlockState(bp.above())) || !isHead(mc.level.getBlockState(bp.above(2)))) continue;
			if (isHead(mc.level.getBlockState(bp.above(3)))) continue;
			double d = mc.player.distanceToSqr(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
			if (d < bd) { bd = d; best = bp.immutable(); }
		}
		return best;
	}

	private static boolean isHead(BlockState st) {
		String id = st.getBlock().toString().toLowerCase();
		return id.contains("head") || id.contains("skull");
	}

	private static Integer rotationOf(BlockState st) {
		for (Property<?> p : st.getProperties()) {
			String n = p.getName();
			if (!n.equals("rotation") && !n.equals("facing")) continue;
			Object v = st.getValue(p);
			if (v instanceof Integer i) { steps = 16; return i; }
			int idx = 0;
			for (Object o : p.getPossibleValues()) {
				if (o.equals(v)) { steps = p.getPossibleValues().size(); return idx; }
				idx++;
			}
		}
		return null;
	}

	private static void learnStep(int[] now) {
		if (steps == 0) return;
		boolean changed = false;
		for (int i = 0; i < 3; i++) if (now[i] != rot[i]) changed = true;
		if (!changed) return;
		if (step == 0) {
			for (int i = 0; i < 3; i++) {
				int d = Math.floorMod(now[i] - rot[i], steps);
				if (d != 0) { step = d > steps / 2 ? d - steps : d; break; }
			}
		}
		log("ход: " + key(rot) + " -> " + key(now) + " (шаг " + step + " из " + steps + ")");
	}

	private static void log(String line) {
		try {
			java.nio.file.Files.writeString(
					net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("skyryn-tiki.txt"),
					line + System.lineSeparator(), java.nio.charset.StandardCharsets.UTF_8,
					java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (Exception ignored) { }
	}

	private static boolean frozen(int[] s, int i) {
		int b = (i + 1) % 3, c = (i + 2) % 3;
		return s[i] == s[b] && s[i] != s[c] || s[i] == s[c] && s[i] != s[b];
	}

	private static int[] apply(int[] s, int i, int dir) {
		int[] n = s.clone();
		int j = (i + 1) % 3;
		if (!frozen(s, i)) n[i] = Math.floorMod(n[i] + dir * step, steps);
		if (!frozen(s, j)) n[j] = Math.floorMod(n[j] + dir * step, steps);
		return n;
	}

	private static boolean allowed(int[] s, int i) {
		return !frozen(s, i);
	}

	private record Move(int head, int dir) { }

	private static java.util.List<Move> solve() {
		if (step == 0 || steps == 0) return null;
		int[] start = rot.clone();
		if (start[0] == start[1] && start[1] == start[2]) return java.util.List.of();
		Map<String, java.util.List<Move>> seen = new HashMap<>();
		ArrayDeque<int[]> q = new ArrayDeque<>();
		seen.put(key(start), java.util.List.of());
		q.add(start);
		while (!q.isEmpty()) {
			int[] cur = q.poll();
			java.util.List<Move> path = seen.get(key(cur));
			if (path.size() > 12) continue;
			for (int i = 0; i < 3; i++) {
				if (!allowed(cur, i)) continue;
				for (int dir : new int[]{ 1, -1 }) {
					int[] nx = apply(cur, i, dir);
					String k = key(nx);
					if (seen.containsKey(k)) continue;
					java.util.List<Move> np = new java.util.ArrayList<>(path);
					np.add(new Move(i, dir));
					if (nx[0] == nx[1] && nx[1] == nx[2]) return np;
					seen.put(k, np);
					q.add(nx);
				}
			}
		}
		return null;
	}

	private static String key(int[] s) { return s[0] + ":" + s[1] + ":" + s[2]; }

	private static void render(GuiGraphicsExtractor ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
		Font font = mc.font;
		if (!on() || base == null) return;
		int x = hudX(), y = hudY();
		int top = y, wide = 0;

		ctx.text(font, "§5§lTiki", x, y, 0xFFFFFFFF, true);
		y += 11;
		String[] name = { Lang.tr("bottom", "низ"), Lang.tr("middle", "центр"), Lang.tr("top", "верх") };
		for (int i = 2; i >= 0; i--) {
			ctx.text(font, "§7" + name[i] + " §f" + rot[i], x, y, 0xFFFFFFFF, true);
			y += 10;
		}
		if (rot[0] == rot[1] && rot[1] == rot[2]) {
			ctx.text(font, "§a" + Lang.tr("Awake — go hunt it", "Проснулся — бей"), x, y, 0xFFFFFFFF, true);
			return;
		}
		if (step == 0) {
			ctx.text(font, "§e" + Lang.tr("hit any head — learning the step",
					"ударь любую — изучаю шаг"), x, y, 0xFFFFFFFF, true);
			return;
		}
		java.util.List<Move> path = solve();
		if (path == null || path.isEmpty()) {
			ctx.text(font, "§8" + Lang.tr("no way found", "путь не найден"), x, y, 0xFFFFFFFF, true);
			return;
		}
		int i = 0;
		while (i < path.size()) {
			int n = 1;
			while (i + n < path.size() && path.get(i + n).equals(path.get(i))) n++;
			Move m = path.get(i);
			String btn = m.dir() > 0 ? "§a" + Lang.tr("LMB", "ЛКМ") : "§c" + Lang.tr("RMB", "ПКМ");
			ctx.text(font, btn + " §f" + name[m.head()]
					+ (n > 1 ? " §7×" + n : ""), x, y, 0xFFFFFFFF, true);
			y += 10;
			i += n;
		}
	}

}
