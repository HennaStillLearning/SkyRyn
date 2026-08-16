package com.ryn.skyryn.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * База шардов и рецептов. Читается из resources/skyryn/shards.json при старте.
 *
 * Данные лежат в файле, а не в коде, потому что их станет ~190 штук после импорта,
 * и после каждого патча Hypixel их надо будет обновлять. Файл вшит в jar —
 * никакой сети в рантайме, кроме цен базара.
 */
public class ShardDb {

	/** Фьюз всегда берёт по 5 шардов каждого входа — константа игры. */
	public static final int INPUT_PER_FUSION = 5;

	/** Рецепт: пара входов -> qty штук на выходе. */
	public static class Recipe {
		public final String output;
		public final String a;
		public final String b;
		public final int qty;
		public Recipe(String output, String a, String b, int qty) {
			this.output = output;
			this.a = a;
			this.b = b;
			this.qty = qty;
		}
	}

	public static class Shard {
		public final String key;
		/** id Hypixel: C12, U7, L3. Им подписан шард в Attribute Menu. */
		public final String id;
		public final String name;
		/** id для базара, либо null — шарда нет на базаре, цены не будет. */
		public final String bazaarId;
		/** Reptile-семейство: их выход умножает Crocodile. */
		public final boolean reptile;
		/** common/uncommon/rare/epic/legendary — от неё зависит XP за фьюз. */
		public final String rarity;
		/** Global / Hunting / Mining / Foraging / ... — откуда шард родом. */
		public final String source;
		/** "Bug", "Reptile and Croco". Пусто, если у шарда семьи нет. */
		public final String family;
		/** Название аттрибута: "Nature Elemental". */
		public final String attrTitle;
		/** Что даёт аттрибут на 1 уровне, для показа (по-русски). */
		public final String attrDesc;
		/**
		 * Оригинал того же описания, по-английски.
		 *
		 * Нужен не для показа, а для разбора: правила баффов написаны в самих
		 * описаниях ("Your Ruler Attributes are +2% stronger"), и по русскому
		 * переводу их не вытащить.
		 */
		public final String attrDescEn;
		/** true — аттрибута нет вовсе, syphon невозможен (Chameleon). */
		public final boolean noLevels;
		/**
		 * true — шард ловится напрямую (охота, ловушки), а не только фьюзится.
		 *
		 * Признак из fusion-properties.json: у таких пустые input1/input2, по
		 * ним же skyshards рисует бейдж Direct. Сверено с известными случаями,
		 * 8 из 9. Дроп из сундуков данжа сюда НЕ входит: SkyShards его не
		 * описывает, это дописывается руками в гайде.
		 */
		public final boolean direct;
		/**
		 * Скорость прямой добычи, шардов/час (0 — напрямую не фармится, только фьюз).
		 * Для ironman-оптимизатора: стоимость шарда во времени = 1/rate, и путь
		 * выбирается по минимуму суммарного времени фарма, а не по цене базара.
		 */
		public final double rate;
		/**
		 * Сколько ЭТОГО шарда берётся за один фьюз (fuse_amount у SkyShards). Не
		 * всегда 5! Многие (Grove, Aero, King Cobra, Python…) берутся по 2. Вход
		 * рецепта = число_фьюзов × fuseAmount(входа). Раньше жёстко брали 5 — из-за
		 * этого количества раздувались и оптимизатор выбирал не те рецепты.
		 */
		public final int fuseAmount;
		/**
		 * Болванка «How to hunt» из аттрибут-меню (§-текст), как на SkyShards.
		 * Импорт кладёт её ТОЛЬКО новым шардам — у старых пусто, там гайд написан
		 * вручную. Пусто = секцию не показываем.
		 */
		public final String howToHunt;

		/**
		 * attrDescEn без §-кодов — для разбора регулярками (StatFilter, AttributeBuffs).
		 * У новых шардов описание цветное (цвета SkyShards), и §-коды рвали бы поиск
		 * фраз вроде "Ruler Attributes". У старых §-кодов нет — вернётся как есть.
		 */
		public String attrDescEnPlain() {
			return attrDescEn == null ? "" : attrDescEn.replaceAll("§.", "");
		}

