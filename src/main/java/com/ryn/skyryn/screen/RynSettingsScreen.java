package com.ryn.skyryn.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;

import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.LocationDb;
import com.ryn.skyryn.hud.Announce;
import com.ryn.skyryn.data.ShardInfo;

public class RynSettingsScreen extends Screen {
	static final int SCREEN_BG = 0xF00A0A0C;
	static final int PANEL     = 0xFF151519;
	static final int PANEL_HI  = 0xFF1C1C22;
	static final int ROW_HOVER = 0xFF202028;
	static final int SEG_OFF   = 0xFF23232B;
	static final int SEG_ON    = 0xFF3A3A45;
	static final int BORDER    = 0xFF2A2A32;
	static final int CAT_SEL   = 0xFF262630;
	static final int ACCENT    = 0xFF5B8DEF;
	static final int TITLE     = 0xFFF0F1F4;
	static final int DESC      = 0xFF888B94;
	static final int CAT       = 0xFF9B9EA8;
	static final int FAINT     = 0xFF6C6F79;

	private static final int OUTER = 12, GAP = 8, SIDEBAR_W = 214, TOP_H = 54, CONTROL_W = 170;
	private static final int ROW_H = 26;

	private final Screen parent;
	private final List<Category> cats = new ArrayList<>();
	private int catSel = 0;
	private int subSel = -1;
	private final List<int[]> subRects = new ArrayList<>();

	private int contentX, contentW, bodyY, bodyBottom;
	private int scrollY = 0, contentH = 0;

	private final List<Hit> hits = new ArrayList<>();
	private final List<int[]> catRects = new ArrayList<>();
	private int[] closeRect, searchRect;

	private String tipText = null;
	private int tipX, tipY;

	private KeyOpt listening = null;
	private String search = "";
	private boolean searchFocused = false;
	private Hit activeSlider = null;
	private boolean rebuild = false;
	private final java.util.Set<String> expanded = new java.util.HashSet<>();
	private String sectionKey(String title) { return catSel + "/" + subSel + "/" + title; }
	private boolean isCollapsed(String title) { return !expanded.contains(sectionKey(title)); }

	private final String openAt;

	public RynSettingsScreen(Screen parent) { this(parent, ""); }

	public RynSettingsScreen(Screen parent, String category) {
		super(Component.literal("SkyRyn Settings"));
		this.parent = parent;
		this.openAt = category == null ? "" : category.trim().toLowerCase();
	}

	public static RynSettingsScreen searching(Screen parent, String query) {
		RynSettingsScreen s = new RynSettingsScreen(parent, "");
		s.search = query == null ? "" : query.trim();
		return s;
	}

	public static final String[] CATEGORY_COMMANDS = {
			"fusion", "calculator", "hunting", "warps", "highlight", "foraging", "interface", "keys" };

	private sealed interface Opt permits Header, Note, Toggle, Slider, Btn, ColorOpt, Cycle, MobOpt, AnnOpt, KeyOpt { }
	private record KeyOpt(String title, net.minecraft.client.KeyMapping key) implements Opt { }
	private record Note(String title, String desc) implements Opt { }
	private record Header(String title, String desc) implements Opt { }
	private record Toggle(String title, String desc, BooleanSupplier get, Consumer<Boolean> set) implements Opt { }
	private record AnnOpt(String title, String desc, BooleanSupplier get, Consumer<Boolean> set, String annId) implements Opt { }
	private static AnnOpt ann(String t, String d, BooleanSupplier g, Consumer<Boolean> s, String annId) {
		return new AnnOpt(t, d, g, s, annId);
	}
	private record Slider(String title, String desc, double min, double max, double step, boolean flt,
						  DoubleSupplier get, DoubleConsumer set) implements Opt { }
	private record Btn(String title, String desc, String text, Runnable action) implements Opt { }
	private record ColorOpt(String title, String desc, IntSupplier get, IntConsumer set) implements Opt { }
	private record Cycle(String title, String desc, String[] options, IntSupplier get, IntConsumer set) implements Opt { }
	private static Cycle cycle(String t, String d, String[] opts, IntSupplier g, IntConsumer s) { return new Cycle(t, d, opts, g, s); }
	private record MobOpt(String name, String mobKey, String colorKey, int defColor) implements Opt { }
	private static MobOpt mobOpt(String name, String mobKey, int defColor) { return new MobOpt(name, mobKey, "mob." + mobKey, defColor); }

	static final int[] PALETTE = {
			0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFFFF5555, 0xFFFF8020, 0xFFFFAA00, 0xFFFFFF55,
			0xFF80FF40, 0xFF50E070, 0xFF00AAAA, 0xFF55FFFF, 0xFF5B8DEF, 0xFF5555FF, 0xFFC050FF, 0xFFFF55FF };
	private record SubCat(String name, List<Opt> opts) { }
	private record Category(String name, List<Opt> opts, List<SubCat> subs) { }

	private static final class Hit {
		final Opt opt; final int x1, y1, x2, y2;
		int offX1, offX2, onX1, onX2;
		int trackX1, trackX2;
		int btnX1, btnX2;
		Hit(Opt o, int x1, int y1, int x2, int y2) { this.opt = o; this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; }
	}

	@Override
	protected void init() { buildCats(); }

