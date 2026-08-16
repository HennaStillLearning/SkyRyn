package com.ryn.skyryn.screen;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lwjgl.glfw.GLFW;

import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.waypoint.SkyBlockCheck;

/**
 * Оверлей «что сфьюзить» у окна Hunting Box (/hb) И Fusion Box.
 *
 * На /hb запас и иконки свежие (HuntingBoxReader/ShardIcons), на машине фьюза —
 * последние виденные. Панель перетаскивается за шапку сетки (позиция в конфиге,
 * как у калькулятора). Детальное меню раскрывается влево. Клик — GLFW-поллинг,
 * скролл — ScreenMouseEvents.
 */
public class BoxGuideOverlay {

	private static boolean wasDown = false;
	private static boolean dragging = false;
	private static int dragDX, dragDY;

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) return;
			if (!SkyBlockCheck.onSkyBlock()) return;
			String title = screen.getTitle().getString();
			if (title == null || !(title.contains("Hunting Box") || title.contains("Fusion Box"))) return;

			BoxBoard.invalidate();

			ScreenEvents.afterExtract(screen).register((scr, ctx, mx, my, delta) -> {
				if (!RynConfig.boxBoardEnabled) return;
				int gx = gridX(scr), gy = gridY(scr), availH = scr.height - gy - 10;
				BoxBoard.render(ctx, Minecraft.getInstance().font, gx, gy, availH, mx, my);
				handleMouse(scr, gx, gy, mx, my);
			});

			ScreenMouseEvents.allowMouseScroll(screen).register((scr, mx, my, hor, ver) -> {
				if (RynConfig.boxBoardEnabled && BoxBoard.contains((int) mx, (int) my)) {
					BoxBoard.scroll(ver);
					return false;
				}
				return true;
			});

			// Блокируем клик от сундука ТОЛЬКО по нашим панелям. Клик по самому /hb
			// (слот/страница) проходит в контейнер как обычно.
			ScreenMouseEvents.allowMouseClick(screen).register((scr, event) ->
					!(RynConfig.boxBoardEnabled && BoxBoard.contains((int) event.x(), (int) event.y())));

			// Ввод в поиск гайда: пока строка поиска активна, буквы/цифры идут в неё.
			ScreenKeyboardEvents.allowKeyPress(screen).register((scr, keyEvent) -> {
				if (!RynConfig.boxBoardEnabled || !BoxBoard.isSearching()) return true;
				int k = keyEvent.key();
				if (k == GLFW.GLFW_KEY_ESCAPE) { BoxBoard.searchClose(); return false; }
				if (k == GLFW.GLFW_KEY_BACKSPACE) { BoxBoard.searchBackspace(); return false; }
				if (k == GLFW.GLFW_KEY_ENTER) return false;
				String ch = charOf(k);
				if (ch != null) { BoxBoard.searchAppend(ch); return false; }
				return true;   // не наша клавиша — в игру
			});
		});
	}

	private static void handleMouse(Screen scr, int gx, int gy, int mx, int my) {
		long window = Minecraft.getInstance().getWindow().handle();
		boolean down = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		if (down && !wasDown) {
			if (BoxBoard.contains(mx, my)) {
				boolean handled = BoxBoard.click(mx, my);
				if (!handled && inStrip(gx, gy, mx, my)) {
					dragging = true;
					dragDX = mx - gx;
					dragDY = my - gy;
				}
			} else {
				// Клик по контейнеру/миру: закрыть только настройки, детали оставить.
				BoxBoard.closeSettings();
			}
		}
		if (dragging) {
			if (down) {
				RynConfig.boxGuideX = clamp(mx - dragDX, 0, scr.width - BoxBoard.gridWidth());
				RynConfig.boxGuideY = clamp(my - dragDY, 0, scr.height - 30);
			} else {
				dragging = false;
				ConfigManager.save();
			}
		}
		wasDown = down;
	}

	/** Сетка: свёрнуто — иконка ПРИКРЕПЛЕНА справа от контейнера (не таскается);
	 *  развёрнуто — сохранённая позиция (свободно таскается) либо авто справа. */
	private static int gridX(Screen scr) {
		if (BoxBoard.isCollapsed())
			return Math.min(containerRight(scr) + 1, scr.width - BoxBoard.ICON_BAR - 2);   // вплотную к правому краю /hb
		int auto = (scr.width + 176) / 2 + 12;
		return RynConfig.boxGuideX >= 0 ? Math.min(RynConfig.boxGuideX, scr.width - BoxBoard.gridWidth()) : auto;
	}
	private static int gridY(Screen scr) {
		if (BoxBoard.isCollapsed()) return containerTop(scr);
		return RynConfig.boxGuideY >= 0 ? Math.min(RynConfig.boxGuideY, scr.height - 30) : 20;
	}

	/** Правый край окна контейнера (точно — через миксин; иначе оценка по 176). */
	private static int containerRight(Screen scr) {
		if (scr instanceof com.ryn.skyryn.mixin.ContainerScreenAccessor a)
			return a.skyryn$leftPos() + a.skyryn$imageWidth();
		return (scr.width + 176) / 2;
	}
	private static int containerTop(Screen scr) {
		if (scr instanceof com.ryn.skyryn.mixin.ContainerScreenAccessor a) return a.skyryn$topPos();
		return 20;
	}

	/** Шапка сетки — за неё таскаем. */
	private static boolean inStrip(int gx, int gy, int mx, int my) {
		return mx >= gx && mx <= gx + BoxBoard.gridWidth() && my >= gy && my <= gy + BoxBoard.STRIP_H;
	}


	private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

	/** Код клавиши → символ для поиска (буква/цифра/пробел), иначе null. */
	private static String charOf(int k) {
		if (k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_Z) return String.valueOf((char) ('a' + (k - GLFW.GLFW_KEY_A)));
		if (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9) return String.valueOf((char) ('0' + (k - GLFW.GLFW_KEY_0)));
		if (k == GLFW.GLFW_KEY_SPACE) return " ";
		return null;
	}
}
