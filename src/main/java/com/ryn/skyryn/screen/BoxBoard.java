package com.ryn.skyryn.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.AttributeLevels;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.data.ShardIcons;
import com.ryn.skyryn.data.ShardInfo;
import com.ryn.skyryn.data.ShardProgress;
import com.ryn.skyryn.fusion.FusionPlanner;
import com.ryn.skyryn.fusion.FusionState;
import com.ryn.skyryn.hud.HuntingFortune;

public class BoxBoard {
	static final int FACE      = 0xF21A1A24;
	static final int BG_BOTTOM = 0xF2131319;
	static final int SHADOW    = 0x66000000;
	static final int BORDER    = 0xFF2E2E3C;
	static final int ACCENT    = 0xFF5B8DEF;
	static final int EDGE_HI   = 0xFF2E2E3C;
	static final int EDGE_LO   = 0xFF14141C;
	static final int SLOT      = 0xFF1F1F29;
	static final int SLOT_DARK = 0xFF2A2A38;
	static final int INK       = 0xFFDDDEE6;
	static final int INK_DIM   = 0xFF9A9CAB;
	static final int HOVER     = 0x33FFFFFF;
	static final int GREEN     = 0xFF5FD68A;
	static final int ORANGE    = 0xFFD9A441;
	static final int REDINK    = 0xFFE06C6C;

	static final int ICON = 16;
	static final int COLS = 3;
	static final int VIS_ROWS = 3;
	static final int PAD = 4;
	static final int STRIP_H = 12;
	static final int ICON_BAR = 18;
	static final int DETAIL_W = 244;

	static int cell() { return Math.max(18, RynConfig.boxGuideSlot); }

	private record Entry(String key, String rarity, int cur) { }

	private static final List<Entry> entries = new ArrayList<>();
	private static boolean built = false;
	private static boolean ironman;
	private static int scroll = 0;
	private static boolean settingsOpen = false;
	private static boolean collapsed = false;

	public static boolean isCollapsed() { return collapsed; }

	private static String selected = null;
	private static int targetLevel = 0;
	private static FusionPlanner.Plan plan = null;
	private static int detailScroll = 0;

	private static final List<Zone> zones = new ArrayList<>();
	private record Zone(int x1, int y1, int x2, int y2, Runnable act) {
		boolean has(int mx, int my) { return mx >= x1 && mx <= x2 && my >= y1 && my <= y2; }
	}

