package com.ryn.skyryn.data;

import com.mojang.authlib.properties.Property;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.ryn.skyryn.config.ConfigManager;

/**
 * Снимает плашки мобов, пока игрок листает /bestiary.
 *
 * Экран не трогаем — только читаем лор предметов. Плашку узнаём по подписи:
 * имя вида "[Lv1,250] Empyrean Sphinx" + строка "Mob Stats:" в лоре. Другого
 * такого в игре нет, поэтому по заголовку меню не привязываемся (он у каждой
 * семьи свой: "Mythological Creatures ➜ Sphinx").
 *
 * Личные Kills/Deaths режем: без перезахода в меню они врут, а статика вечна
 * (вариант, согласованный с автором).
 */
public class BestiaryReader {

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;

			// Слоты сервер досылает не сразу — читаем каждый кадр, пока открыт.
			ScreenEvents.afterExtract(screen).register((scr, ctx, mx, my, delta) -> scan(cs));

			ScreenEvents.remove(screen).register(scr -> {
				if (!dirty) return;
				dirty = false;
				ConfigManager.save();
				com.ryn.skyryn.config.SkyLog.d("Бестиарий сохранён, плашек: " + BestiaryDb.size());
			});
		});
	}

	private static boolean dirty = false;
	private static int lastReported = -1;

	private static void scan(AbstractContainerScreen<?> screen) {
		int found = 0;
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) continue;
			ItemStack stack = slot.getItem();

			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) continue;
			String name = legacy(stack.getHoverName());
			String plain = strip(name);

			List<String> raw = new ArrayList<>();
			boolean isBestiary = false, isScg = false, hasMobTypes = false;
			int kills = -1;
			for (Component line : lore.lines()) {
				String s = legacy(line);
				String p = strip(s);
				if (p.equals("Mob Stats:")) isBestiary = true;
				if (p.startsWith("Spawn Chance:") || p.startsWith("Fishing Skill:")) isScg = true;
				if (p.startsWith("Mob Types:")) hasMobTypes = true;
				if (p.startsWith("Kills:")) {
					java.util.regex.Matcher m = java.util.regex.Pattern.compile("([\\d,]+)").matcher(p);
					if (m.find()) try { kills = Integer.parseInt(m.group(1).replace(",", "")); } catch (Exception ignored) { }
				}
				raw.add(s);
			}

			// Полная плашка "[LvN] Name" + "Mob Stats:" — статика + киллы.
			if (plain.startsWith("[Lv") && isBestiary) {
				if (BestiaryDb.put(mobKey(name), build(name, raw), skin(stack))) dirty = true;
				if (kills >= 0) { BestiaryDb.putKills(mobKey(name), kills); dirty = true; }
				found++;
			} else if (plain.startsWith("[Lv") && isScg) {
				if (SeaGuideDb.put(scgKey(name), buildScg(name, raw), skin(stack))) dirty = true;
				found++;
			} else if (kills >= 0 && (hasMobTypes || plain.matches(".*\\s[IVXLCDM]+$"))) {
				// Сетка «Critter Safari ➜ Cavern Biome» и «/bestiary ➜ Семья»: имя «Gemzie X»
				// + Kills в лоре, но без «Mob Stats:». Снимаем ТОЛЬКО киллы — сразу всех мобов
				// на экране, чтобы не прокликивать каждого по отдельности.
				String gk = gridMobKey(name);
				if (!gk.isBlank()) { BestiaryDb.putKills(gk, kills); dirty = true; found++; }
			}
		}
		if (found > 0 && found != lastReported) {
			lastReported = found;
			com.ryn.skyryn.config.SkyLog.d("Плашек на экране " + found
					+ " (бестиарий " + BestiaryDb.size() + ", SCG " + SeaGuideDb.size() + ")");
		}
	}

	/** SCG-плашка: имя + лор, но без хвостовой подсказки "Click to view Bestiary!". */
	private static List<String> buildScg(String name, List<String> lore) {
		List<String> out = new ArrayList<>();
		out.add(name);
		boolean prevBlank = false;
		for (String line : lore) {
			String p = strip(line);
			if (p.startsWith("Click to view")) continue;
			boolean blank = p.isEmpty();
			if (blank && prevBlank) continue;
			out.add(line);
			prevBlank = blank;
		}
		while (!out.isEmpty() && strip(out.get(out.size() - 1)).isEmpty()) {
			out.remove(out.size() - 1);
		}
		return out;
	}

	/** Ключ SCG: имя без "[Lvl X]" и без " (RARITY)", нижним регистром. */
	private static String scgKey(String name) {
		String s = strip(name);
		int i = s.indexOf(']');
		if (i >= 0) s = s.substring(i + 1);
		s = s.trim();
		int par = s.lastIndexOf('(');
		if (par > 0 && s.endsWith(")")) s = s.substring(0, par).trim();
		return s.toLowerCase();
	}

	/**
	 * Строит плашку: имя + лор, но без блока "Your ... Stats / Kills / Deaths"
	 * (протухает) и без сдвоенных пустых строк, оставшихся после вырезки.
	 */
	private static List<String> build(String name, List<String> lore) {
		List<String> out = new ArrayList<>();
		out.add(name);
		boolean prevBlank = false;
		for (String line : lore) {
			String p = strip(line);
			if (p.startsWith("Kills:") || p.startsWith("Deaths:")) continue;
			if (p.startsWith("Your ") && p.endsWith("Stats:")) continue;
			boolean blank = p.isEmpty();
			if (blank && prevBlank) continue; // схлопываем двойные пустые
			out.add(line);
			prevBlank = blank;
		}
		// хвостовая пустая строка ни к чему
		while (!out.isEmpty() && strip(out.get(out.size() - 1)).isEmpty()) {
			out.remove(out.size() - 1);
		}
		return out;
	}

	/** Base64-текстура головы моба из слота — для иконки в плашке. "" если не голова. */
	private static String skin(ItemStack stack) {
		try {
			ResolvableProfile prof = stack.get(DataComponents.PROFILE);
			if (prof == null) return "";
			for (Property pr : prof.partialProfile().properties().get("textures")) {
				if (pr.value() != null && !pr.value().isBlank()) return pr.value();
			}
		} catch (Exception ignored) { }
		return "";
	}

	/**
	 * Ключ моба из сетки биома: имя «Gemzie X» / «Doomspiral IX» — срезаем хвостовой
	 * римский уровень, остаётся «gemzie». Уровень пишут только римскими заглавными,
	 * поэтому обычное последнее слово («Shrimp») не заденем.
	 */
	private static String gridMobKey(String name) {
		String s = strip(name).trim();
		int sp = s.lastIndexOf(' ');
		if (sp > 0 && s.substring(sp + 1).matches("[IVXLCDM]+")) s = s.substring(0, sp);
		return s.trim().toLowerCase();
	}

	/** Ключ кэша: имя без "[LvX]" и без цветов, нижним регистром. */
	private static String mobKey(String name) {
		String s = strip(name);
		int i = s.indexOf(']');
		if (s.startsWith("[Lv") && i >= 0) s = s.substring(i + 1);
		return s.trim().toLowerCase();
	}

	private static String strip(String s) {
		return s == null ? "" : s.replaceAll("§.", "").trim();
	}

	// ---- Component -> строка с §-кодами (чтобы сохранить цвета лора) ----

	/** Разворачивает Component в легаси-строку с §-кодами. */
	static String legacy(Component c) {
		if (c == null) return "";
		StringBuilder sb = new StringBuilder();
		c.visit((style, text) -> {
			sb.append(codes(style)).append(text);
			return Optional.empty();
		}, Style.EMPTY);
		return sb.toString();
	}

	private static String codes(Style s) {
		StringBuilder sb = new StringBuilder();
		TextColor col = s.getColor();
		if (col != null) {
			ChatFormatting cf = byColor(col.getValue());
			if (cf != null) sb.append('§').append(cf.getChar());
			else sb.append("§r"); // нестандартный цвет — хотя бы сбросим бляклость
		}
		if (s.isBold()) sb.append("§l");
		if (s.isItalic()) sb.append("§o");
		if (s.isUnderlined()) sb.append("§n");
		if (s.isStrikethrough()) sb.append("§m");
		if (s.isObfuscated()) sb.append("§k");
		return sb.toString();
	}

	/** Стандартный §-цвет по RGB. Hypixel красит лор палитрой Minecraft. */
	private static ChatFormatting byColor(int rgb) {
		for (ChatFormatting f : ChatFormatting.values()) {
			if (f.isColor() && f.getColor() != null && f.getColor() == rgb) return f;
		}
		return null;
	}
}
