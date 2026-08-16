package com.ryn.skyryn.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.hud.HuntingHud;
import com.ryn.skyryn.hud.SafariTracker;

public class HudEditScreen extends Screen {
	private static final int BG = 0x90101018;

	private boolean dragging = false;
	private boolean moved = false;
	private int dragDX, dragDY;
	private int drag = 0;

	public HudEditScreen() {
		super(Component.literal(Lang.tr("SkyRyn — edit HUD", "SkyRyn — правка HUD")));
	}

	@Override
	protected void init() {
		HuntingHud.setEditing(true);
	}

	@Override
	public void removed() {
		HuntingHud.setEditing(false);
		ConfigManager.save();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, this.width, this.height, BG);

		String hint = Lang.tr("Drag a panel to move it, scroll the wheel over it to resize",
				"Тащи плашку мышкой, колесо над ней — размер");
		ctx.text(this.font, hint, (this.width - this.font.width(hint)) / 2, 8, 0xFF888B94, true);

		HuntingHud.drawAt(ctx, this.font);
		SafariTracker.drawPlaque(ctx, this.font);
		com.ryn.skyryn.hud.CritterTimer.drawPlaque(ctx, this.font);
		com.ryn.skyryn.hud.TikiHelper.drawSample(ctx, this.font);
	}

	private boolean overTiki(int mx, int my) {
		int x = com.ryn.skyryn.hud.TikiHelper.hudX(), y = com.ryn.skyryn.hud.TikiHelper.hudY();
		return mx >= x && mx <= x + com.ryn.skyryn.hud.TikiHelper.plaqueW()
				&& my >= y && my <= y + com.ryn.skyryn.hud.TikiHelper.plaqueH();
	}

	private boolean overCritter(int mx, int my) {
		int x = com.ryn.skyryn.hud.CritterTimer.hudX(), y = com.ryn.skyryn.hud.CritterTimer.hudY();
		return mx >= x && mx <= x + com.ryn.skyryn.hud.CritterTimer.plaqueW()
				&& my >= y && my <= y + com.ryn.skyryn.hud.CritterTimer.plaqueH();
	}

	private boolean overHunt(int mx, int my) {
		int x = RynConfig.huntHudX, y = RynConfig.huntHudY;
		return mx >= x && mx <= x + HuntingHud.width() && my >= y && my <= y + HuntingHud.height();
	}
	private boolean overSafari(int mx, int my) {
		int x = RynConfig.safariHudX, y = RynConfig.safariHudY;
		return mx >= x && mx <= x + SafariTracker.plaqueW() && my >= y && my <= y + SafariTracker.plaqueH();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		int mx = (int) event.x(), my = (int) event.y();
		if (overSafari(mx, my)) {
			drag = 2; dragging = true; moved = false;
			dragDX = mx - RynConfig.safariHudX; dragDY = my - RynConfig.safariHudY;
			return true;
		}
		if (overTiki(mx, my)) {
			drag = 4; dragging = true; moved = false;
			dragDX = mx - com.ryn.skyryn.hud.TikiHelper.hudX();
			dragDY = my - com.ryn.skyryn.hud.TikiHelper.hudY();
			return true;
		}
		if (overCritter(mx, my)) {
			drag = 3; dragging = true; moved = false;
			dragDX = mx - com.ryn.skyryn.hud.CritterTimer.hudX();
			dragDY = my - com.ryn.skyryn.hud.CritterTimer.hudY();
			return true;
		}
		if (!overHunt(mx, my)) return super.mouseClicked(event, doubled);
		if (event.button() == 1) { HuntingHud.rightClick(); ConfigManager.save(); return true; }
		drag = 1; dragging = true; moved = false;
		dragDX = mx - RynConfig.huntHudX; dragDY = my - RynConfig.huntHudY;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging) return super.mouseDragged(event, dx, dy);
		moved = true;
		if (drag == 4) {
			com.ryn.skyryn.hud.TikiHelper.setHudPos(
					clamp((int) event.x() - dragDX, 0, this.width - com.ryn.skyryn.hud.TikiHelper.plaqueW()),
					clamp((int) event.y() - dragDY, 0, this.height - com.ryn.skyryn.hud.TikiHelper.plaqueH()));
		} else if (drag == 3) {
			com.ryn.skyryn.hud.CritterTimer.setHudPos(
					clamp((int) event.x() - dragDX, 0, this.width - com.ryn.skyryn.hud.CritterTimer.plaqueW()),
					clamp((int) event.y() - dragDY, 0, this.height - com.ryn.skyryn.hud.CritterTimer.plaqueH()));
		} else if (drag == 2) {
			RynConfig.safariHudX = clamp((int) event.x() - dragDX, 0, this.width - SafariTracker.plaqueW());
			RynConfig.safariHudY = clamp((int) event.y() - dragDY, 0, this.height - SafariTracker.plaqueH());
		} else {
			RynConfig.huntHudX = clamp((int) event.x() - dragDX, 0, this.width - HuntingHud.width());
			RynConfig.huntHudY = clamp((int) event.y() - dragDY, 0, this.height - HuntingHud.height());
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			if (!moved && drag == 1) RynConfig.huntTrackerMode = (RynConfig.huntTrackerMode + 1) % 4;
			drag = 0;
			ConfigManager.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		int mx = (int) mouseX, my = (int) mouseY;
		int step = (int) Math.signum(dy);
		if (step == 0) return false;
		if (overCritter(mx, my)) {
			com.ryn.skyryn.hud.CritterTimer.setScalePct(com.ryn.skyryn.hud.CritterTimer.scalePct() + step * 5);
		} else if (overSafari(mx, my)) {
			RynConfig.safariHudScale = clampF(RynConfig.safariHudScale + step * 0.05f);
		} else if (overHunt(mx, my)) {
			RynConfig.huntHudScale = clampF(RynConfig.huntHudScale + step * 0.05f);
		} else {
			return false;
		}
		ConfigManager.save();
		return true;
	}

	private static float clampF(float v) { return Math.max(0.5f, Math.min(1.5f, v)); }

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
