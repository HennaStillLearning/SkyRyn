package com.ryn.skyryn.screen;

import net.minecraft.client.Minecraft;
import com.ryn.skyryn.config.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.fusion.BazaarPrices;
import com.ryn.skyryn.fusion.FusionState;
import com.ryn.skyryn.fusion.FusionTop;

public class FusionTopScreen extends Screen {
	private static final int BG          = 0xF0141419;
	private static final int CARD        = 0xFF1A1A24;
	private static final int CARD_HOVER  = 0xFF23232F;
	private static final int BORDER      = 0xFF2E2E3C;
	private static final int ACCENT      = 0xFF5B8DEF;
	private static final int ACCENT_SOFT = 0xFF2A3F63;
	private static final int TEXT        = 0xFFDDDEE6;
	private static final int TEXT_DIM    = 0xFF9A9CAB;
	private static final int TEXT_FAINT  = 0xFF5E606E;
	private static final int GREEN       = 0xFF5FD68A;
	private static final int RED         = 0xFFE06C6C;
	private static final int WARN_BAD    = 0xFFE06C6C;
	private static final int WARN_SOFT   = 0xFFD9A441;
	private static final int WARN_INFO   = 0xFF7E8496;

	private static final double SLIPPAGE_WARN = 0.05;

	private static final int SEARCH_W = 110;
	private int searchX = 0;

	private static final int ROW_H = 18;
	private static final int WIDTH = 560;

	private static final int C_UNIT   = 268;
	private static final int C_DEMAND = 196;
	private static final int C_INVEST = 124;
	private static final int C_BATCH  = 120;
	private static final int C_DAY    = 8;

	private boolean xpTab = false;
	private boolean xpInstaSell = true;
	private int xpModeX = 0, xpModeW = 0;
	private FusionTop.Sort sort = com.ryn.skyryn.config.RynConfig.ironman
			? FusionTop.Sort.FARM_FAST : FusionTop.Sort.PER_DAY;
	private boolean profitableOnly = true;
	private boolean hideWarned = false;
	private boolean showHelp = false;
	private int scroll = 0;

	private String editingBatch = null;
	private String batchBuffer = "";
	private boolean editingWisdom = false;
	private String wisdomBuffer = "";
	private String search = "";
	private boolean searchFocused = false;
	private boolean searchAllSelected = false;

	private static String trimNum(float v) {
		return v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
	}

	private List<FusionTop.Entry> entries = List.of();

	private String[] tooltip = null;
	private int tooltipX, tooltipY;

	public FusionTopScreen() {
		super(Component.literal(Lang.tr("RynFusion — Fusion Top", "RynFusion — Топ фьюзов")));
	}

	@Override
	protected void init() {
		BazaarPrices.refreshIfNeeded();
		rebuild();
	}

	private void rebuild() {
		entries = FusionTop.build(sort, profitableOnly, hideWarned, xpInstaSell);
		if (!search.isBlank()) {
			String q = search.toLowerCase();
			entries = entries.stream()
					.filter(e -> ShardDb.displayName(e.shard).toLowerCase().contains(q))
					.toList();
		}
		scroll = 0;
	}

