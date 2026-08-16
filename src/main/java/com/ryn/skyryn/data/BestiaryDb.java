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

public class BestiaryDb {
	public record Plaque(List<String> lines, String skin) {}

	private static final Map<String, Plaque> PLAQUES = new LinkedHashMap<>();

	private static final Map<String, List<String>> FAMILIES = Map.of(
			"tidetot", List.of("tidetot", "hydrospear", "seacurse"));

	public static boolean put(String mobKey, List<String> lines, String skin) {
		if (mobKey == null || mobKey.isBlank() || lines == null || lines.isEmpty()) return false;
		String k = mobKey.toLowerCase().trim();
		Plaque p = new Plaque(new ArrayList<>(lines), skin == null ? "" : skin);
		Plaque old = PLAQUES.get(k);
		if (old != null && old.lines().equals(p.lines()) && old.skin().equals(p.skin())) return false;
		PLAQUES.put(k, p);
		return true;
	}

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

	public static List<Plaque> getAll(String query) {
		List<Plaque> out = new ArrayList<>();
		if (query == null || query.isBlank()) return out;
		String q = query.toLowerCase().trim();
		List<String> fam = FAMILIES.get(q);
		if (fam != null) {
			for (String member : fam) {
				for (Map.Entry<String, Plaque> e : PLAQUES.entrySet()) {
					if (baseName(e.getKey()).equals(member)) { out.add(e.getValue()); break; }
				}
			}
			if (!out.isEmpty()) return out;
		}
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

	private static String baseName(String key) {
		int p = key.lastIndexOf(" (");
		return p > 0 && key.endsWith(")") ? key.substring(0, p).trim() : key;
	}

	public static boolean has(String query) { return get(query) != null; }

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

	public static int kills(String query) {
		String k = killKey(query);
		return k == null ? -1 : KILLS.get(k);
	}

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
