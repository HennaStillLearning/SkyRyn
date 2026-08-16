package com.ryn.skyryn.data;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShardAttribute {
	public static final int MAX_LEVEL = 10;

	private static final Pattern PLUS = Pattern.compile("\\+(\\d+(?:\\.\\d+)?)([%s]?)");

	public static String range(String desc) {
		return range(desc, 0);
	}

	public static String range(String desc, double buff) {
		if (desc == null || desc.isEmpty()) return "";
		BigDecimal k = BigDecimal.valueOf(1 + buff);
		Matcher m = PLUS.matcher(desc);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			BigDecimal base = new BigDecimal(m.group(1));
			String suffix = m.group(2);
			String lo = num(base.multiply(k));
			String hi = num(base.multiply(BigDecimal.valueOf(MAX_LEVEL)).multiply(k));
			m.appendReplacement(out, Matcher.quoteReplacement(
					"+" + lo + suffix + ".." + hi + suffix));
		}
		m.appendTail(out);
		return out.toString();
	}

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

	private static String num(BigDecimal v) {
		BigDecimal s = v.stripTrailingZeros();
		return s.scale() <= 0 ? s.toBigInteger().toString() : s.toPlainString();
	}

	public static String titleWithLevels(String title) {
		if (title == null || title.isEmpty()) return "";
		return title + " I→X";
	}
}
