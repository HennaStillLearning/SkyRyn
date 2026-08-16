package com.ryn.skyryn.screen;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardInfo;

/**
 * Главное меню мода — открывается по /sr. Карточками ведёт на экраны (Shards, топ,
 * настройки), сверху — профиль (ник/фортуна), снизу — язык, Ironman, ссылка Modrinth.
 *
 * Профиль: ник из таб-листа (там он уже покрашен по рангу сервером), фортуна — из
 * конфига. Уровень Hunting и SkyBlock пока «—» — их даст Hypixel API (следующий шаг).
 */
public class RynMenuScreen extends Screen {

	static final int WORLD_DIM   = 0xC8000000;   // затемнение мира за панелью
	static final int PANEL_BOX   = 0xFF17171F;   // подложка-окно меню
	static final int PANEL       = 0xFF1E1E28;   // строка профиля (чуть светлее подложки)
	static final int CARD        = 0xFF212130;   // карточки — ещё светлее, «всплывают»
	static final int CARD_HOVER  = 0xFF2A2A3C;
	static final int BORDER      = 0xFF2E2E3C;
	static final int ACCENT      = 0xFF5B8DEF;
	static final int ACCENT_SOFT = 0xFF2A3F63;
	static final int TEXT        = 0xFFF2F3F7;
	static final int DIM         = 0xFFC8CAD4;
	static final int FAINT       = 0xFF9096A6;
	static final int GOLD        = 0xFFFFD24A;
	static final int GREEN       = 0xFF5FD68A;
	static final int MOD_GREEN   = 0xFF4FCF6F;

	private static final int PANEL_W = 460;
	private static final int CARD_H = 58;
	private static final int GAP = 8;
	/** Высота всего содержимого — для вертикального центрирования панели. */
	private static final int CONTENT_H = 277;

	/** Карточка меню: заголовок, описание, цвет-акцент, значок, действие, «скоро». */
	private record Card(String title, String desc, int color, String icon, String action, boolean soon) { }

	private final List<Card> cards = new ArrayList<>();

	// Экранные зоны (считаются при отрисовке, читаются кликом).
	private final List<int[]> cardRects = new ArrayList<>();
	private final List<String> cardActions = new ArrayList<>();
	private int[] langEnRect = null, langRuRect = null, ironRect = null, modRect = null;

	public RynMenuScreen() {
		super(Component.literal("SkyRyn"));
	}

	@Override
	protected void init() {
		cards.clear();
		cards.add(new Card("Shards", Lang.tr("All shards and attributes", "О всех шардах и аттрибутах"),
				ACCENT, "💎", "shards", false));
		cards.add(new Card("Calculator", Lang.tr("Shard calculator and recipes", "Калькулятор и рецепты шардов"),
				0xFFB061E0, "🧮", "calc", false));
		cards.add(new Card("Fusion Tracker", Lang.tr("Some stats over all shards", "Некоторая статистика всех шардов"),
				GREEN, "📊", "top", false));
		cards.add(new Card("HUD Editor", Lang.tr("Position your trackers", "Расположение трекеров на экране"),
				0xFF7A92B0, "🖥", "hud", false));
		cards.add(new Card("Settings", Lang.tr("Language, HUD, Ironman", "Язык, HUD, Ironman"),
				FAINT, "⚙", "settings", false));
		cards.add(new Card("Hunting Methods", "Coming soon..", 0xFFE0A040, "🎯", "", true));
	}

	private int panelX() { return (this.width - PANEL_W) / 2; }

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		cardRects.clear(); cardActions.clear();
		int x = panelX(), w = PANEL_W;
		int y = Math.max(36, (this.height - CONTENT_H) / 2);

		// Затемняем мир и рисуем подложку-окно под меню — чтобы оно читалось как
		// отдельная панель, а не текст поверх мира.
		ctx.fill(0, 0, this.width, this.height, WORLD_DIM);
		int px = x - 16, py = y - 16, pw = w + 32, ph = CONTENT_H + 32;
		ctx.fill(px, py, px + pw, py + ph, PANEL_BOX);
		ctx.fill(px, py, px + pw, py + 2, ACCENT);                 // акцентная кромка сверху
		ctx.fill(px, py + ph - 1, px + pw, py + ph, BORDER);
		ctx.fill(px, py, px + 1, py + ph, BORDER);
		ctx.fill(px + pw - 1, py, px + pw, py + ph, BORDER);

