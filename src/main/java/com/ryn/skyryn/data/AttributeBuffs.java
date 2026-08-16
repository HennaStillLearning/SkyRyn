package com.ryn.skyryn.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ryn.skyryn.config.Lang;

public class AttributeBuffs {
	private static final Pattern BY_TITLE =
			Pattern.compile("Your \"?([A-Za-z]+)\"? Attributes are \\+(\\d+(?:\\.\\d+)?)% stronger");

	private static final Pattern BY_FAMILY =
			Pattern.compile("Buffs all other shards in the ([A-Za-z]+) Family by \\+(\\d+(?:\\.\\d+)?)%");

	private record Buffer(String shard, String group, double perLevel, boolean byFamily) { }

	private static List<Buffer> buffers;
	private static String tiamatKey;

	private static void init() {
		if (buffers != null) return;
		buffers = new ArrayList<>();
		for (String key : ShardDb.allShards()) {
			ShardDb.Shard s = ShardDb.shard(key);
			if (s == null || s.attrDescEn == null || s.attrDescEn.isEmpty()) continue;

			Matcher m = BY_FAMILY.matcher(s.attrDescEnPlain());
			if (m.find()) {
				buffers.add(new Buffer(key, m.group(1), Double.parseDouble(m.group(2)), true));
				continue;
			}
			m = BY_TITLE.matcher(s.attrDescEnPlain());
			if (m.find()) {
				String group = m.group(1);
				double per = Double.parseDouble(m.group(2));
				if (group.equalsIgnoreCase("Echo")) tiamatKey = key;
				else buffers.add(new Buffer(key, group, per, false));
			}
		}
	}

	private static double tiamatFactor() {
		if (tiamatKey == null) return 1.0;
		int lvl = ShardProgress.levelOf(tiamatKey);
		if (lvl <= 0) return 1.0;
		ShardDb.Shard t = ShardDb.shard(tiamatKey);
		Matcher m = BY_TITLE.matcher(t.attrDescEnPlain());
		if (!m.find()) return 1.0;
		return 1.0 + Double.parseDouble(m.group(2)) * lvl / 100.0;
	}

	private static boolean applies(Buffer b, ShardDb.Shard target) {
		if (b.shard.equals(target.key)) return false;
		if (b.byFamily) return b.group.equalsIgnoreCase(target.family);
		if (target.attrTitle == null) return false;
		return Pattern.compile("\\b" + Pattern.quote(b.group)).matcher(target.attrTitle).find();
	}

	private static boolean isEcho(String shardKey) {
		ShardDb.Shard s = ShardDb.shard(shardKey);
		return s != null && s.attrTitle != null && s.attrTitle.startsWith("Echo of");
	}

	public static double bonusFor(String shardKey) {
		init();
		ShardDb.Shard target = ShardDb.shard(shardKey);
		if (target == null || !target.hasAttribute()) return 0;

		double tiamat = tiamatFactor();
		double total = 0;
		if (!shardKey.equals(tiamatKey) && isEcho(shardKey) && tiamat > 1.0) {
			total += tiamat - 1.0;
		}
		for (Buffer b : buffers) {
			if (!applies(b, target)) continue;
			int lvl = ShardProgress.levelOf(b.shard);
			if (lvl <= 0) continue;
			double bonus = b.perLevel * lvl / 100.0;
			if (isEcho(b.shard)) bonus *= tiamat;
			total += bonus;
		}
		return total;
	}

	public static String sourceFor(String shardKey) {
		init();
		ShardDb.Shard target = ShardDb.shard(shardKey);
		if (target == null || !target.hasAttribute()) return "";
		if (!shardKey.equals(tiamatKey) && isEcho(shardKey) && tiamatFactor() > 1.0) {
			return ShardDb.shard(tiamatKey).attrTitle + Lang.tr(" lvl.", " ур.") + ShardProgress.levelOf(tiamatKey);
		}
		for (Buffer b : buffers) {
			if (!applies(b, target)) continue;
			int lvl = ShardProgress.levelOf(b.shard);
			if (lvl <= 0) continue;
			ShardDb.Shard s = ShardDb.shard(b.shard);
			String who = s.attrTitle + Lang.tr(" lvl.", " ур.") + lvl;
			if (isEcho(b.shard) && tiamatFactor() > 1.0) {
				who += " + " + ShardDb.shard(tiamatKey).attrTitle
						+ Lang.tr(" lvl.", " ур.") + ShardProgress.levelOf(tiamatKey);
			}
			return who;
		}
		return "";
	}
}
