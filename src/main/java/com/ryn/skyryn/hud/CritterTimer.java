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

/**
 * Honeycomb tracker — таймеры Critter на помазанных мёдом деревьях (Foraging).
 *
 * Механика: особым предметом «мажешь» дерево мёдом («You lathered the Fig Tree with
 * Honeycomb!») — через 60/30/15 мин появляется Critter. По предмету не понять, сколько
 * ждать: время пишет только сама игра строкой «⏣ Critter in: Xm Ys», и видно её ТОЛЬКО
 * у дерева.
 *
 * Строка приходит по-разному, поэтому читаем все места сразу:
 *   1) голограммы вокруг игрока — основной источник,
 *   2) экшн-бар,
 *   3) субтитр/титр (через GuiSubtitleMixin),
 *   4) обычный чат.
 *
 * Таймер привязывается к месту, где висит строка, поэтому помеченных деревьев
 * может быть сколько угодно и «помазал» ловить не обязательно: подошёл к дереву —
 * таймер сам появился в списке.
 *
 * Когда время вышло, строка не пропадает: криттер уже сидит на дереве и ждёт, пока за
 * ним придут, а голограмма вместо времени показывает «Tree Protection Order» (в плашку
 * это не выводим). Дерево висит с пометкой «Spawned», пока таймер не обновится или пока
 * игрок не подойдёт и Hypixel не напишет «HONEY TREE! … has appeared!».
 *
 * ПОРОДУ дерева голограмма не пишет (там только время и «Tree Protection Order»), её
 * знает единственное сообщение — о мазке. Поэтому породу получает ближайший к игроку
 * безымянный таймер, и дальше она держится за местом: иначе свежее дерево подписалось
 * бы породой предыдущего помазанного.
 */
public class CritterTimer {

	private static final Pattern LATHER =
			Pattern.compile("lathered the (\\w+) tree with honeycomb", Pattern.CASE_INSENSITIVE);
	private static final Pattern CRITTER_IN =
			Pattern.compile("critter in:\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?", Pattern.CASE_INSENSITIVE);
	/**
	 * Криттер появился. Это сообщение приходит, когда подходишь к дереву:
	 * «HONEY TREE! Pollendart has appeared!». Второй вариант — старое «fell from the tree».
	 */
	private static final Pattern SPAWN =
			Pattern.compile("honey tree!.*\\bhas appeared|\\bfell from the tree", Pattern.CASE_INSENSITIVE);
	/**
	 * Улей: «Honeyhive / Refill in: 59m 50s» голограммой над ним. Ульев много и стоят
	 * они кучно, но запоминать каждый не нужно — держим ОДИН таймер по самому большому
	 * увиденному отсчёту: он наполнится последним, значит к этому моменту готовы все.
	 */
	private static final Pattern REFILL_IN =
			Pattern.compile("refill in:\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?", Pattern.CASE_INSENSITIVE);
	private static long hiveEndMs = 0;

	/** Таймер, которому так и не досталось времени, столько не живёт. */
	private static final long UNSYNCED_LIFE = 600_000;
	/** Предохранитель от бесконечного накопления: отработавшее дерево дольше не держим. */
	private static final long SPAWNED_LIFE = 3 * 3600_000L;

	private static final class Timer {
		final String key;      // место (блок) или имя дерева — по чему таймер опознаётся
		String tree;           // подпись в плашке
		BlockPos pos;          // где висит строка времени
		long endMs;
		final long createdAt = now();
		boolean alerted;
		Timer(String key, String tree) { this.key = key; this.tree = tree; }
	}

