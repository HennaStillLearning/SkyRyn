package com.ryn.skyryn.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;

/**
 * Крупные надписи-анонсы на экране: Woodpecker, Timber, Petalfall, таймер криттера,
 * Beeheemoth и соло-анонсы сафари.
 *
 * У каждой надписи своё место, размер и цвет — правятся шестерёнкой у самой функции
 * в /sr (экран {@link com.ryn.skyryn.screen.AnnounceEditScreen}). Место храним в
 * тысячных долях ширины/высоты экрана, чтобы надпись не уезжала при смене разрешения
 * и GUI-масштаба; точка привязки — центр надписи по горизонтали и её верх.
 *
 * Все значения живут в общих картах конфига (ints/colors) под ключами
 * {@code ann.<id>.*}, поэтому новый анонс не требует ни новых полей, ни правок
 * ConfigManager: достаточно завести id и дефолты.
 */
public class Announce {

	public static final String WOODPECKER = "woodpecker", TIMBER = "timber", PETALFALL = "petalfall",
			CRITTER = "critter", BEEHEEMOTH = "beeheemoth", SAFARI = "safari", SPARKLING = "sparkling",
			BELL = "bell";

	/**
	 * Дефолты анонса: цвет, размер в процентах, место в тысячных ширины/высоты,
	 * заводской текст и сколько миллисекунд висит.
	 */
	private record Def(int color, int scale, int x, int y, String text, int ms) { }

	private static final java.util.Map<String, Def> DEFS = java.util.Map.of(
			WOODPECKER, new Def(0xFF8CE04A, 220, 500, 260, "WOODPECKER!", 2200),
			TIMBER,     new Def(0xFFFFB347, 220, 500, 260, "TIMBER!", 2200),
			PETALFALL,  new Def(0xFFFF7ACF, 220, 500, 260, "PETALFALL!", 2200),
			CRITTER,    new Def(0xFFFFD24A, 180, 500, 340, "", 2500),
			BEEHEEMOTH, new Def(0xFFFFE04A, 220, 500, 200, "BEEHEEMOTH!", 2200),
			SAFARI,     new Def(0xFFFFFFFF, 100, 500, 200, "", 4000),
			// Один на 4096 — надпись крупная, висит долго и держится по центру экрана.
			SPARKLING,  new Def(0xFFFFA020, 300, 500, 220, "SPARKLING!", 6000),
			// Колокол: текст задаётся на месте (номер и остаток), поэтому заводского нет.
			BELL,       new Def(0xFFFFE04A, 180, 500, 240, "", 3000));

	private static final Def FALLBACK = new Def(0xFFFFFFFF, 200, 500, 250, "", 2200);
	private static Def def(String id) { return DEFS.getOrDefault(id, FALLBACK); }

	/** Порядок в настройках. */
	public static java.util.List<String> ids() {
		return java.util.List.of(WOODPECKER, TIMBER, PETALFALL, CRITTER, BEEHEEMOTH, SAFARI, SPARKLING, BELL);
	}

	/** Подпись анонса в настройках и в заголовке экрана правки. */
	public static String label(String id) {
		return switch (id) {
			case CRITTER -> Lang.tr("Critter timer", "Таймер криттера");
			case SAFARI -> Lang.tr("Safari announces", "Анонсы сафари");
			case WOODPECKER -> "Woodpecker";
			case TIMBER -> "Timber";
			case PETALFALL -> "Petalfall";
			case BEEHEEMOTH -> "Beeheemoth";
			case SPARKLING -> "Sparkling";
			case BELL -> Lang.tr("Bell", "Колокол");
			default -> id;
		};
	}

	public static int color(String id) { return RynConfig.color("ann." + id, def(id).color()); }
	public static void setColor(String id, int argb) { RynConfig.setColor("ann." + id, argb); }

