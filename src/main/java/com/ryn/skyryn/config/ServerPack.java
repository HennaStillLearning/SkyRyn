package com.ryn.skyryn.config;

import net.minecraft.client.Minecraft;

public final class ServerPack {
	private ServerPack() { }

	public static void dropNow() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) return;
		try {
			mc.getDownloadedPackSource().popAll();
		} catch (Throwable t) {
			SkyLog.d("Не смог снять серверный пак: " + t);
		}
	}
}
