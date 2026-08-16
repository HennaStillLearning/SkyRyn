package com.ryn.skyryn.fusion;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.waypoint.SkyBlockCheck;

public class FusionTracker {
	public static long totalFusions = 0;
	public static long totalShardsObtained = 0;
	public static double totalFusionXp = 0;
	public static double totalSpent = 0;
	public static double totalEarned = 0;

	public static double totalProfit() {
		return totalEarned - totalSpent;
	}

	public static long sessionFusions = 0;
	public static long sessionShardsObtained = 0;
	public static double sessionFusionXp = 0;
	public static long sessionTargetCrafted = 0;
	public static double sessionSpent = 0;
	public static double sessionRevenue = 0;
	public static long sessionStart = System.currentTimeMillis();

	public static double sessionProfit() {
		return sessionRevenue - sessionSpent;
	}

	private static boolean awaitingFusionXp = false;

	public static boolean isAwaitingFusionXp() { return awaitingFusionXp; }
	private static String lastXpText = "";

	private static final Pattern FUSION_MSG =
			Pattern.compile("FUSION! You obtained (?:an?\\s+)?(.+?) Shard(?:\\s*x(\\d+))?!");
	private static final Pattern XP_MSG =
			Pattern.compile("\\+([\\d,.]+)\\s+Hunting");

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!SkyBlockCheck.onSkyBlock()) return;
			String raw = message.getString();
			if (raw == null) return;
			String text = stripColors(raw);

			if (overlay) {
				detectXp(text);
			} else {
				detectFusion(text);
			}
		});
	}

	private static String stripColors(String s) {
		return s.replaceAll("\u00a7.", "");
	}

	private static void detectFusion(String text) {
		Matcher m = FUSION_MSG.matcher(text);
		if (!m.find()) return;

		String shardName = m.group(1).trim();
		String countGroup = m.group(2);
		int obtained = 1;
		if (countGroup != null) {
			try { obtained = Integer.parseInt(countGroup); } catch (NumberFormatException ignored) { }
		}

		totalFusions++;
		sessionFusions++;
		sessionShardsObtained += obtained;
		totalShardsObtained += obtained;

		awaitingFusionXp = true;
		lastXpText = "";

		String key = shardName.toLowerCase();
		String target = FusionState.currentShard;
		if (target != null && key.equals(target.toLowerCase())) {
			double[] econ = FusionCalculator.unitEconomics(target, FusionState.currentAmount);
			if (econ != null) {
				sessionSpent += econ[0] * obtained;
				sessionRevenue += econ[1] * obtained;
				sessionTargetCrafted += obtained;
				totalSpent += econ[0] * obtained;
				totalEarned += econ[1] * obtained;
			}
		}
	}

	private static void detectXp(String text) {
		Matcher m = XP_MSG.matcher(text);
		if (!m.find()) return;

		if (text.equals(lastXpText)) return;
		lastXpText = text;

		if (!awaitingFusionXp) return;
		awaitingFusionXp = false;

		double xp;
		try {
			xp = Double.parseDouble(m.group(1).replace(",", ""));
		} catch (Exception e) {
			return;
		}
		sessionFusionXp += xp;
		totalFusionXp += xp;
	}

	private static double perHour(double value) {
		long elapsed = System.currentTimeMillis() - sessionStart;
		if (elapsed < 1000) return 0;
		return value / (elapsed / 3_600_000.0);
	}

	public static double fusionsPerHour()  { return perHour(sessionFusions); }
	public static double shardsPerHour()   { return perHour(sessionShardsObtained); }
	public static double fusionXpPerHour() { return perHour(sessionFusionXp); }
	public static double profitPerHour()   { return perHour(sessionProfit()); }

	public static void resetSession() {
		sessionFusions = 0;
		sessionShardsObtained = 0;
		sessionFusionXp = 0;
		sessionTargetCrafted = 0;
		sessionSpent = 0;
		sessionRevenue = 0;
		sessionStart = System.currentTimeMillis();
		awaitingFusionXp = false;
	}

	public static void resetAll() {
		totalFusions = 0;
		totalShardsObtained = 0;
		totalFusionXp = 0;
		totalSpent = 0;
		totalEarned = 0;
		resetSession();
		ConfigManager.save();
	}
}
