package com.ryn.skyryn.waypoint;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import com.ryn.skyryn.screen.FusionTopScreen;
import com.ryn.skyryn.screen.HudEditScreen;
import com.ryn.skyryn.screen.RynSettingsScreen;
import com.ryn.skyryn.screen.ShardListScreen;

public class Keybinds {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skyryn", "main"));

	private static KeyMapping top;
	private static KeyMapping shards;
	private static KeyMapping stopTrack;

	public static void register() {
		top = bind("top");
		shards = bind("shards");
		stopTrack = bind("stoptrack");

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean t = top.consumeClick();
			boolean sh = shards.consumeClick();
			boolean stop = stopTrack.consumeClick();

			if (client.player == null) return;
			if (!SkyBlockCheck.onSkyBlock()) return;

			if (stop && Waypoints.count() > 0) Waypoints.clear();
			if (client.screen != null) return;

			if (t) open(new FusionTopScreen());
			else if (sh) open(new ShardListScreen());
		});
	}

	private static KeyMapping bind(String name) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.skyryn." + name, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
	}

	private static void open(Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		mc.schedule(() -> mc.setScreen(screen));
	}

	public static java.util.Map<String, KeyMapping> all() {
		java.util.Map<String, KeyMapping> m = new java.util.LinkedHashMap<>();
		if (top != null) m.put("/sr top", top);
		if (shards != null) m.put("/sr shards", shards);
		if (stopTrack != null) m.put(com.ryn.skyryn.config.Lang.tr("Stop tracking", "Прекратить отслеживание"), stopTrack);
		return m;
	}
}
