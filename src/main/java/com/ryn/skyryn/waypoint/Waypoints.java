package com.ryn.skyryn.waypoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.LocationDb;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.mixin.LevelRendererMixin;

public class Waypoints {
	public record Mark(double x, double y, double z, String name, String shard, int color) { }

	public record Spot(double x, double y, double z, String name, String warp, int color,
					   boolean landsHere) { }

	private static final Set<Mark> MARKS = new LinkedHashSet<>();

	private static final double REACH = 8;
	private static final long CLEAR_DELAY = 2000;
	private static long reachedAt = 0;
	private static String markIsland = "";
	private static boolean arrived = false;
	private static boolean clearOnReach = true;

	private static final List<Mark> ROUTE = new ArrayList<>();
	private static int targetIdx = 0;
	private static boolean forward = true;
	private static boolean routeReady = false;
	private static boolean ordered = false;
	private static boolean patrol = false;
	private static int routeTotal = 0;

	private static final float BEAM_UP = 312f;
	private static final float BEAM_DOWN = 3f;
	private static final float LINE_W = 4f;
	private static final float ROUTE_LINE_W = 8f;
	private static final float ANCHOR_UP = 1.0f;
	private static final float ROUTE_LIFT = 1.0f;
	private static final float BEAM_R = 0.16f;
	private static final float BOX_R = 0.5f;
	private static final float LABEL_UP = 1.6f;
	private static final double MAX_DIST = 2000;

	private static final Matrix4f VP = new Matrix4f();
	private static Vec3 camPos = Vec3.ZERO;
	private static boolean haveFrame = false;

