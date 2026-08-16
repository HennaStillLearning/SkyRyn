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

public class LocationDb {
	public record Loc(String key, String name, String warp, String color,
					  String coords, String note, String warpMvp, String scroll,
					  boolean warpMvpHere) {
		public boolean hasWarp() { return warp != null && !warp.isBlank(); }
		public boolean hasCoords() { return coords != null && !coords.isBlank(); }
		public boolean hasMvpWarp() { return warpMvp != null && !warpMvp.isBlank(); }

		public boolean landsHere() {
			return warpMvpHere && hasMvpWarp() && warpMvp.equals(effectiveWarp());
		}

		public String effectiveWarp() {
			if (hasMvpWarp() && RynConfig.mvpPlus && RynConfig.hasScroll(scroll)) return warpMvp;
			return warp;
		}

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

	public static Map<String, String> scrolls() {
		Map<String, String> out = new LinkedHashMap<>();
		for (Loc l : LOCS.values()) {
			if (l.scroll() != null && !l.scroll().isBlank()) {
				out.putIfAbsent(l.scroll(), l.warpMvp());
			}
		}
		return out;
	}

	public static Loc get(String key) {
		return key == null ? null : LOCS.get(key.toLowerCase());
	}

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
