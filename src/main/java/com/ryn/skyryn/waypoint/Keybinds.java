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

/**
 * Клавиши для открытия экранов мода. Настраиваются в ванильном меню
 * Настройки -> Управление -> SkyRyn.
 *
 * Важно: эти бинды открывают НАШИ экраны и ничего не отправляют на сервер —
 * ни кликов по чужим кнопкам, ни команд. Именно поэтому мы их вернули: то,
 * что убирали раньше, нажимало кнопки в Fusion Box, а это уже автоматизация
 * действий в игре. Переназначение клавиш само по себе Hypixel разрешает.
 *
 * По умолчанию клавиши не назначены (KEY_UNKNOWN): свободных кнопок в модпаке
 * почти нет, и молча занимать чужую — плохая идея. Назначь сам.
 */
public class Keybinds {

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skyryn", "main"));

	// Клавиш ровно три: /sr top, /sr shards и «прекратить отслеживание».
	// Настройки и правка HUD открываются из меню мода — своей клавиши им не надо.
	private static KeyMapping top;
	private static KeyMapping shards;
	private static KeyMapping stopTrack;

	public static void register() {
		top = bind("top");
		shards = bind("shards");
		stopTrack = bind("stoptrack");

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick() чистит очередь нажатий, поэтому дёргаем его всегда,
			// иначе нажатия копятся и выстреливают пачкой при заходе на скайблок.
			boolean t = top.consumeClick();
			boolean sh = shards.consumeClick();
			boolean stop = stopTrack.consumeClick();

			if (client.player == null) return;
			if (!SkyBlockCheck.onSkyBlock()) return;

			// Остановка отслеживания — просто гасим наши метки, работает и с
			// открытым экраном (это не ввод в чужой GUI).
			if (stop && Waypoints.count() > 0) Waypoints.clear();
			// Открываем только с «чистого» экрана: если игрок в сундуке или в чате,
			// клавиша — это его ввод, а не команда нам.
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

	/**
	 * Наши клавиши для экрана настроек: подпись → сама привязка. Назначать их можно
	 * прямо в меню мода, не уходя в управление Minecraft, — пишем в тот же
	 * KeyMapping, поэтому в ванильном экране бинд тоже виден.
	 */
	public static java.util.Map<String, KeyMapping> all() {
		java.util.Map<String, KeyMapping> m = new java.util.LinkedHashMap<>();
		if (top != null) m.put("/sr top", top);
		if (shards != null) m.put("/sr shards", shards);
		if (stopTrack != null) m.put(com.ryn.skyryn.config.Lang.tr("Stop tracking", "Прекратить отслеживание"), stopTrack);
		return m;
	}
}
