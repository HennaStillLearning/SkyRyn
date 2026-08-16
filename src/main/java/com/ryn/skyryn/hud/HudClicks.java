package com.ryn.skyryn.hud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.screen.HudEditScreen;

public class HudClicks {
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof HudEditScreen) return;

			ScreenMouseEvents.allowMouseClick(screen).register((scr, event) -> {
				if (!HuntingHud.active() || HuntingHud.hiddenOn(scr)) return true;
				int mx = (int) event.x(), my = (int) event.y();

				if (HuntingHud.isDropdownOpen()) {
					if (event.button() == 0) {
						int row = HuntingHud.dropdownRowAt(mx, my);
						if (row >= 0) RynConfig.huntTrackerMode = row;
					}
					HuntingHud.closeDropdown();
					ConfigManager.save();
					return !HuntingHud.over(mx, my);
				}

				if (!HuntingHud.over(mx, my)) return true;

				if (event.button() == 0 && HuntingHud.pillOver(mx, my)) {
					HuntingHud.togglePill();
					return false;
				}
				switch (event.button()) {
					case 0 -> {  }
					case 1 -> HuntingHud.rightClick();
					case 2 -> HuntingHud.middleClick();
					default -> { return true; }
				}
				ConfigManager.save();
				return false;
			});

			ScreenMouseEvents.allowMouseScroll(screen).register((scr, mx, my, horAmount, vertAmount) -> {
				if (!HuntingHud.active() || HuntingHud.hiddenOn(scr)) return true;
				if (HuntingHud.isDropdownOpen()) return true;
				if (!HuntingHud.over((int) mx, (int) my)) return true;
				if (HuntingTracker.timerRunning()) return false;

				boolean shift = net.minecraft.client.Minecraft.getInstance().hasShiftDown();
				int step = vertAmount > 0 ? 1 : -1;
				HuntingTracker.adjustTimerMinutes(step * (shift ? 5 : 1));
				return false;
			});
		});
	}
}
