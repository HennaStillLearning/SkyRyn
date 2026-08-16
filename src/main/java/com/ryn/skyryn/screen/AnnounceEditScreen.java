package com.ryn.skyryn.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.hud.Announce;

public class AnnounceEditScreen extends Screen {
	private static final int BG = 0xB0101018;
	private static final int PANEL = 0xFF151519, SEG = 0xFF23232B, BORDER = 0xFF2A2A32;
	private static final int TITLE = 0xFFF0F1F4, DESC = 0xFF888B94, ACCENT = 0xFF5B8DEF;

	private final Screen parent;
	private final String id;

	private int drag = -1;
	private int dragDX, dragDY;

	private static final int S_SIZE = 0, S_TIME = 1, S_R = 2, S_G = 3, S_B = 4;

	private int panelY;
	private final int[][] tracks = new int[5][2];
	private final int[] trackY = new int[5];
	private int[] resetRect = new int[4], doneRect = new int[4], textRect = new int[4];
	private String textBuf;
	private boolean textFocused = false;
	private final int[][] swatches = new int[RynSettingsScreen.PALETTE.length][4];

	public AnnounceEditScreen(Screen parent, String id) {
		super(Component.literal("SkyRyn — " + Announce.label(id)));
		this.parent = parent;
		this.id = id;
		this.textBuf = Announce.text(id, "");
	}

	private String defaultSample() {
		return switch (id) {
			case Announce.WOODPECKER -> "WOODPECKER!";
			case Announce.TIMBER -> "TIMBER!";
			case Announce.PETALFALL -> "PETALFALL!";
			case Announce.BEEHEEMOTH -> "BEEHEEMOTH!";
			case Announce.CRITTER -> Lang.tr("{tree} critter in 5s!", "{tree} криттер через 5с!");
			default -> Lang.tr("Wumpa awoken!", "Wumpa пробудился!");
		};
	}

	private String sample() {
		String t = textBuf.isBlank() ? defaultSample() : textBuf;
		return t.replace("{tree}", "Fig");
	}

	private String sampleSub() {
		return switch (id) {
			case Announce.WOODPECKER -> Lang.tr("Tree felled instantly", "Дерево сломано мгновенно");
			case Announce.TIMBER -> Lang.tr("Extra logs from the tree", "Дополнительные брёвна с дерева");
			case Announce.PETALFALL -> Lang.tr("Extra petals from the tree", "Дополнительные лепестки с дерева");
			case Announce.BEEHEEMOTH -> Lang.tr("spawned at Torrhus Canyon", "заспавнился: Torrhus Canyon");
			default -> "";
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, this.width, this.height, BG);

		String big = sample();
		int x = Announce.px(this.width, id), y = Announce.py(this.height, id);
		int w = Announce.width(this.font, id, big), h = Announce.height(this.font, id);

		ctx.fill(x - w / 2 - 4, y - 3, x + w / 2 + 4, y - 2, BORDER);
		ctx.fill(x - w / 2 - 4, y + h + 2, x + w / 2 + 4, y + h + 3, BORDER);
		Announce.draw(ctx, this.font, id, big, sampleSub(), 255, x, y);

		String hint = Lang.tr("Drag the caption where you want it",
				"Перетащи надпись туда, где она нужна");
		ctx.text(this.font, hint, (this.width - this.font.width(hint)) / 2, 8, DESC, true);
		String name = Announce.label(id);
		ctx.text(this.font, name, (this.width - this.font.width(name)) / 2, 20, TITLE, true);

		int ph = 124;
		panelY = this.height - ph;
		ctx.fill(0, panelY, this.width, this.height, PANEL);
		ctx.fill(0, panelY, this.width, panelY + 1, BORDER);

		int cx1 = Math.max(16, this.width / 2 - 180), cx2 = Math.min(this.width - 16, this.width / 2 + 180);
		int col = Announce.color(id);
		int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;

		int ty = panelY + 8;
		ctx.text(this.font, Lang.tr("Text", "Текст"), cx1, ty + 3, DESC, true);
		int fx1 = cx1 + 34, fx2 = cx2;
		textRect = new int[]{ fx1, ty, fx2, ty + 14 };
		ctx.fill(fx1, ty, fx2, ty + 14, SEG);
		if (textFocused) {
			ctx.fill(fx1, ty, fx2, ty + 1, ACCENT);
			ctx.fill(fx1, ty + 13, fx2, ty + 14, ACCENT);
		}
		String shown = textBuf.isEmpty() ? defaultSample() : textBuf;
		ctx.text(this.font, shown + (textFocused && blink() ? "_" : ""), fx1 + 4, ty + 3,
				textBuf.isEmpty() ? DESC : TITLE, true);
		if (id.equals(Announce.CRITTER))
			ctx.text(this.font, Lang.tr("{tree} = tree name", "{tree} — порода дерева"), fx1 + 4, ty + 16, DESC, true);

		slider(ctx, S_SIZE, cx1, cx2, panelY + 30, Lang.tr("Size", "Размер"), Announce.scalePct(id), 40, 400, ACCENT);
		slider(ctx, S_TIME, cx1, cx2, panelY + 46, Lang.tr("Time", "Время"), Announce.showMs(id), 500, 15000, ACCENT);
		slider(ctx, S_R, cx1, cx2, panelY + 62, "R", r, 0, 255, 0xFFFF5555);
		slider(ctx, S_G, cx1, cx2, panelY + 74, "G", g, 0, 255, 0xFF55FF55);
		slider(ctx, S_B, cx1, cx2, panelY + 86, "B", b, 0, 255, 0xFF5555FF);

		int sw = 16, sx = cx1, sy = panelY + 100;
		for (int i = 0; i < swatches.length; i++) swatches[i] = new int[4];
		for (int i = 0; i < RynSettingsScreen.PALETTE.length; i++) {
			int px = sx + i * (sw + 3);
			if (px + sw > cx2 - 160) break;
			ctx.fill(px - 1, sy - 1, px + sw + 1, sy + 13, BORDER);
			ctx.fill(px, sy, px + sw, sy + 12, 0xFF000000 | (RynSettingsScreen.PALETTE[i] & 0xFFFFFF));
			swatches[i] = new int[]{ px, sy, px + sw, sy + 12 };
		}

		resetRect = button(ctx, cx2 - 150, panelY + 100, Lang.tr("Reset", "Сброс"), mouseX, mouseY);
		doneRect = button(ctx, cx2 - 70, panelY + 100, Lang.tr("Done", "Готово"), mouseX, mouseY);
	}

