package com.ryn.skyryn.data;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.RynConfig;

/**
 * Читает уровни перков из «Safari Essence Shop» (NPC Archie) — как
 * {@link AttributeMenuReader} для аттрибутов: предметы статичны, меняются только
 * уровни. Читаем при открытии, сохраняем (перк-уровни живут в RynConfig.ints
 * под ключом «perk.<имя>»). Ничего не нажимаем.
 *
 * Точный формат лора неизвестен — уровень ищем по: римской цифре в конце имени
 * («Sparkling Specialist V») ИЛИ «Level/Tier N» в лоре. Диагностика в лог, чтобы
 * при промахе поправить парс по реальной строке.
 */
public class SafariPerks {

	private static final Pattern LORE_LEVEL = Pattern.compile("(?:level|tier)\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
	private static boolean dirty = false;
	private static boolean loggedOnce = false;

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			String title = screen.getTitle().getString();
			if (title == null || !title.toLowerCase().contains("essence shop")) return;
			ScreenEvents.afterExtract(screen).register((scr, ctx, mx, my, delta) -> scan(cs));
			ScreenEvents.remove(screen).register(scr -> { if (dirty) { dirty = false; ConfigManager.save(); } });
		});
	}

	private static void scan(AbstractContainerScreen<?> cs) {
		for (Slot s : cs.getMenu().slots) {
			if (!s.hasItem()) continue;
			ItemStack st = s.getItem();
			ItemLore lore = st.get(DataComponents.LORE);
			if (lore == null) continue;
			String name = strip(st.getHoverName().getString());
			List<String> lines = lore.lines().stream().map(c -> strip(c.getString())).toList();
			int lvl = parseLevel(name, lines);
			if (lvl < 0) continue;
			String key = perkKey(name);
			if (key.isEmpty()) continue;
			if (!loggedOnce) com.ryn.skyryn.config.SkyLog.d("перк: '" + name + "' → уровень " + lvl);
			if (RynConfig.getInt("perk." + key, -1) != lvl) { RynConfig.setInt("perk." + key, lvl); dirty = true; }
		}
		loggedOnce = true;
	}

	/** Уровень перка: римская цифра в конце имени ИЛИ «Level N» в лоре; иначе -1 (не перк). */
	private static int parseLevel(String name, List<String> lore) {
		String[] w = name.trim().split("\\s+");
		if (w.length > 0) { int r = roman(w[w.length - 1]); if (r > 0) return r; }
		for (String ln : lore) { Matcher m = LORE_LEVEL.matcher(ln); if (m.find()) return Integer.parseInt(m.group(1)); }
		return -1;
	}

	/** Имя перка без хвостовой римской цифры, нижним регистром. */
	private static String perkKey(String name) {
		String[] w = name.trim().split("\\s+");
		if (w.length > 1 && roman(w[w.length - 1]) > 0)
			name = String.join(" ", java.util.Arrays.copyOf(w, w.length - 1));
		return name.trim().toLowerCase();
	}

	private static int roman(String s) {
		return switch (s.toUpperCase()) {
			case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
			case "VI" -> 6; case "VII" -> 7; case "VIII" -> 8; case "IX" -> 9; case "X" -> 10;
			default -> 0;
		};
	}

	/** Куплен ли перк Sparkling Specialist (уровень ≥ 1). */
	public static boolean hasSparkling() {
		for (var en : RynConfig.ints.entrySet())
			if (en.getKey().startsWith("perk.") && en.getKey().contains("sparkling") && en.getValue() > 0) return true;
		return false;
	}

	/** Список перков «Имя N | …» для #perks. */
	public static String list() {
		StringBuilder sb = new StringBuilder();
		for (var en : RynConfig.ints.entrySet()) {
			if (!en.getKey().startsWith("perk.")) continue;
			if (sb.length() > 0) sb.append(" | ");
			sb.append(cap(en.getKey().substring(5))).append(' ').append(en.getValue());
		}
		return sb.length() == 0 ? "-" : sb.toString();
	}

	private static String cap(String s) {
		String[] w = s.split(" ");
		StringBuilder sb = new StringBuilder();
		for (String x : w) { if (x.isEmpty()) continue; if (sb.length() > 0) sb.append(' '); sb.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1)); }
		return sb.toString();
	}

	private static String strip(String s) { return s == null ? "" : s.replaceAll("§.", ""); }
}
