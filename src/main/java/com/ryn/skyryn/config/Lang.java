package com.ryn.skyryn.config;

public final class Lang {
	private Lang() { }

	public static String tr(String en, String ru) {
		return RynConfig.isRu() ? ru : en;
	}
}
