package com.ryn.skyryn.data;

import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RichText {
	private static final Pattern TAG =
			Pattern.compile("\\[([^\\]]+)\\]\\((\\w+):((?:[^()]|\\([^()]*\\))*)\\)");

	public record Part(String text, String role, String arg) {
		public boolean isTag() { return role != null; }
	}

	public static List<Part> parse(String s) {
		List<Part> out = new ArrayList<>();
		if (s == null || s.isEmpty()) return out;
		Matcher m = TAG.matcher(s);
		int last = 0;
		while (m.find()) {
			if (m.start() > last) out.add(new Part(s.substring(last, m.start()), null, null));
			out.add(new Part(m.group(1), m.group(2), m.group(3)));
			last = m.end();
		}
		if (last < s.length()) out.add(new Part(s.substring(last), null, null));
		return out;
	}

	public static String strip(String s) {
		if (s == null) return "";
		return TAG.matcher(s).replaceAll("$1").replaceAll("§.", "");
	}

	public static String lastCode(String s) {
		String code = "";
		for (int i = 0; i + 1 < s.length(); i++) {
			if (s.charAt(i) == '§') {
				char c = s.charAt(i + 1);
				code = (c == 'r') ? "" : "§" + c;
			}
		}
		return code;
	}

	public static int width(Font f, String s) {
		return f.width(s);
	}
}
