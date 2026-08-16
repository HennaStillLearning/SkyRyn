package com.ryn.skyryn.data;

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

public class AttributeMenuReader {
	private static final Pattern SOURCE =
			Pattern.compile("Source:\\s*(.+?)\\s+Shard\\s*\\(([A-Za-z]\\d+)\\)");

	private static final Pattern LEVEL =
			Pattern.compile("Attribute Level:\\s*(\\d+)");

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			String title = screen.getTitle().getString();
			if (title == null || !title.contains("Attribute Menu")) return;

			ScreenEvents.afterExtract(screen).register((scr, ctx, mx, my, delta) -> scan(cs));

			ScreenEvents.remove(screen).register(scr -> {
				if (!dirty) return;
				dirty = false;
				ConfigManager.save();
				com.ryn.skyryn.config.SkyLog.d("Уровни аттрибутов сохранены: "
						+ ShardProgress.allLevels().size());
			});
		});
	}

	private static boolean dirty = false;

	private static void scan(AbstractContainerScreen<?> screen) {
		int found = 0;
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) continue;
			ItemStack stack = slot.getItem();
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) continue;

			String key = null;
			int level = -1;
			for (Component line : lore.lines()) {
				String s = clean(line.getString());

				Matcher src = SOURCE.matcher(s);
				if (src.find()) {
					key = ShardDb.byId(src.group(2));
					if (key == null) {
						String name = src.group(1).trim().toLowerCase();
						if (ShardDb.shard(name) != null) key = name;
					}
					continue;
				}
				Matcher lvl = LEVEL.matcher(s);
				if (lvl.find()) {
					try { level = Integer.parseInt(lvl.group(1)); }
					catch (NumberFormatException ignored) { }
				}
			}

			if (key != null) {
				ShardIcons.put(key, stack);
			}
			if (key != null && level >= 0) {
				if (ShardProgress.setLevel(key, level)) dirty = true;
				found++;
			}
		}
		if (found > 0 && found != lastReported) {
			lastReported = found;
			com.ryn.skyryn.config.SkyLog.d("Attribute Menu: уровней прочитано " + found
					+ ", всего известно " + ShardProgress.allLevels().size());
		}
	}

	private static int lastReported = -1;

	private static String clean(String s) {
		return s == null ? "" : s.replaceAll("§.", "").trim();
	}
}
