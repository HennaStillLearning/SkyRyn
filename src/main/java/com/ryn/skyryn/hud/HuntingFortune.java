package com.ryn.skyryn.hud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.RynConfig;

/**
 * Hunter Fortune — сколько шардов падает за одну поимку.
 *
 * Берём из игрового меню: SkyBlock Menu -> Your Stats Breakdown, там предмет
 * с лором "Hunter Fortune 121". Просто читаем то, что игрок и так видит на
 * экране — ничего не нажимаем и не открываем сами.
 *
 * Значение живёт до следующего открытия того же меню, и это его слабое место:
 * снял David's Cloak — фортуна упала со 121 до 91, а у нас всё ещё 121.
 *
 * Поймать этот момент неоткуда. Плащи, пояса и прочее лежат в SkyBlock-меню
 * Equipment, которое сервер рисует как обычный сундук: ванильных слотов брони
 * оно не занимает, и клиент смены не видит. Считать фортуну самим — это
 * разбирать лор всего шмота, петов, рефоржей и аттрибутов, отдельный механизм
 * размером с мод.
 *
 * Поэтому просто честно храним, КОГДА прочитали, и показываем возраст. Врать
 * свежей цифрой хуже, чем признать, что она из прошлого часа.
 *
 * Формула выведена из тултипа при фортуне 121:
 *     Chance for double drops: 100%
 *     Chance for triple drops: 21%
 * То есть каждые полные 100 фортуны дают +1 шард гарантированно, остаток —
 * шанс ещё одного. Среднее число шардов за поимку = 1 + fortune/100.
 * Проверка: 2*0.79 + 3*0.21 = 2.21 = 1 + 121/100. Сходится.
 */
public class HuntingFortune {

	private static final Pattern FORTUNE =
			Pattern.compile("Hunter Fortune[:\\s]+([\\d,.]+)");

	/** Когда последний раз читали Stats Breakdown. 0 — ни разу. */
	public static long readAt = 0;

	/** Возраст значения: "сейчас", "12 мин", "3 ч". Пусто — не читали. */
	public static String age() {
		if (readAt == 0) return "";
		long min = (System.currentTimeMillis() - readAt) / 60000;
		if (min < 1) return com.ryn.skyryn.config.Lang.tr("now", "сейчас");
		if (min < 60) return min + com.ryn.skyryn.config.Lang.tr(" min", " мин");
		return (min / 60) + com.ryn.skyryn.config.Lang.tr(" h", " ч");
	}

	/** Стоит ли сомневаться в цифре. Через полчаса шмот уже мог смениться. */
	public static boolean stale() {
		return readAt > 0 && System.currentTimeMillis() - readAt > 30 * 60_000L;
	}

	/** Среднее число шардов за одну поимку при текущей фортуне. */
	public static double dropsPerCatch() {
		return 1.0 + Math.max(0, RynConfig.hunterFortune) / 100.0;
	}

	/** Гарантированные доп. дропы: 121 -> 1 (double всегда). */
	public static int guaranteedExtra() {
		return (int) (Math.max(0, RynConfig.hunterFortune) / 100);
	}

	/** Шанс ещё одного сверх гарантированных, %: 121 -> 21. */
	public static double extraChance() {
		return Math.max(0, RynConfig.hunterFortune) % 100;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			String title = screen.getTitle().getString();
			if (title == null || !title.contains("Stats Breakdown")) return;

			// Слоты наполняются не сразу — сервер досылает содержимое.
			// Поэтому пробуем каждый кадр, пока не найдём.
			ScreenEvents.afterExtract(screen).register((scr, ctx, mx, my, delta) -> scan(cs));
		});
	}

	private static void scan(AbstractContainerScreen<?> screen) {
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) continue;
			ItemStack stack = slot.getItem();

			Double v = find(stack.getHoverName().getString());
			if (v == null) {
				ItemLore lore = stack.get(DataComponents.LORE);
				if (lore == null) continue;
				for (Component line : lore.lines()) {
					v = find(line.getString());
					if (v != null) break;
				}
			}
			if (v == null) continue;

			readAt = System.currentTimeMillis();
			if (Math.abs(v - RynConfig.hunterFortune) > 0.01) {
				RynConfig.hunterFortune = v.floatValue();
				ConfigManager.save();
				com.ryn.skyryn.config.SkyLog.d("Hunter Fortune: " + v);
			}
			return;
		}
	}

	private static Double find(String s) {
		if (s == null) return null;
		Matcher m = FORTUNE.matcher(s.replaceAll("§.", ""));
		if (!m.find()) return null;
		try {
			return Double.parseDouble(m.group(1).replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
