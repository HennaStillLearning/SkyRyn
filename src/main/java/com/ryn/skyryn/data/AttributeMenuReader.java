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

/**
 * Читает уровни аттрибутов из Attribute Menu.
 *
 * Уровень даёт ТОЛЬКО syphon, и живёт он здесь, а не в Hunting Box. В боксе
 * лежит запас шардов ("Owned: 1 Shard") — к уровню он отношения не имеет:
 * у Chill с одним шардом в боксе аттрибут может быть уже X уровня.
 *
 * Формат лора — с записи игры, не с чужих слов:
 *     Skeletal Ruler X
 *     Combat
 *     Increase damage to Skeletal mobs by +30% +39%.
 *     Source: Chill Shard (C12)
 *     Rarity: COMMON
 *     Enabled: Yes
 *     Attribute Level: 10 (MAX!)
 *
 * Шард ищем по id в скобках (C12) — это id самого Hypixel, он же лежит у нас
 * в базе. Сверять имена не нужно вовсе.
 *
 * Только смотрим на экран, который игрок открыл сам: ничего не нажимаем.
 */
public class AttributeMenuReader {

	/** "Source: Chill Shard (C12)" */
	private static final Pattern SOURCE =
			Pattern.compile("Source:\\s*(.+?)\\s+Shard\\s*\\(([A-Za-z]\\d+)\\)");

	/** "Attribute Level: 10 (MAX!)" */
	private static final Pattern LEVEL =
			Pattern.compile("Attribute Level:\\s*(\\d+)");

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			String title = screen.getTitle().getString();
			if (title == null || !title.contains("Attribute Menu")) return;

			// Слоты сервер досылает не сразу, поэтому читаем каждый кадр, пока
			// экран открыт. Дёшево: пара десятков строк лора.
			ScreenEvents.afterExtract(screen).register((scr, ctx, mx, my, delta) -> scan(cs));

			// Сохраняем при закрытии, а не на каждый кадр: писать конфиг 60 раз
			// в секунду незачем. Без сохранения уровни жили до перезахода.
			ScreenEvents.remove(screen).register(scr -> {
				if (!dirty) return;
				dirty = false;
				ConfigManager.save();
				com.ryn.skyryn.config.SkyLog.d("Уровни аттрибутов сохранены: "
						+ ShardProgress.allLevels().size());
			});
		});
	}

	/** Появилось что-то новое с прошлого сохранения. */
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
					// id не нашёлся — падать назад к имени: вдруг патч добавил шард,
					// которого нет в нашей базе по id, но имя совпадает.
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
				// Иконка: в Attribute Menu перечислены ВСЕ шарды — лучший источник
				// иконок для сетки (в боксе недокачанных обычно нет). Ключ уже
				// определён по лору, имя предмета тут — имя аттрибута, не шарда.
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