	private static boolean blink() { return (System.currentTimeMillis() / 500) % 2 == 0; }

	private void slider(GuiGraphicsExtractor ctx, int idx, int x1, int x2, int y, String label,
						int value, int min, int max, int accent) {
		int labW = 34, valW = 34;
		ctx.text(this.font, label, x1, y, DESC, true);
		int t1 = x1 + labW, t2 = x2 - valW;
		tracks[idx] = new int[]{ t1, t2 };
		trackY[idx] = y;
		ctx.fill(t1, y + 3, t2, y + 5, SEG);
		double frac = (value - min) / (double) (max - min);
		int kx = t1 + (int) (frac * (t2 - t1));
		ctx.fill(t1, y + 3, kx, y + 5, accent);
		ctx.fill(kx - 2, y - 1, kx + 2, y + 9, accent);
		String txt = idx == S_TIME ? String.format(java.util.Locale.US, "%.1fs", value / 1000f) : String.valueOf(value);
		ctx.text(this.font, txt, t2 + 6, y, TITLE, true);
	}

	private int[] button(GuiGraphicsExtractor ctx, int x, int y, String text, int mouseX, int mouseY) {
		int w = Math.max(64, this.font.width(text) + 18), h = 14;
		boolean hov = RynSettingsScreen.in(mouseX, mouseY, x, y, x + w, y + h);
		ctx.fill(x, y, x + w, y + h, hov ? 0xFF3A3A45 : SEG);
		ctx.text(this.font, text, x + (w - this.font.width(text)) / 2, y + 3, hov ? TITLE : DESC, true);
		return new int[]{ x, y, x + w, y + h };
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		int mx = (int) event.x(), my = (int) event.y();

		if (RynSettingsScreen.in(mx, my, doneRect[0], doneRect[1], doneRect[2], doneRect[3])) { onClose(); return true; }
		if (RynSettingsScreen.in(mx, my, resetRect[0], resetRect[1], resetRect[2], resetRect[3])) {
			Announce.reset(id); textBuf = ""; ConfigManager.save(); return true;
		}
		if (RynSettingsScreen.in(mx, my, textRect[0], textRect[1], textRect[2], textRect[3])) {
			textFocused = true;
			return true;
		}
		textFocused = false;
		for (int i = 0; i < swatches.length; i++) {
			int[] s = swatches[i];
			if (s[2] > 0 && RynSettingsScreen.in(mx, my, s[0], s[1], s[2], s[3])) {
				Announce.setColor(id, RynSettingsScreen.PALETTE[i]);
				ConfigManager.save();
				return true;
			}
		}
		for (int i = 0; i < tracks.length; i++) {
			if (my >= trackY[i] - 4 && my <= trackY[i] + 12 && mx >= tracks[i][0] - 6 && mx <= tracks[i][1] + 6) {
				drag = 1 + i;
				applySlider(i, mx);
				return true;
			}
		}
		if (my < panelY) {
			drag = 0;
			dragDX = mx - Announce.px(this.width, id);
			dragDY = my - Announce.py(this.height, id);
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		int mx = (int) event.x(), my = (int) event.y();
		if (drag == 0) {
			int nx = mx - dragDX, ny = my - dragDY;
			Announce.setPos(id, nx * 1000 / Math.max(1, this.width), ny * 1000 / Math.max(1, this.height));
			return true;
		}
		if (drag >= 1) { applySlider(drag - 1, mx); return true; }
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (drag >= 0) { drag = -1; ConfigManager.save(); return true; }
		return super.mouseReleased(event);
	}

	private void applySlider(int idx, int mouseX) {
		int t1 = tracks[idx][0], t2 = tracks[idx][1];
		if (t2 <= t1) return;
		double frac = Math.max(0, Math.min(1, (mouseX - t1) / (double) (t2 - t1)));
		if (idx == S_SIZE) { Announce.setScalePct(id, (int) Math.round(40 + frac * (400 - 40))); return; }
		if (idx == S_TIME) {
			Announce.setShowMs(id, (int) (Math.round((500 + frac * (15000 - 500)) / 100) * 100));
			return;
		}
		int v = (int) Math.round(frac * 255);
		int col = Announce.color(id);
		int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
		if (idx == S_R) r = v; else if (idx == S_G) g = v; else b = v;
		Announce.setColor(id, 0xFF000000 | (r << 16) | (g << 8) | b);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (!textFocused) return super.charTyped(event);
		if (textBuf.length() < 64) { textBuf += Character.toString(event.codepoint()); applyText(); }
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (textFocused) {
			int k = event.key();
			if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_ENTER) { textFocused = false; return true; }
			if (k == GLFW.GLFW_KEY_BACKSPACE) {
				if (!textBuf.isEmpty()) { textBuf = textBuf.substring(0, textBuf.length() - 1); applyText(); }
				return true;
			}
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
		return super.keyPressed(event);
	}

	private void applyText() {
		Announce.setText(id, textBuf);
		ConfigManager.save();
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
