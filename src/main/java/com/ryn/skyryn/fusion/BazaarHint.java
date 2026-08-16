package com.ryn.skyryn.fusion;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;

/**
 * Подсказка с количеством при заказе на базаре.
 *
 * Сценарий: кликнул шард в калькуляторе -> открылся базар -> Buy Instantly ->
 * Custom Amount -> Hypixel открывает табличку для ввода числа. Рядом показываем,
 * сколько нужно по рецепту, чтобы не считать в уме.
 *
 * ТОЛЬКО показываем — число игрок вводит сам. Автоподстановка убрана осознанно:
 * правила Hypixel запрещают "mapping chat or commands to buttons" и всё, что
 * "automates any player gameplay action". Ввод текста за игрока попадает под это,
 * а рисование цифры на экране — нет.
 */
public class BazaarHint {

	/** Сколько живёт запомненное количество — потом подсказка неактуальна. */
	private static final long TTL_MS = 10 * 60 * 1000;

	private static String pendingShard = null;
	private static int pendingAmount = 0;
	private static long pendingAt = 0;

	// Границы плашки текущего кадра
	private static int boxX, boxY, boxW, boxH;

	// ===== Палитра (в тон панели) =====
	private static final int TEXT       = 0xFFE6E8F5;
	private static final int TEXT_MUTED = 0xFF8B90AC;
	private static final int GOLD       = 0xFFFFD24A;

	/** Запоминает, сколько чего мы собрались покупать. Зовётся при клике в панели. */
	public static void remember(String shardDisplayName, int amount) {
		pendingShard = shardDisplayName;
		pendingAmount = amount;
		pendingAt = System.currentTimeMillis();
	}

	private static boolean hasPending() {
		return pendingShard != null
				&& pendingAmount > 0
				&& System.currentTimeMillis() - pendingAt < TTL_MS;
	}

	private static boolean active() {
		return RynConfig.bazaarHintEnabled && RynConfig.calculatorEnabled && hasPending();
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractSignEditScreen)) return;

			ScreenEvents.afterExtract(screen).register((scr, ctx, mouseX, mouseY, tickDelta) -> {
				if (!active()) return;
				render(ctx, scr);
			});
		});
	}

	private static void render(GuiGraphicsExtractor ctx, Screen screen) {
		Font font = Minecraft.getInstance().font;

		String amount = String.valueOf(pendingAmount);
		String title = Lang.tr("Needed by recipe", "Нужно по рецепту");
		String shard = pendingShard;

		boxW = Math.max(font.width(title), Math.max(font.width(shard), (int) (font.width(amount) * 1.6f))) + 16;
		boxH = 40;
		// Встаём ЧУТЬ ПРАВЕЕ серверной таблички (она по центру, графика — вверху экрана).
		boxX = screen.width / 2 + 70;
		boxY = Math.max(6, screen.height / 2 - 120);
		if (boxX + boxW > screen.width - 6) boxX = screen.width / 2 - 70 - boxW;   // не влезло — слева

		vanillaBox(ctx, boxX, boxY, boxW, boxH);

		int cx = boxX + boxW / 2;
		int ty = boxY + 5;
		ctx.text(font, title, cx - font.width(title) / 2, ty, TEXT_MUTED, true);
		ty += 10;
		ctx.text(font, shard, cx - font.width(shard) / 2, ty, TEXT, true);
		ty += 13;

		// Само число — крупно, по центру.
		ctx.pose().pushMatrix();
		ctx.pose().translate(cx, ty);
		ctx.pose().scale(1.6f, 1.6f);
		ctx.text(font, amount, -font.width(amount) / 2, 0, GOLD, true);
		ctx.pose().popMatrix();
	}

	/** Ванильный тултип майнкрафта: тёмный фон + фиолетовая градиентная рамка. */
	private static void vanillaBox(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		int bg = 0xF0100010, b1 = 0x505000FF, b2 = 0x5028007F;
		ctx.fill(x - 1, y, x + w + 1, y + h, bg);
		ctx.fill(x, y - 1, x + w, y, bg);
		ctx.fill(x, y + h, x + w, y + h + 1, bg);
		ctx.fillGradient(x, y + 1, x + 1, y + h - 1, b1, b2);
		ctx.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, b1, b2);
		ctx.fillGradient(x, y, x + w, y + 1, b1, b1);
		ctx.fillGradient(x, y + h - 1, x + w, y + h, b2, b2);
	}
}
