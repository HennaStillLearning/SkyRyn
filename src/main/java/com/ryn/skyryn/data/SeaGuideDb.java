package com.ryn.skyryn.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Плашки Sea Creature Guide — снимаются с меню "Sea Creature Guide", пока игрок
 * его листает. В отличие от бестиария ({@link BestiaryDb}) тут не статы/лут, а
 * требования и шансы: уровень, редкость, типы, Fishing Skill, Spawn Chance,
 * Categories (Liquid/Island), Special Requirements. Их вешаем на слово
 * "Требования" морских шардов — игрок сразу видит, что нужно и с каким шансом.
 *
 * Всё как у BestiaryDb: двухслойно (бандл + живой захват), ключ — имя моба без
 * "[Lvl X]" и без "(RARITY)", нижним регистром.
 */
public class SeaGuideDb {

	public record Plaque(List<String> lines, String skin) {}

	private static final Map<String, Plaque> PLAQUES = new LinkedHashMap<>();

	public static boolean put(String mobKey, List<String> lines, String skin) {
		if (mobKey == null || mobKey.isBlank() || lines == null || lines.isEmpty()) return false;
		String k = mobKey.toLowerCase().trim();
		Plaque p = new Plaque(new ArrayList<>(lines), skin == null ? "" : skin);
		Plaque old = PLAQUES.get(k);
		if (old != null && old.lines().equals(p.lines()) && old.skin().equals(p.skin())) return false;
		PLAQUES.put(k, p);
		return true;
	}

	public static List<Plaque> getAll(String query) {
		List<Plaque> out = new ArrayList<>();
		if (query == null || query.isBlank()) return out;
		String q = query.toLowerCase().trim();
		// База без хвостового " (...)" — тиры (Master и т.п.) листаются как один моб.
		for (Map.Entry<String, Plaque> e : PLAQUES.entrySet()) {
			String base = baseName(e.getKey());
			if (base.equals(q) || base.endsWith(" " + q)) out.add(e.getValue());
		}
		if (!out.isEmpty()) return out;
		for (Map.Entry<String, Plaque> e : PLAQUES.entrySet()) {
			if (e.getKey().contains(q)) out.add(e.getValue());
		}
		return out;
	}

	/** Ключ без хвостового " (variant)". */
	private static String baseName(String key) {
		int p = key.lastIndexOf(" (");
		return p > 0 && key.endsWith(")") ? key.substring(0, p).trim() : key;
	}

	public static Map<String, Plaque> all() { return PLAQUES; }

	public static void clear() { PLAQUES.clear(); }

	public static int size() { return PLAQUES.size(); }

	public static void loadBundle() {
		try (InputStream in = SeaGuideDb.class.getResourceAsStream("/skyryn/seaguide.json")) {
			if (in == null) return;
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			fromJson(root);
			com.ryn.skyryn.config.SkyLog.d("Бандл Sea Creature Guide: плашек " + PLAQUES.size());
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("seaguide.json не прочитан: " + e);
		}
	}

	public static void fromJson(JsonObject root) {
		for (String k : root.keySet()) {
			if (!root.get(k).isJsonObject()) continue;
			JsonObject o = root.getAsJsonObject(k);
			List<String> lines = new ArrayList<>();
			if (o.has("lines") && o.get("lines").isJsonArray()) {
				for (var e : o.getAsJsonArray("lines")) lines.add(e.getAsString());
			}
			String skin = o.has("skin") && !o.get("skin").isJsonNull() ? o.get("skin").getAsString() : "";
			put(k, lines, skin);
		}
	}
}