	private static final List<int[]> panelRects = new ArrayList<>();
	public static boolean contains(int mx, int my) {
		for (int[] r : panelRects) if (mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3]) return true;
		return false;
	}

	private static java.util.List<String> tooltip = null;

	private static boolean dianaOnly(String key) {
		return ShardInfo.isDianaOnly(key);
	}

	public static void rebuild() {
		ironman = RynConfig.ironman;
		if (!ironman) com.ryn.skyryn.fusion.BazaarPrices.refreshIfNeeded();
		entries.clear();
		for (String key : ShardDb.allShards()) {
			ShardDb.Shard s = ShardDb.shard(key);
			if (s == null || !s.hasAttribute() || !ShardDb.hasRecipe(key)) continue;
			int cur = Math.max(0, ShardProgress.displayLevel(key));
			if (cur >= AttributeLevels.MAX_LEVEL) continue;
			entries.add(new Entry(key, s.rarity, cur));
		}
		sortEntries();
		built = true;
		scroll = 0;
	}

	private static void sortEntries() {
		entries.sort((a, b) -> {
			int r = rarityRank(b.rarity) - rarityRank(a.rarity);
			if (r != 0) return r;
			return ShardDb.displayName(a.key).compareTo(ShardDb.displayName(b.key));
		});
	}

	private static int rarityRank(String r) {
		return switch (r == null ? "" : r.toLowerCase()) {
			case "legendary" -> 5; case "epic" -> 4; case "rare" -> 3;
			case "uncommon" -> 2; case "common" -> 1; default -> 0;
		};
	}

	public static void ensureBuilt() { if (!built) rebuild(); }
	public static void invalidate() { built = false; selected = null; }

	public static int gridWidth() { return PAD * 2 + COLS * cell(); }

	public static void render(GuiGraphicsExtractor ctx, Font font, int gridX, int gridY, int availH,
							  int mouseX, int mouseY) {
		zones.clear();
		panelRects.clear();
		tooltip = null;
		ensureBuilt();

		if (!ShardProgress.known()) {
			panel(ctx, gridX, gridY, 150, 40);
			wrap(ctx, font, gridX + 6, gridY + 6, 138,
					Lang.tr("Open the Attribute Menu once.", "Открой Attribute Menu разок."));
			return;
		}

		drawGrid(ctx, font, gridX, gridY, availH, mouseX, mouseY);
		if (!collapsed) {
			if (settingsOpen) drawSettings(ctx, font, gridX, gridY, mouseX, mouseY);
			else if (selected != null) drawDetail(ctx, font, gridX, gridY, availH, mouseX, mouseY);
		}
		if (tooltip != null) drawTooltip(ctx, font, mouseX, mouseY);
	}

	private static void drawTooltip(GuiGraphicsExtractor ctx, Font font, int mx, int my) {
		int w = 0;
		for (String s : tooltip) w = Math.max(w, font.width(s));
		w += 8;
		int h = tooltip.size() * 10 + 4;
		int x = mx + 8, y = my - 12;
		int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		if (x + w > screenW - 2) x = mx - w - 8;
		if (y < 2) y = 2;
		ctx.fill(x, y, x + w, y + h, 0xF0100010);
		ctx.fill(x, y, x + w, y + 1, 0xFF9A3A3A);
		int ty = y + 3;
		for (String s : tooltip) { ctx.text(font, s, x + 4, ty, 0xFFEEDDDD, false); ty += 10; }
	}

	private static void drawSettings(GuiGraphicsExtractor ctx, Font font, int gx, int gy, int mouseX, int mouseY) {
		int w = 170;
		int x = gx - w - 3;
		if (x < 2) x = gx + gridWidth() + 3;
		int h = 18 + 2 * 14 + 16 + 16 + 8;
		panel(ctx, x, gy, w, h);
		int tx = x + 8, cy = gy + 6;
		ctx.text(font, Lang.tr("Settings", "Настройки"), tx, cy, INK, false);
		cy += 14;
		cy = toggle(ctx, font, x, w, cy, Lang.tr("Exclude Chameleon", "Искл. Chameleon"),
				RynConfig.excludeChameleon, mouseX, mouseY,
				() -> { RynConfig.excludeChameleon = !RynConfig.excludeChameleon; applySetting(); });
		cy = toggle(ctx, font, x, w, cy, Lang.tr("Exclude Wooden Bait", "Искл. Wooden Bait"),
				RynConfig.excludeWoodenBait, mouseX, mouseY,
				() -> { RynConfig.excludeWoodenBait = !RynConfig.excludeWoodenBait; applySetting(); });

		ctx.text(font, "Crocodile " + RynConfig.crocodileLevel, tx, cy + 2, INK, false);
		int cmx = x + w - 34;
		boolean cml = RynConfig.crocodileLevel > 0;
		miniBtn(ctx, font, "-", cmx, cy, cml, mouseX, mouseY);
		if (cml && inBox(mouseX, mouseY, cmx, cy, cmx + 12, cy + 11))
			zones.add(new Zone(cmx, cy, cmx + 12, cy + 11,
					() -> { RynConfig.crocodileLevel--; applySetting(); }));
		int cpx = x + w - 16;
		boolean cpl = RynConfig.crocodileLevel < RynConfig.CROCODILE_MAX;
		miniBtn(ctx, font, "+", cpx, cy, cpl, mouseX, mouseY);
		if (cpl && inBox(mouseX, mouseY, cpx, cy, cpx + 12, cy + 11))
			zones.add(new Zone(cpx, cy, cpx + 12, cy + 11,
					() -> { RynConfig.crocodileLevel++; applySetting(); }));
		cy += 16;

		ctx.text(font, Lang.tr("Slot size ", "Размер слота ") + RynConfig.boxGuideSlot, tx, cy + 2, INK, false);
		int mx = x + w - 34;
		boolean mh = RynConfig.boxGuideSlot > 18 && inBox(mouseX, mouseY, mx, cy, mx + 12, cy + 11);
		miniBtn(ctx, font, "-", mx, cy, RynConfig.boxGuideSlot > 18, mouseX, mouseY);
		if (mh) zones.add(new Zone(mx, cy, mx + 12, cy + 11, () -> { RynConfig.boxGuideSlot--; ConfigManager.save(); }));
		int px = x + w - 16;
		boolean ph = RynConfig.boxGuideSlot < 64 && inBox(mouseX, mouseY, px, cy, px + 12, cy + 11);
		miniBtn(ctx, font, "+", px, cy, RynConfig.boxGuideSlot < 64, mouseX, mouseY);
		if (ph) zones.add(new Zone(px, cy, px + 12, cy + 11, () -> { RynConfig.boxGuideSlot++; ConfigManager.save(); }));

		zones.add(new Zone(x, gy, x + w, gy + h, () -> { }));
	}

	private static int toggle(GuiGraphicsExtractor ctx, Font font, int x, int w, int y, String label,
							  boolean on, int mouseX, int mouseY, Runnable act) {
		boolean hover = inBox(mouseX, mouseY, x + 6, y, x + w - 6, y + 11);
		button(ctx, x + 6, y, 10, 10, hover);
		if (on) ctx.text(font, "✔", x + 7, y + 1, GREEN, false);
		ctx.text(font, label, x + 20, y + 1, INK, false);
		zones.add(new Zone(x + 6, y, x + w - 6, y + 11, act));
		return y + 14;
	}

	private static void applySetting() {
		ConfigManager.save();
		invalidate();
	}

	private static void drawGrid(GuiGraphicsExtractor ctx, Font font, int gx, int gy, int availH,
								 int mouseX, int mouseY) {
		int panelW = gridWidth();

		if (collapsed) {
			int sz = ICON_BAR;
			boolean hov = inBox(mouseX, mouseY, gx, gy, gx + sz, gy + sz);
			panel(ctx, gx, gy, sz, sz);
			if (hov) ctx.fill(gx + 1, gy + 1, gx + sz - 1, gy + sz - 1, HOVER);
			ctx.text(font, "⚡", gx + (sz - font.width("⚡")) / 2, gy + (sz - 8) / 2, hov ? INK : INK_DIM, false);
			zones.add(new Zone(gx, gy, gx + sz, gy + sz, BoxBoard::toggleCollapse));
			return;
		}

		String cg = "▾";
		int cbw = font.width(cg) + 8;
		int cbx = gx + panelW - cbw - 3;
		int gbw = font.width("⚙") + 8;
		int gbx = cbx - gbw - 3;
		int qbw = font.width("⌕") + 8;
		int qbx = gbx - qbw - 3;

		int searchH = searching ? 13 : 0;
		int gridTop = gy + STRIP_H + searchH;
		java.util.List<Entry> view = filtered();
		int visRows = VIS_ROWS;
		int totalRows = Math.max(1, (view.size() + COLS - 1) / COLS);
		int maxScroll = Math.max(0, totalRows - visRows);
		if (scroll > maxScroll) scroll = maxScroll;
		int rows = Math.min(visRows, totalRows);
		int panelH = STRIP_H + searchH + rows * cell() + PAD;

		panel(ctx, gx, gy, panelW, panelH);

		ctx.text(font, "⠿", gx + 4, gy + 2, INK_DIM, false);
		boolean ch = inBox(mouseX, mouseY, cbx, gy + 1, cbx + cbw, gy + STRIP_H - 1);
		button(ctx, cbx, gy + 1, cbw, STRIP_H - 2, ch);
		ctx.text(font, cg, cbx + 4, gy + 3, INK, false);
		zones.add(new Zone(cbx, gy + 1, cbx + cbw, gy + STRIP_H - 1, BoxBoard::toggleCollapse));

		boolean gh = inBox(mouseX, mouseY, gbx, gy + 1, gbx + gbw, gy + STRIP_H - 1);
		button(ctx, gbx, gy + 1, gbw, STRIP_H - 2, gh || settingsOpen);
		ctx.text(font, "⚙", gbx + 4, gy + 3, INK, false);
		zones.add(new Zone(gbx, gy + 1, gbx + gbw, gy + STRIP_H - 1, () -> toggleSettings()));

		boolean qh = inBox(mouseX, mouseY, qbx, gy + 1, qbx + qbw, gy + STRIP_H - 1);
		button(ctx, qbx, gy + 1, qbw, STRIP_H - 2, qh || searching);
		ctx.text(font, "⌕", qbx + 4, gy + 3, searching ? ACCENT : INK, false);
		zones.add(new Zone(qbx, gy + 1, qbx + qbw, gy + STRIP_H - 1, BoxBoard::toggleSearch));

		if (searching) {
			int fy = gy + STRIP_H;
			button(ctx, gx + 2, fy, panelW - 4, 11, false);
			boolean empty = searchQuery.isEmpty();
			String cur = empty ? "" : ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
			String shown = empty ? Lang.tr("search…", "поиск…") : searchQuery + cur;
			ctx.text(font, "⌕ " + shown, gx + 6, fy + 2, empty ? INK_DIM : INK, false);
		}

		if (view.isEmpty()) {
			wrap(ctx, font, gx + 4, gridTop + 2, panelW - 8, searchQuery.isEmpty()
					? Lang.tr("all maxed", "всё макс") : Lang.tr("nothing found", "ничего не найдено"));
			return;
		}

		int start = scroll * COLS;
		int end = Math.min(view.size(), start + visRows * COLS);
		for (int i = start; i < end; i++) {
			Entry e = view.get(i);
			int idx = i - start;
			int sx = gx + PAD + (idx % COLS) * cell();
			int sy = gridTop + (idx / COLS) * cell();
			boolean hover = mouseX >= sx && mouseX <= sx + cell() && mouseY >= sy && mouseY <= sy + cell();
			drawSlot(ctx, font, e, sx, sy, hover);
			if (dianaOnly(e.key) && inBox(mouseX, mouseY, sx + 1, sy + 1, sx + 8, sy + 8))
				tooltip = java.util.List.of(Lang.tr("Only during Diana mayor", "Только при мэре Diana"));
			zones.add(new Zone(sx, sy, sx + cell(), sy + cell(), () -> select(e.key)));
		}
	}

	private static java.util.List<Entry> filtered() {
		if (searchQuery.isEmpty()) return entries;
		String q = searchQuery.toLowerCase();
		java.util.List<Entry> out = new ArrayList<>();
		for (Entry e : entries) if (ShardDb.displayName(e.key).toLowerCase().contains(q)) out.add(e);
		return out;
	}

	private static boolean searching = false;
	private static String searchQuery = "";
	public static boolean isSearching() { return searching; }
	private static void toggleSearch() { searching = !searching; if (!searching) searchQuery = ""; scroll = 0; }
	public static void searchAppend(String s) { searchQuery += s; scroll = 0; }
	public static void searchBackspace() {
		if (!searchQuery.isEmpty()) searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
		scroll = 0;
	}
	public static void searchClose() { searching = false; }

	private static void drawSlot(GuiGraphicsExtractor ctx, Font font, Entry e, int x, int y, boolean hover) {
		int c = cell();
		vanillaSlot(ctx, x, y, c);
		int off = (c - ICON) / 2;
		drawIcon(ctx, font, e.key, x + off, y + off);
		if (hover) ctx.fill(x + 1, y + 1, x + c - 1, y + c - 1, HOVER);
		if (dianaOnly(e.key)) ctx.text(font, "★", x + 1, y + 1, 0xFFE04040, false);
		String lv = String.valueOf(e.cur);
		ctx.text(font, lv, x + c - 1 - font.width(lv), y + c - 8, 0xFFFFFFFF, true);
	}

	private static void drawIcon(GuiGraphicsExtractor ctx, Font font, String key, int x, int y) {
		ItemStack st = ShardIcons.get(key);
		if (st != null) {
			ctx.item(st, x, y);
		} else {
			String ch = ShardDb.displayName(key).substring(0, 1).toUpperCase();
			ctx.text(font, ch, x + 8 - font.width(ch) / 2, y + 4, INK, false);
		}
	}

	private static void drawDetail(GuiGraphicsExtractor ctx, Font font, int gx, int gy, int availH,
								   int mouseX, int mouseY) {
		ShardDb.Shard s = ShardDb.shard(selected);
		if (s == null) { selected = null; return; }
		if (plan == null) setTarget(targetLevel);

		int cur = Math.max(0, ShardProgress.displayLevel(selected));

		int dw = DETAIL_W;
		int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int dx = gx + gridWidth() + 3;
		if (dx + dw > screenW - 2) dx = gx - dw - 3;
		int dh = measureDetail(font, dw);
		int maxDS = Math.max(0, dh - availH);
		if (detailScroll > maxDS) detailScroll = maxDS;
		int dy = gy - detailScroll;

		panel(ctx, dx, dy, dw, dh);
		int tx = dx + 8;

		vanillaSlot(ctx, dx + 6, dy + 6, 18);
		drawIcon(ctx, font, selected, dx + 7, dy + 7);
		ctx.text(font, s.name, dx + 28, dy + 8, INK, false);
		ctx.text(font, s.rarity, dx + 28, dy + 18, INK_DIM, false);
		if (inBox(mouseX, mouseY, dx + 28, dy + 6, dx + 28 + font.width(s.name), dy + 16)) {
			java.util.List<String> tt = descTooltip(font, s);
			if (!tt.isEmpty()) tooltip = tt;
		}
		boolean xh = inBox(mouseX, mouseY, dx + dw - 14, dy + 5, dx + dw - 4, dy + 15);
		ctx.text(font, "✕", dx + dw - 13, dy + 6, xh ? REDINK : INK, false);
		zones.add(new Zone(dx + dw - 15, dy + 4, dx + dw - 3, dy + 16, () -> selected = null));

		if (RynConfig.hunterFortune > 0) {
			String f = "Fortune " + (int) RynConfig.hunterFortune;
			int fx = dx + dw - 6 - font.width(f);
			ctx.text(font, f, fx, dy + 18, INK_DIM, false);
			if (inBox(mouseX, mouseY, fx, dy + 16, dx + dw - 6, dy + 26))
				tooltip = java.util.List.of(
						Lang.tr("Hunter Fortune — the farm time below is counted from it.",
								"Hunter Fortune — по ней считается время фарма ниже."),
						Lang.tr("Read from Stats Breakdown; you can also type it in the calculator.",
								"Читается из Stats Breakdown; ещё её можно вбить в калькуляторе."),
						Lang.tr("Updated: ", "Обновлено: ") + HuntingFortune.age());
		}

		int y = dy + 30;
		if (s.attrTitle != null && !s.attrTitle.isEmpty()) {
			ctx.text(font, s.attrTitle, tx, y, INK_DIM, false);
			y += 12;
		}

		String pre = Lang.tr("Level ", "Уровень ") + cur + " → ";
		ctx.text(font, pre, tx, y, INK, false);
		int stepX = tx + font.width(pre);
		boolean minusOn = targetLevel > cur + 1;
		miniBtn(ctx, font, "-", stepX, y - 1, minusOn, mouseX, mouseY);
		if (minusOn) zones.add(new Zone(stepX, y - 1, stepX + 11, y + 10, () -> setTarget(targetLevel - 1)));
		String tl = String.valueOf(targetLevel);
		ctx.text(font, tl, stepX + 16, y, ACCENT, false);
		int plusX = stepX + 16 + font.width(tl) + 4;
		boolean plusOn = targetLevel < AttributeLevels.MAX_LEVEL;
		miniBtn(ctx, font, "+", plusX, y - 1, plusOn, mouseX, mouseY);
		if (plusOn) zones.add(new Zone(plusX, y - 1, plusX + 11, y + 10, () -> setTarget(targetLevel + 1)));
		y += 14;

		bar(ctx, tx, y, dw - 16, cur, cur, targetLevel);
		y += 9;

		int total = FusionPlanner.shardsForLevels(s.rarity, cur, targetLevel);
		ctx.text(font, Lang.tr("Need ", "Нужно ") + total + "× " + s.name, tx, y, INK, false);
		y += 12;

		if (plan != null && !plan.steps.isEmpty()) {
			ctx.text(font, Lang.tr("Fuse:", "Фьюз:"), tx, y, INK_DIM, false);
			y += 11;
			int n = 1;
			for (FusionPlanner.Step st : plan.steps) {
				y = drawWrap(ctx, font, n + ". " + stepInputs(st), tx, y, dw - 16, INK);
				y = drawWrap(ctx, font, "   → " + st.outputAmount + "× " + ShardDb.displayName(st.output), tx, y, dw - 16, INK_DIM);
				n++;
			}
			y += 2;
		}
		if (plan != null && !plan.farm.isEmpty()) {
			ctx.text(font, Lang.tr("Materials (click):", "Материалы (клик):"), tx, y, INK_DIM, false);
			y += 11;
			int chipX = tx;
			for (Map.Entry<String, Integer> f : plan.farm.entrySet()) {
				boolean buy = ShardInfo.hasPurchase(f.getKey());
				String t = (buy ? "🛒 " : "") + f.getValue() + "× " + ShardDb.displayName(f.getKey());
				int cw = font.width(t) + 8;
				if (chipX + cw > dx + dw - 8) { chipX = tx; y += 13; }
				boolean hov = inBox(mouseX, mouseY, chipX, y, chipX + cw, y + 11);
				button(ctx, chipX, y, cw, 11, hov);
				ctx.text(font, t, chipX + 4, y + 2, buy ? GREEN : INK, false);
				final String fk = f.getKey();
				zones.add(new Zone(chipX, y, chipX + cw, y + 11, () -> openShard(fk)));
				chipX += cw + 3;
			}
			y += 14;
		}
		String tot = totalLine();
		if (tot != null) { ctx.text(font, tot, tx, y, INK, false); y += 11; }
		if (plan != null && plan.hasBuy)
			ctx.text(font, Lang.tr("🛒 can be bought from NPC", "🛒 можно купить у NPC"), tx, y, GREEN, false);

		int by = dy + dh - 16;
		boolean bh = inBox(mouseX, mouseY, dx + 6, by, dx + dw - 6, by + 12);
		button(ctx, dx + 6, by, dw - 12, 12, bh);
		String bl = Lang.tr("Open in calculator", "Открыть в калькуляторе");
		ctx.text(font, bl, dx + (dw - font.width(bl)) / 2, by + 2, INK, false);
		zones.add(new Zone(dx + 6, by, dx + dw - 6, by + 12, BoxBoard::openInCalculator));

		zones.add(new Zone(dx, dy, dx + dw, dy + dh, () -> { }));
	}

	private static String stepInputs(FusionPlanner.Step st) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Map.Entry<String, Integer> in : st.inputs.entrySet()) {
			if (!first) sb.append(" + ");
			sb.append(in.getValue()).append("× ").append(ShardDb.displayName(in.getKey()));
			first = false;
		}
		return sb.toString();
	}

	private static int measureDetail(Font font, int dw) {
		ShardDb.Shard s = ShardDb.shard(selected);
		int h = 30;
		if (s != null && s.attrTitle != null && !s.attrTitle.isEmpty()) h += 12;
		h += 14 + 9 + 12;
		if (plan != null && !plan.steps.isEmpty()) {
			h += 11 + 2;
			for (FusionPlanner.Step st : plan.steps) {
				h += splitToWidth(font, "9. " + stepInputs(st), dw - 16).size() * 10;
				h += splitToWidth(font, "   → " + st.outputAmount + "× " + ShardDb.displayName(st.output), dw - 16).size() * 10;
			}
		}
		if (plan != null && !plan.farm.isEmpty()) h += 11 + deficitRows(font, dw) * 13 + 3;
		if (totalLine() != null) h += 12;
		if (plan != null && plan.hasBuy) h += 11;
		h += 20;
		return h;
	}

	private static int deficitRows(Font font, int dw) {
		if (plan == null || plan.farm.isEmpty()) return 0;
		int rows = 1, chipX = 0;
		for (Map.Entry<String, Integer> f : plan.farm.entrySet()) {
			int cw = font.width("+" + f.getValue() + "× " + ShardDb.displayName(f.getKey())) + 11;
			if (chipX + cw > dw - 16) { chipX = 0; rows++; }
			chipX += cw;
		}
		return rows;
	}

	private static String totalLine() {
		if (plan == null) return null;
		if (ironman) {
			if (plan.total > 0) {
				String s = Lang.tr("Farm time ~", "Время фарма ~") + fmtTime(plan.total);
				if (plan.hasUnfarmable) s += Lang.tr(" + boss/dungeon", " + боссы/данж");
				return s;
			}
			if (plan.hasUnfarmable) return Lang.tr("boss/dungeon drops", "дроп с боссов/данжа");
			return null;
		}
		return plan.total > 0 ? Lang.tr("Cost ~", "Стоимость ~") + fmtMoney(plan.total) : null;
	}

	private static String fmtTime(double hours) {
		if (hours < 1) return Math.max(1, Math.round(hours * 60)) + Lang.tr(" min", " мин");
		return String.format("%.1f", hours) + Lang.tr(" h", " ч");
	}

	private static String fmtMoney(double v) {
		double a = Math.abs(v);
		if (a >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
		if (a >= 1_000) return String.format("%.0fk", v / 1_000);
		return String.format("%.0f", v);
	}

	private static void select(String key) {
		settingsOpen = false;
		if (key != null && key.equals(selected)) { selected = null; return; }
		selected = key;
		detailScroll = 0;
		int cur = Math.max(0, ShardProgress.displayLevel(key));
		setTarget(Math.min(AttributeLevels.MAX_LEVEL, cur + 1));
	}

	private static void setTarget(int lvl) {
		int cur = selected == null ? 0 : Math.max(0, ShardProgress.displayLevel(selected));
		targetLevel = Math.max(cur + 1, Math.min(AttributeLevels.MAX_LEVEL, lvl));
		if (selected == null) { plan = null; return; }
		plan = FusionPlanner.planForLevel(selected, targetLevel, ironman);
	}

	private static void openShard(String key) {
		Minecraft mc = Minecraft.getInstance();
		mc.schedule(() -> mc.setScreen(new ShardPageScreen(key, mc.screen)));
	}

	private static void openInCalculator() {
		if (selected == null) return;
		ShardDb.Shard s = ShardDb.shard(selected);
		int cur = Math.max(0, ShardProgress.displayLevel(selected));
		int need = s == null ? 0 : FusionPlanner.shardsForLevels(s.rarity, cur, targetLevel);
		String[] top = plan == null ? null : FusionPlanner.topInputs(plan);
		if (top != null) FusionState.set(selected, Math.max(1, need), top[0], top[1]);
		else FusionState.set(selected, Math.max(1, need));
	}

	public static boolean click(int mouseX, int mouseY) {
		for (Zone z : zones) if (z.has(mouseX, mouseY)) { z.act.run(); return true; }
		return false;
	}

	public static void closeSettings() { settingsOpen = false; }
	public static void closePopups() { settingsOpen = false; selected = null; }

	public static void scroll(double dir) {
		if (selected != null && !settingsOpen) detailScroll = Math.max(0, detailScroll - (int) Math.signum(dir) * 14);
		else scroll = Math.max(0, scroll - (int) Math.signum(dir));
	}
	public static boolean popupOpen() { return selected != null; }
	private static void toggleSettings() { settingsOpen = !settingsOpen; }

	public static void toggleCollapse() {
		collapsed = !collapsed;
		if (collapsed) { settingsOpen = false; selected = null; }
	}

	private static void roundRect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1 + 1, y1, x2 - 1, y2, color);
		ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	private static void roundOutline(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1 + 1, y1, x2 - 1, y1 + 1, color);
		ctx.fill(x1 + 1, y2 - 1, x2 - 1, y2, color);
		ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	private static void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		panelRects.add(new int[] { x, y, x + w, y + h });
		roundRect(ctx, x + 2, y + 2, x + w + 2, y + h + 2, SHADOW);
		ctx.fillGradient(x + 1, y, x + w - 1, y + h, FACE, BG_BOTTOM);
		ctx.fill(x, y + 1, x + 1, y + h - 1, FACE);
		ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, BG_BOTTOM);
		roundOutline(ctx, x, y, x + w, y + h, BORDER);
	}

	private static void vanillaSlot(GuiGraphicsExtractor ctx, int x, int y, int sz) {
		roundRect(ctx, x, y, x + sz, y + sz, SLOT);
		roundOutline(ctx, x, y, x + sz, y + sz, BORDER);
	}

	private static void button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean hover) {
		roundRect(ctx, x, y, x + w, y + h, hover ? SLOT_DARK : SLOT);
		roundOutline(ctx, x, y, x + w, y + h, hover ? ACCENT : BORDER);
	}

	private static void miniBtn(GuiGraphicsExtractor ctx, Font font, String s, int x, int y,
							   boolean on, int mx, int my) {
		boolean hover = on && inBox(mx, my, x, y, x + 11, y + 11);
		button(ctx, x, y, 11, 11, hover);
		ctx.text(font, s, x + 3, y + 2, on ? INK : INK_DIM, false);
	}

	private static void bar(GuiGraphicsExtractor ctx, int x, int y, int w, int cur, int boxMax, int target) {
		ctx.fill(x, y, x + w, y + 4, SLOT_DARK);
		int max = AttributeLevels.MAX_LEVEL;
		int cW = w * cur / max, bW = w * Math.max(cur, boxMax) / max, tW = w * target / max;
		ctx.fill(x, y, x + cW, y + 4, 0xFF4A6BB0);
		if (bW > cW) ctx.fill(x + cW, y, x + bW, y + 4, GREEN);
		if (tW > bW) ctx.fill(x + bW, y, x + tW, y + 4, ORANGE);
	}

	private static void wrap(GuiGraphicsExtractor ctx, Font font, int x, int y, int w, String s) {
		for (var line : font.getSplitter().splitLines(net.minecraft.network.chat.Component.literal(s), w,
				net.minecraft.network.chat.Style.EMPTY)) {
			ctx.text(font, line.getString(), x, y, INK, false);
			y += 10;
		}
	}

	private static int drawWrap(GuiGraphicsExtractor ctx, Font font, String s, int x, int y, int w, int color) {
		for (String line : splitToWidth(font, s, w)) { ctx.text(font, line, x, y, color, false); y += 10; }
		return y;
	}

	private static java.util.List<String> splitToWidth(Font font, String s, int w) {
		java.util.List<String> out = new ArrayList<>();
		for (var line : font.getSplitter().splitLines(net.minecraft.network.chat.Component.literal(s), w,
				net.minecraft.network.chat.Style.EMPTY))
			out.add(line.getString());
		if (out.isEmpty()) out.add(s);
		return out;
	}

	private static java.util.List<String> descTooltip(Font font, ShardDb.Shard s) {
		java.util.List<String> out = new ArrayList<>();
		if (s.attrTitle != null && !s.attrTitle.isEmpty()) out.add("§e" + s.attrTitle);
		String d = s.attrDescShown();
		if (d != null && !d.isEmpty()) out.addAll(splitToWidth(font, d, 200));
		return out;
	}

	private static boolean inBox(int mx, int my, int x1, int y1, int x2, int y2) {
		return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
	}
}