	private void buildCats() {
		cats.clear();
		safariSubs.clear();

		cats.add(cat(Lang.tr("Fusion", "Фьюжен"),
				new Header(Lang.tr("Fusion box window", "Окно фьюза"), ""),
				toggle(Lang.tr("Calculator", "Калькулятор"),
						Lang.tr("Shows how many shards the fusion needs.",
								"Показывает количество необходимых шардов для фьюза."),
						() -> RynConfig.calculatorEnabled, v -> RynConfig.calculatorEnabled = v),
				toggle("Attribute Helper",
						Lang.tr("Next to the fusion window lists what is worth making from the shards already in your box.",
								"Рядом с окном фьюза показывает, что выгодно собрать из шардов, которые уже лежат в боксе."),
						() -> RynConfig.boxBoardEnabled, v -> RynConfig.boxBoardEnabled = v),
				toggle(Lang.tr("Mark the needed shards", "Подсвечивать нужные шарды"), "",
						() -> RynConfig.highlightFuseInputs, v -> RynConfig.highlightFuseInputs = v),
				toggle(Lang.tr("Recipes on Ctrl and Shift", "Рецепты по Ctrl и Shift"),
						Lang.tr("Point at a shard in the box: Ctrl shows what you can fuse from it, Shift shows what you can fuse it from — counted by what your box actually holds.",
								"Наведись на шард в боксе: Ctrl покажет, что из него можно собрать, Shift — из чего собрать его самого. Считается по тому, что реально лежит в боксе."),
						() -> RynConfig.flag("peek.recipes", true), v -> RynConfig.setFlag("peek.recipes", v)),
				toggle(Lang.tr("Fusion tracker", "Трекер фьюзов"),
						Lang.tr("Counts how many fusions, shards and coins you made this session.",
								"Считает, сколько фьюзов, шардов и монет ты сделал за сессию."),
						() -> RynConfig.fusionTrackerEnabled, v -> RynConfig.fusionTrackerEnabled = v)));

		cats.add(cat(Lang.tr("Calculator", "Калькулятор"),
				new Header(Lang.tr("Prices and math", "Цены и расчёт"), ""),
				slider("Bazaar Flipper",
						Lang.tr("Your Bazaar Flipper level. It sets the tax you pay when selling.",
								"Твой уровень Bazaar Flipper. От него зависит налог при продаже."),
						0, RynConfig.BAZAAR_FLIPPER_MAX, 1, false,
						() -> RynConfig.bazaarFlipperLevel, v -> RynConfig.bazaarFlipperLevel = (int) v),
				toggle(Lang.tr("Count what you already have", "Учитывать, что уже есть"),
						Lang.tr("The shopping list subtracts the shards already sitting in your Hunting Box and the ones you just bought, so it shows what is left to buy. The box is read page by page, so a shard on page two counts once you open that page.",
								"Список покупок вычитает шарды, которые уже лежат в Hunting Box, и те, что ты только что купил, и показывает, сколько осталось докупить. Бокс читается постранично: шард со второй страницы посчитается, когда ты её откроешь."),
						() -> RynConfig.flag("calc.stock", true), v -> RynConfig.setFlag("calc.stock", v)),
				toggle(Lang.tr("Amount hint on the sign", "Подсказка на табличке"),
						Lang.tr("When you type an amount for a bazaar order, shows the number you need above the sign.",
								"Когда вводишь количество при заказе на базаре, показывает нужное число над табличкой."),
						() -> RynConfig.bazaarHintEnabled, v -> RynConfig.bazaarHintEnabled = v),
				slider("Hunter Fortune",
						Lang.tr("Your Hunter Fortune. The mod needs it to know how many shards drop per catch.",
								"Твоя Hunter Fortune. По ней мод считает, сколько шардов падает за поимку."),
						0, 250, 1, false,
						() -> RynConfig.hunterFortune, v -> RynConfig.hunterFortune = (float) v)));

		List<Opt> hunt = new ArrayList<>(List.of(
				new Header(Lang.tr("Hunting tracker", "Трекер охоты"), ""),
				ann(Lang.tr("Hunting tracker", "Трекер охоты"),
						Lang.tr("Overlay with your hunt stats: how many shards you got and how fast.",
								"Плашка со статистикой охоты: сколько шардов набил и как быстро."),
						() -> RynConfig.huntingTrackerEnabled, v -> RynConfig.huntingTrackerEnabled = v, "hud:hunt"),
				cycle("Price",
						Lang.tr("Which price the loot is valued at.", "По какой цене оценивается добыча."),
						new String[]{ "Insta sell", "Sell offer" },
						() -> RynConfig.huntInstaSell ? 0 : 1, i -> RynConfig.huntInstaSell = (i == 0)),
				new Header(Lang.tr("Count Lootshare, Charm", "Учитывать Lootshare, Charm"), ""),
				toggle("Lootshare", "", () -> RynConfig.huntCountLootShare, v -> RynConfig.huntCountLootShare = v),
				toggle("Charm", "", () -> RynConfig.huntCountCharm, v -> RynConfig.huntCountCharm = v),
				new Header("Pause announce", ""),
				toggle("Pause announce",
						Lang.tr("Writes on screen when the tracker stops and when it starts again.",
								"Пишет на экране, когда трекер встал и когда снова пошёл."),
						() -> RynConfig.huntPauseAnnounce, v -> RynConfig.huntPauseAnnounce = v),
				slider(Lang.tr("Pause timer, sec", "Таймер паузы, сек"),
						Lang.tr("How long without any drop before the tracker freezes.",
								"Сколько секунд без добычи, прежде чем трекер замрёт."),
						0, 300, 5, false,
						() -> RynConfig.huntIdleSeconds, v -> RynConfig.huntIdleSeconds = (int) v),
				new Header(Lang.tr("Hints in the guide", "Подсказки в гайде"), ""),
				toggle("Mob card",
						Lang.tr("Shows the mob stats in the /sr shards guides.",
								"Показывает характеристики моба в /sr shards гайдах."),
						() -> RynConfig.bestiaryHints, v -> RynConfig.bestiaryHints = v),
				toggle("Sea creature card",
						Lang.tr("Shows the sea creature stats in the /sr shards guides.",
								"Показывает характеристики sea creature в /sr shards гайдах."),
						() -> RynConfig.seaGuideHints, v -> RynConfig.seaGuideHints = v)));
		cats.add(new Category(Lang.tr("Hunting", "Охота"), hunt, List.of()));

		List<Opt> warps = new ArrayList<>();
		warps.add(new Header(Lang.tr("Warps", "Варпы"), ""));
		warps.add(toggle("MVP+",
				Lang.tr("When on, the mod replaces the basic warp with a scroll warp while tracking hunting methods.",
						"Если включено, то при отслеживании методов охоты мод заменяет базовый варп на scroll warp."),
				() -> RynConfig.mvpPlus, v -> RynConfig.mvpPlus = v));
		warps.add(toggle("Crystal Nucleus", "",
				() -> RynConfig.preferNucleus, v -> RynConfig.preferNucleus = v));
		warps.add(toggle(Lang.tr("Line to the spot", "Линия к споту"),
				Lang.tr("Draws a line from you to the spot you are tracking.",
						"Рисует линию от тебя к споту, который ты отслеживаешь."),
				() -> RynConfig.routeBeam, v -> RynConfig.routeBeam = v));
		warps.add(new Header("Warp", ""));
		for (var e : LocationDb.scrolls().entrySet()) {
			final String scroll = e.getKey();
			warps.add(toggle(scroll, "", () -> RynConfig.hasScroll(scroll), v -> RynConfig.setScroll(scroll, v)));
		}
		cats.add(new Category(Lang.tr("Warps", "Варпы"), warps, List.of()));

		List<Opt> highlight = new ArrayList<>();
		highlight.add(new Header(Lang.tr("Mobs", "Мобы"), ""));
		highlight.add(toggle(Lang.tr("Highlight mobs", "Подсветка мобов"),
				Lang.tr("Traces an outline around the mobs you pick below, so you can spot them in the leaves and in the dark. Only mobs you can actually see: a mob behind a wall is not shown.",
						"Обводит контуром выбранных ниже мобов, чтобы их было видно в листве и в темноте. Только тех, кого реально видно: моба за стеной не показывает."),
				() -> RynConfig.mobHighlightEnabled, v -> RynConfig.mobHighlightEnabled = v));
		highlight.add(cycle(Lang.tr("Mobs drawn by an armor stand", "Мобы-стойки"),
				Lang.tr("Some mobs are an item on an invisible armor stand, not a creature. The outline hugs the mob but the game draws the stand itself along with it; the box is coarser but shows nothing extra.",
						"Часть мобов — это предмет на невидимой стойке, а не существо. Контур ложится по мобу, но игра обводит вместе с ним и саму стойку; бокс грубее, зато лишнего не показывает."),
				new String[]{ Lang.tr("Box", "Бокс"), Lang.tr("Outline", "Контур") },
				() -> RynConfig.getInt("hl.stand", 0), i -> RynConfig.setInt("hl.stand", i)));
		highlight.add(new Header(Lang.tr("Mobs by place", "Мобы по местам"), ""));
		for (String sub : new String[]{ "Torrhus Canyon", "Galatea", "Critter Safari" })
			highlight.add(button(sub, "", Lang.tr("Open", "Открыть"), () -> goToSub(sub)));
		addHighlight(highlight, com.ryn.skyryn.waypoint.MobHighlight.OTHER);
		cats.add(new Category(Lang.tr("Highlight", "Подсветка"), highlight, List.of()));

		List<Opt> forTop = new ArrayList<>(List.of(
				new Header("Tree falls announce",
						Lang.tr("Announces when the perks go off — a big caption in the middle of the screen so you do not have to watch the chat.",
								"Уведомляет о срабатывании перков — крупной надписью по центру экрана, чтобы не следить за чатом.")),
				ann("Woodpecker",
						Lang.tr("When a tree falls whole from Woodpecker.", "Когда дерево падает целиком от Woodpecker."),
						() -> RynConfig.woodpeckerAlert, v -> RynConfig.woodpeckerAlert = v, Announce.WOODPECKER),
				ann("Timber", Lang.tr("When Timber goes off.", "Когда срабатывает Timber."),
						() -> RynConfig.timberAlert, v -> RynConfig.timberAlert = v, Announce.TIMBER),
				ann("Petalfall", Lang.tr("When Petalfall goes off.", "Когда срабатывает Petalfall."),
						() -> RynConfig.petalfallAlert, v -> RynConfig.petalfallAlert = v, Announce.PETALFALL),
				new Header("Honey tracker", ""),
				ann(Lang.tr("Overlay", "Плашка"),
						Lang.tr("The list of trees and hives with their countdowns. The gear opens HUD editing — drag it where you want it.",
								"Список деревьев и ульев с обратным отсчётом. Шестерёнка открывает правку HUD — перетащи её куда нужно."),
						() -> RynConfig.flag("critter.plaque", true), v -> RynConfig.setFlag("critter.plaque", v),
						"hud:critter"),
				cycle(Lang.tr("Show on", "Показывать на:"),
						Lang.tr("Honeycomb trees grow both in Torrhus Canyon and on Galatea.",
								"Honeycomb-деревья есть и в Torrhus Canyon, и на Galatea."),
						new String[]{ "Torrhus Canyon", "Galatea", "Foraging Island", Lang.tr("Everywhere", "Везде") },
						() -> RynConfig.getInt("critter.where", 3), i -> RynConfig.setInt("critter.where", i)),
				ann("Honeycomb timer",
						Lang.tr("Counts down to the critter on every tree you lathered with honeycomb and warns you five seconds before.",
								"Отсчитывает время до криттера на каждом дереве, которое ты помазал мёдом, и предупреждает за пять секунд."),
						() -> RynConfig.critterTimer, v -> RynConfig.critterTimer = v, Announce.CRITTER),
				cycle(Lang.tr("Tree markers", "Метки деревьев"),
						Lang.tr("Marks honeycomb trees in Torrhus Canyon and on Galatea. Either always, or only while you hold a pot of honeycomb.",
								"Отмечает honeycomb-деревья в Torrhus Canyon и на Galatea. Либо всегда, либо только пока держишь горшок с мёдом."),
						new String[]{ Lang.tr("Off", "Выкл"), Lang.tr("Pot in hand", "Горшок в руке"),
								Lang.tr("Show always", "Показывать всегда") },
						() -> RynConfig.getInt("trees.mode", 0), i -> RynConfig.setInt("trees.mode", i)),
				toggle("Honeyhive timer",
						Lang.tr("Counts down to the hive refill. One timer for all of them — by the last seen countdown.",
								"Отсчитывает время до наполнения улья. Таймер один на все — по последнему увиденному отсчёту."),
						() -> RynConfig.flag("hive.timer", true), v -> RynConfig.setFlag("hive.timer", v))));

		List<SubCat> forSubs = new ArrayList<>();

		List<Opt> torrhus = new ArrayList<>();
		torrhus.add(new Header(Lang.tr("Announces", "Оповещения"), ""));
		torrhus.add(ann("Beeheemoth",
				Lang.tr("Shows on screen where the Beeheemoth has spawned — it appears at a random point on the map.",
						"Показывает на экране, где заспавнился Beeheemoth: он появляется в случайной точке карты."),
				() -> RynConfig.flag("torrhus.beeheemoth", true), v -> RynConfig.setFlag("torrhus.beeheemoth", v),
				Announce.BEEHEEMOTH));
		torrhus.add(new Header("Tiki helper", ""));
		torrhus.add(toggle("Tiki helper",
				Lang.tr("Helps with the totems of three heads in Torrhus Canyon and Torrhus Heights.",
						"Помогает с тотемами из трёх голов в Torrhus Canyon и Torrhus Heights."),
				() -> RynConfig.flag("tiki.on", true), v -> RynConfig.setFlag("tiki.on", v)));
		torrhus.add(toggle(Lang.tr("What to hit", "Что бить"),
				Lang.tr("Shows which head to hit and how many times to wake the mob.",
						"Показывает, по какой голове бить и сколько раз, чтобы заспавнить моба."),
				() -> RynConfig.flag("tiki.hint", true), v -> RynConfig.setFlag("tiki.hint", v)));
		addHighlight(torrhus, com.ryn.skyryn.waypoint.MobHighlight.TORRHUS);
		forSubs.add(new SubCat("Torrhus Canyon", torrhus));

		List<Opt> galatea = new ArrayList<>();
		addHighlight(galatea, com.ryn.skyryn.waypoint.MobHighlight.GALATEA);
		forSubs.add(new SubCat("Galatea", galatea));

		List<Opt> safTop = new ArrayList<>();
		safTop.add(new Header("Solo mode",
				Lang.tr("Single-player announces and local mod commands.",
						"Одиночные анонсы и локальные мод-команды.")));
		safTop.add(ann("Solo mode",
				Lang.tr("Single-player announces and local mod commands.",
						"Одиночные анонсы и локальные мод-команды."),
				() -> RynConfig.safariSolo, v -> RynConfig.safariSolo = v, Announce.SAFARI));
		addMessageOpts(safTop, "msg.", true);

		safTop.add(new Header("Party mode",
				Lang.tr("Party commands and announces.", "Пати команды и анонсы")));
		safTop.add(toggle(Lang.tr("Answer party commands", "Отвечать на команды пати"),
				Lang.tr("Answers with your stats when someone types a # command in party chat.",
						"Отвечает статистикой, когда кто-то пишет команду с решёткой в пати-чат."),
				() -> RynConfig.safariParty, v -> RynConfig.safariParty = v));
		addMessageOpts(safTop, "pmsg.", false);
		safTop.add(button(Lang.tr("Party commands", "Команды пати"),
				Lang.tr("Prints every command and what it answers into your chat.",
						"Печатает в твой чат все команды и что они отвечают."),
				Lang.tr("Show the commands", "Посмотреть команды"),
				() -> com.ryn.skyryn.hud.SafariTracker.printCommands()));
		safTop.add(new Header("Dupe alarm",
				Lang.tr("Warns about doubling up in biomes.", "Предупреждает о дюпе в биомах.")));
		safTop.add(toggle(Lang.tr("Dupe announce", "Dupe анонс"),
				Lang.tr("Warns when two or more players are in one biome.",
						"Предупреждает, если в биоме находится 2 и более игроков."),
				() -> RynConfig.dupeWarn, v -> RynConfig.dupeWarn = v));
		safTop.add(toggle("Haunted", "", () -> RynConfig.flag("dupe.haunted", true), v -> RynConfig.setFlag("dupe.haunted", v)));
		safTop.add(toggle("Icy", "", () -> RynConfig.flag("dupe.icy", true), v -> RynConfig.setFlag("dupe.icy", v)));
		safTop.add(toggle("Cavern", "", () -> RynConfig.flag("dupe.cavern", true), v -> RynConfig.setFlag("dupe.cavern", v)));
		safTop.add(toggle("Forest", "", () -> RynConfig.flag("dupe.forest", false), v -> RynConfig.setFlag("dupe.forest", v)));
		safTop.add(new Header("Sparkling", ""));
		safTop.add(toggle("Highlight Sparkling",
				Lang.tr("Highlights sparkling mobs.", "Подсвечивать Спарклинг мобов."),
				() -> RynConfig.flag("sparkling.hl", true), v -> RynConfig.setFlag("sparkling.hl", v)));
		safTop.add(ann("Announce",
				Lang.tr("An announce on screen and in party chat.", "Анонс на экране и в пати чате."),
				() -> RynConfig.flag("sparkling.ann", true), v -> RynConfig.setFlag("sparkling.ann", v),
				Announce.SPARKLING));

		safTop.add(new Header("Bells",
				Lang.tr("Seven bells are hidden around the safari. Find all seven, come back to «Hunter» Tobias — a cutscene and Miracle Chance levels.",
						"По сафари спрятаны семь колоколов. Найдёшь все семь и вернёшься к «Hunter» Tobias — катсцена и уровни Miracle Chance.")));
		safTop.add(ann(Lang.tr("Show the markers", "Показывать метки"),
				Lang.tr("A marker on every bell you have not rung yet. Ring one and its marker goes out by itself.",
						"Метка на каждом колоколе, в который ты ещё не звонил. Позвонил — метка гаснет сама."),
				() -> RynConfig.flag("bells.show", true), v -> RynConfig.setFlag("bells.show", v),
				Announce.BELL));
		String[] bellWhere = {
				Lang.tr("Icy: under the ice platform at the entrance, needs an Icebreaker", "Icy: под ледяной платформой у входа, нужен Icebreaker"),
				Lang.tr("Icy: behind the bed in the cavern under the lake", "Icy: за кроватью в пещере под озером"),
				Lang.tr("Haunted: the mansion roof", "Haunted: крыша особняка"),
				Lang.tr("Edge of the map by Haunted; climb back via the vine at 16 53 79", "Край карты у Haunted; обратно по лиане с 16 53 79"),
				Lang.tr("Forest: parkour along the big tree to a floating island", "Forest: паркур по большому дереву на островок"),
				Lang.tr("Cavern: climb from the fake cactus at -138 102 26", "Cavern: подъём от фальшивого кактуса с -138 102 26"),
				Lang.tr("Top of the landing zone, from the ice spike by the Icy entrance", "Верх зоны высадки, с ледяного шипа у входа в Icy"),
		};
		for (int i = 0; i < bellWhere.length; i++) {
			final int n = i;
			safTop.add(toggle(Lang.tr("Bell ", "Колокол ") + (i + 1) + Lang.tr(" — found", " — найден"), bellWhere[i],
					() -> RynConfig.flag("bell." + n, false), v -> RynConfig.setFlag("bell." + n, v)));
		}

		addHighlight(safTop, com.ryn.skyryn.waypoint.MobHighlight.SAFARI_NPC);

		List<Opt> trk = new ArrayList<>();
		trk.add(new Header(Lang.tr("Overlay", "Плашка"), ""));
		trk.add(ann(Lang.tr("Safari tracker", "Трекер сафари"),
				Lang.tr("Overlay with the stats of the current run. The gear opens HUD editing — drag it where you want it.",
						"Плашка со статистикой текущего захода. Шестерёнка открывает правку HUD — перетащи её куда нужно."),
				() -> RynConfig.safariTracker, v -> RynConfig.safariTracker = v, "hud:safari"));
		trk.add(button(Lang.tr("Reset the tracker", "Сбросить трекер"),
				Lang.tr("Zeroes coins per hour, shards per hour and the rest. Your all-time totals stay.",
						"Обнуляет коины в час, шарды в час и остальное. Итоги за всё время остаются."),
				Lang.tr("Reset", "Сбросить"), () -> { com.ryn.skyryn.hud.SafariTracker.resetTracker(); }));
		trk.add(button(Lang.tr("Reset everything", "Сбросить всё"),
				Lang.tr("Wipes the tracker AND the all-time totals: profit, essence, shards, catches, exp, time. Click twice to confirm — there is no undo.",
						"Стирает и трекер, и итоги за всё время: профит, эссенцию, шарды, поимки, опыт, время. Нажми дважды для подтверждения — отката нет."),
				Lang.tr("Reset all", "Сбросить всё"), () -> { com.ryn.skyryn.hud.SafariTracker.resetEverythingConfirm(); }));
		trk.add(toggle(Lang.tr("Ticket", "Билет"), "", () -> RynConfig.flag("tr.ticket", true), v -> RynConfig.setFlag("tr.ticket", v)));
		trk.add(toggle(Lang.tr("Capsules", "Капсулы"), "", () -> RynConfig.flag("tr.capsules", true), v -> RynConfig.setFlag("tr.capsules", v)));
		trk.add(toggle(Lang.tr("Coins per hour", "Коины в час"), "", () -> RynConfig.flag("tr.profit", true), v -> RynConfig.setFlag("tr.profit", v)));
		trk.add(cycle(Lang.tr("Count the profit over", "Профит считать за"),
				Lang.tr("Session keeps adding up from run to run until you reset the tracker. Run counts the current visit only. Party commands: #pr for the session, #prr for the run.",
						"Сессия копится от захода к заходу, пока не сбросишь трекер. Заход считает только текущий. Команды пати: #pr — сессия, #prr — заход."),
				new String[]{ Lang.tr("Session", "Сессия"), Lang.tr("Run", "Заход"), Lang.tr("Both", "Оба") },
				() -> RynConfig.getInt("tr.profitScope", 0), i -> RynConfig.setInt("tr.profitScope", i)));
		trk.add(toggle(Lang.tr("Shards picked off the ground", "Шарды, поднятые с земли"), "", () -> RynConfig.flag("tr.floorshards", true), v -> RynConfig.setFlag("tr.floorshards", v)));
		trk.add(toggle("Sparkling",
				Lang.tr("How many sparkling critters you caught: this run and for all time.",
						"Сколько sparkling-криттеров поймал: за этот заход и за всё время."),
				() -> RynConfig.flag("tr.sparkling", true), v -> RynConfig.setFlag("tr.sparkling", v)));
		trk.add(toggle(Lang.tr("Essence", "Эссенция"), "", () -> RynConfig.flag("tr.essence", true), v -> RynConfig.setFlag("tr.essence", v)));
		trk.add(toggle(Lang.tr("Run time", "Время захода"), "", () -> RynConfig.flag("tr.time", true), v -> RynConfig.setFlag("tr.time", v)));
		trk.add(toggle("Hotspot", "", () -> RynConfig.flag("tr.hotspot", true), v -> RynConfig.setFlag("tr.hotspot", v)));
		trk.add(toggle(Lang.tr("Quest items", "Квест-предметы"), "", () -> RynConfig.flag("tr.quest", true), v -> RynConfig.setFlag("tr.quest", v)));
		trk.add(cycle(Lang.tr("Shards", "Шарды"),
				Lang.tr("Which catches to list: only yours, only the party ones, or both.",
						"Какие поимки перечислять: только свои, только с пати или все."),
				new String[]{ Lang.tr("Mine", "Свои"), Lang.tr("Party", "С пати"), Lang.tr("Mine and party", "Свои и с пати"), Lang.tr("Do not show", "Не показывать") },
				() -> RynConfig.getInt("tr.shards", 0), i -> RynConfig.setInt("tr.shards", i)));
		trk.add(new Header(Lang.tr("Coin count", "Подсчёт монет"), ""));
		trk.add(cycle(Lang.tr("Price to count by", "По какой цене считать"),
				Lang.tr("Sell offer is what you really get through an order. Instant sell is what you get right away.",
						"По ордеру — сколько реально получишь, если выставишь. Сразу — сколько дадут прямо сейчас."),
				new String[]{ Lang.tr("Sell offer", "По ордеру"), Lang.tr("Instant sell", "Сразу") },
				() -> RynConfig.safariSellOffer ? 0 : 1, i -> RynConfig.safariSellOffer = (i == 0)));
		trk.add(new Header(Lang.tr("Quest items", "Квест-предметы"), ""));
		trk.add(toggle(Lang.tr("Show where to use them", "Показывать, куда применить"),
				Lang.tr("While you hold a quest item, the mod marks the place it goes.",
						"Пока держишь квест-предмет, мод отмечает место, куда его нести."),
				() -> RynConfig.questHighlight, v -> RynConfig.questHighlight = v));

		String[] offAfterDef = { Lang.tr("Never", "Никогда"), Lang.tr("Until defeated", "Пока не победил"), Lang.tr("Always", "Всегда") };
		List<SubCat> safSubs = new ArrayList<>();
		safSubs.add(new SubCat(Lang.tr("Safari tracker", "Трекер сафари"), trk));

		List<Opt> hau = new ArrayList<>();
		hau.add(new Header(Lang.tr("Biome", "Биом"), ""));
		hau.add(locMarker("haunted"));
		hau.add(new Header(Lang.tr("Quest items", "Квест-предметы"),
				Lang.tr("While you hold the item, the mod marks the place it goes.",
						"Пока держишь предмет, мод отмечает место, куда его нести.")));
		hau.add(qh("coin", "Shining Coin", true));
		hau.add(qh("incense", "Soothing Incense", true));
		hau.add(qh("candle", Lang.tr("Candles", "Свечи"), true));
		hau.add(toggle(Lang.tr("Where Hideonwall can be", "Где может быть Hideonwall"),
				Lang.tr("Marks the spots it hides in.", "Отмечает места, в которых он прячется."),
				() -> RynConfig.flag("haunted.hwguess", false), v -> RynConfig.setFlag("haunted.hwguess", v)));
		addHighlight(hau, "Haunted");
		hau.add(new Header("Doomspiral", ""));
		hau.add(bossMode("doom", offAfterDef));
		hau.add(annToggle("doom", "summoned", Lang.tr("Tell when summoned", "Сообщать о призыве")));
		hau.add(annToggle("doom", "defeated", Lang.tr("Tell when defeated", "Сообщать о победе")));
		safSubs.add(new SubCat("Haunted", hau));

		List<Opt> ic = new ArrayList<>();
		ic.add(new Header(Lang.tr("Biome", "Биом"), ""));
		ic.add(locMarker("icy"));
		ic.add(new Header(Lang.tr("Quest items", "Квест-предметы"),
				Lang.tr("While you hold the item, the mod marks the place it goes.",
						"Пока держишь предмет, мод отмечает место, куда его нести.")));
		ic.add(qh("ice", "Icebreaker", true));
		addHighlight(ic, "Icy");
		ic.add(new Header("Wumpa", ""));
		ic.add(cycle(Lang.tr("List of mobs left", "Список оставшихся мобов"),
				Lang.tr("Under the overlay shows which icy mobs you have not caught yet.",
						"Под плашкой показывает, кого из ледяных мобов ты ещё не поймал."),
				new String[]{ Lang.tr("Always", "Всегда"), Lang.tr("Never", "Никогда"), Lang.tr("Only in the biome", "Только в биоме") },
				() -> RynConfig.getInt("wumpa.tracker", 0), i -> RynConfig.setInt("wumpa.tracker", i)));
		ic.add(bossMode("wumpa", offAfterDef));
		ic.add(annToggle("wumpa", "awoken", Lang.tr("Tell when it wakes up", "Сообщать о пробуждении")));
		ic.add(annToggle("wumpa", "defeated", Lang.tr("Tell when defeated", "Сообщать о победе")));
		safSubs.add(new SubCat("Icy", ic));

		List<Opt> cv = new ArrayList<>();
		cv.add(new Header(Lang.tr("Biome", "Биом"), ""));
		cv.add(locMarker("cavern"));
		cv.add(new Header(Lang.tr("Quest items", "Квест-предметы"),
				Lang.tr("While you hold the gem, the mod marks the slot it goes into.",
						"Пока держишь самоцвет, мод отмечает гнездо, куда его вставить.")));
		cv.add(qh("purple", "Purple Gem", true));
		cv.add(qh("orange", "Orange Gem", true));
		cv.add(qh("lime", "Lime Gem", true));
		addHighlight(cv, "Cavern");
		cv.add(new Header(Lang.tr("Gate", "Врата"), ""));
		cv.add(bossMode("gate", new String[]{ Lang.tr("Never", "Никогда"), Lang.tr("Until cleared", "Пока не зачистил"), Lang.tr("Always", "Всегда") }));
		cv.add(annToggle("gate", "open", Lang.tr("Tell when opened", "Сообщать об открытии")));
		cv.add(annToggle("gate", "cleared", Lang.tr("Tell when cleared", "Сообщать о зачистке")));
		safSubs.add(new SubCat("Cavern", cv));

		List<Opt> fo = new ArrayList<>();
		fo.add(new Header(Lang.tr("Biome", "Биом"), ""));
		fo.add(locMarker("forest"));
		fo.add(new Header(Lang.tr("Quest items", "Квест-предметы"),
				Lang.tr("While you hold the item, the mod marks the place it goes.",
						"Пока держишь предмет, мод отмечает место, куда его нести.")));
		fo.add(qh("yogi", "Yogi Berry", true));
		fo.add(qh("seeds", "Bag of Seeds", true));
		fo.add(qh("wriggle", "Wriggleworm", true));
		addHighlight(fo, "Forest");
		safSubs.add(new SubCat("Forest", fo));

		forSubs.add(new SubCat(SAFARI, safTop));
		forSubs.addAll(safSubs);
		for (SubCat s : safSubs) safariSubs.add(s.name());
		cats.add(new Category(Lang.tr("Foraging", "Форагинг"), forTop, forSubs));

		cats.add(cat(Lang.tr("Interface", "Интерфейс"),
				new Header(Lang.tr("General", "Общее"), ""),
				cycle("Language", "",
						new String[]{ "English", "Russian" },
						() -> RynConfig.isRu() ? 1 : 0, i -> { RynConfig.lang = i == 1 ? "ru" : "en"; ShardInfo.load(); rebuild = true; }),
				cycle(Lang.tr("Server texture pack", "Серверный ресурспак"),
						Lang.tr("Off — turns off custom item textures completely, including the server textures laid over vanilla items such as paper. Instead of the familiar Sublime Milk and the other items released in the Torrhus Canyon update and later, you will see paper.\n"
										+ "Hybrid — turns off custom item textures, but keeps the server textures laid over them.",
								"Off — полностью отключает кастомные текстуры предметов, но так же отключает и серверные текстуры кастомных предметов, наложенных на ванильные предметы, например на бумагу. Вместо привычной текстуры Sublime Milk и других предметов, вышедших в обновлении Torrhus Canyon и позже, вы будете видеть бумагу.\n"
										+ "Hybrid — отключает кастомные текстуры предметов, но оставляет наложенные на них серверные текстуры."),
						new String[]{ "Off", "Hybrid" },
						() -> RynConfig.packMode == RynConfig.PACK_OFF ? 0 : 1,
						i -> {
							RynConfig.packMode = i == 0 ? RynConfig.PACK_OFF : RynConfig.PACK_HYBRID;
							if (i == 0) com.ryn.skyryn.config.ServerPack.dropNow();
						})));

		List<Opt> keys = new ArrayList<>();
		keys.add(new Header(Lang.tr("Keys", "Клавиши"),
				Lang.tr("Click a row and press the key. Esc clears the bind.",
						"Нажми на строку и нажми клавишу. Esc снимает бинд.")));
		for (var en : com.ryn.skyryn.waypoint.Keybinds.all().entrySet())
			keys.add(new KeyOpt(en.getKey(), en.getValue()));
		cats.add(new Category(Lang.tr("Keys", "Клавиши"), keys, List.of()));

		if (catSel >= cats.size()) catSel = 0;
		if (!openAt.isBlank()) {
			for (int i = 0; i < CATEGORY_COMMANDS.length && i < cats.size(); i++) {
				if (CATEGORY_COMMANDS[i].equals(openAt)) { catSel = i; subSel = -1; break; }
			}
		}
	}

