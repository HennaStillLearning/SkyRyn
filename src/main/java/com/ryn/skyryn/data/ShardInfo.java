package com.ryn.skyryn.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Гайд по шардам: что даёт шард, как работает, где добыть.
 *
 * Читается в два слоя:
 *   1. jar — гайд, который едет вместе с модом. Обновляется с каждой версией.
 *   2. config/skyryn-shards.json — правки игрока. Перекрывают то, что в jar,
 *      но только там, где игрок реально что-то написал.
 *
 * Почему именно так. Если гайд держать ТОЛЬКО в конфиге, он не доедет до тех,
 * кто ставит мод: файл создаётся пустым. Если ТОЛЬКО в jar — игрок не сможет
 * ничего дописать под себя. А если копировать jar в конфиг один раз, как было
 * сначала, то у всех, кто уже играл, файл уже есть, и новые описания к ним не
 * придут никогда — самая противная из трёх, потому что молча.
 *
 * Держим отдельно от ShardDb: shards.json генерится импортом из SkyShards и
 * перезаписывается после каждого патча.
 */
public class ShardInfo {

	/**
	 * Способ добычи. Задаётся автором, а НЕ выводится из данных.
	 *
	 * Пробовали выводить: в fusion-properties есть признак "нет входов =
	 * ловится напрямую". На известных случаях он сходился, а на Alligator
	 * соврал — тот ловится рыбалкой, хотя признак говорит "только фьюз".
	 * Значит признак не про добычу, и гадать по нему больше не будем.
	 *
	 * type: hunting | fusing | trap | chest | purchase | tuning. У fusing текста нет — мод сам
	 * показывает кнопку в калькулятор.
	 */
	public static class Method {
		public final String type;
		public final String text;
		public final String warp;
		public final String coords;
		public final List<String> images;
		public final String video;
		/** true — луч ведёт по спотам в заданном порядке, а не по ближайшему. */
		public final boolean ordered;
		/**
		 * true — метод берётся Pocket Black Hole на 10% HP, шард также падает с
		 * Charm/Naga Shard/Salts. Общая фраза одна на ~60 методов — незачем
		 * копировать её текст в каждый, рисуем один раз в коде (см.
		 * ShardPageScreen.methodBody).
		 */
		public final boolean blackHole;
		/**
		 * Оружие, которым ТОЛЬКО и можно опускать HP моба (напр. "Axes" —
		 * Stridersurfer). Пусто — опускается чем угодно. Подставляется в фразу
		 * blackHole (см. ShardPageScreen.blackHoleText), чтобы уточнение жило в
		 * данных метода, а не копипастой в тексте.
		 */
		public final String weapon;

		Method(String type, String text, String warp, String coords,
			   List<String> images, String video, boolean ordered, boolean blackHole, String weapon) {
			this.type = type;
			this.text = text;
			this.warp = warp;
			this.coords = coords;
			this.images = images;
			this.video = video;
			this.ordered = ordered;
			this.blackHole = blackHole;
			this.weapon = weapon;
		}

		/**
		 * Синтетический фьюз-метод: мод добавляет его в /sr shards для ironman у
		 * фьюзабельных шардов, где автор не расписал фьюз руками (кроме Chameleon/
		 * Cocoaleech). Для ironman базара нет, но собрать шард фьюзом — реальный путь.
		 */
		public static Method fusing() {
			return new Method("fusing", "", "", "", null, null, false, false, "");
		}

		/** Заголовок метода. Незнакомый тип показываем как есть, а не молчим. */
		public String title() {
			return switch (type) {
				case "hunting" -> com.ryn.skyryn.config.Lang.tr("Hunting", "Охота");
				case "fusing" -> com.ryn.skyryn.config.Lang.tr("Fusion", "Фьюз");
				case "trap" -> com.ryn.skyryn.config.Lang.tr("Huntraps", "Huntraps");
				case "chest" -> com.ryn.skyryn.config.Lang.tr("Chest reward", "Награда из сундука");
				case "slayer" -> com.ryn.skyryn.config.Lang.tr("Slayer reward", "Награда Slayer");
				case "foraging" -> com.ryn.skyryn.config.Lang.tr("Foraging", "Собирательство");
				case "purchase" -> com.ryn.skyryn.config.Lang.tr("Purchase", "Покупка");
				case "crafting" -> com.ryn.skyryn.config.Lang.tr("Crafting", "Крафт");
				case "tuning" -> com.ryn.skyryn.config.Lang.tr("Tuning", "Тюнинг");
				default -> type;
			};
		}
	}

	public static class Info {
		/** "Подробнее": что шард даёт и как работает. */
		public final String details;
		/** Способы добычи в том порядке, в котором их задал автор. */
		public final List<Method> methods;

		Info(String details, List<Method> methods) {
			this.details = details;
			this.methods = methods;
		}

		public boolean hasDetails() { return notBlank(details); }
	}

