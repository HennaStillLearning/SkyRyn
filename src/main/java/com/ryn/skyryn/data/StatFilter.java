package com.ryn.skyryn.data;

import com.ryn.skyryn.config.Lang;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class StatFilter {
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

	public static List<Stat> stats() {
		return STATS;
	}

	public static Stat byId(String id) {
		if (id == null) return null;
		for (Stat s : STATS) if (s.id().equals(id)) return s;
		return null;
	}

	public static boolean matches(ShardDb.Shard s, String id) {
		Stat st = byId(id);
		if (s == null || st == null) return false;
		String en = s.attrDescEnPlain();
		return !en.isEmpty() && st.pattern().matcher(en).find();
	}
}