	private static Cycle locMarker(String key) {
		return cycle(Lang.tr("Marker of this biome", "Метка этого биома"),
				Lang.tr("Hidden while you are inside it.", "Прячется, пока ты внутри него."),
				new String[]{ Lang.tr("Hide", "Не показывать"), Lang.tr("Show", "Показывать") },
				() -> RynConfig.flag("bm." + key, true) ? 1 : 0, i -> RynConfig.setFlag("bm." + key, i == 1));
	}
	private static Toggle qh(String key, String label, boolean def) {
		final String fk = "qh." + key;
		return toggle(label, "", () -> RynConfig.flag(fk, def), v -> RynConfig.setFlag(fk, v));
	}
	private static void addMessageOpts(List<Opt> o, String p, boolean solo) {
		String to = solo ? Lang.tr("Shows you", "Показывает тебе") : Lang.tr("Sends to the party", "Отправляет в пати");
		o.add(toggle(Lang.tr("Message at the start", "Сообщение на входе"),
				to + Lang.tr(": how many capsules you have and whether the necklace is on.",
						": сколько у тебя капсул и надето ли ожерелье."),
				() -> RynConfig.flag(p + "entry", true), v -> RynConfig.setFlag(p + "entry", v)));
		o.add(toggle(Lang.tr("Message at the end", "Сообщение на выходе"),
				to + Lang.tr(": run summary — capsules, essence, catches and time.",
						": итог захода — капсулы, эссенция, поимки и время."),
				() -> RynConfig.flag(p + "exit", true), v -> RynConfig.setFlag(p + "exit", v)));
		o.add(toggle(Lang.tr("Biome you entered", "Куда ты зашёл"),
				to + Lang.tr(": when you cross into another biome.", ": когда ты переходишь в другой биом."),
				() -> RynConfig.flag(p + "biome", true), v -> RynConfig.setFlag(p + "biome", v)));
		o.add(toggle(Lang.tr("Times in messages", "Время в сообщениях"),
				Lang.tr("Adds how long it took to the Wumpa, Doomspiral and gate messages.",
						"Добавляет к сообщениям про Wumpa, Doomspiral и врата, за сколько ты справился."),
				() -> RynConfig.flag(p + "times", true), v -> RynConfig.setFlag(p + "times", v)));
		o.add(toggle(Lang.tr("Personal best", "Личный рекорд"),
				Lang.tr("Marks a message when you beat your record. The record itself is kept either way.",
						"Отмечает в сообщении, что ты побил рекорд. Сам рекорд ведётся в любом случае."),
				() -> RynConfig.flag(p + "pb", true), v -> RynConfig.setFlag(p + "pb", v)));
	}