		/** Есть ли у шарда аттрибут, который вообще можно качать. */
		public boolean hasAttribute() {
			return !noLevels && attrTitle != null && !attrTitle.isEmpty();
		}

		/**
		 * Описание аттрибута на языке интерфейса (тумблер /sr). attrDesc/attrDescEn
		 * хранятся оба всегда — иначе показ не мог бы переключаться без перезагрузки
		 * ShardDb, а её тумблер языка не перезагружает (только ShardInfo — гайд).
		 */
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

	/** Строка из json с защитой от null — иначе одно поле роняет всю загрузку. */
	private static String str(JsonObject o, String field, String fallback) {
		if (!o.has(field) || o.get(field).isJsonNull()) return fallback;
		return o.get(field).getAsString();
	}

	private static final Map<String, Shard> SHARDS = new LinkedHashMap<>();
	/** id Hypixel (C12) -> ключ шарда. Attribute Menu подписывает шарды именно им. */
	private static final Map<String, String> BY_ID = new LinkedHashMap<>();
	/** Имя шарда (нижним регистром) -> ключ. Для профита: имя из чата → цена базара. */
	private static final Map<String, String> BY_NAME = new LinkedHashMap<>();
	/** Ключ шарда -> все рецепты, которыми его можно получить. */
	private static final Map<String, List<Recipe>> RECIPES = new LinkedHashMap<>();

	private static boolean loaded = false;

	/**
	 * Русские описания аттрибутов из отдельного файла.
	 *
	 * Отдельно от shards.json намеренно: тот перезаписывается импортом из
	 * SkyShards целиком, и перевод бы стирало каждый раз.
	 */
	private static Map<String, String> loadRu() {
		Map<String, String> out = new LinkedHashMap<>();
		try (InputStream in = ShardDb.class.getResourceAsStream("/skyryn/attr-ru.json")) {
			if (in == null) return out;
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (String k : root.keySet()) {
				if (k.startsWith("_")) continue; // _comment — не перевод
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
				// bazaarId может быть null — шарда просто нет на базаре. Цены у него
				// не будет, и рецепты с ним оптимизатор пропустит сам.
				String bz = str(s, "bazaarId", null);
				boolean reptile = s.has("reptile") && !s.get("reptile").isJsonNull()
						&& s.get("reptile").getAsBoolean();
				String rarity = str(s, "rarity", "common");
				String source = str(s, "source", "");
				String family = str(s, "family", "");
				String attrTitle = str(s, "attrTitle", "");
				// Русское описание берём в приоритете. Нет его — покажем оригинал:
				// пустая строка была бы хуже английской.
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
					// Кривая строка не должна ронять мод — просто пропускаем её.
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

	public static Set<String> allShards() { return SHARDS.keySet(); }

	public static Set<String> allCraftable() { return RECIPES.keySet(); }

	/** Ключ шарда по id Hypixel ("C12" -> "chill"). null — такого id у нас нет. */
	public static String byId(String id) {
		return id == null ? null : BY_ID.get(id.toUpperCase());
	}

	/** Ключ шарда по имени из чата (без « Shard»), либо null. */
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

	/** Красивое имя для показа. */
	public static String displayName(String key) {
		Shard s = shard(key);
		if (s != null) return s.name;
		return key == null ? "?" : key;
	}

	/** Сколько этого шарда берётся за фьюз (fuse_amount). По умолчанию 5. */
	public static int fuseAmount(String key) {
		Shard s = shard(key);
		return s == null ? INPUT_PER_FUSION : s.fuseAmount;
	}

	/** Редкость шарда — определяет XP за фьюз. */
	public static String rarity(String key) {
		Shard s = shard(key);
		return s == null ? "common" : s.rarity;
	}

	/** Имя для команды /bz. */
	public static String bazaarName(String key) {
		return displayName(key) + " Shard";
	}
}
