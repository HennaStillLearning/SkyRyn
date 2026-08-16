package com.ryn.skyryn.config;

/**
 * Локализация интерфейса без ключей и lang-файлов: оба варианта пишем прямо
 * в месте вызова, а {@link #tr} отдаёт нужный по тумблеру {@link RynConfig#lang}.
 *
 * Пример: {@code Lang.tr("Hunting", "Охота")}. База — английский; русский
 * показывается, только когда игрок включил его в /sr.
 */
public final class Lang {
	private Lang() { }

	/** en — базовый текст, ru — русский вариант. */
	public static String tr(String en, String ru) {
		return RynConfig.isRu() ? ru : en;
	}
}
