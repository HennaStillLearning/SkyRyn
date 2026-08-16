package com.ryn.skyryn.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import java.util.Map;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.fusion.BazaarPrices;
import com.ryn.skyryn.screen.FusionTopScreen;
import com.ryn.skyryn.screen.HudEditScreen;
import com.ryn.skyryn.screen.ShardListScreen;
import com.ryn.skyryn.screen.ShardPageScreen;
import com.ryn.skyryn.waypoint.SkyBlockCheck;

public class HuntingHud {
	private static final int BG         = 0xD2141419;
	private static final int BG_EDIT    = 0xE01A2438;
	private static final int BORDER     = 0xFF2E2E3C;
	private static final int ACCENT     = 0xFF5B8DEF;
	private static final int SURFACE_HI = 0xFF272733;
	private static final int TEXT       = 0xFFFFFFFF;
	private static final int LABEL      = 0xFFB9BCC7;
	private static final int TEXT_FAINT = 0xFF7E8496;
	private static final int GREEN      = 0xFF5FD68A;
	private static final int GOLD       = 0xFFFFD24A;
	private static final int MOBS       = 0xFF6FC7E0;
	private static final int ZEBRA      = 0x14FFFFFF;

	private static final int WIDTH = 160;
	private static final int TOP_SHARDS = 4;

	private static boolean editing = false;
	private static int lastH = 60;

	private static boolean dropdownOpen = false;
	private static int pillX1, pillY1, pillX2, pillY2;
	private static final int DROPDOWN_ROW_H = 12;
	private static final int DROPDOWN_W = 74;

	public static void setEditing(boolean v) { editing = v; }
	public static boolean isEditing() { return editing; }
	public static int height() { return lastH; }
	public static int width() { return (int) (WIDTH * RynConfig.huntHudScale); }

	public static boolean isDropdownOpen() { return dropdownOpen; }
	public static void closeDropdown() { dropdownOpen = false; }

	public static boolean pillOver(int mx, int my) {
		return mx >= pillX1 && mx <= pillX2 && my >= pillY1 && my <= pillY2;
	}

	public static void togglePill() { dropdownOpen = !dropdownOpen; }

	private static int[] dropdownBounds() {
		int x2 = pillX2, x1 = x2 - DROPDOWN_W;
		int y1 = pillY2 + 2, y2 = y1 + DROPDOWN_ROW_H * 4;
		return new int[] {x1, y1, x2, y2};
	}

