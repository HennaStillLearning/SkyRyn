package com.ryn.skyryn.data;

import java.util.HashMap;
import java.util.Map;

public class ShardProgress {
	private static final Map<String, Integer> LEVEL = new HashMap<>();

	public static boolean known() {
		return !LEVEL.isEmpty();
	}

	public static int levelOf(String shard) {
		if (shard == null) return -1;
		Integer v = LEVEL.get(shard.toLowerCase());
		return v == null ? -1 : v;
	}

	public static int displayLevel(String shard) {
		int raw = levelOf(shard);
		if (raw >= 0) return raw;
		if (!known()) return -1;
		ShardDb.Shard s = ShardDb.shard(shard);
		return s != null && s.hasAttribute() ? 0 : -1;
	}

	public static boolean setLevel(String shard, int level) {
		if (shard == null || level < 0) return false;
		Integer old = LEVEL.put(shard.toLowerCase(), Math.min(level, ShardAttribute.MAX_LEVEL));
		return old == null || old != level;
	}

	public static Map<String, Integer> allLevels() { return LEVEL; }

	public static void clear() {
		LEVEL.clear();
	}
}