	private static final Map<String, Timer> timers = new LinkedHashMap<>();
	/** Порода дерева по месту его голограммы — запоминается, когда ты его мажешь. */
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
			// Экшн-бар тоже может нести «Critter in».
			if (overlay) { readTime(s, null, "action bar"); return; }
			Matcher m = LATHER.matcher(s);
			if (m.find()) {
				// Породу знает только это сообщение. Запоминаем и место игрока: голограмма
				// с временем висит тут же рядом — ей имя и достанется.
				pendingTree = cap(m.group(1).toLowerCase());
				var pl = Minecraft.getInstance().player;
				pendingPos = pl != null ? pl.position() : null;
				pendingAt = now();
				return;
			}
			if (SPAWN.matcher(s).find()) { removeNearestExpired(); return; }
			readTime(s, null, "чат");
		});

		// Основной источник: голограмма над деревом. Смотрим два раза в секунду.
		ClientTickEvents.END_CLIENT_TICK.register(CritterTimer::scanHolograms);

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyryn", "critter-timer"),
				(ctx, tick) -> renderHud(ctx));
	}

	/** Титр/субтитр (из миксина Gui). */
	public static void onSubtitle(String raw) {
		if (!RynConfig.critterTimer) return;
		readTime(strip(raw), null, "титр");
	}

	// ===== Чтение времени =====

	/**
	 * Разбирает строку «Critter in: Xm Ys». pos — где строка висит (голограмма),
	 * либо null, если источник без места (чат/титр/экшн-бар).
	 */
	private static Timer readTime(String s, BlockPos pos, String source) {
		if (s == null || s.isEmpty()) return null;
		Matcher m = CRITTER_IN.matcher(s);
		if (!m.find()) return null;
		int mm = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
		int ss = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
		long secs = mm * 60L + ss;
		if (secs <= 0) return null;

		Timer t = pos != null ? timerAt(pos) : pick(secs);
		if (t == null) {   // источник без места и ни одного известного дерева — заводим по факту чтения
			t = new Timer("loose", pendingTree != null ? pendingTree : "");
			timers.put(t.key, t);
		}
		long end = now() + secs * 1000;
		// Не дёргаем таймер туда-сюда от секундных расхождений между источниками.
		if (Math.abs(end - t.endMs) > 2000) { t.endMs = end; t.alerted = false; }
		logOnce(source, secs, t.tree);
		return t;
	}

	/** Отсчёт улья. Берём самый большой из увиденных — по нему готовы будут все. */
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

	/** Таймер по месту голограммы (создаётся при первом чтении). */
	private static Timer timerAt(BlockPos pos) {
		// Голограмма между тиками чуть съезжает — таймер вплотную это то же самое
		// дерево, а не второе. Иначе одно дерево размножалось бы в плашке.
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
			// Если рядом уже болтался таймер «без места» — он про это же дерево, убираем дубль.
			timers.remove("loose");
		} else if (t.tree.isEmpty()) {
			t.tree = nameFor(pos);   // породу могли узнать уже после того, как завели таймер
		}
		t.pos = pos;
		return t;
	}

	/**
	 * Порода дерева, если она уже известна. Голограмма над деревом пишет только время
	 * («⏣ Critter in: 12m 22s») и «Tree Protection Order» — породы там нет, а угадывать
	 * её по соседним строкам нельзя: так все деревья подписываются последним помазанным.
	 * Единственный источник — сообщение о мазке.
	 */
	private static String nameFor(BlockPos pos) {
		String k = knownTrees.get(key(pos));
		return k != null ? k : "";
	}

	/**
	 * Отдаёт имя из «You lathered the X Tree» ближайшей безымянной голограмме.
	 * Мазок делается вплотную, так что ближайшая — она и есть.
	 */
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
		knownTrees.put(best.key, pendingTree);   // вернёшься к этому дереву — подпишется само
		clearPending();
	}

	private static void clearPending() { pendingTree = null; pendingPos = null; }

	private static String key(BlockPos pos) { return pos.getX() + ":" + pos.getY() + ":" + pos.getZ(); }

	/** Голограммы вокруг игрока: «⏣ Critter in: Xm Ys» висит над помазанным деревом. */
	private static void scanHolograms(Minecraft mc) {
		if (!RynConfig.critterTimer || mc.level == null || mc.player == null) return;
		if (now() - lastScan < 500) return;
		lastScan = now();
		if (!SkyBlockCheck.onSkyBlock()) return;
		// Отработавшее дерево из плашки НЕ убираем — оно висит как «Spawned», пока
		// таймер не обновится или пока криттер не появится (сообщение в чате).
		// Убираем только то, чему так и не досталось времени, и совсем старое.
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

	/**
	 * Какому дереву принадлежит время, прочитанное БЕЗ места (чат/титр/экшн-бар).
	 * По породе НЕ выбираем: её знает только сообщение о мазке, и такой выбор уводит
	 * время свежего дерева в таймер предыдущего помазанного.
	 */
	private static Timer pick(long secs) {
		// Ещё не синканный таймер (endMs=0) — первое же прочитанное время его и задаёт.
		for (Timer t : timers.values()) if (t.endMs == 0) return t;
		// Иначе — тот, чей остаток ближе к прочитанному значению.
		Timer best = null; long bd = Long.MAX_VALUE;
		for (Timer t : timers.values()) {
			long d = Math.abs((t.endMs - now()) / 1000 - secs);
			if (d < bd) { bd = d; best = t; }
		}
		return (best != null && bd <= 120) ? best : null;
	}

	/**
	 * Криттер появился — убираем дерево из плашки. Сообщение приходит у самого дерева,
	 * поэтому берём отработавший таймер, ближайший к игроку; если места нет ни у кого —
	 * тот, что отработал первым (дольше всех ждёт).
	 */
	private static void removeNearestExpired() {
		Minecraft mc = Minecraft.getInstance();
		Vec3 p = mc.player != null ? mc.player.position() : null;
		Timer near = null; double nd = Double.MAX_VALUE;   // ближайшее к игроку
		Timer old = null;                                  // отработавшее раньше всех
		for (Timer t : timers.values()) {
			if (t.endMs == 0 || t.endMs > now()) continue;   // ещё тикает — не оно
			if (old == null || t.endMs < old.endMs) old = t;
			if (p == null || t.pos == null) continue;
			double d = p.distanceToSqr(Vec3.atCenterOf(t.pos));
			if (d < nd) { nd = d; near = t; }
		}
		Timer best = near != null ? near : old;
		if (best != null) timers.remove(best.key);
	}

	/** Диагностика: раз в 5 секунд пишем в лог, откуда взяли время (искали долго). */
	private static long lastLog = 0;
	private static void logOnce(String source, long secs, String tree) {
		if (now() - lastLog < 5000) return;
		lastLog = now();
		com.ryn.skyryn.config.SkyLog.d("Critter: " + secs + "с (" + tree + ") источник — " + source);
	}


	// ===== Метки деревьев =====
	// Живут здесь, в honey-трекере: это его функция, тумблер тоже его. Метка —
	// короткая, с подписью, как места применения Icebreaker; столба в небо нет.

	private static final double[][] HONEY_TREES = {
			{ -618, 99, 233 }, { -549, 111, 298 }, { -535, 110, 275 }, { -512, 108, 259 },   // Torrhus, Helix
			{ -605, 115, 9 }, { -661, 115, -79 }, { -731, 120, 38 },                          // Galatea, Fig
			{ -611, 90, 32 }, { -610, 99, 94 }, { -717, 100, 38 },                            // Galatea, Mangrove
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

	/** Горшок с мёдом в руке — любой из трёх размеров. */
	private static boolean holdingHoneyPot(Minecraft mc) {
		for (var st : new net.minecraft.world.item.ItemStack[]{
				mc.player.getMainHandItem(), mc.player.getOffhandItem() }) {
			if (st != null && !st.isEmpty()
					&& st.getHoverName().getString().toLowerCase().contains("pot of honeycomb")) return true;
		}
		return false;
	}

	/** Короткая метка на каждом дереве. Зовётся из миксина. */
	public static void renderWorld(com.mojang.blaze3d.vertex.PoseStack ps,
								   net.minecraft.client.renderer.MultiBufferSource.BufferSource buf, Vec3 cam) {
		treeLabels.clear();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		// Режим меток: 0 — выключены, 1 — только с горшком в руке, 2 — всегда.
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

	/** Подписи деревьев — проекцией мир→экран, как подписи меток сафари. */
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

	// ===== Плашка =====
	private static void renderHud(GuiGraphicsExtractor ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
		drawTreeLabels(ctx, mc, mc.font);
		boolean hive = RynConfig.flag("hive.timer", true) && hiveEndMs > now();
		if (!RynConfig.critterTimer || (timers.isEmpty() && !hive) || !plaqueHere()) return;
		Font font = mc.font;
		if (RynConfig.flag("critter.plaque", true)) drawPlaque(ctx, font);

		// Вспышка за 5 сек. Место, размер, цвет, длительность и сам текст — из настроек
		// анонса. Свой текст подставляется целиком, поэтому в нём есть {tree}.
		long showMs = Announce.showMs(Announce.CRITTER);
		long dt = now() - flashAt;
		if (dt >= 0 && dt < showMs) {
			int a = Math.max(24, 255 - (int) (dt * 255 / showMs));
			String msg = Announce.text(Announce.CRITTER, "{tree}" + Lang.tr(" critter in 5s!", " криттер через 5с!"))
					.replace("{tree}", flashTree);
			Announce.draw(ctx, font, Announce.CRITTER, msg, null, a);
		}
	}

	// ===== Плашка: место и размер правятся мышкой в режиме правки HUD =====
	private static int plaqueW = 90, plaqueH = 30;   // габариты последней отрисовки
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

	/** Плашка таймеров в позиции и масштабе из конфига. Зовётся и с HUD, и с экрана правки. */
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
			// Алерт за 5 сек.
			if (rem <= 5 && rem >= 0 && !t.alerted) { t.alerted = true; flashAt = now(); flashTree = name; }
			// Ноль — не конец строки: криттер уже сидит на дереве и ждёт, пока за ним
			// придут. Строка так и висит, пока таймер не обновится или криттер не
			// появится (тогда Hypixel пишет «HONEY TREE! … has appeared!»).
			int col = rem <= 0 ? 0xFF8CE04A : (rem <= 10 ? 0xFFFF5A5A : (rem <= 60 ? 0xFFFFD24A : 0xFF5FD68A));
			String time = rem <= 0 ? Lang.tr("Spawned", "Появился") : fmt(rem);
			ctx.text(font, "§f" + name + " §7— ", 0, y, 0xFFFFFFFF, true);
			ctx.text(font, time, font.width(name + " — "), y, col, true);
			maxW = Math.max(maxW, font.width(name + " — " + time));
			y += 10;
		}
		// Ульи — одной строкой под деревьями: таймер у них общий.
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

	/**
	 * Показывать ли плашку здесь. Режим 0 — везде (таймер тикает, даже когда ты уехал
	 * на базар), 1 — только в Torrhus Canyon и его подзонах: деревья всё равно там,
	 * и в остальном мире плашка только занимает угол экрана.
	 */
	private static boolean plaqueHere() {
		// Остров берём из areas.json, а не ищем «torrhus» в названии зоны: половина
		// подзон каньона называется иначе (Hotspot Haven, Miria's Hut, Ant's Cave,
		// Desert Temple), и по имени они в проверку не попадали.
		String isl = SkyBlockCheck.currentIsland();
		return switch (RynConfig.getInt("critter.where", 3)) {
			case 0 -> "torrhus".equals(isl);
			case 1 -> "galatea".equals(isl);
			case 2 -> "torrhus".equals(isl) || "galatea".equals(isl);   // Foraging Island
			default -> true;                                            // везде
		};
	}

	/** Подпись в плашке: пока породу не узнали — просто «Дерево». */
	private static String label(Timer t) { return t.tree.isEmpty() ? Lang.tr("Tree", "Дерево") : t.tree; }
	private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
	private static String fmt(long sec) { return String.format("%d:%02d", sec / 60, sec % 60); }
	private static long now() { return System.currentTimeMillis(); }
	private static String strip(String s) { return s == null ? null : s.replaceAll("§.", ""); }
}
