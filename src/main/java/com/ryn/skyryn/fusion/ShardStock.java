package com.ryn.skyryn.fusion;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ryn.skyryn.data.ShardDb;

public final class ShardStock {
	private ShardStock() { }

	private static final Pattern BOUGHT = Pattern.compile(
			"bought\\s+([\\d,]+)x?\\s+(.+?)\\s+for\\s", Pattern.CASE_INSENSITIVE);
	private static final Pattern SENT = Pattern.compile(
			"you sent\\s+([\\d,]+)\\s+(.+?)\\s+to your hunting box", Pattern.CASE_INSENSITIVE);

	private static final Map<Integer, Map<String, Integer>> pages = new HashMap<>();
	private static final Map<String, Integer> pending = new HashMap<>();
	private static final Map<Integer, Long> pageSeen = new HashMap<>();

	private static final long PAGE_LIFE = 30 * 60_000L;

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			String s = message.getString().replaceAll("§.", "");
			Matcher sent = SENT.matcher(s);
			if (sent.find()) {
				String k = ShardDb.keyByName(stripShardWord(sent.group(2)));
				if (k != null && !pending.containsKey(k)) {
					try { pending.merge(k, Integer.parseInt(sent.group(1).replace(",", "")), Integer::sum); }
					catch (NumberFormatException ignored) { }
				}
				return;
			}
			Matcher m = BOUGHT.matcher(s);
			if (!m.find()) return;
			String key = ShardDb.keyByName(stripShardWord(m.group(2)));
			if (key == null) return;
			int n;
			try { n = Integer.parseInt(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return; }
			pending.merge(key, n, Integer::sum);
		});
	}

	private static String stripShardWord(String name) {
		return name.replaceAll("(?i)\\s*shards?$", "").trim();
	}

	public static void putPage(int page, Map<String, Integer> counts) { putPage(page, 0, counts); }

	public static void putPage(int page, int total, Map<String, Integer> counts) {
		pages.put(page, new HashMap<>(counts));
		pageSeen.put(page, System.currentTimeMillis());
		if (total > 0) totalPages = total;
		for (String key : counts.keySet()) pending.remove(key);
	}

	private static int totalPages = 0;
	public static int totalPages() { return totalPages; }

	public static java.util.List<Integer> freshPages() {
		long now = System.currentTimeMillis();
		java.util.List<Integer> out = new java.util.ArrayList<>();
		for (var e : pageSeen.entrySet())
			if (now - e.getValue() <= PAGE_LIFE) out.add(e.getKey());
		java.util.Collections.sort(out);
		return out;
	}

	public static long lastSeenAt() {
		long best = 0;
		for (long v : pageSeen.values()) best = Math.max(best, v);
		return best;
	}

	public static int owned(String shardKey) {
		if (shardKey == null) return 0;
		String key = shardKey.toLowerCase();
		long now = System.currentTimeMillis();
		int total = 0;
		for (Map.Entry<Integer, Map<String, Integer>> p : pages.entrySet()) {
			Long seen = pageSeen.get(p.getKey());
			if (seen == null || now - seen > PAGE_LIFE) continue;
			Integer n = p.getValue().get(key);
			if (n != null) total += n;
		}
		return total + pending.getOrDefault(key, 0);
	}

	public static void clear() {
		pages.clear();
		pageSeen.clear();
		pending.clear();
	}
}