	private static boolean loggedCall = false;

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyryn", "waypoints"),
				(ctx, tick) -> renderHud(ctx));
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
				.register(mc -> tickReach());
	}

	private static long lastAreaDiag = 0;

	private static void tickReach() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		if (SkyBlockCheck.onSkyBlock() && System.currentTimeMillis() - lastAreaDiag > 3000) {
			lastAreaDiag = System.currentTimeMillis();
			com.ryn.skyryn.config.SkyLog.d("зона='" + SkyBlockCheck.currentArea() + "' остров='"
					+ SkyBlockCheck.currentIsland() + "'  " + SkyBlockCheck.sidebarDump());
		}

		if (MARKS.isEmpty()) { reachedAt = 0; return; }

		if (!markIsland.isBlank()) {
			String cur = SkyBlockCheck.currentIsland();
			if (!cur.isBlank()) {
				if (cur.equals(markIsland)) arrived = true;
				else if (arrived) { clear(); return; }
			}
		}

		Vec3 p = mc.player.position();

		if (!clearOnReach) {
			if (!ordered && !routeReady && (markIsland.isBlank() || arrived)) computeRoute(p);
			if (routeReady && !ROUTE.isEmpty() && targetIdx >= 0 && targetIdx < ROUTE.size()) {
				Mark t = ROUTE.get(targetIdx);
				double dx = p.x - t.x, dy = p.y - t.y, dz = p.z - t.z;
				boolean near = dx * dx + dy * dy + dz * dz <= REACH * REACH;
				if (patrol) {
					if (near) {
						MARKS.remove(t);
						if (MARKS.isEmpty()) { clear(); return; }
						computeRoute(p);
					}
					reachedAt = 0;
				} else if (ordered && ROUTE.size() > 1) {
					if (near) {
						MARKS.remove(t);
						ROUTE.remove(targetIdx);
						if (targetIdx >= ROUTE.size()) targetIdx = ROUTE.size() - 1;
						reachedAt = 0;
					}
				} else {
					long now = System.currentTimeMillis();
					if (!near) reachedAt = 0;
					else if (reachedAt == 0) reachedAt = now;
					if (reachedAt != 0 && now - reachedAt >= CLEAR_DELAY) { clear(); return; }
				}
			}
			return;
		}

		boolean near = false;
		for (Mark m : new ArrayList<>(MARKS)) {
			double dx = p.x - m.x, dy = p.y - m.y, dz = p.z - m.z;
			if (dx * dx + dy * dy + dz * dz <= REACH * REACH) { near = true; break; }
		}
		long now = System.currentTimeMillis();
		if (near && reachedAt == 0) reachedAt = now;
		if (reachedAt != 0 && now - reachedAt >= CLEAR_DELAY) clear();
	}

	private static void computeRoute(Vec3 from) {
		ROUTE.clear();
		List<Mark> rem = new ArrayList<>(MARKS);
		double cx = from.x, cy = from.y, cz = from.z;
		while (!rem.isEmpty()) {
			Mark best = null; double bd = Double.MAX_VALUE;
			for (Mark m : rem) {
				double dd = (m.x - cx) * (m.x - cx) + (m.y - cy) * (m.y - cy) + (m.z - cz) * (m.z - cz);
				if (dd < bd) { bd = dd; best = m; }
			}
			ROUTE.add(best); rem.remove(best);
			cx = best.x; cy = best.y; cz = best.z;
		}
		targetIdx = 0; forward = true; routeReady = true; routeTotal = ROUTE.size();
		patrol = ROUTE.size() > 1;
	}

	private static Mark currentTarget() {
		return routeReady && targetIdx >= 0 && targetIdx < ROUTE.size() ? ROUTE.get(targetIdx) : null;
	}

	private static Mark nearestMark(Vec3 from) {
		Mark best = null; double bd = Double.MAX_VALUE;
		for (Mark m : new ArrayList<>(MARKS)) {
			double dd = (m.x - from.x) * (m.x - from.x) + (m.y - from.y) * (m.y - from.y)
					+ (m.z - from.z) * (m.z - from.z);
			if (dd < bd) { bd = dd; best = m; }
		}
		return best;
	}

	private static Mark activeMark() {
		Mark t = currentTarget();
		return t != null ? t : nearestMark(camPos);
	}

	private static void resetRoute() { ROUTE.clear(); routeReady = false; targetIdx = 0; forward = true; ordered = false; patrol = false; routeTotal = 0; }

	public static void track(List<Spot> spots, String shard, boolean clearOnReach, boolean ordered) {
		MARKS.clear();
		reachedAt = 0;
		for (Spot s : spots) MARKS.add(new Mark(s.x(), s.y(), s.z(), s.name(), shard, s.color()));
		markIsland = spots.isEmpty() ? "" : SkyBlockCheck.islandOfWarp(spots.get(0).warp());
		arrived = false;
		Waypoints.clearOnReach = clearOnReach;
		resetRoute();
		Waypoints.ordered = ordered;
		if (ordered && MARKS.size() > 1) {
			ROUTE.addAll(MARKS);
			targetIdx = 0; forward = true; routeReady = true; routeTotal = ROUTE.size();
		}
	}

	public static void captureFrame(Vec3 cam) {
		Minecraft.getInstance().gameRenderer.getMainCamera().getViewRotationProjectionMatrix(VP);
		camPos = cam;
		haveFrame = true;
	}

	public static void renderWorld(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam) {
		if (!loggedCall) {
			loggedCall = true;
			com.ryn.skyryn.config.SkyLog.d("renderWorld вызван — рисуем луч+box");
		}
		if (MARKS.isEmpty()) return;

		VertexConsumer vc = buf.getBuffer(RenderTypes.lines());
		PoseStack.Pose e = ps.last();

		Mark m = activeMark();
		if (m != null) {
			float x = (float) (m.x - cam.x);
			float y = (float) (m.y - cam.y);
			float z = (float) (m.z - cam.z);
			if (Math.sqrt((double) x * x + y * y + z * z) <= MAX_DIST) {
				int r = (m.color >> 16) & 0xFF, g = (m.color >> 8) & 0xFF, b = m.color & 0xFF;

				for (int i = 0; i < 4; i++) {
					float ox = (i == 0 || i == 3) ? BEAM_R : -BEAM_R;
					float oz = (i < 2) ? BEAM_R : -BEAM_R;
					line(vc, e, x + ox, y - BEAM_DOWN, z + oz, x + ox, y + BEAM_UP, z + oz, r, g, b);
				}
				if (RynConfig.routeBeam) renderRoute(vc, e, cam, m, r, g, b);
			}
		}
		buf.endBatch();

		if (m != null) {
			float x = (float) (m.x - cam.x), y = (float) (m.y - cam.y), z = (float) (m.z - cam.z);
			if (Math.sqrt((double) x * x + y * y + z * z) <= MAX_DIST) {
				int r = (m.color >> 16) & 0xFF, g = (m.color >> 8) & 0xFF, b = m.color & 0xFF;
				VertexConsumer fill = buf.getBuffer(RenderTypes.debugQuads());
				filledBox(fill, ps.last(), x, y, z, r, g, b, 110);
				buf.endBatch();
			}
		}
	}

	private static void renderRoute(VertexConsumer vc, PoseStack.Pose e, Vec3 cam, Mark m,
									int r, int g, int b) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 anchor = mc.player.getPosition(partial).add(0, ANCHOR_UP, 0);
		Vec3 target = new Vec3(m.x, m.y, m.z);

		List<Vec3> path = null;
		try {
			path = NavGraph.route(anchor, target);
		} catch (Throwable ex) {
		}
		if (path == null) {
			PathFinder.requestIfNeeded(mc.level, anchor, target);
			path = PathFinder.currentPath();
		}

		List<Vec3> pts = new ArrayList<>();
		pts.add(anchor);
		if (path != null) pts.addAll(path);
		pts.add(target);
		dedupe(pts);
		for (int i = 1; i < pts.size(); i++) pts.set(i, pts.get(i).add(0, ROUTE_LIFT, 0));

		List<Vec3> smooth = chaikin(pts, 2);
		Vec3 prev = smooth.get(0);
		for (int i = 1; i < smooth.size(); i++) {
			Vec3 cur = smooth.get(i);
			line(vc, e, (float) (prev.x - cam.x), (float) (prev.y - cam.y), (float) (prev.z - cam.z),
					(float) (cur.x - cam.x), (float) (cur.y - cam.y), (float) (cur.z - cam.z),
					r, g, b, ROUTE_LINE_W);
			prev = cur;
		}
	}

	private static void dedupe(List<Vec3> pts) {
		for (int i = pts.size() - 2; i >= 1; i--) {
			if (pts.get(i).distanceToSqr(pts.get(i + 1)) < 0.36) pts.remove(i);
		}
	}

	private static List<Vec3> chaikin(List<Vec3> pts, int iters) {
		List<Vec3> cur = pts;
		for (int it = 0; it < iters && cur.size() >= 3; it++) {
			List<Vec3> next = new ArrayList<>(cur.size() * 2);
			next.add(cur.get(0));
			for (int i = 0; i < cur.size() - 1; i++) {
				Vec3 p = cur.get(i), q = cur.get(i + 1);
				next.add(lerp(p, q, 0.25));
				next.add(lerp(p, q, 0.75));
			}
			next.add(cur.get(cur.size() - 1));
			cur = next;
		}
		return cur;
	}

	private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
		return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
	}

	private static long lastHudErr = 0;

	private static void renderHud(GuiGraphicsExtractor ctx) {
		try {
			if (MARKS.isEmpty()) return;
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

			if (!haveFrame) return;
			Font font = mc.font;
			int sw = mc.getWindow().getGuiScaledWidth();
			int sh = mc.getWindow().getGuiScaledHeight();
			Matrix4f vp = VP;

			Mark m = activeMark();
			if (m == null) return;
			float rx = (float) (m.x - camPos.x);
			float ry = (float) (m.y - camPos.y);
			float rz = (float) (m.z - camPos.z);
			double dist = Math.sqrt((double) rx * rx + ry * ry + rz * rz);
			if (dist > MAX_DIST) return;

			Vector4f clip = vp.transform(new Vector4f(rx, ry + LABEL_UP, rz, 1f));
			float ndcx = clip.w == 0 ? 0 : clip.x / clip.w, ndcy = clip.w == 0 ? 0 : clip.y / clip.w;
			int px = Math.round((ndcx * 0.5f + 0.5f) * sw);
			int py = Math.round((1f - (ndcy * 0.5f + 0.5f)) * sh);
			if (clip.w <= 0.05f) return;
			if (ndcx < -1.2f || ndcx > 1.2f || ndcy < -1.2f || ndcy > 1.2f) return;

			int s = 3;
			ctx.fill(px - s - 1, py - s - 1, px + s + 1, py + s + 1, 0xFF141419);
			ctx.fill(px - s, py - s, px + s, py + s, m.color);

			java.util.List<String> lbl = new ArrayList<>();
			boolean multi = routeReady && routeTotal > 1;
			int idx = patrol ? targetIdx : (routeTotal - ROUTE.size());
			lbl.add((multi ? "§e" + (idx + 1) + "/" + routeTotal + " §f" : "§f") + m.name);
			String mob = m.shard == null || m.shard.isBlank() ? "" : ShardDb.displayName(m.shard);
			if (!mob.isBlank() && !mob.equalsIgnoreCase(m.name)) lbl.add("§7" + mob);
			lbl.add("§b" + (int) Math.round(dist) + "m");
			int ly = py + s + 3;
			for (String ln : lbl) {
				ctx.text(font, ln, px - font.width(ln) / 2, ly, 0xFFFFFFFF, true);
				ly += 10;
			}
		} catch (Exception e) {
			if (System.currentTimeMillis() - lastHudErr > 2000) {
				lastHudErr = System.currentTimeMillis();
				com.ryn.skyryn.config.SkyLog.d("HUD меток упал: " + e);
			}
		}
	}

	private static void line(VertexConsumer vc, PoseStack.Pose e,
							 float x1, float y1, float z1, float x2, float y2, float z2,
							 int r, int g, int b) {
		line(vc, e, x1, y1, z1, x2, y2, z2, r, g, b, LINE_W);
	}

	private static void line(VertexConsumer vc, PoseStack.Pose e,
							 float x1, float y1, float z1, float x2, float y2, float z2,
							 int r, int g, int b, float width) {
		float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len > 0) { nx /= len; ny /= len; nz /= len; }
		vc.addVertex(e, x1, y1, z1).setNormal(e, nx, ny, nz).setColor(r, g, b, 255).setLineWidth(width);
		vc.addVertex(e, x2, y2, z2).setNormal(e, nx, ny, nz).setColor(r, g, b, 255).setLineWidth(width);
	}

	public static void filledBox(VertexConsumer vc, PoseStack.Pose e, float cx, float cy, float cz,
								 int r, int g, int b, int a) {
		filledBox(vc, e, cx - BOX_R, cy - BOX_R, cz - BOX_R, cx + BOX_R, cy + BOX_R, cz + BOX_R, r, g, b, a);
	}

	public static void filledBox(VertexConsumer vc, PoseStack.Pose e,
								 float x1, float y1, float z1, float x2, float y2, float z2,
								 int r, int g, int b, int a) {
		quad(vc, e, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
		quad(vc, e, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, r, g, b, a);
		quad(vc, e, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, a);
		quad(vc, e, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
		quad(vc, e, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
		quad(vc, e, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, r, g, b, a);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose e,
							 float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz,
							 int r, int g, int b, int a) {
		vc.addVertex(e, ax, ay, az).setColor(r, g, b, a);
		vc.addVertex(e, bx, by, bz).setColor(r, g, b, a);
		vc.addVertex(e, cx, cy, cz).setColor(r, g, b, a);
		vc.addVertex(e, dx, dy, dz).setColor(r, g, b, a);
	}

	public static void only(LocationDb.Loc loc, String shard) {
		MARKS.clear();
		reachedAt = 0;
		add(loc, shard);
		markIsland = loc == null ? "" : SkyBlockCheck.islandOfWarp(loc.effectiveWarp());
		arrived = false;
		clearOnReach = true;
		resetRoute();
	}

	public static void add(LocationDb.Loc loc, String shard) {
		if (loc == null) return;
		double[] p = loc.xyz();
		if (p == null) return;
		MARKS.add(new Mark(p[0] + 0.5, p[1] + 0.5, p[2] + 0.5, loc.name(), shard, color(loc)));
	}

	public static void addRaw(double x, double y, double z, String name) {
		MARKS.add(new Mark(x, y, z, name, null, 0xFF5FD68A));
		com.ryn.skyryn.config.SkyLog.d("тест-метка: " + (int) x + " " + (int) y + " " + (int) z
				+ ", всего меток " + MARKS.size());
	}

	private static int color(LocationDb.Loc loc) {
		String code = loc.color();
		if (code == null || code.length() < 2) return 0xFFFFFFFF;
		net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(code.charAt(1));
		return f == null || f.getColor() == null ? 0xFFFFFFFF : 0xFF000000 | f.getColor();
	}

	public static void clear() {
		MARKS.clear(); reachedAt = 0; markIsland = ""; arrived = false; clearOnReach = true;
		resetRoute();
	}

	public static int count() { return MARKS.size(); }

	public static List<Mark> all() { return new ArrayList<>(MARKS); }
}