	private static void addHighlight(List<Opt> o, String group) {
		o.add(new Header(Lang.tr("Highlight mobs", "Подсветка мобов"),
				Lang.tr("Works when the highlight is on in the Highlight section.",
						"Работает, когда включена подсветка в разделе «Подсветка».")));
		for (var d : com.ryn.skyryn.waypoint.MobHighlight.MOBS)
			if (d.group().equalsIgnoreCase(group)) o.add(mobOpt(d.label().replaceAll("§.", ""), d.key(), d.color()));
	}
	private static Cycle bossMode(String key, String[] opts) {
		return cycle(Lang.tr("Show the marker", "Показывать метку"),
				Lang.tr("Where to look for it on the map.", "Где его искать на карте."),
				opts, () -> RynConfig.getInt(key + ".mode", 2), i -> RynConfig.setInt(key + ".mode", i));
	}
	private static Toggle annToggle(String key, String event, String label) {
		final String fk = key + ".ann." + event;
		return toggle(label, "", () -> RynConfig.flag(fk, true), v -> RynConfig.setFlag(fk, v));
	}

	private static int cyclePalette(int cur, int dir) {
		int cur24 = cur & 0xFFFFFF, idx = 0;
		for (int i = 0; i < PALETTE.length; i++) if ((PALETTE[i] & 0xFFFFFF) == cur24) { idx = i; break; }
		return PALETTE[((idx + dir) % PALETTE.length + PALETTE.length) % PALETTE.length];
	}

