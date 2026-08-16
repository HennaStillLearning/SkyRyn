package com.ryn.skyryn.hud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.screen.HudEditScreen;

/**
 * Клик по плашке трекера прямо поверх чужого экрана — инвентаря, сундука, чата.
 *
 * Плашка не прячется при открытом инвентаре, значит и тыкать в неё логично
 * там же, не заходя в /sr hud. Экран правки остаётся только для перетаскивания.
 *
 * Клик по плашке ЗАБИРАЕМ себе (возвращаем false), иначе он же уйдёт в слот
 * сундука под ней — а это уже клик по чужому меню, чего мы не хотим совсем.
 */
public class HudClicks {

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			// В режиме правки у экрана свои мышь и перетаскивание — не мешаем.
			if (screen instanceof HudEditScreen) return;

			ScreenMouseEvents.allowMouseClick(screen).register((scr, event) -> {
				if (!HuntingHud.active() || HuntingHud.hiddenOn(scr)) return true;
				int mx = (int) event.x(), my = (int) event.y();

				// Открытый список режимов ловит клики ПЕРВЫМ, даже вне тела плашки —
				// иначе выбор пункта или клик "мимо, чтобы закрыть" не сработают.
				if (HuntingHud.isDropdownOpen()) {
					if (event.button() == 0) {
						int row = HuntingHud.dropdownRowAt(mx, my);
						if (row >= 0) RynConfig.huntTrackerMode = row;
					}
					HuntingHud.closeDropdown();
					ConfigManager.save();
					// Клик вне плашки и списка — просто закрыли список, отдаём клик дальше.
					return !HuntingHud.over(mx, my);
				}

				if (!HuntingHud.over(mx, my)) return true;

				if (event.button() == 0 && HuntingHud.pillOver(mx, my)) {
					HuntingHud.togglePill();
					return false;
				}
				switch (event.button()) {
					case 0 -> { /* клик по телу плашки — ничего, режим меняется через пилюлю */ }
					case 1 -> HuntingHud.rightClick();  // способ продажи, а в режиме Timer — сброс отсчёта
					case 2 -> HuntingHud.middleClick(); // старт/рестарт таймера + переключение на его вид
					default -> { return true; }
				}
				ConfigManager.save();
				return false; // клик наш — дальше в меню он не пойдёт
			});

			ScreenMouseEvents.allowMouseScroll(screen).register((scr, mx, my, horAmount, vertAmount) -> {
				if (!HuntingHud.active() || HuntingHud.hiddenOn(scr)) return true;
				if (HuntingHud.isDropdownOpen()) return true; // список открыт — колесо не про таймер
				if (!HuntingHud.over((int) mx, (int) my)) return true;
				if (HuntingTracker.timerRunning()) return false; // крутить длительность на бегу смысла нет

				boolean shift = net.minecraft.client.Minecraft.getInstance().hasShiftDown();
				int step = vertAmount > 0 ? 1 : -1;
				HuntingTracker.adjustTimerMinutes(step * (shift ? 5 : 1));
				return false;
			});
		});
	}
}
