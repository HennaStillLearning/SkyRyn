package com.ryn.skyryn.fusion;

/**
 * Хранит текущий запрос (шард + количество), чтобы панель показывала его
 * и после похода на базар/возврата в машину не нужно было вводить заново.
 * Живёт в памяти на время сессии.
 */
public class FusionState {
	public static String currentShard = null;
	public static int currentAmount = 0;
	/**
	 * Форсированный верхний рецепт цели (вход a/b) — чтобы калькулятор показал
	 * ТОТ ЖЕ путь, что в плашке BoxBoard, а не пересчитывал свой дешёвый.
	 * null — калькулятор считает как обычно (лучший рецепт).
	 */
	public static String forcedTopA = null;
	public static String forcedTopB = null;
	/** Растёт при каждом set — панель по нему понимает, что состояние сменили извне
	 *  (напр. кнопкой «в калькулятор»), и подтягивает поля ввода. */
	public static int version = 0;

	/** Ручной ввод в панели — форс сбрасываем, считаем оптимальный путь. */
	public static void set(String shard, int amount) {
		set(shard, amount, null, null);
	}

	/** Из плашки BoxBoard — передаём конкретный верхний рецепт. */
	public static void set(String shard, int amount, String topA, String topB) {
		currentShard = shard;
		currentAmount = amount;
		forcedTopA = topA;
		forcedTopB = topB;
		version++;
	}
}
