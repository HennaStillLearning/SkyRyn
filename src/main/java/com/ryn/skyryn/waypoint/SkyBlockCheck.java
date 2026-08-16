package com.ryn.skyryn.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import java.util.Map;

public class SkyBlockCheck {
	private static boolean onSkyBlock = false;
	private static long lastCheck = 0;

	public static boolean onSkyBlock() {
		long now = System.currentTimeMillis();
		if (now - lastCheck >= 1000) {
			lastCheck = now;
			onSkyBlock = compute();
		}
		return onSkyBlock;
	}

	private static boolean compute() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return false;
		if (!onHypixel(mc)) return false;

		Objective obj = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return false;
		String title = obj.getDisplayName().getString().replaceAll("§.", "");
		return title.toUpperCase().contains("SKYBLOCK");
	}

	private static java.util.List<String> sidebarLines() {
		java.util.List<String> out = new java.util.ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return out;
		Scoreboard sb = mc.level.getScoreboard();
		Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return out;
		for (ScoreHolder h : sb.getTrackedPlayers()) {
			if (!sb.listPlayerScores(h).containsKey(obj)) continue;
			PlayerTeam team = sb.getPlayersTeam(h.getScoreboardName());
			if (team == null) continue;
			out.add(team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString());
		}
		return out;
	}

	private static String cleanArea(String raw) {
		String s = raw.replaceAll("§.", "").replace('’', '\'').trim();
		return s.replaceFirst("^[^\\p{L}0-9]+", "").trim();
	}

	public static String currentArea() {
		for (String raw : sidebarLines()) {
			String name = cleanArea(raw);
			if (!name.isEmpty() && AREA_ISLAND.containsKey(name.toLowerCase())) return name;
		}
		return "";
	}

	private static final Map<String, String> AREA_ISLAND = new java.util.HashMap<>();

	public static void loadAreas() {
		AREA_ISLAND.clear();
		try (java.io.InputStream in = SkyBlockCheck.class.getResourceAsStream("/skyryn/areas.json")) {
			if (in == null) return;
			com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(
					new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			for (String k : root.keySet()) {
				if (k.startsWith("_")) continue;
				AREA_ISLAND.put(k.toLowerCase(), root.get(k).getAsString());
			}
			com.ryn.skyryn.config.SkyLog.d("Карта зон: " + AREA_ISLAND.size());
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("areas.json не прочитан: " + e);
		}
	}

	private static final Map<String, String> WARP_ISLAND = Map.ofEntries(
			Map.entry("galatea", "galatea"), Map.entry("bayou", "bayou"),
			Map.entry("spider", "spider"), Map.entry("arachne", "spider"),
			Map.entry("crimson", "crimson"), Map.entry("skull", "crimson"),
			Map.entry("smold", "crimson"), Map.entry("end", "end"),
			Map.entry("drag", "end"), Map.entry("crystals", "crystal"),
			Map.entry("nucleus", "crystal"), Map.entry("dwarves", "dwarven"));

	private static final java.util.Set<String> KNOWN_WARPS = java.util.Set.of(
			"galatea", "bayou", "spider", "arachne", "crimson", "skull", "smold",
			"end", "drag", "crystals", "nucleus", "dwarves", "hub", "deep", "park",
			"gold", "barn", "lotus", "dungeon_hub");

	public static boolean isWarp(String token) {
		if (token == null) return false;
		return KNOWN_WARPS.contains(token.toLowerCase().replaceFirst("^/warp\\s+", "").replaceFirst("^/", "").trim());
	}

	public static String islandOfWarp(String warp) {
		if (warp == null || warp.isBlank()) return "";
		String t = warp.toLowerCase().replaceFirst("^/warp\\s+", "").trim();
		return WARP_ISLAND.getOrDefault(t, t);
	}

	public static String sidebarDump() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return "(нет level)";
		Scoreboard sb = mc.level.getScoreboard();
		Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return "(нет sidebar objective)";
		StringBuilder out = new StringBuilder("заголовок='"
				+ obj.getDisplayName().getString().replaceAll("§.", "") + "'");
		int n = 0;
		for (ScoreHolder h : sb.getTrackedPlayers()) {
			if (!sb.listPlayerScores(h).containsKey(obj)) continue;
			PlayerTeam team = sb.getPlayersTeam(h.getScoreboardName());
			String line = team == null ? "" :
					team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();
			out.append(" | [").append(line.replaceAll("§.", "")).append("]");
			n++;
		}
		out.append("  (строк ").append(n).append(")");
		return out.toString();
	}

	public static long safariEssence() {
		for (String raw : sidebarLines()) {
			String s = raw.replaceAll("§.", "");
			if (!s.toLowerCase().contains("essence")) continue;
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("([\\d,]+)").matcher(s);
			if (m.find()) { try { return Long.parseLong(m.group(1).replace(",", "")); } catch (Exception ignored) { } }
		}
		return -1;
	}

	private static int sbLevel = -1;
	private static long sbLevelAt = 0;

	public static int skyblockLevel() {
		long now = System.currentTimeMillis();
		if (now - sbLevelAt < 1000) return sbLevel;
		sbLevelAt = now;
		sbLevel = readSkyblockLevel();
		return sbLevel;
	}

	private static int readSkyblockLevel() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null || !onSkyBlock()) return -1;

		var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		if (info != null && info.getTabListDisplayName() != null) {
			int lvl = levelIn(info.getTabListDisplayName().getString());
			if (lvl > 0) return lvl;
		}
		if (mc.level != null) {
			var team = mc.level.getScoreboard().getPlayersTeam(mc.player.getScoreboardName());
			if (team != null) {
				int lvl = levelIn(team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString());
				if (lvl > 0) return lvl;
			}
		}
		return levelIn(mc.player.getDisplayName().getString());
	}

	private static int levelIn(String raw) {
		if (raw == null) return -1;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[(\\d{1,4})]")
				.matcher(raw.replaceAll("§.", ""));
		if (!m.find()) return -1;
		try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return -1; }
	}

	public static String currentIsland() {
		String area = currentArea().toLowerCase();
		return area.isBlank() ? "" : AREA_ISLAND.getOrDefault(area, "");
	}

	public static boolean onIslandOf(String warp) {
		String cur = currentIsland();
		return !cur.isBlank() && cur.equals(islandOfWarp(warp));
	}

	private static boolean onHypixel(Minecraft mc) {
		if (mc.isLocalServer()) return false;
		ServerData s = mc.getCurrentServer();
		if (s == null || s.ip == null) return false;
		String ip = s.ip.toLowerCase().trim();
		int port = ip.indexOf(':');
		if (port > 0) ip = ip.substring(0, port);
		return ip.equals("hypixel.net") || ip.endsWith(".hypixel.net");
	}
}
