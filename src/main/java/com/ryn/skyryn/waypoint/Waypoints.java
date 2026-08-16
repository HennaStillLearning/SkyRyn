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

/**
 * Метки в мире: столб-луч + box в точке (3D-примитивы через миксин) и подпись
 * с дистанцией (2D поверх, проекцией мировой точки на экран).
 *
 * 3D рисует миксин (LevelRendererMixin) в точке debug-гизмо — единственная, что
 * вызывается даже при Sodium; там примитивы (линии) видны. А вот ТЕКСТ в мире с
 * Sodium не рисуется, поэтому подпись даём на HUD: проецируем worldPos в экранные
 * координаты (proj*view) и пишем ctx.text. Так же делают многие waypoint-моды.
 */
public class Waypoints {

	/** Метка: где, что за место и ради какого шарда. */
	public record Mark(double x, double y, double z, String name, String shard, int color) { }

	/** Спот для отслеживания: точка + куда варпать + цвет. landsHere — варп кидает прямо сюда. */
	public record Spot(double x, double y, double z, String name, String warp, int color,
					   boolean landsHere) { }

	private static final Set<Mark> MARKS = new LinkedHashSet<>();

	/** Радиус «дошёл» в блоках. */
	private static final double REACH = 8;
	/** Через сколько мс после «дошёл» метки гаснут. */
	private static final long CLEAR_DELAY = 2000;
	/** Когда игрок впервые оказался у метки (0 — ещё нет). */
	private static long reachedAt = 0;
	/** Остров, к которому относятся метки — ушёл на другой, метки не нужны. */
	private static String markIsland = "";
	/** Игрок уже добрался до острова метки — чтобы не гасить во время самого ТП. */
	private static boolean arrived = false;
	/**
	 * Гасить метки по достижению спота. true — для Huntstrap (ловушки ставятся в
	 * одном месте). false — для Hunting: моб фармится по нескольким точкам, метки
	 * должны жить, пока не остановишь трек вручную.
	 */
	private static boolean clearOnReach = true;

	/**
	 * Маршрут-тур по спотам (для Hunting с несколькими точками). Луч ведёт к
	 * одному споту за раз: ближайший → следующий → ... → последний, потом в
	 * обратную сторону (пинг-понг). Порядок считаем от точки прибытия игрока.
	 */
	private static final List<Mark> ROUTE = new ArrayList<>();
	private static int targetIdx = 0;
	private static boolean forward = true;
	private static boolean routeReady = false;
	/**
	 * Заданный порядок (Bambuleaf): маршрут строго как выписан, метка ОДНОРАЗОВАЯ —
	 * дошёл, она гаснет, ведём к следующей; последняя достигнута — трек завершён.
	 */
	private static boolean ordered = false;
	/**
	 * Патруль (пинг-понг): для острова с НЕСКОЛЬКИМИ фарм-спотами (напр. Bezal, 2 спота).
	 * Луч ведёт к одному споту за раз, дошёл — переключается на следующий, метки живут
	 * до ручной остановки. Для ОДНОГО спота патруля нет: дошёл — метка гаснет (не мозолит).
	 */
	private static boolean patrol = false;
	/** Сколько спотов было в маршруте изначально — для прогресса «N/M» в подписи. */
	private static int routeTotal = 0;

	/** Высота луча вверх — до неба, чтобы столб торчал над рельефом и был виден издалека. */
	private static final float BEAM_UP = 312f;
	/** На сколько луч уходит вниз от точки. */
	private static final float BEAM_DOWN = 3f;
	/** Толщина линий. */
	private static final float LINE_W = 4f;
	/** Толщина поводка-пути — заметно толще линий столба/бокса, чтобы не терялась на фоне. */
	private static final float ROUTE_LINE_W = 8f;
	/** Насколько поднять анкер поводка над ногами игрока (примерно пояс). */
	private static final float ANCHOR_UP = 1.0f;
	/** На сколько поднять точки пути над рельефом (анкер у игрока не трогаем). */
	private static final float ROUTE_LIFT = 1.0f;
	/** Полуширина пучка: столб рисуем квадратом из линий — толще и заметнее. */
	private static final float BEAM_R = 0.16f;
	/** Полуразмер box вокруг точки. */
	private static final float BOX_R = 0.5f;
	/** На сколько поднять подпись над точкой. */
	private static final float LABEL_UP = 1.6f;
	/** Дальше — не рисуем. */
	private static final double MAX_DIST = 2000;

