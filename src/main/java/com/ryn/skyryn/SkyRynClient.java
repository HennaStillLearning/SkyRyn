package com.ryn.skyryn;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import java.util.Map;
import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.AttributeMenuReader;
import com.ryn.skyryn.data.BestiaryDb;
import com.ryn.skyryn.data.BestiaryReader;
import com.ryn.skyryn.data.LocationDb;
import com.ryn.skyryn.data.SeaGuideDb;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.data.ShardInfo;
import com.ryn.skyryn.fusion.BazaarHint;
import com.ryn.skyryn.fusion.BazaarPrices;
import com.ryn.skyryn.fusion.FusionCalculator;
import com.ryn.skyryn.fusion.FusionPanel;
import com.ryn.skyryn.fusion.FusionState;
import com.ryn.skyryn.fusion.FusionTracker;
import com.ryn.skyryn.hud.HudClicks;
import com.ryn.skyryn.hud.HuntingBoxReader;
import com.ryn.skyryn.hud.HuntingFortune;
import com.ryn.skyryn.hud.HuntingHud;
import com.ryn.skyryn.hud.HuntingTracker;
import com.ryn.skyryn.screen.FusionTopScreen;
import com.ryn.skyryn.screen.HudEditScreen;
import com.ryn.skyryn.screen.RynMenuScreen;
import com.ryn.skyryn.screen.ShardListScreen;
import com.ryn.skyryn.waypoint.Keybinds;
import com.ryn.skyryn.waypoint.SkyBlockCheck;
import com.ryn.skyryn.waypoint.PathRecorder;
import com.ryn.skyryn.waypoint.SpotRecorder;
import com.ryn.skyryn.waypoint.Waypoints;

