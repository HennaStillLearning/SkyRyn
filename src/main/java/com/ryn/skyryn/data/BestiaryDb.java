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
import com.ryn.skyryn.config.ConfigManager;

/**
 * Плашки бестиария: типы моба, статы, лут + текстура головы для иконки.
 *
 * Два слоя, как у гайда: jar-ресурс /skyryn/bestiary.json — основа для ВСЕХ
 * игроков (снята с прокачанного бестиария и вшита), а живой захват из GUI
 * (BestiaryReader) кладётся сверху и обновляет. Так плашку видит даже тот, кто
 * моба ни разу не убивал — данные-то публичные (та же /bestiary, вики).
 *
 * Личные Kills/Deaths тут не храним: режем при записи (вариант с автором) —
 * они протухают без перезахода в меню, а статика вечна.
 *
 * Ключ — очищенное имя моба без "[LvX]" в нижнем регистре ("empyrean sphinx",
 * "alligator"). Ищем по имени из аргумента команды в гайде (/bestiary Sphinx).
 */
public class BestiaryDb {

	/** lines — §-строки плашки (имя + типы + статы + лут). skin — base64 текстуры головы или "". */
	public record Plaque(List<String> lines, String skin) {}

	private static final Map<String, Plaque> PLAQUES = new LinkedHashMap<>();

	/**
	 * Семьи бестиария, чьи члены НЕ содержат общее имя в названии (суффикс-матч
	 * getAll их не собирает). Ключ — имя-запрос из гайда (cmd:/bestiary Tidetot),
	 * значение — члены семьи по порядку. Плашка члена берётся, если он захвачен.
	 * (Tidetot = семья Drowned Reliquary: Tidetot, Hydrospear, Seacruse.)
	 */
	private static final Map<String, List<String>> FAMILIES = Map.of(
			"tidetot", List.of("tidetot", "hydrospear", "seacurse"));

	/** Кладёт плашку. true — что-то изменилось (нужно сохранить). */
	public static boolean put(String mobKey, List<String> lines, String skin) {
		if (mobKey == null || mobKey.isBlank() || lines == null || lines.isEmpty()) return false;
		String k = mobKey.toLowerCase().trim();
		Plaque p = new Plaque(new ArrayList<>(lines), skin == null ? "" : skin);
		Plaque old = PLAQUES.get(k);
		if (old != null && old.lines().equals(p.lines()) && old.skin().equals(p.skin())) return false;
		PLAQUES.put(k, p);
		return true;
	}

	/**
	 * Плашка для имени моба из гайда. Точное совпадение → имя, оканчивающееся на
	 * запрос ("Empyrean Sphinx" для "Sphinx") → просто вхождение. Нет — null.
	 */
	public static Plaque get(String query) {
		if (query == null || query.isBlank()) return null;
		String q = query.toLowerCase().trim();
		Plaque exact = PLAQUES.get(q);
		if (exact != null) return exact;
		Plaque best = null;
		for (Map.Entry<String, Plaque> e : PLAQUES.entrySet()) {
			String k = e.getKey();
			if (k.endsWith(" " + q)) return e.getValue();
			if (best == null && k.contains(q)) best = e.getValue();
		}
		return best;
	}

