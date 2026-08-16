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

public class HuntingFortune {
	private static final Pattern FORTUNE =
			Pattern.compile("Hunter Fortune[:\\s]+([\\d,.]+)");

	public static long readAt = 0;

	public static String age() {
		if (readAt == 0) return "";
		long min = (System.currentTimeMillis() - readAt) / 60000;
		if (min < 1) return com.ryn.skyryn.config.Lang.tr("now", "сейчас");
		if (min < 60) return min + com.ryn.skyryn.config.Lang.tr(" min", " мин");
		return (min / 60) + com.ryn.skyryn.config.Lang.tr(" h", " ч");
	}

	public static boolean stale() {
		return readAt > 0 && System.currentTimeMillis() - readAt > 30 * 60_000L;
	}

	public static double dropsPerCatch() {
		return 1.0 + Math.max(0, RynConfig.hunterFortune) / 100.0;
	}

	public static int guaranteedExtra() {
		return (int) (Math.max(0, RynConfig.hunterFortune) / 100);
	}

	public static double extraChance() {
		return Math.max(0, RynConfig.hunterFortune) % 100;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			String title = screen.getTitle().getString();
			if (title == null || !title.contains("Stats Breakdown")) return;

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