		// Заголовок SkyRyn — по центру
		String sky = "Sky", ryn = "Ryn";
		int titleW = this.font.width(sky + ryn);
		int tx = x + (w - titleW) / 2;
		ctx.text(this.font, sky, tx, y, GOLD, true);
		ctx.text(this.font, ryn, tx + this.font.width(sky), y, ACCENT, true);
		ctx.text(this.font, "v1.0", x + w - this.font.width("v1.0"), y, FAINT, true);
		String slogan = Lang.tr("Everything about hunting in one place", "Всё, что нужно знать о хантинге — в одном месте");
		ctx.text(this.font, slogan, x + (w - this.font.width(slogan)) / 2, y + 12, FAINT, true);
		y += 28;

		// Профиль
		int psH = 22;
		ctx.fill(x, y, x + w, y + psH, PANEL);
		ctx.fill(x, y, x + w, y + 1, BORDER);
		ctx.fill(x, y + psH, x + w, y + psH + 1, BORDER);
		String left = "Hunting §7—§r   " + Lang.tr("Fortune ", "Fortune ") + (int) RynConfig.hunterFortune;
		ctx.text(this.font, left, x + 9, y + 7, DIM, true);
		Component nick = playerName();
		String sb = "  ✦ —";
		int nickW = this.font.width(nick), sbW = this.font.width(sb);
		int rx = x + w - 9 - nickW - sbW;
		ctx.text(this.font, nick.getVisualOrderText(), rx, y + 7, TEXT, true);
		ctx.text(this.font, sb, rx + nickW, y + 7, GREEN, true);
		y += psH + 1 + 12;

		// Карточки 2×N — сетка целиком, без отдельно повисшей плашки настроек.
		int cw = (w - GAP) / 2;
		int rows = (cards.size() + 1) / 2;
		for (int i = 0; i < cards.size(); i++) {
			int cx = x + (i % 2) * (cw + GAP);
			int cy = y + (i / 2) * (CARD_H + GAP);
			Card c = cards.get(i);
			boolean hover = in(mouseX, mouseY, cx, cy, cx + cw, cy + CARD_H);
			drawCard(ctx, cx, cy, cw, c, hover);
			cardRects.add(new int[]{cx, cy, cx + cw, cy + CARD_H});
			cardActions.add(c.soon() ? "" : c.action());
		}
		y += rows * (CARD_H + GAP) + 4;

		// Футер: язык · Ironman · Modrinth
		ctx.fill(x, y, x + w, y + 1, BORDER);
		int fy = y + 9;
		int lx = x;
		String langLbl = Lang.tr("Language: ", "Язык: ");
		ctx.text(this.font, langLbl, lx, fy, FAINT, true);
		lx += this.font.width(langLbl) + 2;
		langEnRect = drawSeg(ctx, "EN", lx, fy - 3, !RynConfig.isRu(), mouseX, mouseY);
		langRuRect = drawSeg(ctx, "RU", langEnRect[2], fy - 3, RynConfig.isRu(), mouseX, mouseY);

		String mod = "Modrinth";
		int modW = this.font.width(mod) + 16;
		int modX = x + w - modW;
		boolean modHover = in(mouseX, mouseY, modX, fy - 4, modX + modW, fy + 9);
		ctx.fill(modX, fy - 4, modX + modW, fy + 9, modHover ? 0xFF17301D : 0xFF132417);
		ctx.fill(modX, fy - 4, modX + modW, fy - 3, MOD_GREEN);
		ctx.text(this.font, mod, modX + 8, fy, MOD_GREEN, true);
		modRect = new int[]{modX, fy - 4, modX + modW, fy + 9};