	/**
	 * Все варианты моба. У мифологических их несколько — уровень зависит от
	 * редкости Griffin pet (Exalted, Empyrean...), статы отличаются, поэтому
	 * показываем все. Сначала совпадения по суффиксу ("... sphinx"), если нет —
	 * по вхождению; точное имя тоже сюда попадёт.
	 */
	public static List<Plaque> getAll(String query) {
		List<Plaque> out = new ArrayList<>();
		if (query == null || query.isBlank()) return out;
		String q = query.toLowerCase().trim();
		// Явная семья (члены с разными именами, напр. Tidetot/Hydrospear/Seacruse):
		// собираем плашки по списку членов, в заданном порядке — то, что захвачено.
		List<String> fam = FAMILIES.get(q);
		if (fam != null) {
			for (String member : fam) {
				for (Map.Entry<String, Plaque> e : PLAQUES.entrySet()) {
					if (baseName(e.getKey()).equals(member)) { out.add(e.getValue()); break; }
				}
			}
			if (!out.isEmpty()) return out;
		}
		// База ключа без хвостового " (...)" — так "zombie soldier" и
		// "zombie soldier (master)" попадают в ОДИН запрос и листаются колесом
		// (Master Mode — отдельный тир с другими статами).
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

	/** Ключ без хвостового " (variant)" — "zombie soldier (master)" -> "zombie soldier". */
	private static String baseName(String key) {
		int p = key.lastIndexOf(" (");
		return p > 0 && key.endsWith(")") ? key.substring(0, p).trim() : key;
	}

	public static boolean has(String query) { return get(query) != null; }

	// ===== Личные киллы (строка «Kills: N» из лора; снимаются при листании /bestiary) =====
	// KILLS — число из игры на момент снятия. CAP_SNAP — сколько мы насчитали своих поимок
	// в ТОТ момент. Дальше показываем kills + (наши поимки сейчас − снимок), т.е. счёт
	// продолжается от игрового числа, а не начинается с нуля и не двоится.
	private static final java.util.Map<String, Integer> KILLS = new java.util.HashMap<>();
	private static final java.util.Map<String, Integer> CAP_SNAP = new java.util.HashMap<>();

	public static void putKills(String mobKey, int n) {
		if (mobKey == null || mobKey.isBlank()) return;
		String k = mobKey.toLowerCase().trim();
		KILLS.put(k, n);
		CAP_SNAP.put(k, com.ryn.skyryn.config.RynConfig.lifeCaptures.getOrDefault(k, 0));
	}
	public static java.util.Map<String, Integer> allKills() { return KILLS; }
	public static java.util.Map<String, Integer> allCapSnaps() { return CAP_SNAP; }
	public static void putCapSnap(String mobKey, int n) { if (mobKey != null && !mobKey.isBlank()) CAP_SNAP.put(mobKey.toLowerCase().trim(), n); }

	/** Ключ в базе киллов по запросу (точный → по суффиксу → по вхождению), либо null. */
	public static String killKey(String query) {
		if (query == null || query.isBlank()) return null;
		String q = query.toLowerCase().trim();
		if (KILLS.containsKey(q)) return q;
		String best = null;
		for (String k : KILLS.keySet()) {
			if (k.endsWith(" " + q)) return k;
			if (best == null && k.contains(q)) best = k;
		}
		return best;
	}

	/** Киллы моба на момент снятия, -1 если неизвестно. */
	public static int kills(String query) {
		String k = killKey(query);
		return k == null ? -1 : KILLS.get(k);
	}

	/**
	 * Живое число убийств: игровое (из бестиария) + наши поимки, сделанные ПОСЛЕ снятия.
	 * -1 — моба в бестиарии не видели.
	 */
	public static int killsLive(String query) {
		String k = killKey(query);
		if (k == null) return -1;
		int base = KILLS.get(k);
		int since = com.ryn.skyryn.config.RynConfig.lifeCaptures.getOrDefault(k, 0) - CAP_SNAP.getOrDefault(k, 0);
		return base + Math.max(0, since);
	}

	public static Map<String, Plaque> all() { return PLAQUES; }

	public static void clear() { PLAQUES.clear(); }

	public static int size() { return PLAQUES.size(); }

	/** Загружает вшитый бандл. Живой захват из конфига кладётся поверх (в ConfigManager). */
	public static void loadBundle() {
		try (InputStream in = BestiaryDb.class.getResourceAsStream("/skyryn/bestiary.json")) {
			if (in == null) return;
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			fromJson(root);
			com.ryn.skyryn.config.SkyLog.d("Бандл бестиария: плашек " + PLAQUES.size());
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("bestiary.json не прочитан: " + e);
		}
	}

	/** Разбирает {"mob": {"lines":[...], "skin":"..."}} в кэш. */
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
