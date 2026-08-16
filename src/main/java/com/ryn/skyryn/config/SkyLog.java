package com.ryn.skyryn.config;

/**
 * Отладочный вывод мода.
 *
 * Раньше все сообщения шли прямо в System.out, и часть из них печаталась каждые
 * несколько секунд — в latest.log это выглядело как сплошной поток от SkyRyn.
 * Для релиза так нельзя: чужой лог не наше место для заметок, а при разборе чужого
 * краша этот шум только мешает.
 *
 * Теперь всё идёт сюда и по умолчанию молчит. Включается тумблером в конфиге
 * (флаг {@code debug}) — на время поиска бага.
 */
public final class SkyLog {

	private SkyLog() { }

	public static boolean on() { return RynConfig.flag("debug", false); }

	/** Отладка: видно, только когда включён debug. */
	public static void d(String msg) {
		if (on()) System.out.println("[SkyRyn] " + msg);
	}

	/** Сбой, о котором стоит знать всегда: не смогли прочитать данные, упал парсер. */
	public static void warn(String msg) {
		System.out.println("[SkyRyn] " + msg);
	}
}