	private static final Map<String, Info> INFO = new HashMap<>();
	private static final Info EMPTY = new Info("", List.of());

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}

	/**
	 * Файлы по языку. База — англ.; русский лежит рядом с суффиксом -ru.
	 * jar: /skyryn/shard-info.json | shard-info-ru.json
	 * config: skyryn-shards.json | skyryn-shards-ru.json
	 */
	private static String jarResource() {
		return com.ryn.skyryn.config.RynConfig.isRu()
				? "/skyryn/shard-info-ru.json" : "/skyryn/shard-info.json";
	}

	/** config/skyryn-shards[-ru].json — рядом с настройками, а не внутри мода. */
	private static Path path() {
		String name = com.ryn.skyryn.config.RynConfig.isRu()
				? "skyryn-shards-ru.json" : "skyryn-shards.json";
		return net.fabricmc.loader.api.FabricLoader.getInstance()
				.getConfigDir().resolve(name);
	}

	public static void load() {
		INFO.clear();
		int fromJar = 0;
		try (InputStream in = ShardInfo.class.getResourceAsStream(jarResource())) {
			if (in != null) {
				parse(JsonParser.parseReader(
						new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
				fromJar = INFO.size();
			}
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("Ошибка чтения гайда из jar: " + e);
		}

		int overridden = 0;
		try {
			Path p = path();
			if (Files.exists(p)) {
				try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
					overridden = parse(JsonParser.parseReader(r).getAsJsonObject());
				}
			}
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("Ошибка чтения skyryn-shards.json: " + e);
		}

		com.ryn.skyryn.config.SkyLog.d("Гайд: " + fromJar + " из мода"
				+ (overridden > 0 ? ", " + overridden + " своих правок" : ""));
	}

	/** @return сколько записей реально что-то принесли (пустые поля не в счёт). */
	private static int parse(JsonObject root) {
		int touched = 0;
		for (String key : root.keySet()) {
			if (key.startsWith("_")) continue; // _comment
			if (!root.get(key).isJsonObject()) continue;
			JsonObject o = root.getAsJsonObject(key);
			String k = key.toLowerCase();
			Info old = INFO.getOrDefault(k, EMPTY);

			// Пустое поле НЕ затирает то, что уже есть: болванка конфига полна
			// пустых строк, и иначе она бы стёрла весь гайд из мода.
			String details = pick(str(o, "details"), old.details);

			List<Method> methods = old.methods;
			if (o.has("methods") && o.get("methods").isJsonArray()) {
				List<Method> list = new ArrayList<>();
				for (var el : o.getAsJsonArray("methods")) {
					if (!el.isJsonObject()) continue;
					JsonObject m = el.getAsJsonObject();
					String type = str(m, "type");
					if (type.isBlank()) continue;
					list.add(new Method(type, str(m, "text"), str(m, "warp"),
							str(m, "coords"), strList(m, "images"), str(m, "video"),
							m.has("ordered") && m.get("ordered").getAsBoolean(),
							m.has("blackHole") && m.get("blackHole").getAsBoolean(),
							str(m, "weapon")));
				}
				if (!list.isEmpty()) methods = list;
			}

			Info info = new Info(details, methods);
			if (notBlank(details) || !methods.isEmpty()) touched++;
			INFO.put(k, info);
		}
		return touched;
	}

	private static String pick(String fresh, String fallback) {
		return notBlank(fresh) ? fresh : fallback;
	}

	private static List<String> strList(JsonObject o, String field) {
		List<String> out = new ArrayList<>();
		if (o.has(field) && o.get(field).isJsonArray()) {
			JsonArray arr = o.getAsJsonArray(field);
			for (int i = 0; i < arr.size(); i++) {
				if (arr.get(i).isJsonPrimitive()) out.add(arr.get(i).getAsString());
			}
		}
		return out;
	}

	private static String str(JsonObject o, String field) {
		if (!o.has(field) || o.get(field).isJsonNull()) return "";
		return o.get(field).getAsString();
	}

	/** Описание шарда. Никогда не null — просто пустое, если не написано. */
	public static Info get(String shard) {
		if (shard == null) return EMPTY;
		return INFO.getOrDefault(shard.toLowerCase(), EMPTY);
	}

	/** Сколько шардов уже описано — для прогресса в интерфейсе. */
	public static int described() { return INFO.size(); }

	/** Покупается ли шард у NPC — в гайде есть метод типа "purchase" (Kirara/Agatha и пр.). */
	public static boolean hasPurchase(String shard) {
		for (Method m : get(shard).methods) if ("purchase".equals(m.type)) return true;
		return false;
	}

	/** Добывается только в ивент мэра Diana — гайд упоминает Diana/Mythological. */
	public static boolean isDianaOnly(String shard) {
		Info info = get(shard);
		if (mentionsDiana(info.details)) return true;
		for (Method m : info.methods) if (mentionsDiana(m.text)) return true;
		return false;
	}

	private static boolean mentionsDiana(String s) {
		if (s == null || s.isBlank()) return false;
		String l = s.toLowerCase();
		return l.contains("diana") || l.contains("mytholog") || l.contains("диана") || l.contains("мифолог");
	}
}
