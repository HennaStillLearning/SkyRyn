package com.ryn.skyryn.config;

public final class SkyLog {
	private SkyLog() { }

	public static boolean on() { return RynConfig.flag("debug", false); }

	public static void d(String msg) {
		if (on()) System.out.println("[SkyRyn] " + msg);
	}

	public static void warn(String msg) {
		System.out.println("[SkyRyn] " + msg);
	}
}