public class SkyRynClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ShardDb.load();
		ShardInfo.load();
		LocationDb.load();
		BestiaryDb.loadBundle();
		SeaGuideDb.loadBundle();
		SkyBlockCheck.loadAreas();
		ConfigManager.load();
		com.ryn.skyryn.data.ShardIcons.load();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ConfigManager.save();
			com.ryn.skyryn.data.ShardIcons.save();
		});

		FusionPanel.register();
		com.ryn.skyryn.screen.BoxGuideOverlay.register();
		com.ryn.skyryn.data.ShardIcons.register();
		HuntingTracker.register();
		FusionTracker.register();
		HuntingHud.register();
		Waypoints.register();
		if (RynConfig.flag("debug", false)) {
			SpotRecorder.register();
			PathRecorder.register();
		}
		com.ryn.skyryn.waypoint.SafariBiomes.register();
		com.ryn.skyryn.data.SafariPerks.register();
		HudClicks.register();
		HuntingFortune.register();
		HuntingBoxReader.register();
		com.ryn.skyryn.fusion.ShardStock.register();
		AttributeMenuReader.register();
		BestiaryReader.register();
		BazaarHint.register();
		Keybinds.register();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
				.register(com.ryn.skyryn.waypoint.MobHighlight::tick);
		com.ryn.skyryn.hud.TikiHelper.register();
		if (RynConfig.flag("debug", false)) com.ryn.skyryn.waypoint.MobScanner.register();
		com.ryn.skyryn.hud.ForagingAlerts.register();
		com.ryn.skyryn.hud.CritterTimer.register();
		com.ryn.skyryn.hud.SafariTracker.register();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			for (String cmd : new String[]{ "srhighlight" })
				dispatcher.register(ClientCommands.literal(cmd)
						.executes(ctx -> { highlightList(ctx.getSource()); return 1; })
						.then(ClientCommands.argument("mob", StringArgumentType.greedyString())
								.executes(ctx -> { highlightToggle(ctx.getSource(), StringArgumentType.getString(ctx, "mob")); return 1; })));

			var sr = ClientCommands.literal("sr")
					.executes(ctx -> open(new RynMenuScreen()));
			for (String c : com.ryn.skyryn.screen.RynSettingsScreen.CATEGORY_COMMANDS)
				sr.then(ClientCommands.literal(c).executes(
						ctx -> open(new com.ryn.skyryn.screen.RynSettingsScreen(null, c))));
			if (RynConfig.flag("debug", false)) sr
					.then(ClientCommands.literal("reload").executes(ctx -> {
						ShardInfo.load();
						LocationDb.load();
						ctx.getSource().sendFeedback(Component.literal(
								Lang.tr("SkyRyn: guide reloaded, descriptions — ", "SkyRyn: гайд перечитан, описаний — ") + ShardInfo.described())
								.withStyle(ChatFormatting.GREEN));
						return 1;
					}))
					.then(ClientCommands.literal("wp")
							.executes(ctx -> {
								Minecraft mc = Minecraft.getInstance();
								if (mc.player != null) {
									Waypoints.addRaw(mc.player.getX(), mc.player.getY() + 1,
											mc.player.getZ(), Lang.tr("TEST", "ТЕСТ"));
									ctx.getSource().sendFeedback(Component.literal(
											Lang.tr("SkyRyn: test waypoint at your position — walk away and look",
													"SkyRyn: тест-метка на твоей позиции — отойди и посмотри"))
											.withStyle(ChatFormatting.GREEN));
								}
								return 1;
							})
							.then(ClientCommands.argument("x", IntegerArgumentType.integer())
									.then(ClientCommands.argument("y", IntegerArgumentType.integer())
											.then(ClientCommands.argument("z", IntegerArgumentType.integer())
													.executes(ctx -> {
														Waypoints.addRaw(
																IntegerArgumentType.getInteger(ctx, "x") + 0.5,
																IntegerArgumentType.getInteger(ctx, "y") + 0.5,
																IntegerArgumentType.getInteger(ctx, "z") + 0.5,
																Lang.tr("TEST", "ТЕСТ"));
														ctx.getSource().sendFeedback(Component.literal(
																Lang.tr("SkyRyn: waypoint placed", "SkyRyn: метка поставлена"))
																.withStyle(ChatFormatting.GREEN));
														return 1;
													})))))
					.then(ClientCommands.literal("unmark").executes(ctx -> {
						int n = Waypoints.count();
						Waypoints.clear();
						ctx.getSource().sendFeedback(Component.literal(
								Lang.tr("SkyRyn: waypoints cleared — ", "SkyRyn: снято меток — ") + n).withStyle(ChatFormatting.YELLOW));
						return 1;
					}))
					.then(ClientCommands.literal("reset").executes(ctx -> {
						FusionTracker.resetAll();
						HuntingTracker.resetAll();
						ConfigManager.save();
						ctx.getSource().sendFeedback(Component.literal(Lang.tr("SkyRyn: stats reset", "SkyRyn: статистика сброшена"))
								.withStyle(ChatFormatting.YELLOW));
						return 1;
					}));

			dispatcher.register(sr
					.then(ClientCommands.literal("gui").executes(SkyRynClient::openSettings))
					.then(ClientCommands.literal("settings").executes(SkyRynClient::openSettings))
					.then(ClientCommands.literal("top").executes(ctx -> open(new FusionTopScreen())))
					.then(ClientCommands.literal("shards")
							.executes(ctx -> open(new ShardListScreen()))
							.then(ClientCommands.argument("shard", StringArgumentType.greedyString())
									.executes(ctx -> openShardPage(ctx.getSource(),
											StringArgumentType.getString(ctx, "shard")))))
					.then(ClientCommands.literal("hud").executes(ctx -> open(new HudEditScreen())))
					.then(ClientCommands.argument("query", StringArgumentType.greedyString())
							.executes(ctx -> open(com.ryn.skyryn.screen.RynSettingsScreen.searching(
									null, StringArgumentType.getString(ctx, "query")))))
					.then(ClientCommands.literal("setfortune")
							.then(ClientCommands.argument("value", IntegerArgumentType.integer(0, 10000))
									.executes(ctx -> {
										int v = IntegerArgumentType.getInteger(ctx, "value");
										RynConfig.hunterFortune = v;
										ConfigManager.save();
										ctx.getSource().sendFeedback(Component.literal(
												"SkyRyn: Hunter Fortune = " + v + " (×"
												+ String.format("%.2f", HuntingFortune.dropsPerCatch())
												+ Lang.tr(" shards per catch)", " шардов за поимку)")).withStyle(ChatFormatting.GREEN));
										return 1;
									})))
					.then(ClientCommands.literal("calc")
							.executes(ctx -> openHb())
							.then(ClientCommands.argument("shard", StringArgumentType.word())
									.then(ClientCommands.argument("amount", IntegerArgumentType.integer(1))
											.executes(ctx -> {
												String shard = StringArgumentType.getString(ctx, "shard");
												int amount = IntegerArgumentType.getInteger(ctx, "amount");
												FusionState.set(shard, amount);
												showFusion(ctx.getSource(), shard, amount);
												return 1;
											})))));
		});
	}

	private static int openHb() {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() != null) {
			client.schedule(() -> client.getConnection().sendCommand("hb"));
		}
		return 1;
	}

	private static int open(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft client = Minecraft.getInstance();
		client.schedule(() -> client.setScreen(screen));
		return 1;
	}

	private static int openShardPage(FabricClientCommandSource src, String name) {
		String q = name.trim().toLowerCase();
		String key = ShardDb.keyByName(q);
		if (key == null) {
			for (String k : ShardDb.allShards())
				if (k.startsWith(q) || ShardDb.displayName(k).toLowerCase().startsWith(q)) { key = k; break; }
		}
		if (key == null) {
			src.sendFeedback(Component.literal(PREFIX + Lang.tr("No such shard: ", "Нет такого шарда: ") + name)
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		return open(new com.ryn.skyryn.screen.ShardPageScreen(key, null));
	}

	private static int openSettings(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx) {
		Minecraft client = Minecraft.getInstance();
		client.schedule(() -> client.setScreen(new com.ryn.skyryn.screen.RynSettingsScreen(null)));
		return 1;
	}

	private static final String PREFIX = "§5§l[§dSkyRyn§5§l]§r ";

	private static void highlightToggle(FabricClientCommandSource src, String mob) {
		String m = mob == null ? "" : mob.toLowerCase();
		if (m.equals("floordrop") || m.equals("drops") || m.equals("floor")) {
			RynConfig.floorDropHighlight = !RynConfig.floorDropHighlight;
			com.ryn.skyryn.config.ConfigManager.save();
			src.sendFeedback(Component.literal(PREFIX + Lang.tr("Floor drops §7— ", "Дроп на полу §7— ")
					+ (RynConfig.floorDropHighlight ? Lang.tr("§aON", "§aВКЛ") : Lang.tr("§cOFF", "§cВЫКЛ"))));
			return;
		}
		com.ryn.skyryn.waypoint.MobHighlight.MobDef d = com.ryn.skyryn.waypoint.MobHighlight.def(mob);
		if (d == null) {
			src.sendFeedback(Component.literal(PREFIX + Lang.tr("§cUnknown mob: §f", "§cНеизвестный моб: §f") + mob
					+ Lang.tr(" — see /highlight", " — см. /highlight")));
			return;
		}
		boolean on = !RynConfig.hasHighlightMob(d.key());
		RynConfig.setHighlightMob(d.key(), on);
		if (on) RynConfig.mobHighlightEnabled = true;
		com.ryn.skyryn.config.ConfigManager.save();
		src.sendFeedback(Component.literal(PREFIX + d.label() + " §7— "
				+ (on ? Lang.tr("§ahighlight ON", "§aподсветка ВКЛ") : Lang.tr("§chighlight OFF", "§cподсветка ВЫКЛ"))));
	}

	private static void highlightList(FabricClientCommandSource src) {
		src.sendFeedback(Component.literal(PREFIX + Lang.tr("Highlightable mobs (§f/highlight <mob>§r):", "Подсвечиваемые мобы (§f/highlight <моб>§r):")));
		for (var d : com.ryn.skyryn.waypoint.MobHighlight.MOBS) {
			boolean on = RynConfig.hasHighlightMob(d.key());
			src.sendFeedback(Component.literal("§7  " + d.key() + " §8— §r" + d.label() + " " + (on ? "§a✔" : "§8✘")));
		}
	}

	private void showFusion(FabricClientCommandSource source, String shard, int amount) {
		if (!ShardDb.hasRecipe(shard)) {
			source.sendFeedback(Component.literal(Lang.tr("No recipe for '", "Нет рецепта для '") + shard
					+ Lang.tr("'. Available: ", "'. Доступно: ") + String.join(", ", ShardDb.allCraftable()))
					.withStyle(ChatFormatting.RED));
			return;
		}

		BazaarPrices.refreshIfNeeded();
		FusionCalculator.Result res = FusionCalculator.calculate(shard, amount);

		source.sendFeedback(Component.literal("═══ " + Lang.tr("Fusion: ", "Фьюжен: ") + amount + "x "
				+ ShardDb.displayName(shard) + " ═══").withStyle(ChatFormatting.GOLD));

		if (!BazaarPrices.isLoaded()) {
			source.sendFeedback(Component.literal(Lang.tr("Bazaar prices are still loading, try again in a couple seconds.",
					"Цены базара ещё грузятся, попробуй снова через пару секунд."))
					.withStyle(ChatFormatting.GRAY));
		}

		source.sendFeedback(Component.literal(Lang.tr("─── Buy (cheap path, click) ───", "─── Купить (дешёвый путь, клик) ───")).withStyle(ChatFormatting.YELLOW));
		for (Map.Entry<String, Integer> e : res.shoppingList.entrySet()) {
			source.sendFeedback(clickableBuy(e.getKey(), e.getValue()));
		}

		source.sendFeedback(Component.literal(Lang.tr("Cost: ", "Стоимость: ") + fmt(res.totalCost)).withStyle(ChatFormatting.WHITE));
		source.sendFeedback(Component.literal(Lang.tr("Sale: ", "Продажа: ") + fmt(res.sellRevenue)).withStyle(ChatFormatting.WHITE));
		ChatFormatting pc = res.profit >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
		source.sendFeedback(Component.literal(Lang.tr("Profit: ", "Профит: ") + fmt(res.profit)).withStyle(pc));
	}

	private static String fmt(double v) {
		double a = Math.abs(v);
		if (a >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
		if (a >= 1_000) return String.format("%.1fk", v / 1_000);
		return String.format("%.0f", v);
	}

	private MutableComponent clickableBuy(String shard, int amount) {
		String bz = ShardDb.bazaarName(shard);
		return Component.literal(amount + "x " + ShardDb.displayName(shard))
				.setStyle(Style.EMPTY
						.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/bz " + bz))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.literal(Lang.tr("Open bazaar: ", "Открыть базар: ") + bz))));
	}
}