		String iron = "Ironman";
		int ironLblW = this.font.width(iron);
		int ironX = modX - 14 - (ironLblW + 26);
		ctx.text(this.font, iron, ironX, fy, DIM, true);
		int togX = ironX + ironLblW + 6;
		boolean on = RynConfig.ironman;
		ctx.fill(togX, fy - 2, togX + 20, fy + 7, on ? ACCENT_SOFT : BORDER);
		ctx.fill(on ? togX + 12 : togX + 2, fy - 1, on ? togX + 18 : togX + 8, fy + 6, on ? ACCENT : FAINT);
		ironRect = new int[]{ironX, fy - 4, togX + 20, fy + 9};
	}

	/** Кнопка сегмента (EN/RU). Возвращает свою зону {x1,y1,x2,y2}. */
	private int[] drawSeg(GuiGraphicsExtractor ctx, String s, int x, int y, boolean active, int mouseX, int mouseY) {
		int wpad = this.font.width(s) + 14;
		boolean hover = in(mouseX, mouseY, x, y, x + wpad, y + 15);
		ctx.fill(x, y, x + wpad, y + 15, active ? ACCENT_SOFT : (hover ? CARD_HOVER : CARD));
		ctx.text(this.font, s, x + 7, y + 4, active ? TEXT : FAINT, true);
		return new int[]{x, y, x + wpad, y + 15};
	}

	private void drawCard(GuiGraphicsExtractor ctx, int cx, int cy, int cw, Card c, boolean hover) {
		boolean live = !c.soon();
		ctx.fill(cx, cy, cx + cw, cy + CARD_H, hover && live ? CARD_HOVER : CARD);
		if (hover && live) {
			ctx.fill(cx, cy, cx + cw, cy + 1, ACCENT);
			ctx.fill(cx, cy + CARD_H - 1, cx + cw, cy + CARD_H, ACCENT);
			ctx.fill(cx + cw - 1, cy, cx + cw, cy + CARD_H, ACCENT);
		}
		ctx.fill(cx, cy, cx + 3, cy + CARD_H, c.color());
		ctx.text(this.font, c.icon(), cx + 12, cy + 16, live ? c.color() : FAINT, true);
		int titleX = cx + 12 + this.font.width(c.icon()) + 5;
		ctx.text(this.font, c.title(), titleX, cy + 16, live ? TEXT : FAINT, true);
		ctx.text(this.font, fit(c.desc(), cw - 20), cx + 12, cy + 32,
				live ? FAINT : 0xFFE0A040, true);
	}

	private String fit(String s, int maxW) {
		if (this.font.width(s) <= maxW) return s;
		return this.font.plainSubstrByWidth(s, maxW - this.font.width("…")) + "…";
	}

	/** Ник из таб-листа (сервер уже красит по рангу); иначе — простое имя. */
	private Component playerName() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null && mc.player != null) {
			var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (info != null && info.getTabListDisplayName() != null) return info.getTabListDisplayName();
		}
		if (mc.player != null) return mc.player.getName();
		return Component.literal(mc.getUser().getName());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		int mx = (int) event.x(), my = (int) event.y();

		for (int i = 0; i < cardRects.size(); i++) {
			int[] r = cardRects.get(i);
			String act = cardActions.get(i);
			if (!act.isEmpty() && in(mx, my, r[0], r[1], r[2], r[3])) { doAction(act); return true; }
		}
		if (langEnRect != null && in(mx, my, langEnRect[0], langEnRect[1], langEnRect[2], langEnRect[3])) { setLang("en"); return true; }
		if (langRuRect != null && in(mx, my, langRuRect[0], langRuRect[1], langRuRect[2], langRuRect[3])) { setLang("ru"); return true; }
		if (ironRect != null && in(mx, my, ironRect[0], ironRect[1], ironRect[2], ironRect[3])) {
			RynConfig.ironman = !RynConfig.ironman; ConfigManager.save(); return true;
		}
		if (modRect != null && in(mx, my, modRect[0], modRect[1], modRect[2], modRect[3])) {
			try { Util.getPlatform().openUri("https://modrinth.com"); } catch (Exception ignored) { }
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	private void doAction(String act) {
		switch (act) {
			case "shards" -> this.minecraft.setScreen(new ShardListScreen());
			case "top" -> this.minecraft.setScreen(new FusionTopScreen());
			case "settings" -> this.minecraft.setScreen(new RynSettingsScreen(this));
			case "hud" -> this.minecraft.setScreen(new HudEditScreen());
			case "calc" -> {
				// Открываем реальный Hunting Box — гайд «что сфьюзить» рисуется поверх.
				if (this.minecraft.getConnection() != null) {
					this.minecraft.setScreen(null);
					this.minecraft.getConnection().sendCommand("hb");
				}
			}
			default -> { }
		}
	}

	private void setLang(String lang) {
		if (lang.equals(RynConfig.lang)) return;
		RynConfig.lang = lang;
		ShardInfo.load();     // гайд грузится по языку — перечитываем
		ConfigManager.save();
	}

	static boolean in(int mx, int my, int x1, int y1, int x2, int y2) {
		return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
