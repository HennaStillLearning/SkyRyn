package com.ryn.skyryn.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShardDb {
	public static final int INPUT_PER_FUSION = 5;

	public static class Recipe {
		public final String output;
		public final String a;
		public final String b;
		public final int qty;

		public String firstClick() { return b; }
		public String secondClick() { return a; }
		public Recipe(String output, String a, String b, int qty) {
			this.output = output;
			this.a = a;
			this.b = b;
			this.qty = qty;
		}
	}

	public static class Shard {
		public final String key;
		public final String id;
		public final String name;
		public final String bazaarId;
		public final boolean reptile;
		public final String rarity;
		public final String source;
		public final String family;
		public final String attrTitle;
		public final String attrDesc;
		public final String attrDescEn;
		public final boolean noLevels;
		public final boolean direct;
		public final double rate;
		public final int fuseAmount;
		public final String howToHunt;

		public String attrDescEnPlain() {
			return attrDescEn == null ? "" : attrDescEn.replaceAll("§.", "");
		}

		public boolean hasAttribute() {
			return !noLevels && attrTitle != null && !attrTitle.isEmpty();
		}

		public String attrDescShown() {
			if (com.ryn.skyryn.config.RynConfig.isRu()) {
				return attrDesc != null && !attrDesc.isEmpty() ? attrDesc : attrDescEn;
			}
			return attrDescEn != null && !attrDescEn.isEmpty() ? attrDescEn : attrDesc;
		}

		public Shard(String key, String id, String name, String bazaarId, boolean reptile,
					 String rarity, String source, String family,
					 String attrTitle, String attrDesc, String attrDescEn, boolean noLevels,
					 boolean direct, double rate, int fuseAmount, String howToHunt) {
			this.attrDescEn = attrDescEn;
			this.noLevels = noLevels;
			this.direct = direct;
			this.rate = rate;
			this.fuseAmount = fuseAmount;
			this.howToHunt = howToHunt;
			this.key = key;
			this.id = id;
			this.name = name;
			this.bazaarId = bazaarId;
			this.reptile = reptile;
			this.rarity = rarity;
			this.source = source;
			this.family = family;
			this.attrTitle = attrTitle;
			this.attrDesc = attrDesc;
		}
	}

	private static String str(JsonObject o, String field, String fallback) {
		if (!o.has(field) || o.get(field).isJsonNull()) return fallback;
		return o.get(field).getAsString();
	}

	private static final Map<String, Shard> SHARDS = new LinkedHashMap<>();
	private static final Map<String, String> BY_ID = new LinkedHashMap<>();
	private static final Map<String, String> BY_NAME = new LinkedHashMap<>();
	private static final Map<String, List<Recipe>> RECIPES = new LinkedHashMap<>();

	private static boolean loaded = false;

	private static Map<String, String> loadRu() {
		Map<String, String> out = new LinkedHashMap<>();
		try (InputStream in = ShardDb.class.getResourceAsStream("/skyryn/attr-ru.json")) {
			if (in == null) return out;
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (String k : root.keySet()) {
				if (k.startsWith("_")) continue;
				if (root.get(k).isJsonPrimitive()) out.put(k.toLowerCase(), root.get(k).getAsString());
			}
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("Ошибка чтения attr-ru.json: " + e);
		}
		return out;
	}

	public static void load() {
		SHARDS.clear();
		RECIPES.clear();
		BY_ID.clear();
		BY_NAME.clear();
		Map<String, String> ru = loadRu();
		try (InputStream in = ShardDb.class.getResourceAsStream("/skyryn/shards.json")) {
			if (in == null) {
				com.ryn.skyryn.config.SkyLog.d("shards.json не найден в jar");
				return;
			}
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

			JsonObject shards = root.getAsJsonObject("shards");
			for (String key : shards.keySet()) {
				JsonObject s = shards.getAsJsonObject(key);
				String name = str(s, "name", key);
				String bz = str(s, "bazaarId", null);
				boolean reptile = s.has("reptile") && !s.get("reptile").isJsonNull()
						&& s.get("reptile").getAsBoolean();
				String rarity = str(s, "rarity", "common");
				String source = str(s, "source", "");
				String family = str(s, "family", "");
				String attrTitle = str(s, "attrTitle", "");
				String attrDescEn = str(s, "attrDesc", "");
				String attrDesc = ru.getOrDefault(key, attrDescEn);
				String id = str(s, "id", "");
				boolean noLevels = s.has("noLevels") && !s.get("noLevels").isJsonNull()
						&& s.get("noLevels").getAsBoolean();
				boolean direct = s.has("direct") && !s.get("direct").isJsonNull()
						&& s.get("direct").getAsBoolean();
				double rate = s.has("rate") && !s.get("rate").isJsonNull()
						? s.get("rate").getAsDouble() : 0;
				int fuse = s.has("fuse") && !s.get("fuse").isJsonNull()
						? s.get("fuse").getAsInt() : 5;
				String howToHunt = str(s, "howToHunt", "");
				Shard shard = new Shard(key, id, name, bz, reptile, rarity, source, family,
						attrTitle, attrDesc, attrDescEn, noLevels, direct, rate, fuse, howToHunt);
				SHARDS.put(key, shard);
				if (!id.isEmpty()) BY_ID.put(id.toUpperCase(), key);
				BY_NAME.put(name.toLowerCase(), key.toLowerCase());
			}

			JsonObject recipes = root.getAsJsonObject("recipes");
			int pairs = 0;
			for (String out : recipes.keySet()) {
				JsonArray arr = recipes.getAsJsonArray(out);
				List<Recipe> list = new ArrayList<>();
				for (int i = 0; i < arr.size(); i++) {
					JsonObject r = arr.get(i).getAsJsonObject();
					String a = r.get("a").getAsString();
					String b = r.get("b").getAsString();
					int qty = r.get("qty").getAsInt();
					if (!SHARDS.containsKey(a) || !SHARDS.containsKey(b) || qty <= 0) {
						com.ryn.skyryn.config.SkyLog.d("пропущен рецепт " + out + ": " + a + " + " + b);
						continue;
					}
					list.add(new Recipe(out, a, b, qty));
					pairs++;
				}
				if (!list.isEmpty()) RECIPES.put(out, list);
			}

			loaded = true;
			com.ryn.skyryn.config.SkyLog.d("Загружено шардов: " + SHARDS.size()
					+ ", рецептов: " + RECIPES.size() + " (" + pairs + " пар)"
					+ ", описаний по-русски: " + ru.size());
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("Ошибка чтения shards.json: " + e);
		}
	}

	public static boolean isLoaded() { return loaded; }

	public static boolean hasRecipe(String key) {
		return key != null && RECIPES.containsKey(key.toLowerCase());
	}

	public static List<Recipe> recipesFor(String key) {
		if (key == null) return Collections.emptyList();
		return RECIPES.getOrDefault(key.toLowerCase(), Collections.emptyList());
	}

	private static Map<String, List<Recipe>> USED_IN = null;

	public static List<Recipe> usedIn(String key) {
		if (key == null) return Collections.emptyList();
		if (USED_IN == null) {
			Map<String, List<Recipe>> idx = new HashMap<>();
			for (List<Recipe> list : RECIPES.values())
				for (Recipe r : list) {
					idx.computeIfAbsent(r.a, k -> new ArrayList<>()).add(r);
					if (!r.b.equals(r.a)) idx.computeIfAbsent(r.b, k -> new ArrayList<>()).add(r);
				}
			USED_IN = idx;
		}
		return USED_IN.getOrDefault(key.toLowerCase(), Collections.emptyList());
	}

	public static Set<String> allShards() { return SHARDS.keySet(); }

	public static Set<String> allCraftable() { return RECIPES.keySet(); }

	public static String byId(String id) {
		return id == null ? null : BY_ID.get(id.toUpperCase());
	}

	public static String keyByName(String name) {
		return name == null ? null : BY_NAME.get(name.trim().toLowerCase());
	}

	public static Shard shard(String key) {
		return key == null ? null : SHARDS.get(key.toLowerCase());
	}

	public static String bazaarId(String key) {
		Shard s = shard(key);
		return s == null ? null : s.bazaarId;
	}

	public static String displayName(String key) {
		Shard s = shard(key);
		if (s != null) return s.name;
		return key == null ? "?" : key;
	}

	public static int fuseAmount(String key) {
		Shard s = shard(key);
		return s == null ? INPUT_PER_FUSION : s.fuseAmount;
	}

	public static String rarity(String key) {
		Shard s = shard(key);
		return s == null ? "common" : s.rarity;
	}

	public static String bazaarName(String key) {
		return displayName(key) + " Shard";
	}
}
