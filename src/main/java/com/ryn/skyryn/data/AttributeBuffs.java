package com.ryn.skyryn.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ryn.skyryn.config.Lang;

/**
 * Аттрибуты, которые усиливают другие аттрибуты.
 *
 * Правила не зашиты руками — они написаны в самих описаниях, оттуда и читаем:
 *     "Your Ruler Attributes are +2% stronger"        -> все аттрибуты со словом Ruler
 *     "Your \"Box\" Attributes are +2% stronger"      -> со словом Box
 *     "Buffs all other shards in the Elemental Family by +2%"  -> по СЕМЬЕ, не по названию
 *
 * Формула выведена из замеров в игре, все пять сошлись точь-в-точь:
 *     Undead Ruler 10ур, Tiamat выкл: 30 * (1 + 0.02*10)        = 36%   ✓
 *     Undead Ruler 10ур, Tiamat вкл:  30 * (1 + 0.2*1.5)        = 39%   ✓
 *     Undead Resistance, Tiamat выкл: 40 * 1.2                  = 48    ✓
 *     Undead Resistance, Tiamat вкл:  40 * 1.3                  = 52    ✓
 *     Nature Elemental, Starborn 2ур: 20 * (1 + 0.02*2*1.5)     = 21.2  ✓
 *
 * Отсюда:
 *     бонус = процент_эхо * уровень_эхо * (1 + 0.05 * уровень_Tiamat)
 *     итог  = база * (1 + бонус)
 *
 * Tiamat усиливает только ДРУГИЕ Echo, себя не усиливает: иначе в замере с
 * Undead Ruler вышло бы 40.5%, а игра показала 39%.
 */
public class AttributeBuffs {

	/** "Your Ruler Attributes are +2% stronger" / 'Your "Box" Attributes ...' */
	private static final Pattern BY_TITLE =
			Pattern.compile("Your \"?([A-Za-z]+)\"? Attributes are \\+(\\d+(?:\\.\\d+)?)% stronger");

	/** "Buffs all other shards in the Elemental Family by +2%" */
	private static final Pattern BY_FAMILY =
			Pattern.compile("Buffs all other shards in the ([A-Za-z]+) Family by \\+(\\d+(?:\\.\\d+)?)%");

	/** Кто усиливает: чей это шард, по какому признаку и на сколько за уровень. */
	private record Buffer(String shard, String group, double perLevel, boolean byFamily) { }

	private static List<Buffer> buffers;
	/** Шард Tiamat — он усиливает сами Echo, поэтому считается первым. */
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
				// "Your Echo Attributes are +5% stronger" — это Tiamat, он особый:
				// усиливает не цель напрямую, а сами усилители.
				if (group.equalsIgnoreCase("Echo")) tiamatKey = key;
				else buffers.add(new Buffer(key, group, per, false));
			}
		}
	}

	/** Множитель, который Tiamat даёт всем Echo-аттрибутам: 10 ур -> 1.5. */
	private static double tiamatFactor() {
		if (tiamatKey == null) return 1.0;
		int lvl = ShardProgress.levelOf(tiamatKey);
		if (lvl <= 0) return 1.0;
		ShardDb.Shard t = ShardDb.shard(tiamatKey);
		Matcher m = BY_TITLE.matcher(t.attrDescEnPlain());
		if (!m.find()) return 1.0;
		return 1.0 + Double.parseDouble(m.group(2)) * lvl / 100.0;
	}

	/** Подходит ли усилитель этому шарду. */
	private static boolean applies(Buffer b, ShardDb.Shard target) {
		if (b.shard.equals(target.key)) return false; // "all other" — себя не усиливает
		if (b.byFamily) return b.group.equalsIgnoreCase(target.family);
		if (target.attrTitle == null) return false;
		// Слово целиком: "Ruler" не должен цеплять "Rulership".
		return Pattern.compile("\\b" + Pattern.quote(b.group)).matcher(target.attrTitle).find();
	}

	/** Является ли аттрибут "Echo" — такие усиливает Tiamat. */
	private static boolean isEcho(String shardKey) {
		ShardDb.Shard s = ShardDb.shard(shardKey);
		return s != null && s.attrTitle != null && s.attrTitle.startsWith("Echo of");
	}

	/**
	 * Насколько усилен аттрибут этого шарда. 0 — не усилен.
	 *
	 * Считаем по ТВОИМ уровням из Attribute Menu: не знаем уровень усилителя —
	 * не считаем его вовсе, вместо того чтобы предполагать десятку.
	 */
	public static double bonusFor(String shardKey) {
		init();
		ShardDb.Shard target = ShardDb.shard(shardKey);
		if (target == null || !target.hasAttribute()) return 0;

		double tiamat = tiamatFactor();
		double total = 0;
		// Tiamat усиливает и сам Echo-шард напрямую (его собственное значение
		// растёт), не только то, что этот Echo даёт другим — иначе страница
		// самого Echo-шарда молчала о буфере, который в игре точно есть.
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

	/** Кто именно усиливает — строка для интерфейса. Пусто, если никто. */
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
