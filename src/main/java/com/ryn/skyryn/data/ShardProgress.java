package com.ryn.skyryn.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Уровень аттрибута каждого шарда (0..10).
 *
 * Уровень даёт только syphon: вложил шарды в аттрибут — уровень вырос. Читается
 * из Attribute Menu (AttributeMenuReader) — просто разбор экрана, который игрок
 * открыл сам.
 *
 * Ничего не знаем — говорим -1, а не 0: интерфейс покажет "?" вместо вранья.
 */
public class ShardProgress {

	/** ключ шарда -> уровень аттрибута 0..10. */
	private static final Map<String, Integer> LEVEL = new HashMap<>();

	/** Видели ли мы Attribute Menu хоть раз. */
	public static boolean known() {
		return !LEVEL.isEmpty();
	}

	/** Сырой уровень: -1 = в Attribute Menu такого шарда не встречали. */
	public static int levelOf(String shard) {
		if (shard == null) return -1;
		Integer v = LEVEL.get(shard.toLowerCase());
		return v == null ? -1 : v;
	}

	/**
	 * Уровень для показа. -1 = ещё не знаем.
	 *
	 * Attribute Menu перечисляет только ОТКРЫТЫЕ аттрибуты. Значит, если меню мы
	 * уже читали, а шарда там не было — уровень у него ровно 0, а не «неизвестно».
	 * До первого открытия меню честно не знаем ничего.
	 *
	 * Проверка сходится: у игрока 185 из 188 прочитанных уровней, и SkyHanni в
	 * своём оверлее показывает ровно те же 185/188 найденных.
	 */
	public static int displayLevel(String shard) {
		int raw = levelOf(shard);
		if (raw >= 0) return raw;
		if (!known()) return -1;
		ShardDb.Shard s = ShardDb.shard(shard);
		return s != null && s.hasAttribute() ? 0 : -1;
	}

	/** @return true, если значение и правда изменилось — по этому решаем, сохранять ли. */
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