	private static Category cat(String name, Opt... opts) { return new Category(name, List.of(opts), List.of()); }
	private static Toggle toggle(String t, String d, BooleanSupplier g, Consumer<Boolean> s) { return new Toggle(t, d, g, s); }
	private static Slider slider(String t, String d, double mn, double mx, double st, boolean f, DoubleSupplier g, DoubleConsumer s) { return new Slider(t, d, mn, mx, st, f, g, s); }
	private static Btn button(String t, String d, String bt, Runnable a) { return new Btn(t, d, bt, a); }

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		if (rebuild) { rebuild = false; buildCats(); }
		hits.clear(); catRects.clear(); subRects.clear();
		ctx.fill(0, 0, this.width, this.height, SCREEN_BG);

		int topY = OUTER;
		int sideX = OUTER;
		contentX = OUTER + SIDEBAR_W + GAP;
		contentW = this.width - contentX - OUTER;
		bodyY = topY + TOP_H + GAP;
		bodyBottom = this.height - OUTER;

		panel(ctx, sideX, topY, sideX + SIDEBAR_W, topY + TOP_H, PANEL);
		int cbs = 40, cbx = sideX + 7, cby = topY + (TOP_H - cbs) / 2;
		boolean closeHover = in(mouseX, mouseY, cbx, cby, cbx + cbs, cby + cbs);
		panel(ctx, cbx, cby, cbx + cbs, cby + cbs, closeHover ? ROW_HOVER : SEG_OFF);
		ctx.text(this.font, "✕", cbx + cbs / 2 - 3, cby + cbs / 2 - 4, closeHover ? TITLE : CAT, true);
		closeRect = new int[]{cbx, cby, cbx + cbs, cby + cbs};
		int sfx = cbx + cbs + 8, sfy = topY + (TOP_H - 20) / 2;
		panel(ctx, sfx, sfy, sideX + SIDEBAR_W - 8, sfy + 20, searchFocused ? PANEL_HI : SEG_OFF);
		if (searchFocused) outline(ctx, sfx, sfy, sideX + SIDEBAR_W - 8, sfy + 20, ACCENT);
		String shown = search.isEmpty() && !searchFocused ? Lang.tr("Search...", "Поиск...") : search + (searchFocused && blink() ? "_" : "");
		ctx.text(this.font, shown, sfx + 7, sfy + 6, search.isEmpty() && !searchFocused ? FAINT : TITLE, true);
		searchRect = new int[]{sfx, sfy, sideX + SIDEBAR_W - 8, sfy + 20};

