package com.ryn.skyryn.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import com.ryn.skyryn.config.RynConfig;

/**
 * Локации игры: как называется, каким цветом, куда варпает.
 *
 * Отдельным справочником, а не строками в гайде, по трём причинам: локация
 * упоминается у десятков шардов и красить её руками каждый раз — работа на
 * ровном месте; варп меняется в одном месте; и это же понадобится для
 * "Отслеживать", где надо будет сказать, куда телепортироваться.
 *
 * Два слоя, как у гайда: jar — основа, config/skyryn-locations.json — правки
 * игрока поверх. После патча Hypixel новую локацию можно добавить самому.
 */
public class LocationDb {

	public record Loc(String key, String name, String warp, String color,
					  String coords, String note, String warpMvp, String scroll,
					  boolean warpMvpHere) {
		public boolean hasWarp() { return warp != null && !warp.isBlank(); }
		public boolean hasCoords() { return coords != null && !coords.isBlank(); }
		public boolean hasMvpWarp() { return warpMvp != null && !warpMvp.isBlank(); }

		/** Ближний варп приземляет прямо в эту точку — метку/путь ставить не надо. */
		public boolean landsHere() {
			return warpMvpHere && hasMvpWarp() && warpMvp.equals(effectiveWarp());
		}

		/**
		 * Какой варп использовать сейчас.
		 *
		 * Ближний (warpMvp) — только если игрок отметил MVP+ и включил нужный
		 * свиток: без свитка команда просто не сработает, и отправлять к ней —
		 * значит послать жать кнопку впустую. Иначе обычный варп, он у всех есть.
		 */
		public String effectiveWarp() {
			if (hasMvpWarp() && RynConfig.mvpPlus && RynConfig.hasScroll(scroll)) return warpMvp;
			return warp;
		}

		/** "x y z" в числа. null — не заданы или заданы криво. */
		public double[] xyz() {
			if (!hasCoords()) return null;
			String[] p = coords.trim().split("[ ,]+");
			if (p.length != 3) return null;
			try {
				return new double[] {Double.parseDouble(p[0]),
						Double.parseDouble(p[1]), Double.parseDouble(p[2])};
			} catch (NumberFormatException e) {
				return null;
			}
		}
	}

	private static final Map<String, Loc> LOCS = new LinkedHashMap<>();

	private static Path path() {
		return net.fabricmc.loader.api.FabricLoader.getInstance()
				.getConfigDir().resolve("skyryn-locations.json");
	}

	public static void load() {
		LOCS.clear();
		try (InputStream in = LocationDb.class.getResourceAsStream("/skyryn/locations.json")) {
			if (in != null) {
				parse(JsonParser.parseReader(
						new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
			}
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("Ошибка чтения locations.json: " + e);
		}
		try {
			Path p = path();
			if (Files.exists(p)) {
				try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
					parse(JsonParser.parseReader(r).getAsJsonObject());
				}
			}
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("Ошибка чтения skyryn-locations.json: " + e);
		}
		com.ryn.skyryn.config.SkyLog.d("Локаций: " + LOCS.size());
	}

	private static void parse(JsonObject root) {
		for (String k : root.keySet()) {
			if (k.startsWith("_") || !root.get(k).isJsonObject()) continue;
			JsonObject o = root.getAsJsonObject(k);
			LOCS.put(k.toLowerCase(), new Loc(k.toLowerCase(),
					str(o, "name", k), str(o, "warp", ""), str(o, "color", "§f"),
					str(o, "coords", ""), str(o, "note", ""),
					str(o, "warpMvp", ""), str(o, "scroll", ""),
					o.has("warpMvpHere") && o.get("warpMvpHere").getAsBoolean()));
		}
	}

	private static String str(JsonObject o, String f, String fallback) {
		return o.has(f) && !o.get(f).isJsonNull() ? o.get(f).getAsString() : fallback;
	}

	/**
	 * Свитки, встречающиеся в справочнике: свиток -> пример команды варпа.
	 * По этому списку строится группа тумблеров в настройках — добавил игрок
	 * локацию с новым свитком, тумблер появился сам.
	 */
	public static Map<String, String> scrolls() {
		Map<String, String> out = new LinkedHashMap<>();
		for (Loc l : LOCS.values()) {
			if (l.scroll() != null && !l.scroll().isBlank()) {
				out.putIfAbsent(l.scroll(), l.warpMvp());
			}
		}
		return out;
	}

	/** null — такой локации в справочнике нет. */
	public static Loc get(String key) {
		return key == null ? null : LOCS.get(key.toLowerCase());
	}

	/**
	 * Подменяет базовый варп на ближний, если у игрока есть MVP+ и нужный свиток.
	 *
	 * Нужно там, где варп задан строкой в самом методе, а не loc-ссылкой: у loc
	 * подмену делает {@link Loc#effectiveWarp()}, а голая строка шла на сервер как
	 * есть, и тумблер MVP+ на такие методы не действовал. Ищем локацию с таким же
	 * базовым варпом и спрашиваем у неё, куда лететь сейчас.
	 */
	public static String upgradeWarp(String warp) {
		if (warp == null || warp.isBlank()) return warp;
		String w = warp.trim();
		for (Loc l : LOCS.values()) {
			if (!l.hasMvpWarp() || !w.equalsIgnoreCase(l.warp())) continue;
			String eff = l.effectiveWarp();
			if (eff != null && !eff.isBlank()) return eff;
		}
		return warp;
	}
}
