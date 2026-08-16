package com.ryn.skyryn.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.ryn.skyryn.waypoint.SkyBlockCheck;

/**
 * Кэш настоящих иконок шардов (ItemStack). Шарды — кастомные головы Hypixel, из
 * наших данных их не собрать, зато из открытого меню — легко.
 *
 * Снимаем со ВСЕХ контейнер-экранов на скайблоке (Hunting Box, Fusion Box, базар,
 * инвентарь — по имени) и из Attribute Menu (по ключу из лора — там ВСЕ шарды).
 *
 * СОХРАНЯЮТСЯ между сессиями: при захвате предмет сериализуется в SNBT-строку и
 * пишется в config/skyryn-icons.json при выходе; при старте строки читаются, а в
 * ItemStack разворачиваются лениво (нужен registryAccess живого мира).
 */
public class ShardIcons {

	private static final Map<String, ItemStack> ICONS = new HashMap<>();     // рантайм
	private static final Map<String, JsonElement> RAW = new HashMap<>();     // для сохранения (JSON предмета)
	private static boolean dirty = false;

	private static RegistryOps<JsonElement> ops() {
		var conn = Minecraft.getInstance().getConnection();
		return conn == null ? null : RegistryOps.create(JsonOps.INSTANCE, conn.registryAccess());
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			if (!SkyBlockCheck.onSkyBlock()) return;
			ScreenEvents.afterExtract(screen).register((scr, ctx, mx, my, d) -> scan(cs));
		});
	}

	private static void scan(AbstractContainerScreen<?> screen) {
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) continue;
			ItemStack st = slot.getItem();
			String key = resolveKey(st.getHoverName().getString());
			if (key != null) put(key, st);
		}
	}

	public static void put(String key, ItemStack stack) {
		if (key == null || stack == null || stack.isEmpty()) return;
		String k = key.toLowerCase();
		// Рантайм-иконку обновляем ВСЕГДА свежим стаком: кастомные головы Hypixel
		// подгружают текстуру асинхронно, и первый пойманный стак мог быть «пустым»
		// (тёмный квадрат). Свежий из открытого контейнера — уже с текстурой.
		ICONS.put(k, stack.copy());
		if (!RAW.containsKey(k)) {                       // на диск — первый увиденный
			RegistryOps<JsonElement> ops = ops();
			if (ops != null) {
				ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(json -> {
					RAW.put(k, json);
					dirty = true;
				});
			}
		}
	}

	/** Иконка шарда либо null. Лениво разворачивает сохранённый JSON. */
	public static ItemStack get(String key) {
		if (key == null) return null;
		String k = key.toLowerCase();
		ItemStack st = ICONS.get(k);
		if (st != null) return st;
		JsonElement raw = RAW.get(k);
		if (raw == null) return null;
		RegistryOps<JsonElement> ops = ops();
		if (ops == null) return null;
		var parsed = ItemStack.CODEC.parse(ops, raw).result();
		if (parsed.isPresent()) { ICONS.put(k, parsed.get()); return parsed.get(); }
		return null;
	}

	public static boolean has(String key) { return get(key) != null; }
	public static int count() { return Math.max(ICONS.size(), RAW.size()); }

	// ===== Персист =====

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("skyryn-icons.json");
	}

	public static void load() {
		try {
			Path p = file();
			if (!Files.exists(p)) return;
			JsonObject json = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			for (var e : json.entrySet()) RAW.put(e.getKey(), e.getValue());
			com.ryn.skyryn.config.SkyLog.d("Иконок из кэша: " + RAW.size());
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("skyryn-icons.json не прочитан: " + e);
		}
	}

	public static void save() {
		if (!dirty) return;
		try {
			JsonObject json = new JsonObject();
			for (var e : RAW.entrySet()) json.add(e.getKey(), e.getValue());
			Files.writeString(file(), json.toString());
			dirty = false;
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("skyryn-icons.json не сохранён: " + e);
		}
	}

	// ===== Разбор имени → ключ шарда =====

	static String resolveKey(String rawName) {
		if (rawName == null) return null;
		return tryName(rawName.replaceAll("§.", "").trim());
	}

	private static String tryName(String n) {
		if (n == null || n.isBlank()) return null;
		if (ShardDb.shard(n) != null) return n.toLowerCase();
		if (n.endsWith(" Shard")) {
			String r = tryName(n.substring(0, n.length() - 6).trim());
			if (r != null) return r;
		}
		int sp = n.lastIndexOf(' ');
		if (sp > 0 && n.substring(sp + 1).matches("[IVXL]+")) {
			return tryName(n.substring(0, sp).trim());
		}
		return null;
	}
}
