package com.ryn.skyryn.data;

import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разметка для текста гайда: цвета и кликабельные пометки.
 *
 * Цвет — обычными §-кодами, как в самой игре: "§2dwarven mines§r". Ничего
 * своего для цветов не выдумываем: игрок и так их знает, а Minecraft умеет
 * рисовать их прямо из строки.
 *
 * Пометки — "[текст](роль:аргумент)":
 *     [Wooden Bait](bz:Wooden Bait)   клик открывает базар
 *     [Rosemary](tip:Galatea, -703 162)  подсказка при наведении
 *     [Naga](shard:naga)              клик открывает страницу шарда
 *
 * Почему не Component с готовым стилем: гайд пишется руками в json, и
 * заставлять автора городить там дерево компонентов — верный способ,
 * чтобы гайд никто не написал.
 */
public class RichText {

	/**
	 * "[label](role:arg)" — роль без пробелов, аргумент до закрывающей скобки.
	 * Аргумент может содержать скобки одного уровня вложенности, например
	 * "(tip:Basic Net (uncommon)|Turbo Net (epic))" — иначе первая же "(rare)"
	 * закрыла бы тег раньше времени, и хвост подсказки вывалился бы в текст.
	 */
	private static final Pattern TAG =
			Pattern.compile("\\[([^\\]]+)\\]\\((\\w+):((?:[^()]|\\([^()]*\\))*)\\)");

	/** Кусок строки: либо просто текст, либо пометка со своей ролью. */
	public record Part(String text, String role, String arg) {
		public boolean isTag() { return role != null; }
	}

	/** Разбирает строку на куски. Пометок нет — вернётся один кусок текста. */
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

	/** Текст без разметки — нужен, чтобы мерить ширину и искать по гайду. */
	public static String strip(String s) {
		if (s == null) return "";
		return TAG.matcher(s).replaceAll("$1").replaceAll("§.", "");
	}

	/**
	 * Последний §-код в строке.
	 *
	 * Нужен для переноса: если строка оборвалась посреди раскрашенного куска,
	 * следующая строка начнётся с чистого цвета и хвост фразы побелеет. Поэтому
	 * перенос продолжаем тем же кодом.
	 */
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

	/** Ширина текста с учётом того, что §-коды не занимают места. */
	public static int width(Font f, String s) {
		return f.width(s);
	}
}
