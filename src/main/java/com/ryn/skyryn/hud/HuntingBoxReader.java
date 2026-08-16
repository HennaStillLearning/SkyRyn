package com.ryn.skyryn.hud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.data.ShardIcons;
import com.ryn.skyryn.fusion.FusionPanel;
import com.ryn.skyryn.mixin.ContainerScreenAccessor;

/**
 * Работа с окнами Hunting Box / Fusion Box: пока они открыты, ловим настоящие
 * иконки шардов для наших экранов и подсвечиваем слоты, чей шард участвует в
 * текущем фьюзе калькулятора.
 *
 * Только смотрим на экран, который игрок открыл сам, — ничего не нажимаем.
 */
public class HuntingBoxReader {

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			String title = screen.getTitle().getString();
			// «Shard Fusion» — окно, которое открывается из Fusion Box по клику на шард.
			// Без него подсветка нужных шардов гасла ровно там, где выбираешь второй вход.
			if (title == null || !(title.contains("Hunting Box") || title.contains("Fusion Box")
					|| title.contains("Shard Fusion"))) return;

			// Слоты сервер досылает не сразу — читаем каждый кадр, пока экран открыт.
			ScreenEvents.afterExtract(screen).register((scr, ctx, mx, my, delta) -> {
				captureIcons(cs);
				highlight(ctx, cs);
			});
		});
	}

	/** Снимаем настоящие иконки шардов из открытого бокса для наших экранов. */
	private static void captureIcons(AbstractContainerScreen<?> screen) {
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) continue;
			String key = matchShard(clean(slot.getItem().getHoverName().getString()));
			if (key != null) ShardIcons.put(key, slot.getItem());
		}
	}

	/** Зелёная рамка на слотах бокса, чей шард участвует в текущем фьюзе калькулятора.
	 *  Набор входов берём напрямую из результата калькулятора (FusionPanel) — та же
	 *  расстановка, что показана в панели, и меняется вместе с рецептом. */
	private static void highlight(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> screen) {
		if (!RynConfig.highlightFuseInputs) return;
		if (!(screen instanceof ContainerScreenAccessor acc)) return;
		java.util.Set<String> hl = FusionPanel.highlightInputs;
		if (hl.isEmpty()) return;
		int left = acc.skyryn$leftPos(), top = acc.skyryn$topPos();
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) continue;
			String key = matchShard(clean(slot.getItem().getHoverName().getString()));
			if (key == null || !hl.contains(key)) continue;
			int sx = left + slot.x, sy = top + slot.y;
			int g = 0xFF3FE05F;
			ctx.fill(sx - 1, sy - 1, sx + 17, sy, g);          // рамка
			ctx.fill(sx - 1, sy + 16, sx + 17, sy + 17, g);
			ctx.fill(sx - 1, sy, sx, sy + 16, g);
			ctx.fill(sx + 16, sy, sx + 17, sy + 16, g);
			ctx.fill(sx, sy, sx + 16, sy + 16, 0x4033C059); // лёгкая заливка
		}
	}

	/** Ищет шард по имени: сначала целиком, потом без римского тира на конце. */
	private static String matchShard(String name) {
		if (name == null || name.isBlank()) return null;
		String n = name.trim();
		if (ShardDb.shard(n) != null) return n.toLowerCase();

		// "Kada Knight III" -> "Kada Knight"
		int sp = n.lastIndexOf(' ');
		if (sp > 0 && n.substring(sp + 1).matches("[IVXL]+")) {
			String base = n.substring(0, sp);
			if (ShardDb.shard(base) != null) return base.toLowerCase();
		}
		return null;
	}

	private static String clean(String s) {
		return s == null ? "" : s.replaceAll("§.", "").trim();
	}
}