	private int listX() { return (this.width - WIDTH) / 2; }
	private int listTop() { return 76; }
	private int listBottom() { return this.height - 18; }
	private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW_H); }

	private static int warnColor(BazaarPrices.Warning w) {
		return switch (w.severity) {
			case 2 -> WARN_BAD;
			case 1 -> WARN_SOFT;
			default -> WARN_INFO;
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		tooltip = null;
		ctx.fill(0, 0, this.width, this.height, BG);

		int x = listX();
		int right = x + WIDTH;

		int t1w = this.font.width(Lang.tr("FUSION TOP", "ТОП ФЬЮЗОВ"));
		ctx.text(this.font, Lang.tr("FUSION TOP", "ТОП ФЬЮЗОВ"), x, 18, xpTab ? TEXT_FAINT : TEXT, true);
		if (!xpTab) ctx.fill(x, 28, x + t1w, 29, ACCENT);

		if (!iron()) {
			int t2x = x + t1w + 22;
			String xpTabLabel = Lang.tr("XP TOP", "XP ТОП");
			int t2w = this.font.width(xpTabLabel);
			ctx.text(this.font, xpTabLabel, t2x, 18, xpTab ? TEXT : TEXT_FAINT, true);
			if (xpTab) ctx.fill(t2x, 28, t2x + t2w, 29, ACCENT);

			String age = Lang.tr("prices: ", "цены: ") + BazaarPrices.ageText();
			ctx.text(this.font, age, right - this.font.width(age), 18, TEXT_FAINT, true);
		}

		int y = 34;
		int bx = x;
		for (FusionTop.Sort s : FusionTop.Sort.values()) {
			if (s.xp != xpTab || !sortFits(s)) continue;
			if (iron()) continue;
			int w = this.font.width(s.label()) + 12;
			boolean active = s == sort;
			boolean hover = in(mouseX, mouseY, bx, y, bx + w, y + 14);
			ctx.fill(bx, y, bx + w, y + 14, active ? ACCENT_SOFT : (hover ? CARD_HOVER : CARD));
			ctx.text(this.font, s.label(), bx + 6, y + 3, active ? TEXT : TEXT_DIM, true);
			bx += w + 4;
		}

		if (xpTab) {
			String sm = xpInstaSell ? Lang.tr("sell instantly", "продать сразу") : Lang.tr("via sell offer", "через sell offer");
			int smw = this.font.width(sm) + 12;
			boolean smh = in(mouseX, mouseY, bx + 8, y, bx + 8 + smw, y + 14);
			ctx.fill(bx + 8, y, bx + 8 + smw, y + 14, smh ? CARD_HOVER : CARD);
			ctx.text(this.font, sm, bx + 14, y + 3, smh ? TEXT : TEXT_DIM, true);
			xpModeX = bx + 8;
			xpModeW = smw;
		}

		String f1 = (profitableOnly ? "☑" : "☐") + Lang.tr(" positive only", " только плюсовые");
		String f2 = (hideWarned ? "☑" : "☐") + Lang.tr(" hide questionable", " скрыть спорные");
		int f2w = this.font.width(f2), f1w = this.font.width(f1);
		if (!iron()) ctx.text(this.font, f2, right - f2w, y + 3, hideWarned ? TEXT : TEXT_FAINT, true);
		if (!xpTab && !iron()) {
			ctx.text(this.font, f1, right - f2w - f1w - 12, y + 3, profitableOnly ? TEXT : TEXT_FAINT, true);
		}

		String help = showHelp ? Lang.tr("[close]", "[закрыть]") : Lang.tr("[what do these numbers mean?]", "[что значат эти числа?]");
		int helpW = this.font.width(help);
		ctx.text(this.font, help, x, 54,
				in(mouseX, mouseY, x, 52, x + helpW, 62) ? ACCENT : TEXT_FAINT, true);

		searchX = x + helpW + 16;
		ctx.fill(searchX, 52, searchX + SEARCH_W, 64, searchFocused ? ACCENT_SOFT : CARD);
		String sv = search.isEmpty() && !searchFocused ? Lang.tr("search...", "поиск...") : search;
		ctx.text(this.font, sv, searchX + 5, 54,
				search.isEmpty() && !searchFocused ? TEXT_FAINT : TEXT, true);
		if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = searchX + 6 + this.font.width(search);
			ctx.fill(cx, 54, cx + 1, 62, ACCENT);
		}

		String wl = iron() ? "hunter fortune" : "hunting wisdom";
		int wlw = this.font.width(wl);
		int wfx = right - 46;
		ctx.text(this.font, wl, wfx - wlw - 6, 54, TEXT_FAINT, true);
		String wv = editingWisdom ? (wisdomBuffer.isEmpty() ? "_" : wisdomBuffer + "_")
				: (iron() ? String.valueOf((int) RynConfig.hunterFortune)
						  : trimNum(RynConfig.huntingWisdom) + "%");
		boolean wHover = in(mouseX, mouseY, wfx, 52, wfx + 44, 64);
		ctx.fill(wfx, 52, wfx + 44, 64, editingWisdom ? ACCENT_SOFT : (wHover ? CARD_HOVER : CARD));
		ctx.text(this.font, wv, wfx + 4, 54, editingWisdom ? TEXT : TEXT_DIM, true);

		if (showHelp) {
			if (iron()) drawIronHelp(ctx, x, 70);
			else if (xpTab) drawXpHelp(ctx, x, 70);
			else drawHelp(ctx, x, 70);
			return;
		}

		if (entries.isEmpty()) {
			ctx.text(this.font, BazaarPrices.isLoaded() ? Lang.tr("Empty — clear filters", "Пусто — сними фильтры")
					: Lang.tr("Loading bazaar prices...", "Загрузка цен базара..."), x, 80, TEXT_FAINT, true);
			return;
		}

		int hy = listTop() - 11;
		ctx.text(this.font, Lang.tr("shard", "шард"), x + 30, hy, TEXT_FAINT, true);
		if (xpTab) {
			rightText(ctx, Lang.tr("fusions", "фьюзов"), right - C_UNIT, hy, TEXT_FAINT);
			rightText(ctx, Lang.tr("XP per branch", "XP за ветку"), right - C_DEMAND, hy, TEXT_FAINT);
			rightText(ctx, Lang.tr("invest", "вложить"), right - C_INVEST, hy, TEXT_FAINT);
			rightText(ctx, Lang.tr("total", "итог"), right - C_DAY, hy, TEXT_FAINT);
		} else {
			if (iron()) {
				rightText(ctx, Lang.tr("farm time", "время фарма"), right - C_UNIT, hy, TEXT_FAINT);
			} else {
				rightText(ctx, Lang.tr("profit/day", "профит/д"), right - C_UNIT, hy, TEXT_FAINT);
				rightText(ctx, Lang.tr("sold/d", "прод/д"), right - C_DEMAND, hy, TEXT_FAINT);
				rightText(ctx, Lang.tr("invest", "вложить"), right - C_INVEST, hy, TEXT_FAINT);
				rightText(ctx, Lang.tr("return", "вернётся"), right - C_DAY, hy, TEXT_FAINT);
			}
		}
		ctx.fill(x, listTop() - 2, right, listTop() - 1, BORDER);

		int top = listTop();
		int shown = Math.min(visibleRows(), entries.size() - scroll);
		for (int i = 0; i < shown; i++) {
			FusionTop.Entry e = entries.get(scroll + i);
			int ry = top + i * ROW_H;
			boolean hover = in(mouseX, mouseY, x, ry, right, ry + ROW_H - 2);

			ctx.fill(x, ry, right, ry + ROW_H - 2, hover ? CARD_HOVER : CARD);
			if (hover) ctx.fill(x, ry, x + 2, ry + ROW_H - 2, ACCENT);

			int ty = ry + 5;
			ctx.text(this.font, String.valueOf(scroll + i + 1), x + 8, ty, TEXT_FAINT, true);
			ctx.text(this.font, ShardDb.displayName(e.shard), x + 30, ty, TEXT, true);

			if (e.reptile) {
				int rx = x + 30 + this.font.width(ShardDb.displayName(e.shard)) + 3;
				ctx.text(this.font, "🐊", rx, ty, 0xFFFFC24A, true);
				if (in(mouseX, mouseY, rx, ty - 2, rx + this.font.width("🐊"), ty + 9))
					tooltip = new String[]{
							Lang.tr("Reptile fusion.", "Reptile-фьюз."),
							Lang.tr("These numbers change with your Crocodile level", "Эти цифры меняются от уровня Crocodile"),
							Lang.tr("(set it at the bottom of the fusion calculator).", "(задаётся внизу калькулятора фьюза).") };
			}

			int wx = x + 148;
			boolean showOfferWarning = !xpTab || !xpInstaSell;
			if (showOfferWarning && e.warning.isBad()) {
				int ww = this.font.width(e.warning.tag());
				ctx.text(this.font, e.warning.tag(), wx, ty, warnColor(e.warning), true);
				if (in(mouseX, mouseY, wx, ty - 2, wx + ww, ty + 9)) {
					tooltip = e.warning.explain();
					tooltipX = mouseX;
					tooltipY = mouseY;
				}
				wx += ww + 8;
			}
			if (xpTab && e.batchSlippage >= SLIPPAGE_WARN) {
				String tag = Lang.tr("price will drop ", "цена просядет ") + String.format("%.0f%%", e.batchSlippage * 100);
				int ww = this.font.width(tag);
				ctx.text(this.font, tag, wx, ty, WARN_SOFT, true);
				if (in(mouseX, mouseY, wx, ty - 2, wx + ww, ty + 9)) {
					tooltip = new String[]{
							Lang.tr("There are fewer buy orders at the top price than you're about to sell.", "Заявок на покупку по верхней цене меньше, чем ты собрался продать."),
							Lang.tr("The batch will slide down the order book, and the average price comes out lower.", "Партия съедет вниз по стакану, и средняя цена выйдет ниже."),
							"",
							Lang.tr("The total on the right is already calculated from the real order book, not the top", "Итог справа уже посчитан по реальному стакану, а не по верхней"),
							Lang.tr("price — so you can trust the number. Just know where it comes from.", "цене — так что цифре верить можно. Просто знай, откуда она такая.")};
					tooltipX = mouseX;
					tooltipY = mouseY;
				}
			}

			int batch = RynConfig.batchOf(e.shard);
			boolean editing = e.shard.equals(editingBatch);
			String batchStr = editing ? (batchBuffer.isEmpty() ? "_" : batchBuffer + "_")
					: String.valueOf(batch);
			int effBatch = editing ? parseOr(batchBuffer, batch) : batch;
			String invest = fmt(e.unitCost * effBatch);
			if (xpTab) {
				rightText(ctx, String.valueOf(e.batchFusions), right - C_UNIT, ty, TEXT_DIM);
				rightText(ctx, fmt(e.batchXp), right - C_DEMAND, ty, TEXT);
				rightText(ctx, fmt(e.batchCost), right - C_INVEST, ty, TEXT_DIM);
			} else {
				if (iron()) {
					rightText(ctx, fmtHours(e.farmHours), right - C_UNIT, ty, GREEN);
				} else {
					rightText(ctx, fmt(e.profitPerDay), right - C_UNIT, ty, e.profitPerDay >= 0 ? GREEN : RED);
					rightText(ctx, fmtInt(e.demandPerDay), right - C_DEMAND, ty, TEXT_DIM);
					rightText(ctx, invest, right - C_INVEST, ty, TEXT_DIM);
				}
			}

			int bxx = right - C_BATCH;
			boolean bHover = in(mouseX, mouseY, bxx, ty - 2, bxx + 40, ty + 9);
			ctx.text(this.font, Lang.tr("per ", "за ") + batchStr, bxx, ty,
					editing ? ACCENT : (bHover ? TEXT : TEXT_FAINT), true);

			if (xpTab) {
				rightText(ctx, (e.batchNet >= 0 ? "+" : "") + fmt(e.batchNet), right - C_DAY, ty,
						e.batchNet >= 0 ? GREEN : RED);
			} else if (!iron()) {
				rightText(ctx, fmt(e.unitRevenue * effBatch), right - C_DAY, ty,
						e.unitProfit >= 0 ? GREEN : RED);
			}

			if (hover && tooltip == null) {
				tooltip = new String[]{
						Lang.tr("click — open in calculator", "клик — открыть в калькуляторе"),
						Lang.tr("RMB — open on bazaar and check the price yourself", "ПКМ — открыть на базаре и посмотреть цену самому")};
				tooltipX = mouseX;
				tooltipY = mouseY;
			}
		}

		if (entries.size() > visibleRows()) {
			int trackH = listBottom() - top;
			int barH = Math.max(12, trackH * visibleRows() / entries.size());
			int barY = top + (trackH - barH) * scroll / Math.max(1, entries.size() - visibleRows());
			ctx.fill(right + 4, top, right + 6, listBottom(), CARD);
			ctx.fill(right + 4, barY, right + 6, barY + barH, BORDER);
		}

		String hint = entries.size() + Lang.tr(" shards · click a row — open in calculator · ", " шардов · клик по строке — открыть в калькуляторе · ")
				+ Lang.tr("click «per N» — custom batch", "клик по «за N» — своя партия");
		ctx.text(this.font, hint, x, this.height - 13, TEXT_FAINT, true);

		if (tooltip != null) drawTooltip(ctx);
	}

	private void drawTooltip(GuiGraphicsExtractor ctx) {
		int w = 0;
		for (String l : tooltip) w = Math.max(w, this.font.width(l));
		int h = tooltip.length * 10 + 6;
		int tx = Math.min(tooltipX + 10, this.width - w - 12);
		int ty = Math.min(tooltipY + 8, this.height - h - 4);

		ctx.fill(tx - 4, ty - 3, tx + w + 4, ty + h - 3, 0xF01A1A24);
		ctx.fill(tx - 4, ty - 3, tx + w + 4, ty - 2, BORDER);
		ctx.fill(tx - 4, ty + h - 4, tx + w + 4, ty + h - 3, BORDER);
		ctx.fill(tx - 4, ty - 3, tx - 3, ty + h - 3, BORDER);
		ctx.fill(tx + w + 3, ty - 3, tx + w + 4, ty + h - 3, BORDER);
		for (String l : tooltip) {
			ctx.text(this.font, l, tx, ty, TEXT_DIM, true);
			ty += 10;
		}
	}

	private void drawXpHelp(GuiGraphicsExtractor ctx, int x, int y) {
		String[][] lines = {
				{Lang.tr("You can fuse without a loss.", "Фьюзить можно не в убыток."), "h"},
				{"", ""},
				{Lang.tr("Here shards are sorted by what a thousand", "Здесь шарды отсортированы по тому, во что тебе обойдётся тысяча"), ""},
				{Lang.tr("Hunting XP costs you. A minus in this price means you level AND earn.", "Hunting XP. Минус в этой цене значит, что ты качаешься И зарабатываешь."), ""},
				{"", ""},
				{Lang.tr("XP is given PER FUSION, not per shard. A fusion with x2 output gives the same", "XP даётся ЗА ФЬЮЗ, а не за шард. Фьюз с выходом x2 даёт столько же,"), ""},
				{Lang.tr("as x1. So what matters is not how many shards come out, but how many", "сколько с x1. Поэтому важно не сколько шардов на выходе, а сколько"), ""},
				{Lang.tr("fusions are in the branch — intermediate ones count too, and there are usually more.", "фьюзов в ветке — промежуточные тоже считаются, и их обычно больше."), ""},
				{"", ""},
				{Lang.tr("    XP per fusion  =  base(rarity)  ×  (1 + hunting wisdom / 100)", "    XP за фьюз  =  база(редкость)  ×  (1 + hunting wisdom / 100)"), "a"},
				{Lang.tr("    base: 75 / 150 / 300 / 500 / 1000  for common…legendary", "    база: 75 / 150 / 300 / 500 / 1000  за common…legendary"), ""},
				{"", ""},
				{Lang.tr("Enter your wisdom in the top-right field — without it XP can't be calculated,", "Свой wisdom вбей в поле справа сверху — без него XP не посчитать,"), ""},
				{Lang.tr("everyone's multiplier is different.", "множитель у каждого свой."), ""},
				{"", ""},
				{Lang.tr("fusions — how many times you'll fuse for the batch, including intermediate ones", "фьюзов — сколько раз придётся фьюзнуть на партию, вместе с промежуточными"), ""},
				{Lang.tr("XP per branch — how much you get for the whole batch", "XP за ветку — сколько получишь за всю партию целиком"), ""},
				{Lang.tr("invest — how much goes into materials", "вложить — сколько уйдёт на материалы"), ""},
				{Lang.tr("total — what's left if you sell everything straight back to the bazaar", "итог — что останется, если сразу слить всё обратно на базар"), ""},
				{"", ""},
				{Lang.tr("The total is calculated from the REAL order book.", "Итог считается по РЕАЛЬНОМУ стакану заявок."), "h"},
				{Lang.tr("An instant sale goes top-down through buy orders. There are usually only", "Мгновенная продажа идёт сверху вниз по заявкам покупателей. Заявок"), ""},
				{Lang.tr("a few dozen at the top price, so the batch slides down the book", "по верхней цене обычно на десятки штук, поэтому партия съезжает вниз"), ""},
				{Lang.tr("and the average price comes out worse. A visible tag appears if the drop matters.", "и средняя цена выходит хуже. Если просадка заметная — увидишь флажок."), ""},
				{"", ""},
				{Lang.tr("price will drop N%", "цена просядет N%"), "soft"},
				{Lang.tr("      The batch doesn't fit in the top orders. The total on the right already accounts for it.", "      Партия не влезает в верхние заявки. Итог справа уже это учитывает."), ""},
				{"", ""},
				{Lang.tr("This is an estimate for \"sell right now\". If you're not in a hurry and", "Это оценка на «продать прямо сейчас». Если не спешить и выставить"), ""},
				{Lang.tr("place a sell offer instead, you'll get more — but you'll have to wait.", "sell offer, выйдет дороже — но и ждать придётся."), ""},
		};
		for (String[] l : lines) {
			int c = switch (l[1]) {
				case "h" -> TEXT;
				case "a" -> ACCENT;
				case "soft" -> WARN_SOFT;
				default -> TEXT_DIM;
			};
			ctx.text(this.font, l[0], x, y, c, true);
			y += 10;
		}
	}

	private void drawIronHelp(GuiGraphicsExtractor ctx, int x, int y) {
		String[][] lines = {
				{Lang.tr("On Ironman the only currency is time.", "В ironman единственная валюта — время."), "h"},
				{"", ""},
				{Lang.tr("The bazaar is closed to you, so a shard costs exactly as long as it takes", "Базар тебе закрыт, поэтому шард стоит ровно столько, сколько его добывать"), ""},
				{Lang.tr("to get. The list is sorted by that: the fastest are on top.", "или собирать. По этому список и отсортирован: быстрые сверху."), ""},
				{"", ""},
				{Lang.tr("farm time", "время фарма") + Lang.tr("  — how long the whole batch takes: farming the missing inputs", "  — сколько займёт вся партия: добыть недостающие входы"), "a"},
				{Lang.tr("             plus every fusion on the way. Click «per N» to change the batch.", "             и сделать все фьюзы по пути. Клик по «за N» меняет партию."), ""},
				{"", ""},
				{Lang.tr("Speed depends on your Hunter Fortune and attribute levels — set the fortune", "Скорость зависит от Hunter Fortune и уровней аттрибутов — фортуна задаётся"), ""},
				{Lang.tr("in the field on the right. Raise it, and the whole list gets faster.", "полем справа. Поднимешь её — весь список станет быстрее."), ""},
				{"", ""},
				{Lang.tr("Shards you can farm directly sit at the top for a reason: fusing them", "Шарды, которые добываются напрямую, стоят наверху не случайно: фьюзить их"), ""},
				{Lang.tr("would take longer than just going and getting them.", "дольше, чем просто пойти и нафармить."), ""},
				{"", ""},
				{Lang.tr("Shards whose inputs come from bosses or dungeons are not listed at all:", "Шарды, входы которых падают с боссов или в данже, в списке не показаны:"), ""},
				{Lang.tr("their farming speed is unknown, and a guess would only mislead.", "их скорость добычи неизвестна, а выдумывать её — врать."), ""},
		};
		int ly = y;
		for (String[] l : lines) {
			int color = switch (l[1]) { case "h" -> TEXT; case "a" -> ACCENT; default -> TEXT_DIM; };
			ctx.text(this.font, l[0], x, ly, color, true);
			ly += l[0].isEmpty() ? 6 : 11;
		}
	}

	private void drawHelp(GuiGraphicsExtractor ctx, int x, int y) {
		String[][] lines = {
				{Lang.tr("An expensive shard doesn't mean a profitable one.", "Дорогой шард — не значит выгодный."), "h"},
				{"", ""},
				{Lang.tr("If a shard sells a couple times a day, your money is stuck in it for a week.", "Если шард покупают пару раз в день, деньги застрянут в нём на неделю."), ""},
				{Lang.tr("That's why the list is sorted by earnings per day, not per piece.", "Поэтому список отсортирован по заработку за день, а не за штуку."), ""},
				{"", ""},
				{Lang.tr("    profit/day  =  profit/pc  ×  sold/day", "    профит/д  =  профит/шт  ×  купят/д"), "a"},
				{"", ""},
				{Lang.tr("profit/pc — what's left from one piece: sold minus spent", "профит/шт — сколько останется с одной штуки: продал минус потратил"), ""},
				{Lang.tr("            on materials. This is not the shard's price.", "            на материалы. Это не цена шарда."), ""},
				{Lang.tr("sold/day  — how many pieces players buy out per day. Same number as", "купят/д   — сколько штук игроки выкупают за сутки. Та же цифра, что"), ""},
				{Lang.tr("            /bz shows (\"insta-buys in 7d\"), just divided by 7.", "            в /bz («insta-buys in 7d»), просто делённая на 7."), ""},
				{Lang.tr("invest    — how many coins go into materials for the batch. Click", "вложить   — сколько монет уйдёт на материалы для партии. Кликни"), ""},
				{Lang.tr("            \"per N\" to set your own batch size.", "            по «за N», чтобы поставить свой размер партии."), ""},
				{"", ""},
				{Lang.tr("Look at the investment, not just the profit.", "Смотри на вложения, а не только на профит."), "h"},
				{Lang.tr("A row can promise hundreds of millions a day — but to actually earn", "Строка может обещать сотни миллионов в день — но чтобы столько"), ""},
				{Lang.tr("that much, you need to invest billions and wait for it all to sell.", "заработать, надо вложить миллиарды и дождаться, пока всё раскупят."), ""},
				{Lang.tr("Profit/day assumes the whole market demand is yours. In reality it's split", "Профит/д считает, будто весь спрос рынка твой. На деле его делят все,"), ""},
				{Lang.tr("among everyone fusing the same thing.", "кто фьюзит то же самое."), ""},
				{"", ""},
				{Lang.tr("Tags — the price can't be trusted:", "Пометки — цене верить нельзя:"), "h"},
				{"", ""},
				{Lang.tr("possible manipulation", "возможна манипуляция"), "bad"},
				{Lang.tr("      Few sellers, and they ask for far more than buyers offer.", "      Продавцов мало, и просят они намного больше, чем дают покупатели."), ""},
				{Lang.tr("      This happens when a shard sells out and the first seller back", "      Так бывает, когда шард разобрали, а первый вернувшийся продавец"), ""},
				{Lang.tr("      jacks up the price. Or a player bought out all sell offers and put up", "      заломил цену. Либо игрок сам выкупил все sell offer и выставил"), ""},
				{Lang.tr("      everything as one offer of their own, to inflate the price.", "      всё в один свой, чтобы задрать цену."), ""},
				{Lang.tr("      Profit at that price is paper profit.", "      Профит по такой цене бумажный."), ""},
				{Lang.tr("single seller", "один продавец"), "soft"},
				{Lang.tr("      One person is holding the whole price. Pull their offer — the price flies.", "      Всю цену держит один человек. Уберёт оффер — цена улетит."), ""},
				{Lang.tr("no offers", "нет офферов"), "info"},
				{Lang.tr("      Nobody's selling the shard, nothing to compare against.", "      Шард никто не продаёт, сравнивать не с чем."), ""},
				{"", ""},
				{Lang.tr("We don't catch a sharp price drop: a cheap offer gets bought out in seconds,", "Резкое падение цены мы не ловим: дешёвый оффер разбирают за секунды,"), ""},
				{Lang.tr("and it doesn't survive until we check. Only the inflated price shows.", "и до нас он не доживает. Видно только задранную цену."), ""},
		};
		for (String[] l : lines) {
			int c = switch (l[1]) {
				case "h" -> TEXT;
				case "a" -> ACCENT;
				case "bad" -> WARN_BAD;
				case "soft" -> WARN_SOFT;
				case "info" -> WARN_INFO;
				default -> TEXT_DIM;
			};
			ctx.text(this.font, l[0], x, y, c, true);
			y += 10;
		}
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (!searchFocused) return super.charTyped(event);
		if (searchAllSelected) { search = ""; searchAllSelected = false; }
		search += Character.toString(event.codepoint());
		rebuild();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchFocused) {
			int k = event.key();
			if (event.isSelectAll()) { searchAllSelected = !search.isEmpty(); return true; }
			if (event.isCopy()) { if (!search.isEmpty()) this.minecraft.keyboardHandler.setClipboard(search); return true; }
			if (event.isCut()) {
				if (!search.isEmpty()) this.minecraft.keyboardHandler.setClipboard(search);
				search = ""; searchAllSelected = false; rebuild(); return true;
			}
			if (event.isPaste()) {
				String clip = ShardListScreen.sanitizeClip(this.minecraft.keyboardHandler.getClipboard());
				search = searchAllSelected ? clip : search + clip;
				searchAllSelected = false; rebuild(); return true;
			}
			if (k == GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; searchAllSelected = false; return true; }
			if (k == GLFW.GLFW_KEY_ENTER) { searchFocused = false; return true; }
			if (k == GLFW.GLFW_KEY_BACKSPACE) {
				if (searchAllSelected) { search = ""; searchAllSelected = false; rebuild(); }
				else if (!search.isEmpty()) { search = search.substring(0, search.length() - 1); rebuild(); }
				return true;
			}
			return true;
		}
		if (editingWisdom) {
			int k = event.key();
			if (k == GLFW.GLFW_KEY_ESCAPE) { editingWisdom = false; return true; }
			if (k == GLFW.GLFW_KEY_ENTER) { commitWisdom(); return true; }
			if (k == GLFW.GLFW_KEY_BACKSPACE) {
				if (!wisdomBuffer.isEmpty()) wisdomBuffer = wisdomBuffer.substring(0, wisdomBuffer.length() - 1);
				return true;
			}
			if ((k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9) && wisdomBuffer.length() < 6) {
				wisdomBuffer += (char) ('0' + (k - GLFW.GLFW_KEY_0));
				return true;
			}
			if ((k == GLFW.GLFW_KEY_PERIOD || k == GLFW.GLFW_KEY_KP_DECIMAL)
					&& !wisdomBuffer.contains(".") && !wisdomBuffer.isEmpty()) {
				wisdomBuffer += ".";
				return true;
			}
			return true;
		}
		if (editingBatch != null) {
			int k = event.key();
			if (k == GLFW.GLFW_KEY_ESCAPE) { editingBatch = null; return true; }
			if (k == GLFW.GLFW_KEY_ENTER) { commitBatch(); return true; }
			if (k == GLFW.GLFW_KEY_BACKSPACE) {
				if (!batchBuffer.isEmpty()) batchBuffer = batchBuffer.substring(0, batchBuffer.length() - 1);
				return true;
			}
			if (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9 && batchBuffer.length() < 5) {
				batchBuffer += (char) ('0' + (k - GLFW.GLFW_KEY_0));
				return true;
			}
			return true;
		}
		return super.keyPressed(event);
	}

	private void commitWisdom() {
		try {
			float v = Float.parseFloat(wisdomBuffer);
			if (v >= 0 && v <= 10000) {
				if (iron()) RynConfig.hunterFortune = v; else RynConfig.huntingWisdom = v;
			}
		} catch (Exception ignored) { }
		ConfigManager.save();
		editingWisdom = false;
		rebuild();
	}

	private void commitBatch() {
		if (editingBatch == null) return;
		int v = parseOr(batchBuffer, RynConfig.DEFAULT_BATCH);
		RynConfig.setBatch(editingBatch, v);
		ConfigManager.save();
		editingBatch = null;
		if (xpTab) rebuild();
	}

	private static int parseOr(String s, int fallback) {
		try { int v = Integer.parseInt(s); return v > 0 ? v : fallback; }
		catch (Exception e) { return fallback; }
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		int mouseX = (int) event.x(), mouseY = (int) event.y();
		int x = listX(), right = x + WIDTH;

		String help = showHelp ? Lang.tr("[close]", "[закрыть]") : Lang.tr("[what do these numbers mean?]", "[что значат эти числа?]");
		if (in(mouseX, mouseY, x, 52, x + this.font.width(help), 62)) {
			showHelp = !showHelp;
			return true;
		}
		if (showHelp) return super.mouseClicked(event, doubled);

		if (in(mouseX, mouseY, searchX, 52, searchX + SEARCH_W, 64)) {
			searchFocused = true;
			if (editingWisdom) commitWisdom();
			return true;
		}
		searchFocused = false;

		int wfx = right - 46;
		if (in(mouseX, mouseY, wfx, 52, wfx + 44, 64)) {
			if (editingBatch != null) commitBatch();
			editingWisdom = true;
			wisdomBuffer = "";
			return true;
		}
		if (editingWisdom) { commitWisdom(); return true; }
		if (editingBatch != null) { commitBatch(); return true; }

		int t1w = this.font.width(Lang.tr("FUSION TOP", "ТОП ФЬЮЗОВ"));
		if (in(mouseX, mouseY, x, 16, x + t1w, 29)) {
			if (xpTab) { xpTab = false; sort = FusionTop.Sort.PER_DAY; rebuild(); }
			return true;
		}
		int t2x = x + t1w + 22;
		int t2w = this.font.width(Lang.tr("XP TOP", "XP ТОП"));
		if (in(mouseX, mouseY, t2x, 16, t2x + t2w, 29)) {
			if (!xpTab) { xpTab = true; sort = FusionTop.Sort.XP_CHEAP; rebuild(); }
			return true;
		}

		int bx = x;
		for (FusionTop.Sort s : FusionTop.Sort.values()) {
			if (s.xp != xpTab || !sortFits(s)) continue;
			int w = this.font.width(s.label()) + 12;
			if (in(mouseX, mouseY, bx, 34, bx + w, 48)) { sort = s; rebuild(); return true; }
			bx += w + 4;
		}

		if (xpTab && in(mouseX, mouseY, xpModeX, 34, xpModeX + xpModeW, 48)) {
			xpInstaSell = !xpInstaSell;
			rebuild();
			return true;
		}

		String f1 = (profitableOnly ? "☑" : "☐") + Lang.tr(" positive only", " только плюсовые");
		String f2 = (hideWarned ? "☑" : "☐") + Lang.tr(" hide questionable", " скрыть спорные");
		int f2w = this.font.width(f2), f1w = this.font.width(f1);
		if (in(mouseX, mouseY, right - f2w, 34, right, 48)) { hideWarned = !hideWarned; rebuild(); return true; }
		if (!xpTab && in(mouseX, mouseY, right - f2w - f1w - 12, 34, right - f2w - 12, 48)) {
			profitableOnly = !profitableOnly; rebuild(); return true;
		}

		int top = listTop();
		int shown = Math.min(visibleRows(), Math.max(0, entries.size() - scroll));
		for (int i = 0; i < shown; i++) {
			int ry = top + i * ROW_H;
			if (!in(mouseX, mouseY, x, ry, right, ry + ROW_H - 2)) continue;
			FusionTop.Entry e = entries.get(scroll + i);

			int bxx = right - C_BATCH;
			if (in(mouseX, mouseY, bxx, ry + 3, bxx + 40, ry + 14)) {
				editingBatch = e.shard;
				batchBuffer = "";
				return true;
			}
			if (event.button() == 1) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.player != null) {
					this.onClose();
					mc.player.connection.sendCommand("bz " + ShardDb.bazaarName(e.shard));
				}
				return true;
			}
			int amount = RynConfig.batchOf(e.shard);
			FusionState.set(e.shard, amount);
			this.onClose();
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		if (showHelp) return true;
		int max = Math.max(0, entries.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy) * 3));
		return true;
	}

	private boolean in(int mx, int my, int x1, int y1, int x2, int y2) {
		return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
	}

	private void rightText(GuiGraphicsExtractor ctx, String s, int rightX, int y, int color) {
		ctx.text(this.font, s, rightX - this.font.width(s), y, color, true);
	}

	private static boolean iron() { return com.ryn.skyryn.config.RynConfig.ironman; }

	private static boolean sortFits(FusionTop.Sort s) {
		boolean iron = com.ryn.skyryn.config.RynConfig.ironman;
		if (s == FusionTop.Sort.FARM_FAST) return iron;
		return s.xp || !iron;
	}

	private static String fmtHours(double hours) {
		double min = hours * 60;
		if (min < 1) return Math.max(1, Math.round(min * 60)) + Lang.tr(" s", " с");
		if (min < 10) return String.format("%.1f", min) + Lang.tr(" min", " мин");
		if (min < 60) return Math.round(min) + Lang.tr(" min", " мин");
		long h = (long) hours;
		long m = Math.round(min - h * 60);
		if (m == 60) { h++; m = 0; }
		return h + Lang.tr(" h", " ч") + (m > 0 ? " " + m + Lang.tr(" min", " мин") : "");
	}

	private static String fmt(double v) {
		double a = Math.abs(v);
		if (a >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
		if (a >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
		if (a >= 1_000) return String.format("%.1fk", v / 1_000);
		return String.format("%.0f", v);
	}

	private static String fmtInt(double v) {
		if (v >= 1000) return String.format("%.1fk", v / 1000);
		return String.format("%.0f", v);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