	// --- кадр для проекции подписей (снимается миксином каждый кадр) ---
	/** view×projection камеры (без трансляции — мир рисуется относительно камеры). */
	private static final Matrix4f VP = new Matrix4f();
	private static Vec3 camPos = Vec3.ZERO;
	private static boolean haveFrame = false;

	private static boolean loggedCall = false;

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyryn", "waypoints"),
				(ctx, tick) -> renderHud(ctx));
		// Дошёл до метки (радиус REACH) — через CLEAR_DELAY гасим всё, чтобы не мозолило.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
				.register(mc -> tickReach());
	}

	private static long lastAreaDiag = 0;

	private static void tickReach() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		// Диагностика зоны: логируем формат скорборда каждые 3 сек (даже без меток),
		// чтобы починить currentArea и карту островов по реальным строкам.
		if (SkyBlockCheck.onSkyBlock() && System.currentTimeMillis() - lastAreaDiag > 3000) {
			lastAreaDiag = System.currentTimeMillis();
			com.ryn.skyryn.config.SkyLog.d("зона='" + SkyBlockCheck.currentArea() + "' остров='"
					+ SkyBlockCheck.currentIsland() + "'  " + SkyBlockCheck.sidebarDump());
		}

		if (MARKS.isEmpty()) { reachedAt = 0; return; }

		// Ушёл на другой остров — метки этого острова больше не нужны. Но не
		// гасим во время самого ТП: сначала дождёмся прибытия (arrived), потом
		// реагируем на уход.
		if (!markIsland.isBlank()) {
			String cur = SkyBlockCheck.currentIsland();
			if (!cur.isBlank()) {
				if (cur.equals(markIsland)) arrived = true;
				else if (arrived) { clear(); return; }
			}
		}

		Vec3 p = mc.player.position();

		if (!clearOnReach) {
			// Hunting: показываем ОДИН спот за раз. Порядок: обычный — ближайший-сосед
			// (считаем по прибытии), заданный (ordered) — как выписан (готов из track).
			if (!ordered && !routeReady && (markIsland.isBlank() || arrived)) computeRoute(p);
			if (routeReady && !ROUTE.isEmpty() && targetIdx >= 0 && targetIdx < ROUTE.size()) {
				Mark t = ROUTE.get(targetIdx);
				double dx = p.x - t.x, dy = p.y - t.y, dz = p.z - t.z;
				boolean near = dx * dx + dy * dy + dz * dz <= REACH * REACH;
				if (patrol) {
					// Несколько спотов — visit-once: дошёл до спота, он гаснет НАВСЕГДА,
					// луч ведёт к ближайшему из оставшихся; все пройдены — трек завершён.
					if (near) {
						MARKS.remove(t);
						if (MARKS.isEmpty()) { clear(); return; }
						computeRoute(p); // маршрут по оставшимся, ближайший — новая цель
					}
					reachedAt = 0;
				} else if (ordered && ROUTE.size() > 1) {
					// Промежуточный спот заданного маршрута — дошёл, гасим и сразу ведём
					// к следующему (не тянем, чтобы направление было понятно).
					if (near) {
						MARKS.remove(t);
						ROUTE.remove(targetIdx);
						if (targetIdx >= ROUTE.size()) targetIdx = ROUTE.size() - 1;
						reachedAt = 0;
					}
				} else {
					// Единственный/последний спот — гасим с задержкой (grace), НЕ мгновенно.
					// Иначе, если игрок уже стоит на споте (напр. на точке рыбалки), метка
					// исчезнет сразу и это выглядит как баг. Отошёл до срока — таймер сброс.
					long now = System.currentTimeMillis();
					if (!near) reachedAt = 0;
					else if (reachedAt == 0) reachedAt = now;
					if (reachedAt != 0 && now - reachedAt >= CLEAR_DELAY) { clear(); return; }
				}
			}
			return;
		}

		// Huntstrap: гасим по достижению любой метки (ловушки ставятся в одном месте).
		boolean near = false;
		for (Mark m : new ArrayList<>(MARKS)) {
			double dx = p.x - m.x, dy = p.y - m.y, dz = p.z - m.z;
			if (dx * dx + dy * dy + dz * dz <= REACH * REACH) { near = true; break; }
		}
		long now = System.currentTimeMillis();
		if (near && reachedAt == 0) reachedAt = now;
		if (reachedAt != 0 && now - reachedAt >= CLEAR_DELAY) clear();
	}

	/** Ближайший-сосед маршрут от точки from по всем меткам. */
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
		// Несколько спотов — патрулируем (пинг-понг); один — гасим по достижении.
		patrol = ROUTE.size() > 1;
	}

	/** Текущая цель тура (к ней ведёт луч), или null. */
	private static Mark currentTarget() {
		return routeReady && targetIdx >= 0 && targetIdx < ROUTE.size() ? ROUTE.get(targetIdx) : null;
	}

	/** Ближайшая метка к точке — пока маршрут не готов. */
	private static Mark nearestMark(Vec3 from) {
		Mark best = null; double bd = Double.MAX_VALUE;
		for (Mark m : new ArrayList<>(MARKS)) {
			double dd = (m.x - from.x) * (m.x - from.x) + (m.y - from.y) * (m.y - from.y)
					+ (m.z - from.z) * (m.z - from.z);
			if (dd < bd) { bd = dd; best = m; }
		}
		return best;
	}

	/**
	 * Единственная метка, которую сейчас показываем (луч+box+подпись). Это цель
	 * тура; пока маршрут не готов (ещё ТП-имся) — ближайшая метка. На экране всегда
	 * одна, чтобы группы спотов не перегружали вид.
	 */
	private static Mark activeMark() {
		Mark t = currentTarget();
		return t != null ? t : nearestMark(camPos);
	}

	private static void resetRoute() { ROUTE.clear(); routeReady = false; targetIdx = 0; forward = true; ordered = false; patrol = false; routeTotal = 0; }

	/**
	 * Отслеживать споты метода: метки + поводок-линии, ТП делает вызывающий.
	 * clearOnReach — гасить ли по достижению (Huntstrap да, Hunting нет).
	 * ordered — вести луч по спотам в заданном порядке (как выписаны), а не по
	 * ближайшему. Для Bambuleaf: сначала Enterance, потом Sanctum, независимо от
	 * расстояния. Остальным шардам — ближайший-сосед (ordered=false).
	 */
	public static void track(List<Spot> spots, String shard, boolean clearOnReach, boolean ordered) {
		MARKS.clear();
		reachedAt = 0;
		for (Spot s : spots) MARKS.add(new Mark(s.x(), s.y(), s.z(), s.name(), shard, s.color()));
		markIsland = spots.isEmpty() ? "" : SkyBlockCheck.islandOfWarp(spots.get(0).warp());
		arrived = false;
		Waypoints.clearOnReach = clearOnReach;
		resetRoute();
		Waypoints.ordered = ordered;
		// Заданный порядок: маршрут = споты как выписаны (MARKS сохраняет порядок),
		// без пересчёта по ближайшему. Готов сразу, ТП-прибытия не ждём.
		if (ordered && MARKS.size() > 1) {
			ROUTE.addAll(MARKS);
			targetIdx = 0; forward = true; routeReady = true; routeTotal = ROUTE.size();
		}
	}

	/**
	 * Снимок матрицы кадра — для проекции подписей на HUD. Берём view×projection
	 * прямо с камеры (projection-арг миксина оказался пустым — clipW=1).
	 */
	public static void captureFrame(Vec3 cam) {
		Minecraft.getInstance().gameRenderer.getMainCamera().getViewRotationProjectionMatrix(VP);
		camPos = cam;
		haveFrame = true;
	}

	/** 3D: луч + box. Зовётся из миксина. buf — живой буфер pass. */
	public static void renderWorld(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam) {
		if (!loggedCall) {
			loggedCall = true;
			com.ryn.skyryn.config.SkyLog.d("renderWorld вызван — рисуем луч+box");
		}
		if (MARKS.isEmpty()) return;

		VertexConsumer vc = buf.getBuffer(RenderTypes.lines());
		PoseStack.Pose e = ps.last();

		// Рисуем ТОЛЬКО активную метку — одну за раз, чтобы группы спотов не
		// перегружали экран лучами и боксами.
		Mark m = activeMark();
		if (m != null) {
			float x = (float) (m.x - cam.x);
			float y = (float) (m.y - cam.y);
			float z = (float) (m.z - cam.z);
			if (Math.sqrt((double) x * x + y * y + z * z) <= MAX_DIST) {
				int r = (m.color >> 16) & 0xFF, g = (m.color >> 8) & 0xFF, b = m.color & 0xFF;

				// Столб: 4 вертикали по углам квадрата — «толстый» луч.
				for (int i = 0; i < 4; i++) {
					float ox = (i == 0 || i == 3) ? BEAM_R : -BEAM_R;
					float oz = (i < 2) ? BEAM_R : -BEAM_R;
					line(vc, e, x + ox, y - BEAM_DOWN, z + oz, x + ox, y + BEAM_UP, z + oz, r, g, b);
				}
				if (RynConfig.routeBeam) renderRoute(vc, e, cam, m, r, g, b);
			}
		}
		buf.endBatch();

		// Закрашенный блок метки — отдельным батчем: у него свой тип рендера (квады).
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

	/**
	 * Поводок до метки: путь в обход препятствий (PathFinder), пока не готов или
	 * не нашёлся — прямая, как раньше. Старт — позиция игрока (не камеры и не
	 * прицела: смотреть/крутить головой можно куда угодно), поднятая до пояса —
	 * ровно в глазах анкер давал вырожденный отрезок нулевой длины и линия
	 * пропадала/резалась near-плоскостью камеры.
	 *
	 * Ломаную из углов A* сглаживаем обрезкой углов по Чайкину — линия идёт
	 * плавно, но, в отличие от Catmull-Rom, НЕ перелетает на поворотах (остаётся
	 * внутри выпуклой оболочки ломаной), потому не выпучивается в стены.
	 */
	private static void renderRoute(VertexConsumer vc, PoseStack.Pose e, Vec3 cam, Mark m,
									int r, int g, int b) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 anchor = mc.player.getPosition(partial).add(0, ANCHOR_UP, 0);
		Vec3 target = new Vec3(m.x, m.y, m.z);

		// Граф-навигация (записанные /srpath пути) в приоритете; нет графа/метка не
		// рядом с сетью — откат на живой A* (PathFinder). try/catch — чтобы падение
		// графа не убивало саму линию (тогда хотя бы A*), и диагностика в чат.
		List<Vec3> path = null;
		try {
			path = NavGraph.route(anchor, target);
		} catch (Throwable ex) {
			// Падение графа не должно убивать линию — молча откатываемся на A*.
		}
		if (path == null) {
			PathFinder.requestIfNeeded(mc.level, anchor, target);
			path = PathFinder.currentPath();
		}

		// Опорные точки: анкер у игрока, углы пути, сама метка. Если путь не
		// найден/не готов — только анкер и метка, т.е. чистая прямая.
		List<Vec3> pts = new ArrayList<>();
		pts.add(anchor);
		if (path != null) pts.addAll(path);
		pts.add(target);
		dedupe(pts);
		// Поднимаем над рельефом ТОЛЬКО точки пути, НЕ анкер у игрока (иначе линия
		// начинается выше головы и в 1-м лице «уходит вверх» от модельки).
		for (int i = 1; i < pts.size(); i++) pts.set(i, pts.get(i).add(0, ROUTE_LIFT, 0));

		List<Vec3> smooth = chaikin(pts, 2); // 2 итерации обрезки углов
		Vec3 prev = smooth.get(0);
		for (int i = 1; i < smooth.size(); i++) {
			Vec3 cur = smooth.get(i);
			line(vc, e, (float) (prev.x - cam.x), (float) (prev.y - cam.y), (float) (prev.z - cam.z),
					(float) (cur.x - cam.x), (float) (cur.y - cam.y), (float) (cur.z - cam.z),
					r, g, b, ROUTE_LINE_W);
			prev = cur;
		}
	}

	/** Выкидывает подряд идущие точки ближе ~0.6 блока — иначе сглаживание петляет. */
	private static void dedupe(List<Vec3> pts) {
		for (int i = pts.size() - 2; i >= 1; i--) {
			if (pts.get(i).distanceToSqr(pts.get(i + 1)) < 0.36) pts.remove(i);
		}
	}

	/**
	 * Сглаживание Чайкина: каждый внутренний отрезок режется на точках 1/4 и 3/4,
	 * концы сохраняются. Результат — скруглённая ломаная строго внутри исходной,
	 * без перелётов. Несколько итераций дают всё более гладкую кривую.
	 */
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

	/** 2D: подписи с дистанцией, спроецированные на экран. */
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

			// Подписываем ТОЛЬКО активную метку — одну за раз.
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
			if (clip.w <= 0.05f) return; // за спиной
			if (ndcx < -1.2f || ndcx > 1.2f || ndcy < -1.2f || ndcy > 1.2f) return;

			// Иконка-box: HUD поверх всего — видна сквозь стены, отовсюду.
			int s = 3;
			ctx.fill(px - s - 1, py - s - 1, px + s + 1, py + s + 1, 0xFF141419);
			ctx.fill(px - s, py - s, px + s, py + s, m.color);

			// Подпись: [N/M] локация, моб (если отличается), дистанция. N/M — прогресс
			// тура: у патруля — текущая цель, у гашения — сколько пройдено из исходного.
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

	// --- примитивы ---

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

	/**
	 * Куб со стороной 2*BOX_R вокруг (cx,cy,cz), закрашенный целиком.
	 *
	 * Раньше рисовали 12 рёбер — «полусетка» терялась на пёстром фоне. Теперь это
	 * полупрозрачный блок: шесть граней квадами (debugQuads — формат позиция+цвет,
	 * без отсечения задних граней, так что куб виден и изнутри).
	 */
	public static void filledBox(VertexConsumer vc, PoseStack.Pose e, float cx, float cy, float cz,
								 int r, int g, int b, int a) {
		filledBox(vc, e, cx - BOX_R, cy - BOX_R, cz - BOX_R, cx + BOX_R, cy + BOX_R, cz + BOX_R, r, g, b, a);
	}

	/** Закрашенный параллелепипед по двум углам (координаты уже относительно камеры). */
	public static void filledBox(VertexConsumer vc, PoseStack.Pose e,
								 float x1, float y1, float z1, float x2, float y2, float z2,
								 int r, int g, int b, int a) {
		quad(vc, e, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);   // север
		quad(vc, e, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, r, g, b, a);   // юг
		quad(vc, e, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, a);   // запад
		quad(vc, e, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);   // восток
		quad(vc, e, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);   // низ
		quad(vc, e, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, r, g, b, a);   // верх
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

	// --- управление метками ---

	/** Одна метка вместо всех — клик по локации в гайде. */
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

	/** Тестовая метка на произвольных координатах. */
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