		panel(ctx, contentX, topY, contentX + contentW, topY + TOP_H, PANEL);
		ctx.text(this.font, "Sky", contentX + 14, topY + 12, 0xFFFFD24A, true);
		ctx.text(this.font, "Ryn", contentX + 14 + this.font.width("Sky"), topY + 12, ACCENT, true);
		ctx.text(this.font, Lang.tr("Settings v1.0", "Настройки v1.0"), contentX + 14, topY + 30, DESC, true);

		panel(ctx, sideX, bodyY, sideX + SIDEBAR_W, bodyBottom, PANEL);
		int cy = bodyY + 8;
		for (int i = 0; i < cats.size(); i++) {
			int rh = 24;
			boolean sel = i == catSel && search.isEmpty();
			boolean hov = in(mouseX, mouseY, sideX + 6, cy, sideX + SIDEBAR_W - 6, cy + rh);
			if (sel || hov) panel(ctx, sideX + 6, cy, sideX + SIDEBAR_W - 6, cy + rh, sel ? CAT_SEL : ROW_HOVER);
			if (sel) ctx.fill(sideX + 6, cy + 4, sideX + 8, cy + rh - 4, ACCENT);
			String nm = cats.get(i).name();
			ctx.text(this.font, nm, sideX + (SIDEBAR_W - this.font.width(nm)) / 2, cy + 8, sel ? TITLE : CAT, true);
			catRects.add(new int[]{sideX + 6, cy, sideX + SIDEBAR_W - 6, cy + rh});
			cy += rh + 2;
			if (sel && !cats.get(i).subs().isEmpty()) {
				List<SubCat> subs = cats.get(i).subs();
				for (int j = 0; j < subs.size(); j++) {
					if (isSafariSub(subs.get(j).name()) && !safariOpen(subs)) continue;
					int srh = 20;
					boolean ssel = j == subSel;
					boolean shov = in(mouseX, mouseY, sideX + 18, cy, sideX + SIDEBAR_W - 6, cy + srh);
					if (ssel || shov) panel(ctx, sideX + 18, cy, sideX + SIDEBAR_W - 6, cy + srh, ssel ? CAT_SEL : ROW_HOVER);
					ctx.text(this.font, subs.get(j).name(), sideX + 28, cy + 6, ssel ? TITLE : CAT, true);
					subRects.add(new int[]{ j, sideX + 18, cy, sideX + SIDEBAR_W - 6, cy + srh });
					cy += srh + 1;
				}
			}
		}

		panel(ctx, contentX, bodyY, contentX + contentW, bodyBottom, PANEL);
		List<Opt> list = visibleOpts();
		int innerX = contentX + 14, innerR = contentX + contentW - 14;
		int y = bodyY + 10 - scrollY;
		tipText = null;
		for (Opt o : list) {
			int rh = rowHeight(o);
			if (y + rh > bodyY && y < bodyBottom) drawRow(ctx, o, innerX, innerR, y, rh, mouseX, mouseY);
			y += rh;
		}
		contentH = (y + scrollY) - (bodyY + 10);
		int visH = bodyBottom - bodyY - 14;
		int maxScroll = Math.max(0, contentH - visH);
		scrollY = Math.max(0, Math.min(scrollY, maxScroll));
		if (maxScroll > 0) {
			int th = Math.max(20, (int) ((long) visH * visH / contentH));
			int ty = bodyY + 8 + (int) ((long) (visH - th) * scrollY / maxScroll);
			int sx = contentX + contentW - 4;
			ctx.fill(sx, bodyY + 8, sx + 2, bodyBottom - 8, 0xFF101014);
			ctx.fill(sx, ty, sx + 2, ty + th, SEG_ON);
		}

		boolean down = GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (activeSlider != null) {
			if (down) applySlider(activeSlider, mouseX);
			else { activeSlider = null; save(); }
		}

