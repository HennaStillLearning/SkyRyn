package com.ryn.skyryn.data;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Аттрибут шарда: что он даёт от 1 до 10 уровня.
 *
 * В данных лежит эффект ОДНОГО уровня ("Даёт +2 ❤ Health"), а игроку нужен
 * весь диапазон ("Даёт +2..+20 ❤ Health"). Правило простое и одно на всех:
 * каждое "+N" в строке превращается в "+N..+N*10", суффикс % или s остаётся
 * на месте. Уровней всегда 10 — отсюда и множитель.
 *
 * Правило не выдумано: это ровно то, что делает skyshards.com. Их регулярка
 *     /\+(\d+(?:\.\d+)?)([%s]?)/g  ->  `+${n}${suf} to +${n*10}${suf}`
 * Числа держим один в один — сверено на всех 189. Разделитель у нас "..", а не
 * "to": описания русские, и английский предлог в них смотрелся чужеродно.
 */
public class ShardAttribute {

	/** Уровней у аттрибута — от I до X. */
	public static final int MAX_LEVEL = 10;

	private static final Pattern PLUS = Pattern.compile("\\+(\\d+(?:\\.\\d+)?)([%s]?)");

	/** "Даёт +2 ❤ Health" -> "Даёт +2..+20 ❤ Health". */
	public static String range(String desc) {
		return range(desc, 0);
	}

	/**
	 * То же, но с учётом усиления от Echo-аттрибутов.
	 *
	 * @param buff 0.3 -> оба конца диапазона умножаются на 1.3, как в игре:
	 *             там при усилении показывают зачёркнутую базу и итог рядом.
	 */
	public static String range(String desc, double buff) {
		if (desc == null || desc.isEmpty()) return "";
		BigDecimal k = BigDecimal.valueOf(1 + buff);
		Matcher m = PLUS.matcher(desc);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			BigDecimal base = new BigDecimal(m.group(1));
			String suffix = m.group(2); // "%", "s" или пусто
			String lo = num(base.multiply(k));
			String hi = num(base.multiply(BigDecimal.valueOf(MAX_LEVEL)).multiply(k));
			m.appendReplacement(out, Matcher.quoteReplacement(
					"+" + lo + suffix + ".." + hi + suffix));
		}
		m.appendTail(out);
		return out.toString();
	}

	/**
	 * Значение на КОНКРЕТНОМ уровне: "Даёт +2 ❤ Health", уровень 3 -> "+6".
	 *
	 * Каждое "+N" умножается на уровень и на усиление. Нужно, чтобы игрок видел,
	 * что даёт шард сейчас, а не только полный диапазон 1-10.
	 */
	public static String atLevel(String desc, int level, double buff) {
		if (desc == null || desc.isEmpty() || level <= 0) return "";
		BigDecimal k = BigDecimal.valueOf(level).multiply(BigDecimal.valueOf(1 + buff));
		Matcher m = PLUS.matcher(desc);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String v = num(new BigDecimal(m.group(1)).multiply(k));
			m.appendReplacement(out, Matcher.quoteReplacement("+" + v + m.group(2)));
		}
		m.appendTail(out);
		return out.toString();
	}

	/** Печатает число без хвостовых нулей: 20.0 -> "20", 0.20 -> "0.2". */
	private static String num(BigDecimal v) {
		BigDecimal s = v.stripTrailingZeros();
		return s.scale() <= 0 ? s.toBigInteger().toString() : s.toPlainString();
	}

	/** "Nature Elemental I→X" — заголовок аттрибута с диапазоном уровней. */
	public static String titleWithLevels(String title) {
		if (title == null || title.isEmpty()) return "";
		return title + " I→X";
	}
}