	public static int scalePct(String id) { return RynConfig.getInt("ann." + id + ".s", def(id).scale()); }
	public static void setScalePct(String id, int v) { RynConfig.setInt("ann." + id + ".s", Math.max(40, Math.min(400, v))); }
	public static float scale(String id) { return scalePct(id) / 100f; }

	public static int xPm(String id) { return RynConfig.getInt("ann." + id + ".x", def(id).x()); }
	public static int yPm(String id) { return RynConfig.getInt("ann." + id + ".y", def(id).y()); }
	public static void setPos(String id, int xPm, int yPm) {
		RynConfig.setInt("ann." + id + ".x", Math.max(0, Math.min(1000, xPm)));
		RynConfig.setInt("ann." + id + ".y", Math.max(0, Math.min(1000, yPm)));
	}

	/**
	 * Текст надписи. Пустая строка в конфиге = заводской: так игрок может стереть
	 * своё и вернуться к исходному, не зная, что там было написано.
	 * У анонсов, где текст приходит из игры (таймер криттера, соло-сафари),
	 * заводского текста нет — свой подставляется вместо игрового только если задан.
	 */
	public static String text(String id, String fallback) {
		return RynConfig.getText("ann." + id + ".t", fallback);
	}
	public static String text(String id) { return text(id, def(id).text()); }
	public static void setText(String id, String v) { RynConfig.setText("ann." + id + ".t", v); }
	/** Заводской текст — показать в поле правки как подсказку. */
	public static String defText(String id) { return def(id).text(); }

	/** Сколько миллисекунд надпись висит на экране. */
	public static int showMs(String id) { return RynConfig.getInt("ann." + id + ".ms", def(id).ms()); }
	public static void setShowMs(String id, int v) {
		RynConfig.setInt("ann." + id + ".ms", Math.max(500, Math.min(15000, v)));
	}

	/** Вернуть анонсу заводские место, размер, цвет, текст и длительность. */
	public static void reset(String id) {
		Def d = def(id);
		setPos(id, d.x(), d.y());
		setScalePct(id, d.scale());
		setColor(id, d.color());
		setText(id, "");
		setShowMs(id, d.ms());
	}

	/** Место надписи на экране в пикселях (центр по X, верх по Y). */
	public static int px(int screenW, String id) { return screenW * xPm(id) / 1000; }
	public static int py(int screenH, String id) { return screenH * yPm(id) / 1000; }

	/**
	 * Крупная надпись с подписью под ней. alpha 0..255 — для затухания.
	 * sub может быть null/пустым.
	 */
	public static void draw(GuiGraphicsExtractor ctx, Font font, String id, String big, String sub, int alpha) {
		Minecraft mc = Minecraft.getInstance();
		draw(ctx, font, id, big, sub, alpha,
				px(mc.getWindow().getGuiScaledWidth(), id), py(mc.getWindow().getGuiScaledHeight(), id));
	}

	/** То же, но в заданной точке — для предпросмотра на экране правки. */
	public static void draw(GuiGraphicsExtractor ctx, Font font, String id, String big, String sub, int alpha,
							int x, int y) {
		float s = scale(id);
		int a = Math.max(8, Math.min(255, alpha)) << 24;
		int col = color(id) & 0xFFFFFF;
		ctx.pose().pushMatrix();
		ctx.pose().translate(x, y);
		ctx.pose().scale(s, s);
		int hw = font.width(big) / 2;
		ctx.text(font, big, -hw, 1, a | 0x141410, false);   // тень-подложка
		ctx.text(font, big, -hw, 0, a | col, true);
		ctx.pose().popMatrix();
		if (sub != null && !sub.isEmpty())
			ctx.text(font, sub, x - font.width(sub) / 2, y + Math.round(10 * s) + 3, a | 0xC8CAD4, true);
	}

	/** Ширина/высота надписи на экране — чтобы поймать перетаскивание за неё. */
	public static int width(Font font, String id, String text) { return Math.round(font.width(text) * scale(id)); }
	public static int height(Font font, String id) { return Math.round(font.lineHeight * scale(id)); }
}