		drawTip(ctx);
	}

	private void drawTip(GuiGraphicsExtractor ctx) {
		if (tipText == null || tipText.isEmpty()) return;
		int maxW = Math.min(260, this.width - 24);
		List<String> lines = wrap(tipText, maxW);
		int w = 0;
		for (String ln : lines) w = Math.max(w, this.font.width(ln));
		int h = lines.size() * 10 + 8;
		w += 12;
		int x = tipX + 12, y = tipY + 14;
		if (x + w > this.width - 4) x = Math.max(4, tipX - 12 - w);
		if (y + h > this.height - 4) y = Math.max(4, tipY - 14 - h);
		panel(ctx, x, y, x + w, y + h, PANEL_HI);
		outline(ctx, x, y, x + w, y + h, BORDER);
		int ty = y + 5;
		for (String ln : lines) { ctx.text(this.font, ln, x + 6, ty, DESC, true); ty += 10; }
	}

	private List<Opt> visibleOpts() {
		if (search.isEmpty()) {
			Category c = cats.get(catSel);
			List<Opt> base = (subSel >= 0 && subSel < c.subs().size()) ? c.subs().get(subSel).opts() : c.opts();
			List<Opt> out = new ArrayList<>();
			boolean skip = false;
			for (Opt o : base) {
				if (o instanceof Header h) { skip = isCollapsed(h.title()); out.add(o); }
				else if (!skip) out.add(o);
			}
			return out;
		}
		String q = search.toLowerCase();
		List<Opt> out = new ArrayList<>();
		for (Category c : cats) {
			collect(out, c.opts(), q);
			for (SubCat s : c.subs()) collect(out, s.opts(), q);
		}
		return out;
	}

	private void collect(List<Opt> out, List<Opt> src, String q) {
		for (Opt o : src) {
			if (o instanceof Header) continue;
			if (title(o).toLowerCase().contains(q) || desc(o).toLowerCase().contains(q)) out.add(o);
		}
	}

	private final java.util.Set<String> safariSubs = new java.util.LinkedHashSet<>();
	private static final String SAFARI = "Critter Safari";

	private boolean isSafariSub(String name) { return safariSubs.contains(name); }

	private boolean safariExpanded = false;

	private boolean safariOpen(List<SubCat> subs) {
		if (subSel >= 0 && subSel < subs.size() && isSafariSub(subs.get(subSel).name())) return true;
		return safariExpanded;
	}

	private void goToSub(String subName) {
		for (int i = 0; i < cats.size(); i++) {
			List<SubCat> subs = cats.get(i).subs();
			for (int j = 0; j < subs.size(); j++) {
				if (!subs.get(j).name().equals(subName)) continue;
				catSel = i; subSel = j; scrollY = 0; search = "";
				if (subs.get(j).name().equals(SAFARI) || isSafariSub(subName)) safariExpanded = true;
				return;
			}
		}
	}

	private int rowHeight(Opt o) {
		if (o instanceof Header) return 22;
		return ROW_H;
	}

	private void drawRow(GuiGraphicsExtractor ctx, Opt o, int x1, int x2, int y, int rh, int mouseX, int mouseY) {
		if (o instanceof Header h) {
			boolean col = isCollapsed(h.title());
			boolean hov = in(mouseX, mouseY, x1 - 6, y, x2 + 6, y + rh) && mouseY >= bodyY && mouseY <= bodyBottom;
			ctx.text(this.font, (col ? "▸ " : "▾ ") + h.title().toUpperCase(), x1, y + 4, TITLE, true);
			if (hov && !h.desc().isEmpty()) { tipText = h.desc(); tipX = mouseX; tipY = mouseY; }
			ctx.fill(x1, y + rh - 4, x2, y + rh - 3, BORDER);
			hits.add(new Hit(o, x1 - 6, y, x2 + 6, y + rh));
			return;
		}
		boolean hover = in(mouseX, mouseY, x1 - 6, y, x2 + 6, y + rh) && mouseY >= bodyY && mouseY <= bodyBottom;
		if (hover) ctx.fill(x1 - 6, y, x2 + 6, y + rh - 1, ROW_HOVER);

		ctx.text(this.font, title(o), x1, y + (rh - 8) / 2, TITLE, true);
		if (hover && !desc(o).isEmpty()) { tipText = desc(o); tipX = mouseX; tipY = mouseY; }

		Hit hit = new Hit(o, x1 - 6, y, x2 + 6, y + rh);
		int cyMid = y + rh / 2;
		switch (o) {
			case Toggle t -> {
				boolean on = t.get().getAsBoolean();
				String off = Lang.tr("Off", "Выкл"), onL = Lang.tr("On", "Вкл");
				int offW = this.font.width(off) + 14, onW = this.font.width(onL) + 14, h = 16;
				int gx2 = x2, gy1 = cyMid - h / 2;
				int onx1 = gx2 - onW, offx1 = onx1 - offW;
				panel(ctx, offx1, gy1, offx1 + offW, gy1 + h, !on ? SEG_ON : SEG_OFF);
				panel(ctx, onx1, gy1, onx1 + onW, gy1 + h, on ? SEG_ON : SEG_OFF);
				ctx.text(this.font, off, offx1 + 7, gy1 + 4, !on ? TITLE : FAINT, true);
				ctx.text(this.font, onL, onx1 + 7, gy1 + 4, on ? TITLE : FAINT, true);
				hit.offX1 = offx1; hit.offX2 = offx1 + offW; hit.onX1 = onx1; hit.onX2 = onx1 + onW;
			}
			case AnnOpt t -> {
				boolean on = t.get().getAsBoolean();
				String off = Lang.tr("Off", "Выкл"), onL = Lang.tr("On", "Вкл");
				int offW = this.font.width(off) + 14, onW = this.font.width(onL) + 14, h = 16;
				int gy1 = cyMid - h / 2;
				int onx1 = x2 - onW, offx1 = onx1 - offW;
				panel(ctx, offx1, gy1, offx1 + offW, gy1 + h, !on ? SEG_ON : SEG_OFF);
				panel(ctx, onx1, gy1, onx1 + onW, gy1 + h, on ? SEG_ON : SEG_OFF);
				ctx.text(this.font, off, offx1 + 7, gy1 + 4, !on ? TITLE : FAINT, true);
				ctx.text(this.font, onL, onx1 + 7, gy1 + 4, on ? TITLE : FAINT, true);
				String gear = "⚙";
				int gw = Math.max(18, this.font.width(gear) + 10), gx = offx1 - 8 - gw;
				boolean ghov = in(mouseX, mouseY, gx, gy1, gx + gw, gy1 + h);
				panel(ctx, gx, gy1, gx + gw, gy1 + h, ghov ? SEG_ON : SEG_OFF);
				ctx.text(this.font, gear, gx + (gw - this.font.width(gear)) / 2, gy1 + 4, ghov ? TITLE : CAT, true);
				hit.offX1 = offx1; hit.offX2 = offx1 + offW; hit.onX1 = onx1; hit.onX2 = onx1 + onW;
				hit.btnX1 = gx; hit.btnX2 = gx + gw;
			}
			case Slider s -> {
				double v = s.get().getAsDouble();
				String val = s.flt() ? String.format(java.util.Locale.US, "%.2f", v) : String.valueOf((int) Math.round(v));
				int valW = Math.max(this.font.width(val), 24);
				ctx.text(this.font, val, x2 - valW + (valW - this.font.width(val)), cyMid - 4, TITLE, true);
				int t2 = x2 - valW - 8, t1 = t2 - 110;
				hit.trackX1 = t1; hit.trackX2 = t2;
				int midY = cyMid - 1;
				panel(ctx, t1, midY - 1, t2, midY + 3, SEG_OFF);
				double frac = (s.max() - s.min()) <= 0 ? 0 : (v - s.min()) / (s.max() - s.min());
				int kx = t1 + (int) (frac * (t2 - t1));
				ctx.fill(t1, midY, kx, midY + 2, ACCENT);
				ctx.fill(kx - 2, cyMid - 5, kx + 2, cyMid + 5, ACCENT);
			}
			case Btn b -> {
				int bw = this.font.width(b.text()) + 18, h = 16, bx = x2 - bw, by = cyMid - h / 2;
				boolean bhov = in(mouseX, mouseY, bx, by, bx + bw, by + h);
				panel(ctx, bx, by, bx + bw, by + h, bhov ? SEG_ON : SEG_OFF);
				ctx.text(this.font, b.text(), bx + 9, by + 4, bhov ? TITLE : CAT, true);
				hit.btnX1 = bx; hit.btnX2 = bx + bw;
			}
			case ColorOpt c -> {
				int sw = 30, h = 16, sx = x2 - sw, sy = cyMid - h / 2;
				ctx.fill(sx - 1, sy - 1, sx + sw + 1, sy + h + 1, BORDER);
				ctx.fill(sx, sy, sx + sw, sy + h, 0xFF000000 | (c.get().getAsInt() & 0xFFFFFF));
				hit.btnX1 = sx; hit.btnX2 = sx + sw;
			}
			case MobOpt m -> {
				boolean on = RynConfig.hasHighlightMob(m.mobKey());
				String off = Lang.tr("Off", "Выкл"), onL = Lang.tr("On", "Вкл");
				int offW = this.font.width(off) + 14, onW = this.font.width(onL) + 14, h = 16, gy1 = cyMid - h / 2;
				int onx1 = x2 - onW, offx1 = onx1 - offW;
				panel(ctx, offx1, gy1, offx1 + offW, gy1 + h, !on ? SEG_ON : SEG_OFF);
				panel(ctx, onx1, gy1, onx1 + onW, gy1 + h, on ? SEG_ON : SEG_OFF);
				ctx.text(this.font, off, offx1 + 7, gy1 + 4, !on ? TITLE : FAINT, true);
				ctx.text(this.font, onL, onx1 + 7, gy1 + 4, on ? TITLE : FAINT, true);
				int swc = 26, sx = offx1 - 8 - swc;
				ctx.fill(sx - 1, gy1 - 1, sx + swc + 1, gy1 + h + 1, BORDER);
				ctx.fill(sx, gy1, sx + swc, gy1 + h, 0xFF000000 | (RynConfig.color(m.colorKey(), m.defColor()) & 0xFFFFFF));
				hit.offX1 = offx1; hit.offX2 = offx1 + offW; hit.onX1 = onx1; hit.onX2 = onx1 + onW;
				hit.btnX1 = sx; hit.btnX2 = sx + swc;
			}
			case KeyOpt k -> {
				boolean wait = listening == k;
				String txt = wait ? "> ? <"
						: k.key().isUnbound() ? Lang.tr("Not bound", "Не назначено")
						: k.key().getTranslatedKeyMessage().getString();
				int bw = Math.max(96, this.font.width(txt) + 20), h = 16, bx = x2 - bw, by = cyMid - h / 2;
				panel(ctx, bx, by, bx + bw, by + h, wait ? SEG_ON : SEG_OFF);
				ctx.text(this.font, txt, bx + (bw - this.font.width(txt)) / 2, by + 4, wait ? ACCENT : TITLE, true);
				hit.btnX1 = bx; hit.btnX2 = bx + bw;
			}
			case Cycle cyc -> {
				String cur = cyc.options()[Math.floorMod(cyc.get().getAsInt(), cyc.options().length)];
				int bw = Math.max(96, this.font.width(cur) + 20), h = 16, bx = x2 - bw, by = cyMid - h / 2;
				boolean bhov = in(mouseX, mouseY, bx, by, bx + bw, by + h);
				panel(ctx, bx, by, bx + bw, by + h, bhov ? SEG_ON : SEG_OFF);
				ctx.text(this.font, cur, bx + (bw - this.font.width(cur)) / 2, by + 4, bhov ? TITLE : TITLE, true);
				hit.btnX1 = bx; hit.btnX2 = bx + bw;
			}
			default -> { }
		}
		ctx.fill(x1, y + rh - 1, x2, y + rh, 0xFF202026);
		hits.add(hit);
	}

	private String title(Opt o) {
		return switch (o) { case Header h -> h.title(); case Note n -> n.title(); case KeyOpt k -> k.title(); case Toggle t -> t.title(); case AnnOpt t -> t.title(); case Slider s -> s.title(); case Btn b -> b.title(); case ColorOpt c -> c.title(); case Cycle cy -> cy.title(); case MobOpt m -> m.name(); };
	}
	private String desc(Opt o) {
		return switch (o) { case Header h -> h.desc(); case Note n -> n.desc(); case KeyOpt k -> ""; case Toggle t -> t.desc(); case AnnOpt t -> t.desc(); case Slider s -> s.desc(); case Btn b -> b.desc(); case ColorOpt c -> c.desc(); case Cycle cy -> cy.desc(); case MobOpt m -> ""; };
	}

	private List<String> wrap(String s, int maxW) {
		List<String> out = new ArrayList<>();
		if (s == null || s.isEmpty() || maxW < 20) { if (s != null && !s.isEmpty()) out.add(s); return out; }
		if (s.indexOf('\n') >= 0) {
			for (String part : s.split("\n")) out.addAll(wrap(part, maxW));
			return out;
		}
		StringBuilder line = new StringBuilder();
		for (String w : s.split(" ")) {
			String test = line.length() == 0 ? w : line + " " + w;
			if (this.font.width(test) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(w); }
			else line = new StringBuilder(test);
		}
		if (line.length() > 0) out.add(line.toString());
		return out;
	}

	private void applySlider(Hit r, int mouseX) {
		if (!(r.opt instanceof Slider s) || r.trackX2 <= r.trackX1) return;
		double frac = Math.max(0, Math.min(1, (mouseX - r.trackX1) / (double) (r.trackX2 - r.trackX1)));
		double raw = s.min() + frac * (s.max() - s.min());
		double stepped = s.min() + Math.round((raw - s.min()) / s.step()) * s.step();
		s.set().accept(Math.max(s.min(), Math.min(s.max(), stepped)));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		int mx = (int) event.x(), my = (int) event.y();

		if (closeRect != null && in(mx, my, closeRect[0], closeRect[1], closeRect[2], closeRect[3])) { onClose(); return true; }
		if (searchRect != null && in(mx, my, searchRect[0], searchRect[1], searchRect[2], searchRect[3])) { searchFocused = true; return true; }
		searchFocused = false;

		for (int[] r : subRects) {
			if (!in(mx, my, r[1], r[2], r[3], r[4])) continue;
			List<SubCat> subs = cats.get(catSel).subs();
			if (r[0] < subs.size() && subs.get(r[0]).name().equals(SAFARI))
				safariExpanded = !(subSel == r[0] && safariExpanded);
			subSel = r[0]; scrollY = 0;
			return true;
		}
		for (int i = 0; i < catRects.size(); i++) {
			int[] r = catRects.get(i);
			if (in(mx, my, r[0], r[1], r[2], r[3])) { catSel = i; subSel = -1; safariExpanded = false; scrollY = 0; search = ""; return true; }
		}

		if (my >= bodyY && my <= bodyBottom) {
			for (Hit h : hits) {
				if (!in(mx, my, h.x1, h.y1, h.x2, h.y2)) continue;
				switch (h.opt) {
					case Toggle t -> {
						if (in(mx, my, h.offX1, h.y1, h.offX2, h.y2)) { t.set().accept(false); save(); }
						else if (in(mx, my, h.onX1, h.y1, h.onX2, h.y2)) { t.set().accept(true); save(); }
						else { t.set().accept(!t.get().getAsBoolean()); save(); }
					}
					case AnnOpt t -> {
						if (in(mx, my, h.btnX1, h.y1, h.btnX2, h.y2)) {
							save();
							this.minecraft.setScreen(t.annId().startsWith("hud:")
									? new HudEditScreen()
									: new AnnounceEditScreen(this, t.annId()));
						}
						else if (in(mx, my, h.offX1, h.y1, h.offX2, h.y2)) { t.set().accept(false); save(); }
						else if (in(mx, my, h.onX1, h.y1, h.onX2, h.y2)) { t.set().accept(true); save(); }
						else { t.set().accept(!t.get().getAsBoolean()); save(); }
					}
					case Slider s -> { if (in(mx, my, h.trackX1 - 6, h.y1, h.trackX2 + 6, h.y2)) { activeSlider = h; applySlider(h, mx); } }
					case Btn b -> b.action().run();
					case Header hh -> { String k = sectionKey(hh.title()); if (!expanded.remove(k)) expanded.add(k); }
					case ColorOpt c -> { c.set().accept(cyclePalette(c.get().getAsInt(), event.button() == 1 ? -1 : 1)); save(); }
					case Cycle cyc -> { int n = cyc.options().length; int nx = event.button() == 1 ? cyc.get().getAsInt() - 1 : cyc.get().getAsInt() + 1; cyc.set().accept(Math.floorMod(nx, n)); save(); }
					case MobOpt m -> {
						if (in(mx, my, h.btnX1, h.y1, h.btnX2, h.y2)) RynConfig.setColor(m.colorKey(), cyclePalette(RynConfig.color(m.colorKey(), m.defColor()), event.button() == 1 ? -1 : 1));
						else if (in(mx, my, h.offX1, h.y1, h.offX2, h.y2)) RynConfig.setHighlightMob(m.mobKey(), false);
						else if (in(mx, my, h.onX1, h.y1, h.onX2, h.y2)) RynConfig.setHighlightMob(m.mobKey(), true);
						else RynConfig.setHighlightMob(m.mobKey(), !RynConfig.hasHighlightMob(m.mobKey()));
						save();
					}
					default -> { }
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		if (mouseX >= contentX) { scrollY -= (int) Math.signum(dy) * 22; if (scrollY < 0) scrollY = 0; }
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!searchFocused) return super.charTyped(event);
		search += Character.toString(event.codepoint());
		scrollY = 0;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchFocused) {
			int k = event.key();
			if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_ENTER) { searchFocused = false; return true; }
			if (k == GLFW.GLFW_KEY_BACKSPACE) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); scrollY = 0; return true; }
			return true;
		}
		if (listening != null) {
			var k = event.key() == GLFW.GLFW_KEY_ESCAPE
					? com.mojang.blaze3d.platform.InputConstants.UNKNOWN
					: com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(event.key());
			listening.key().setKey(k);
			net.minecraft.client.KeyMapping.resetMapping();
			this.minecraft.options.save();
			listening = null;
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
		return super.keyPressed(event);
	}

	private void save() { ConfigManager.save(); }

	@Override
	public void onClose() { save(); this.minecraft.setScreen(parent); }

	@Override
	public boolean isPauseScreen() { return false; }

	private static void panel(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int col) {
		ctx.fill(x1 + 2, y1, x2 - 2, y2, col);
		ctx.fill(x1, y1 + 2, x2, y2 - 2, col);
		ctx.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, col);
	}
	private static void outline(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int col) {
		ctx.fill(x1 + 1, y1, x2 - 1, y1 + 1, col);
		ctx.fill(x1 + 1, y2 - 1, x2 - 1, y2, col);
		ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, col);
		ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, col);
	}
	private static boolean blink() { return (System.currentTimeMillis() / 500) % 2 == 0; }
	static boolean in(int mx, int my, int x1, int y1, int x2, int y2) { return mx >= x1 && mx <= x2 && my >= y1 && my <= y2; }
}
