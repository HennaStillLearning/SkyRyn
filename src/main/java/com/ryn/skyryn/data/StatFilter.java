package com.ryn.skyryn.data;

import com.ryn.skyryn.config.Lang;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Статы SkyBlock для фильтра в /sr shards.
 *
 * Категории и ключевые слова выведены из реальных описаний (attrDesc), а не
 * выдуманы: проверяем по английскому оригиналу attrDescEn — там стабильные
 * названия статов, которые Hypixel и пишет в тултипах.
 *
 * Один шард может попасть в несколько статов (Galaxy Fish даёт три Fortune) —
 * это нормально, фильтр покажет его в каждом.
 *
 * Ярлык двуязычный (Lang.tr), а матчим и храним выбор по стабильному id —
 * иначе смена языка на лету ломала бы уже выбранный фильтр.
 */
public class StatFilter {

	/** Стат: стабильный id, двуязычный ярлык, регэксп по английскому описанию. */
	public record Stat(String id, String en, String ru, Pattern pattern) {
		public String label() { return Lang.tr(en, ru); }
	}

	private static final List<Stat> STATS = new ArrayList<>();

	static {
		add("strength", "Strength", "Сила", "Strength");
		add("health", "Health", "Здоровье", "\\bHealth\\b");
		add("intelligence", "Intelligence", "Интеллект", "Intelligence");
		add("defense", "Defense", "Защита", "Defense|True Defense");
		add("fortune", "Fortune", "Fortune", "Fortune");
		add("wisdom", "Wisdom", "Wisdom", "Wisdom");
		add("magic_find", "Magic Find", "Magic Find", "Magic Find");
		add("sweep", "Sweep", "Sweep", "Sweep");
		add("damage", "Damage", "Урон", "Damage");
		add("speed", "Speed", "Скорость", "Speed|\\bSpeed\\b");
		add("fishing", "Fishing", "Рыбалка", "Fishing|Hook|Sea Creature|Trophy Fish|Bait|Chum");
		add("crit", "Crit", "Крит", "Crit");
		add("mana", "Mana", "Мана", "Mana");
		add("essence", "Essence", "Essence", "Essence");
		add("powder", "Powder", "Powder", "Powder");
		add("pets", "Pets", "Питомцы", "Pet |Pet EXP|Pet Luck");
		add("dungeons", "Dungeons", "Данжи", "Dungeon|Catacombs|Kuudra|Blessing|Bonus Score");
		add("events", "Events", "Ивенты", "Diana|Mythological|Griffin|Winter Gift|Tree Gift|Garden Visitor");
	}

	private static void add(String id, String en, String ru, String regex) {
		STATS.add(new Stat(id, en, ru, Pattern.compile(regex, Pattern.CASE_INSENSITIVE)));
	}

	/** Все статы в порядке добавления — для выпадающего меню. */
	public static List<Stat> stats() {
		return STATS;
	}

	/** Стат по стабильному id, null — нет такого. */
	public static Stat byId(String id) {
		if (id == null) return null;
		for (Stat s : STATS) if (s.id().equals(id)) return s;
		return null;
	}

	/** Даёт ли шард стат с этим id. Смотрим по английскому описанию. */
	public static boolean matches(ShardDb.Shard s, String id) {
		Stat st = byId(id);
		if (s == null || st == null) return false;
		String en = s.attrDescEnPlain();
		return !en.isEmpty() && st.pattern().matcher(en).find();
	}
}
