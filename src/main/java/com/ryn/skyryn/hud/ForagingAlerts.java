package com.ryn.skyryn.hud;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.waypoint.SkyBlockCheck;

/**
 * Оповещения по Foraging (деревья Galatea / Critter Safari) и спавн Beeheemoth.
 *
 * Все три срабатывания перков Hypixel объявляет в чат заглавными («WOODPECKER! You
 * felled the entire Tree!»). Ловим ключевое слово и на пару секунд показываем крупную
 * цветную вспышку по центру экрана — чтобы не листать чат в разгар рубки.
 *
 * Ключевое слово ищем в ЗАГЛАВНОМ виде: обычный текст вроде «timber» в чужом
 * сообщении так не поймается, а само событие всегда капсом.
 *
 * Beeheemoth спавнится в случайной точке карты и объявляется отдельным сообщением
 * («BEEHEEMOTH! A Beeheemoth has spawned at Critter Safari Entrance!») — место
 * вытаскиваем в подпись под надписью, иначе бежать некуда.
 *
 * Место, размер и цвет каждой надписи правятся отдельно — см. {@link Announce}.
 */
public class ForagingAlerts {

	/** Одно оповещение: слово-триггер, крупная надпись, подпись под ней, тумблер. */
	private record Alert(String keyword, String annId, String big, String subEn, String subRu, BooleanSupplier on) {
		String sub() { return Lang.tr(subEn, subRu); }
	}

	private static final List<Alert> ALERTS = List.of(
			new Alert("WOODPECKER", Announce.WOODPECKER, "WOODPECKER!",
					"Tree felled instantly", "Дерево сломано мгновенно",
					() -> RynConfig.woodpeckerAlert),
			new Alert("TIMBER", Announce.TIMBER, "TIMBER!",
					"Extra logs from the tree", "Дополнительные брёвна с дерева",
					() -> RynConfig.timberAlert),
			new Alert("PETALFALL", Announce.PETALFALL, "PETALFALL!",
					"Extra petals from the tree", "Дополнительные лепестки с дерева",
					() -> RynConfig.petalfallAlert));

	/** «A Beeheemoth has spawned at Critter Safari Entrance!» — берём место спавна. */
	private static final java.util.regex.Pattern BEEHEEMOTH =
			java.util.regex.Pattern.compile("beeheemoth has spawned at (.+?)!", java.util.regex.Pattern.CASE_INSENSITIVE);

	private static long shownAt = -100000;
	private static String shownSub = "";
	private static String shownId = Announce.WOODPECKER;
	private static String shownBig = "";

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			if (!SkyBlockCheck.onSkyBlock()) return;
			String s = message.getString();
			if (s == null) return;
			var bm = BEEHEEMOTH.matcher(s);
			if (bm.find() && RynConfig.flag("torrhus.beeheemoth", true)) {
				show(Announce.BEEHEEMOTH, "BEEHEEMOTH!",
						Lang.tr("spawned at ", "заспавнился: ") + bm.group(1).trim());
				return;
			}
			for (Alert a : ALERTS) {
				if (!a.on().getAsBoolean()) continue;
				if (!s.contains(a.keyword())) continue;
				show(a.annId(), a.big(), a.sub());
				return;
			}
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyryn", "foraging-alerts"),
				(ctx, tick) -> renderHud(ctx));
	}

	/** Надпись берём из настроек анонса: игрок мог вписать свою вместо заводской. */
	private static void show(String annId, String big, String sub) {
		shownId = annId; shownBig = Announce.text(annId, big); shownSub = sub;
		shownAt = System.currentTimeMillis();
	}

	private static void renderHud(GuiGraphicsExtractor ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
		if (shownBig.isEmpty()) return;
		long showMs = Announce.showMs(shownId);   // длительность настраивается шестерёнкой
		long dt = System.currentTimeMillis() - shownAt;
		if (dt < 0 || dt > showMs) return;

		// Плавное затухание за отведённое время.
		float p = 1f - dt / (float) showMs;
		Announce.draw(ctx, mc.font, shownId, shownBig, shownSub, Math.round(p * 255));
	}
}