	public static boolean dropdownOver(int mx, int my) {
		if (!dropdownOpen) return false;
		int[] b = dropdownBounds();
		return mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3];
	}

	public static int dropdownRowAt(int mx, int my) {
		if (!dropdownOver(mx, my)) return -1;
		int[] b = dropdownBounds();
		return Math.max(0, Math.min(3, (my - b[1]) / DROPDOWN_ROW_H));
	}

	public static String modeName(int mode) {
		return switch (mode) {
			case 1 -> Lang.tr("Total", "Всего");
			case 2 -> Lang.tr("Per hour", "В час");
			case 3 -> Lang.tr("Timer", "Таймер");
			default -> Lang.tr("Session", "Сессия");
		};
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyryn", "hunting"),
				(ctx, tickCounter) -> render(ctx));
	}

	public static boolean active() {
		if (!RynConfig.huntingTrackerEnabled) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return false;
		if (!SkyBlockCheck.onSkyBlock()) return false;
		return HuntingTracker.totalShards > 0 || HuntingTracker.sessionShards > 0;
	}

	public static boolean hiddenOn(Screen s) {
		if (s == null) return false;
		if (s instanceof FusionTopScreen || s instanceof HudEditScreen
				|| s instanceof ShardListScreen || s instanceof ShardPageScreen) return true;
		return s.getClass().getName().startsWith("dev.isxander.yacl3");
	}

	public static boolean over(int mx, int my) {
		return mx >= RynConfig.huntHudX && mx <= RynConfig.huntHudX + width()
				&& my >= RynConfig.huntHudY && my <= RynConfig.huntHudY + height();
	}

	public static void rightClick() {
		if (RynConfig.huntTrackerMode == RynConfig.TRACKER_TIMER) {
			HuntingTracker.stopTimer();
		} else {
			RynConfig.huntInstaSell = !RynConfig.huntInstaSell;
		}
	}

	public static void middleClick() {
		HuntingTracker.startTimer();
		RynConfig.huntTrackerMode = RynConfig.TRACKER_TIMER;
	}

	private static void render(GuiGraphicsExtractor ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!active()) return;
		if (mc.screen != null) {
			if (editing) return;
			if (hiddenOn(mc.screen)) return;
		}

		BazaarPrices.refreshIfNeeded();

		drawAt(ctx, mc.font);
		drawPauseAnnounce(ctx, mc);
		if (dropdownOpen) drawDropdown(ctx, mc.font);
	}

	private static void drawDropdown(GuiGraphicsExtractor ctx, Font f) {
		int[] b = dropdownBounds();
		roundRect(ctx, b[0], b[1], b[2], b[3], BG_EDIT);
		ctx.fill(b[0], b[1], b[2], b[1] + 1, ACCENT);
		ctx.fill(b[0], b[1], b[0] + 1, b[3], BORDER);
		ctx.fill(b[2] - 1, b[1], b[2], b[3], BORDER);
		ctx.fill(b[0], b[3] - 1, b[2], b[3], BORDER);

		int cur = RynConfig.huntTrackerMode;
		for (int i = 0; i < 4; i++) {
			int ry = b[1] + i * DROPDOWN_ROW_H + 2;
			boolean selected = i == cur;
			if (selected) ctx.fill(b[0] + 1, b[1] + i * DROPDOWN_ROW_H, b[2] - 1, b[1] + (i + 1) * DROPDOWN_ROW_H, SURFACE_HI);
			ctx.text(f, (selected ? "› " : "  ") + modeName(i), b[0] + 4, ry, selected ? ACCENT : TEXT, true);
		}
	}

	private static final long ANNOUNCE_MS = 4000;
	private static boolean wasPaused = false;
	private static long announceAt = 0;
	private static boolean announceResume = false;

	private static void drawPauseAnnounce(GuiGraphicsExtractor ctx, Minecraft mc) {
		boolean paused = HuntingTracker.idle();
		if (paused != wasPaused) {
			wasPaused = paused;
			announceAt = System.currentTimeMillis();
			announceResume = !paused;
		}
		if (!RynConfig.huntPauseAnnounce) return;
		if (announceAt == 0) return;
		if (System.currentTimeMillis() - announceAt > ANNOUNCE_MS) return;

		String text = announceResume
				? Lang.tr("▶ Tracker resumed", "▶ Трекер продолжил")
				: Lang.tr("⏸ Tracker paused — no shards for ", "⏸ Трекер на паузе — нет шардов ")
						+ RynConfig.huntIdleSeconds + Lang.tr(" sec", " сек");
		int color = announceResume ? GREEN : GOLD;

		int w = mc.font.width(text);
		int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
		int y = mc.getWindow().getGuiScaledHeight() - 75;
		ctx.fill(x - 5, y - 4, x + w + 5, y + 12, 0xC0141419);
		ctx.fill(x - 5, y - 4, x + w + 5, y - 3, color);
		ctx.text(mc.font, text, x, y, color, true);
	}

	public static void drawAt(GuiGraphicsExtractor ctx, Font f) {
		ctx.pose().pushMatrix();
		ctx.pose().translate(RynConfig.huntHudX, RynConfig.huntHudY);
		ctx.pose().scale(RynConfig.huntHudScale, RynConfig.huntHudScale);
		draw(ctx, f);
		ctx.pose().popMatrix();
	}

	private static void roundRect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1 + 1, y1, x2 - 1, y2, color);
		ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	private static void draw(GuiGraphicsExtractor ctx, Font f) {
		final int mode = RynConfig.huntTrackerMode;
		final boolean total = mode == RynConfig.TRACKER_TOTAL;
		final boolean perHour = mode == RynConfig.TRACKER_PER_HOUR;
		final boolean timer = mode == RynConfig.TRACKER_TIMER;

		Map<String, Integer> caught = timer ? HuntingTracker.timerCaught()
				: total ? HuntingTracker.totalCaught : HuntingTracker.sessionCaught;
		Map<HuntingTracker.Source, Integer> bySrc = timer ? HuntingTracker.timerBySource()
				: total ? HuntingTracker.totalBySource : HuntingTracker.sessionBySource;
		Map<String, Integer> catchesByShard = timer ? HuntingTracker.timerCatchesByShard()
				: total ? HuntingTracker.totalCatchesByShard : HuntingTracker.sessionCatchesByShard;

		int rows = Math.min(TOP_SHARDS, caught.size());
		int srcRows = RynConfig.huntShowSources ? bySrc.size() : 0;
		boolean showLast = HuntingTracker.lastCaughtKey != null;
		int h = 16 + 44 + 11
				+ (showLast ? 11 : 0)
				+ (rows > 0 ? 4 + rows * 10 : 0) + (srcRows > 0 ? 4 + srcRows * 10 : 0) + 6;
		lastH = (int) (h * RynConfig.huntHudScale);

		roundRect(ctx, 0, 0, WIDTH, h, editing ? BG_EDIT : BG);
		ctx.fill(1, 0, WIDTH - 1, 1, ACCENT);
		ctx.fill(0, h - 1, WIDTH, h, BORDER);
		ctx.fill(0, 1, 1, h - 1, BORDER);
		ctx.fill(WIDTH - 1, 1, WIDTH, h - 1, BORDER);

		int lx = 6, rx = WIDTH - 6, y = 5;
		boolean paused = perHour && HuntingTracker.idle();
		ctx.text(f, Lang.tr("HUNTING", "ХАНТИНГ") + (paused ? " ⏸" : ""), lx, y,
				paused ? GOLD : (editing ? ACCENT : LABEL), true);

		String pill; int pillColor;
		if (timer) {
			if (HuntingTracker.timerFrozen()) { pill = Lang.tr("done", "готово"); pillColor = GREEN; }
			else if (HuntingTracker.timerRunning()) { pill = fmtTime(HuntingTracker.timerRemainingMs()); pillColor = GOLD; }
			else { pill = HuntingTracker.timerMinutes() + Lang.tr("m ▷", "м ▷"); pillColor = GOLD; }
		} else {
			pill = RynConfig.huntTrackerModeName(); pillColor = editing ? ACCENT : TEXT_FAINT;
		}
		int pw = f.width(pill);
		roundRect(ctx, rx - pw - 5, y - 2, rx + 2, y + 9, dropdownOpen ? ACCENT : SURFACE_HI);
		ctx.text(f, pill, rx - pw, y, dropdownOpen ? TEXT : pillColor, true);
		float scale = RynConfig.huntHudScale;
		pillX1 = RynConfig.huntHudX + Math.round((rx - pw - 5) * scale);
		pillY1 = RynConfig.huntHudY + Math.round((y - 2) * scale);
		pillX2 = RynConfig.huntHudX + Math.round((rx + 2) * scale);
		pillY2 = RynConfig.huntHudY + Math.round((y + 9) * scale);
		y += 12;

		String mobs, shards, value, xp;
		double val = HuntingTracker.value(caught, RynConfig.huntInstaSell);
		if (perHour) {
			mobs = fmt(HuntingTracker.catchesPerHour());
			shards = fmt(HuntingTracker.shardsPerHour());
			value = fmt(HuntingTracker.valuePerHour(RynConfig.huntInstaSell));
			xp = fmt(HuntingTracker.xpPerHour());
		} else if (timer) {
			mobs = String.valueOf(HuntingTracker.timerCatches());
			shards = String.valueOf(HuntingTracker.timerShards());
			value = fmt(val);
			xp = fmt(HuntingTracker.timerXp());
		} else if (total) {
			mobs = String.valueOf(HuntingTracker.totalCatches);
			shards = String.valueOf(HuntingTracker.totalShards);
			value = fmt(val);
			xp = fmt(HuntingTracker.totalXp);
		} else {
			mobs = String.valueOf(HuntingTracker.sessionCatches);
			shards = String.valueOf(HuntingTracker.sessionShards);
			value = fmt(val);
			xp = fmt(HuntingTracker.sessionXp);
		}

		row(ctx, f, Lang.tr("Mobs", "Мобов"), mobs, lx, rx, y, MOBS); y += 11;
		row(ctx, f, Lang.tr("Shards", "Шардов"), shards, lx, rx, y, TEXT); y += 11;
		row(ctx, f, RynConfig.huntInstaSell ? Lang.tr("Sell instantly", "Продать сразу") : Lang.tr("Via offer", "Через оффер"), value, lx, rx, y, GREEN); y += 11;
		row(ctx, f, "Hunting XP", xp, lx, rx, y, TEXT); y += 11;

		if (RynConfig.hunterFortune > 0) {
			row(ctx, f, Lang.tr("Fortune", "Фортуна"), String.format("%.0f (×%.2f)",
					RynConfig.hunterFortune, HuntingFortune.dropsPerCatch()), lx, rx, y, TEXT);
		} else {
			row(ctx, f, Lang.tr("Fortune", "Фортуна"), Lang.tr("none — /sr setfortune", "нет — /sr setfortune"), lx, rx, y, TEXT_FAINT);
		}
		y += 11;

		if (showLast) {
			int cnt = catchesByShard.getOrDefault(HuntingTracker.lastCaughtKey, 0);
			String lastVal = fit(f, cnt + "x " + ShardDb.displayName(HuntingTracker.lastCaughtKey), 96);
			row(ctx, f, Lang.tr("Last", "Последнее"), lastVal, lx, rx, y, TEXT);
			y += 11;
		}

		if (rows > 0) {
			ctx.fill(lx, y, rx, y + 1, BORDER);
			y += 4;
			int i = 0;
			for (Map.Entry<String, Integer> e : caught.entrySet().stream()
					.sorted((a, b) -> Double.compare(shardValue(b), shardValue(a)))
					.limit(TOP_SHARDS).toList()) {
				if (i % 2 == 1) ctx.fill(lx - 2, y + i * 10 - 1, rx + 2, y + i * 10 + 9, ZEBRA);
				String left = e.getValue() + "x " + ShardDb.displayName(e.getKey());
				ctx.text(f, fit(f, left, 96), lx, y + i * 10, TEXT, true);
				String v = fmt(shardValue(e));
				ctx.text(f, v, rx - f.width(v), y + i * 10, LABEL, true);
				i++;
			}
			y += rows * 10;
		}

		if (srcRows > 0) {
			ctx.fill(lx, y, rx, y + 1, BORDER);
			y += 4;
			int i = 0;
			for (Map.Entry<HuntingTracker.Source, Integer> e : bySrc.entrySet()) {
				ctx.text(f, e.getKey().label(), lx, y + i * 10, TEXT_FAINT, true);
				String v = String.valueOf(e.getValue());
				ctx.text(f, v, rx - f.width(v), y + i * 10, LABEL, true);
				i++;
			}
		}
	}

	private static double shardValue(Map.Entry<String, Integer> e) {
		return HuntingTracker.value(Map.of(e.getKey(), e.getValue()), RynConfig.huntInstaSell);
	}

	private static void row(GuiGraphicsExtractor ctx, Font f, String label, String value,
							int lx, int rx, int y, int color) {
		ctx.text(f, label, lx, y, LABEL, true);
		ctx.text(f, value, rx - f.width(value), y, color, true);
	}

	private static String fit(Font f, String s, int maxW) {
		if (f.width(s) <= maxW) return s;
		return f.plainSubstrByWidth(s, maxW - f.width("…")) + "…";
	}

	private static String fmtTime(long ms) {
		long totalSec = ms / 1000;
		return String.format("%d:%02d", totalSec / 60, totalSec % 60);
	}

	private static String fmt(double v) {
		double a = Math.abs(v);
		if (a >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
		if (a >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
		if (a >= 1_000) return String.format("%.1fk", v / 1_000);
		return String.format("%.0f", v);
	}
}
