package com.ryn.skyryn.screen;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import static com.ryn.skyryn.screen.ShardListScreen.*;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.AttributeBuffs;
import com.ryn.skyryn.data.AttributeLevels;
import com.ryn.skyryn.data.BestiaryDb;
import com.ryn.skyryn.data.LocationDb;
import com.ryn.skyryn.data.RichText;
import com.ryn.skyryn.data.SeaGuideDb;
import com.ryn.skyryn.data.ShardAttribute;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.data.ShardInfo;
import com.ryn.skyryn.data.ShardProgress;
import com.ryn.skyryn.fusion.BazaarPrices;
import com.ryn.skyryn.fusion.FusionCalculator;
import com.ryn.skyryn.fusion.FusionState;
import com.ryn.skyryn.waypoint.NavGraph;
import com.ryn.skyryn.waypoint.SkyBlockCheck;
import com.ryn.skyryn.waypoint.Waypoints;

/**
 * Страница одного шарда — заготовка под полноценный гайд.
 *
 * Стандартное описание видно всегда, остальное свёрнуто: иначе страница
 * превращается в простыню. Картинки и видео открываются в браузере — держать
 * сотни изображений в jar нельзя, он раздуется до десятков мегабайт.
 */
public class ShardPageScreen extends Screen {

	private static final int WIDTH = 420;
	private static final int GOLD = 0xFFFFD24A;

	private final String key;
	private final Screen parent;

	/** Что развёрнуто. По умолчанию всё свёрнуто: аттрибут виден и так. */
	private boolean openAttribute = false;
	private boolean openMedia = false;
	private boolean openHowTo = false;
	/** Индексы развёрнутых методов внутри OBTAIN METHODS — сворачиваются по клику на заголовок. */
	private final java.util.Set<Integer> openMethods = new java.util.HashSet<>();
	private int scroll = 0;

	/** Кликзоны кадра: y1,y2 -> действие. */
	private final List<Zone> zones = new ArrayList<>();
	private int contentH = 0;

	/** Подсказка под курсором. Рисуем последней — поверх всего остального. */
	private String[] tooltip = null;
	private int tooltipX, tooltipY;
	/** Голова моба над плашкой бестиария (base64-текстура) или null. */
	private String tooltipHead = null;

	/**
	 * Многостраничная плашка бестиария (напр. Minotaur — 4 тира): показываем одну
	 * страницу за раз, колесо листает. plaqueArg — чей это счётчик (сброс при смене
	 * ховера). hoverCmdArg/hoverCmdPages пишутся каждый кадр — их читает mouseScrolled.
	 */
	private int plaquePage = 0;
	private String plaqueArg = null;
	private String hoverCmdArg = null;
	private int hoverCmdPages = 0;

	private record Zone(int x1, int y1, int x2, int y2, Runnable action) { }

	public ShardPageScreen(String key, Screen parent) {
		super(Component.literal("SkyRyn — " + ShardDb.displayName(key)));
		this.key = key;
		this.parent = parent;
		com.ryn.skyryn.config.RynConfig.pushRecent(key); // для «последних просмотренных» в хабе
	}

	private int x() { return (this.width - WIDTH) / 2; }

	/** Уровень Hunting, нужный для syphon шарда этой редкости. */
	private static int huntingReq(String rarity) {
		if (rarity == null) return 0;
		return switch (rarity.toLowerCase()) {
			case "uncommon" -> 5;
			case "rare" -> 10;
			case "epic" -> 15;
			case "legendary" -> 20;
			default -> 0; // common
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		zones.clear();
		tooltip = null;
		tooltipHead = null;
		hoverCmdArg = null;
		hoverCmdPages = 0;
		ctx.fill(0, 0, this.width, this.height, BG);

		ShardDb.Shard s = ShardDb.shard(key);
		if (s == null) return;
		ShardInfo.Info info = ShardInfo.get(key);

		int x = x();
		int right = x + WIDTH;

		// Назад
		boolean backHover = in(mouseX, mouseY, x, 14, x + this.font.width(Lang.tr("← back", "← назад")), 26);
		ctx.text(this.font, Lang.tr("← back", "← назад"), x, 16, backHover ? ACCENT : TEXT_FAINT, true);
		zones.add(new Zone(x, 14, x + this.font.width(Lang.tr("← back", "← назад")), 26,
				() -> this.minecraft.setScreen(parent)));

		int y = 34 - scroll;

		// Заголовок
		ctx.text(this.font, s.name, x, y, rarityColor(s.rarity), true);
		String meta = s.rarity + (s.source != null ? " · " + s.source : "");
		ctx.text(this.font, meta, right - this.font.width(meta), y, TEXT_FAINT, true);
		y += 12;
		if (s.family != null && !s.family.isEmpty()) {
			ctx.text(this.font, s.family, x, y, TEXT_FAINT, true);
			y += 12;
		}
		ctx.fill(x, y, right, y + 1, BORDER);
		y += 8;

		// ===== Аттрибут =====
		// Что шард даёт — главное, зачем сюда заходят. Берём из данных: название
		// аттрибута и эффект от 1 до 10 уровня.
		int total = AttributeLevels.totalForMax(s.rarity);
		int lvl = ShardProgress.displayLevel(key);
		ctx.text(this.font, Lang.tr("ATTRIBUTE", "АТТРИБУТ"), x, y, TEXT_FAINT, true);
		// На максимуме про Lang.tr("how much more is needed", "сколько ещё надо") не пишем: надо ноль.
		String prog;
		if (!s.hasAttribute()) prog = Lang.tr("no levels — syphon not possible", "уровней нет — syphon невозможен");
		else if (lvl < 0) prog = Lang.tr("lvl ? · ", "ур. ? · ") + total + Lang.tr(" left to lvl 10", " шт до 10 ур.");
		else if (lvl >= ShardAttribute.MAX_LEVEL) prog = Lang.tr("lvl 10/10 · max", "ур. 10/10 · максимум");
		else prog = Lang.tr("lvl ", "ур. ") + lvl + "/10 · "
				+ AttributeLevels.nextLevelCost(s.rarity, lvl) + Lang.tr(" left to ", " шт до ") + (lvl + 1)
				+ Lang.tr(" lvl · ", " ур. · ") + AttributeLevels.toMax(s.rarity, lvl) + Lang.tr(" left to max", " шт до максимума");
		ctx.text(this.font, prog, right - this.font.width(prog), y,
				lvl < 0 || !s.hasAttribute() ? TEXT_FAINT : TEXT, true);
		y += 12;

		// Требуемый Hunting-уровень для syphon — зависит от редкости шарда.
		// У шардов без аттрибута syphon невозможен, там строка не нужна.
		if (s.hasAttribute()) {
			int req = huntingReq(s.rarity);
			String r = Lang.tr("Requires Hunting: ", "Требуется Hunting: ") + req;
			ctx.text(this.font, r, right - this.font.width(r), y, TEXT_FAINT, true);
			y += 12;
		}

		if (s.attrTitle != null && !s.attrTitle.isEmpty()) {
			// "I→X" только там, где уровни есть. У Chameleon их нет, и обещать
			// ему десять уровней — то же враньё, только мелким шрифтом.
			ctx.text(this.font, s.hasAttribute()
					? ShardAttribute.titleWithLevels(s.attrTitle) : s.attrTitle, x, y, GOLD, true);
			y += 11;
		}
		String attrDesc = s.attrDescShown();
		if (attrDesc != null && !attrDesc.isEmpty()) {
			double buff = AttributeBuffs.bonusFor(key);
			// 1) База: что аттрибут даёт с 1 по 10 уровень. Тускло, если у игрока
			//    есть уровень (тогда ниже своя строка «Ваш бонус»).
			y = drawWrapped(ctx, ShardAttribute.range(attrDesc), x, y, WIDTH,
					lvl > 0 ? TEXT_FAINT : TEXT);
			// 2) Ваш бонус: значение на ТЕКУЩЕМ уровне игрока, с усилением Echo.
			//    Уровень 1 -> бонус 1 уровня, уровень 10 -> бонус 10 уровня.
			if (lvl > 0) {
				String now = ShardAttribute.atLevel(attrDesc, lvl, buff);
				if (!now.isBlank()) {
					y = drawWrapped(ctx, Lang.tr("Your bonus: ", "Ваш бонус: ") + now, x, y, WIDTH, GREEN);
				}
			}
			// 3) Усилено: чем именно (только если усиление есть).
			if (buff > 0) {
				String who = AttributeBuffs.sourceFor(key);
				if (!who.isEmpty()) {
					ctx.text(this.font, Lang.tr("Boosted by: ", "Усилено: ") + who + "  (+"
							+ String.format("%.0f", buff * 100) + "%)", x, y, TEXT_FAINT, true);
					y += 11;
				}
			}
			y += 4;
		}

		if (s.hasAttribute()) {
			int bw = WIDTH;
			ctx.fill(x, y, x + bw, y + 3, TRACK);
			if (lvl > 0) {
				int fill = (int) (bw * Math.min(1.0, lvl / (double) ShardAttribute.MAX_LEVEL));
				ctx.fill(x, y, x + fill, y + 3, rarityColor(s.rarity));
			}
			y += 8;

			if (lvl < 0) {
				ctx.text(this.font, Lang.tr("The level will appear once you open the Attribute Menu", "Уровень появится, когда откроешь Attribute Menu"),
						x, y, TEXT_FAINT, true);
				y += 14;
			} else {
				y += 4; // всё нужное уже сказано строкой выше
			}
		}

		// ===== Сворачиваемые секции =====
		// Lang.tr("Details", "Подробнее") первым: аттрибут выше сказал ЧТО даёт, а тут — как это
		// работает на деле. Добыча ниже: она нужна реже, чем понять смысл шарда.
		y = section(ctx, Lang.tr("DETAILS", "ПОДРОБНЕЕ"), info.details, openAttribute,
				v -> openAttribute = v, x, y, mouseX, mouseY);
		// «How to hunt» из аттрибут-меню (болванка от импорта). Есть только у новых
		// шардов: у остальных гайд написан вручную, и поле пустое — секции нет.
		if (s.howToHunt != null && !s.howToHunt.isBlank()) {
			y = section(ctx, Lang.tr("HOW TO HUNT", "КАК ДОБЫТЬ"), s.howToHunt, openHowTo,
					v -> openHowTo = v, x, y, mouseX, mouseY);
		}
		y = obtainSection(ctx, s, info, x, y, mouseX, mouseY);

		// ===== Базар =====
		ctx.fill(x, y, right, y + 1, BORDER);
		y += 7;
		ctx.text(this.font, Lang.tr("BAZAAR", "БАЗАР"), x, y, TEXT_FAINT, true);
		y += 12;

		String id = ShardDb.bazaarId(key);
		BazaarPrices.Price p = id == null ? null : BazaarPrices.get(id);
		if (p == null) {
			ctx.text(this.font, Lang.tr("No prices", "Цен нет"), x, y, TEXT_FAINT, true);
			y += 12;
		} else {
			// Только две цены, как договаривались: почём купить и почём продать.
			row(ctx, Lang.tr("Sell offer (buy)", "Sell offer (купить)"), fmt(p.instaBuy), x, right, y, TEXT); y += 11;
			row(ctx, Lang.tr("Buy order (sell instantly)", "Buy order (продать сразу)"), fmt(p.sellOffer), x, right, y, TEXT); y += 11;
			if (p.warning().isBad()) {
				ctx.text(this.font, "⚠ " + p.warning().tag(), x, y, 0xFFD9A441, true);
				y += 11;
			}
			y += 2;
			boolean bHover = in(mouseX, mouseY, x, y, right, y + 12);
			ctx.fill(x, y, right, y + 12, bHover ? CARD_HOVER : CARD);
			String hint = Lang.tr("click — open on bazaar · RMB — fusion top", "клик — открыть на базаре · ПКМ — в топ фьюзов");
			ctx.text(this.font, hint, x + 6, y + 2, bHover ? TEXT : TEXT_DIM, true);
			final int zy = y;
			zones.add(new Zone(x, zy, right, zy + 12, null)); // обработка в mouseClicked
			y += 16;
		}

		contentH = y + scroll;
		drawTooltip(ctx);
	}

	private void drawTooltip(GuiGraphicsExtractor ctx) {
		if (tooltip == null) return;
		int w = 0;
		for (String line : tooltip) w = Math.max(w, this.font.width(line));
		ItemStack head = tooltipHead != null ? headStack(tooltipHead) : null;
		int headH = head != null ? 20 : 0;
		int h = tooltip.length * 10 + 6 + headH;
		int tx = Math.min(tooltipX + 10, this.width - w - 10);
		int ty = Math.min(tooltipY + 10, this.height - h - 4);
		ctx.fill(tx - 4, ty - 3, tx + w + 4, ty + h - 3, 0xF0141419);
		ctx.fill(tx - 4, ty - 3, tx + w + 4, ty - 2, ACCENT);
		int yy = ty;
		if (head != null) {
			try { ctx.item(head, tx, yy); } catch (Exception ignored) { }
			yy += headH;
		}
		for (String line : tooltip) {
			ctx.text(this.font, line, tx, yy, TEXT_DIM, true);
			yy += 10;
		}
	}

	/** Player-head с текстурой моба из base64 — для иконки в плашке. null при сбое. */
	private static ItemStack headStack(String skin) {
		try {
			com.mojang.authlib.GameProfile gp =
					new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "mob");
			gp.properties().put("textures",
					new com.mojang.authlib.properties.Property("textures", skin));
			ItemStack s = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
			s.set(net.minecraft.core.component.DataComponents.PROFILE,
					net.minecraft.world.item.component.ResolvableProfile.createResolved(gp));
			return s;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Lang.tr("Obtain methods", "Способы добычи") — ровно те методы, что задал автор, и в его порядке.
	 *
	 * Из данных методы НЕ выводим: пробовали, и признак Lang.tr("caught directly", "ловится напрямую")
	 * соврал на Alligator. Ничего не написано — так и говорим.
	 */
	/**
	 * Методы для показа. Для ironman у фьюзабельного шарда, где автор фьюз не
	 * расписал, добавляем синтетический фьюз-метод — базара у ironman нет, зато
	 * собрать шард фьюзом реально. Исключения: Chameleon/Cocoaleech (фьюзить
	 * бессмысленно/нельзя).
	 */
	private java.util.List<ShardInfo.Method> methodsToShow(ShardInfo.Info info) {
		if (!RynConfig.ironman || !ShardDb.hasRecipe(key)) return info.methods;
		if (key.equals("chameleon") || key.equals("cocoaleech")) return info.methods;
		for (ShardInfo.Method m : info.methods) if (m.type.equals("fusing")) return info.methods;
		java.util.List<ShardInfo.Method> aug = new java.util.ArrayList<>(info.methods);
		aug.add(ShardInfo.Method.fusing());
		return aug;
	}

	private int obtainSection(GuiGraphicsExtractor ctx, ShardDb.Shard s, ShardInfo.Info info,
							  int x, int y, int mouseX, int mouseY) {
		int right = x + WIDTH;
		String head = (openMedia ? "▼ " : "▶ ") + Lang.tr("OBTAIN METHODS", "СПОСОБЫ ДОБЫЧИ");
		boolean hover = in(mouseX, mouseY, x, y - 2, right, y + 9);
		ctx.text(this.font, head, x, y, hover ? TEXT : TEXT_DIM, true);

		java.util.List<ShardInfo.Method> methods = methodsToShow(info);
		int n = methods.size();
		if (n == 0) {
			String no = Lang.tr("not written yet", "пока не написано");
			ctx.text(this.font, no, right - this.font.width(no), y, TEXT_FAINT, true);
		}
		final int hy = y;
		zones.add(new Zone(x, hy - 2, right, hy + 9, () -> openMedia = !openMedia));
		y += 13;
		if (!openMedia || n == 0) return y;

		// Заголовки методов сами сворачиваются: список открытых — openMethods.
		// Свернули — виден только "Method N: Title", ничего больше. Кнопка track
		// (если есть спот) появляется на той же строке заголовка, но только у
		// раскрытого метода — не заводим ради неё отдельную пустую строку.
		int i = 1;
		for (ShardInfo.Method m : methods) {
			int idx = i - 1;
			boolean mOpen = openMethods.contains(idx);
			String mHead = (mOpen ? "▼ " : "▶ ") + Lang.tr("Method ", "Метод ") + i + ": " + m.title();
			boolean mHover = in(mouseX, mouseY, x + 4, y - 2, right, y + 9);
			ctx.text(this.font, mHead, x + 4, y, mHover ? TEXT : ACCENT, true);

			int toggleRight = right;
			if (mOpen) {
				// Кнопка «Отслеживать» у методов со спотами (координаты есть). У
				// Fusing спотов нет — кнопка не появится.
				java.util.List<Waypoints.Spot> spots = methodSpots(m);
				if (!spots.isEmpty()) {
					String btn = Lang.tr("⇱ track", "⇱ отслеживать");
					int bw = this.font.width(btn);
					int bx = right - bw;
					boolean bh = in(mouseX, mouseY, bx, y - 1, right, y + 9);
					ctx.text(this.font, btn, bx, y, bh ? 0xFF5FD68A : TEXT_FAINT, true);
					if (bh) { tooltip = new String[] {Lang.tr("Click to track the route.", "Нажмите, чтобы отслеживать путь.")};
						tooltipX = mouseX; tooltipY = mouseY; }
					final java.util.List<Waypoints.Spot> fs = spots;
					final String w = methodWarp(m, spots);
					zones.add(new Zone(bx, y - 1, right, y + 9, () -> {
						// Споты, куда ближний варп кидает прямо, метить не нужно.
						java.util.List<Waypoints.Spot> mark =
								fs.stream().filter(sp -> !sp.landsHere()).toList();
						// Huntstrap — гасить по достижению; Hunting — жить до остановки.
						Waypoints.track(mark, key, m.type.equals("trap"), m.ordered);
						if (!w.isBlank() && !SkyBlockCheck.onIslandOf(w)) warp(w);
						this.minecraft.setScreen(null);
					}));
					toggleRight = bx;
				}
			}
			final int fidx = idx;
			zones.add(new Zone(x + 4, y - 2, toggleRight, y + 9, () -> {
				if (!openMethods.remove(fidx)) openMethods.add(fidx);
			}));
			y += 12;
			if (mOpen) y = methodBody(ctx, m, x, y, mouseX, mouseY);
			i++;
		}
		return y + 2;
	}

	/**
	 * Купить готовым или зафьюзить — что дешевле, прямо на странице шарда.
	 *
	 * Считаем на количество до максимума аттрибута — столько обычно и берут.
	 * Цены нужны свежие: init() уже дёрнул refreshIfNeeded, но если не успели
	 * — честно пишем, что грузятся, а не показываем нули.
	 */
	private int fusePreview(GuiGraphicsExtractor ctx, int x, int y) {
		if (!BazaarPrices.isLoaded()) {
			ctx.text(this.font, Lang.tr("bazaar prices loading...", "цены базара грузятся..."), x + 12, y, TEXT_FAINT, true);
			return y + 11;
		}
		int amount = Math.max(1, amountToMax());
		double buy = FusionCalculator.unitBuyPrice(key);
		double fuse = ShardDb.hasRecipe(key)
				? FusionCalculator.calculate(key, amount).totalCost : Double.MAX_VALUE;

		int right = x() + WIDTH;
		if (buy != Double.MAX_VALUE) {
			row(ctx, Lang.tr("Buy ", "Купить ") + amount + Lang.tr(" pcs", " шт"), fmt(buy * amount), x + 12, right, y, TEXT_DIM);
			y += 11;
		}
		if (fuse != Double.MAX_VALUE) {
			row(ctx, Lang.tr("Fuse ", "Зафьюзить ") + amount + Lang.tr(" pcs", " шт"), fmt(fuse), x + 12, right, y, TEXT_DIM);
			y += 11;
		}
		// Вывод — что выгоднее. Разница важнее самих цифр: по ней и решают.
		if (buy != Double.MAX_VALUE && fuse != Double.MAX_VALUE) {
			boolean fuseWin = fuse < buy * amount;
			double save = Math.abs(buy * amount - fuse);
			ctx.text(this.font, (fuseWin ? Lang.tr("→ fusing cheaper by ", "→ фьюз дешевле на ") : Lang.tr("→ buying cheaper by ", "→ купить дешевле на "))
					+ fmt(save), x + 12, y, fuseWin ? GREEN : GOLD, true);
			y += 12;
		}
		return y;
	}

	private int methodBody(GuiGraphicsExtractor ctx, ShardInfo.Method m,
						   int x, int y, int mouseX, int mouseY) {
		if (m.type.equals("fusing")) {
			if (!m.text.isBlank()) {
				y = drawRich(ctx, m.text, x + 12, y, WIDTH - 12, TEXT_DIM, mouseX, mouseY);
				y += 2;
			}
			// Сравнение купить/фьюз прямо тут — не отходя к машине. Ironman
			// пропускаем: базара у него нет, это был бы шум (по умолчанию on).
			if (!RynConfig.ironman) y = fusePreview(ctx, x, y);

			int bw = 190;
			boolean hover = in(mouseX, mouseY, x + 12, y, x + 12 + bw, y + 12);
			ctx.fill(x + 12, y, x + 12 + bw, y + 12, hover ? CARD_HOVER : CARD);
			ctx.text(this.font, Lang.tr("open in calculator", "открыть в калькуляторе"), x + 16, y + 2,
					hover ? TEXT : TEXT_DIM, true);
			final int zy = y;
			zones.add(new Zone(x + 12, zy, x + 12 + bw, zy + 12, () -> {
				FusionState.set(key, amountToMax());
				this.minecraft.setScreen(null);
			}));
			return y + 16;
		}

		// Координаты метода на экран НЕ выводим: они и так висят в подсказке
		// каждой локации, а вторым экземпляром только загромождают текст.
		// В поле coords они остаются — оттуда их возьмёт отслеживание.
		// Строку "/warp ..." не рисуем: варп доступен через клик по локации в
		// тексте, а видеть команду постоянно незачем. Поле m.warp остаётся в
		// данных — для отслеживания и клика по локации.
		// Фраза про Black Hole + Charm одна на десятки методов — держим
		// её в коде, а не копипастим в JSON. Клеим отдельным абзацем (пустая
		// строка), чтобы не сливалась с описанием моба.
		String body = m.text;
		if (m.blackHole) body = (body.isBlank() ? "" : body + "\n\n") + blackHoleText(m.weapon);
		if (!body.isBlank()) y = drawRich(ctx, body, x + 12, y, WIDTH - 12, TEXT_DIM, mouseX, mouseY);
		y = media(ctx, m, x, y, mouseX, mouseY);
		return y + 4;
	}

	/**
	 * Универсальная приписка для методов с флагом blackHole (см. ShardInfo.Method).
	 * Основной способ (Black Hole на 10% HP) + Charm. С патча 0.27 Salts/Naga/скилл
	 * Hunting — это ОДИН складывающийся шанс Charm; поясняем его во всплывающей
	 * подсказке на слове Charm, а не отдельными способами в тексте.
	 */
	private static String blackHoleText(String weapon) {
		// weapon (напр. "Axes"/"Топорами") — если задано, шард опускается ТОЛЬКО им.
		boolean w = weapon != null && !weapon.isBlank();
		String lowerEn = w ? "Lower the mob to 10% HP with §e" + weapon + "§r only" : "Lower the mob to 10% HP";
		String lowerRu = w ? "Снизьте здоровье моба до 10% только §e" + weapon + "§r" : "Опустите моба до 10% HP";
		return Lang.tr(
				lowerEn + ", then use §bPocket Black Hole§r.\n"
						+ "It can also drop by [Charm](tip:A single Charm chance — its sources stack.|"
						+ "Adds up from your §6Hunting§r level, §6Naga Shard§r and §aSalts§r.).",
				lowerRu + ", затем используйте §bPocket Black Hole§r.\n"
						+ "Также может выпасть по [Charm](tip:Единый шанс Charm — источники складываются.|"
						+ "Суммируется из уровня §6Hunting§r, §6Naga Shard§r и §aSalts§r.).");
	}

	/**
	 * Сколько шардов не хватает до 10 уровня аттрибута.
	 *
	 * Раньше подставлялось 100 — число с потолка, оно не нужно ни одному шарду:
	 * до максимума надо от 24 (legendary) до 96 (common), а если уровень уже
	 * есть — и того меньше.
	 *
	 * Уровень неизвестен — считаем от нуля: это верхняя граница, и лучше
	 * показать больше нужного, чем меньше.
	 */
	private int amountToMax() {
		ShardDb.Shard s = ShardDb.shard(key);
		if (s == null) return RynConfig.DEFAULT_BATCH;
		int lvl = ShardProgress.displayLevel(key);
		if (lvl >= ShardAttribute.MAX_LEVEL) return RynConfig.batchOf(key);
		int need = AttributeLevels.toMax(s.rarity, Math.max(0, lvl));
		return need > 0 ? need : AttributeLevels.totalForMax(s.rarity);
	}


	private int media(GuiGraphicsExtractor ctx, ShardInfo.Method m,
					  int x, int y, int mouseX, int mouseY) {
		int right = x + WIDTH;
		for (String url : m.images) {
			boolean hv = in(mouseX, mouseY, x + 12, y - 2, right, y + 9);
			ctx.text(this.font, "🖼 " + shortUrl(url), x + 12, y, hv ? ACCENT : TEXT_DIM, true);
			final int zy = y;
			zones.add(new Zone(x + 12, zy - 2, right, zy + 9, () -> Util.getPlatform().openUri(url)));
			y += 11;
		}
		if (!m.video.isBlank()) {
			boolean hv = in(mouseX, mouseY, x + 12, y - 2, right, y + 9);
			ctx.text(this.font, "▶ " + shortUrl(m.video), x + 12, y, hv ? ACCENT : TEXT_DIM, true);
			final String v = m.video;
			final int zy = y;
			zones.add(new Zone(x + 12, zy - 2, right, zy + 9, () -> Util.getPlatform().openUri(v)));
			y += 11;
		}
		return y;
	}

	/** Варп — обычная команда от лица игрока, как если бы он её набрал. */
	private void warp(String cmd) {
		if (this.minecraft.player == null) return;
		// Crystal Hollows: /warp crystals и /warp nucleus оба бесплатны и ведут в
		// одну локацию. Кому удобнее спавн у Нуклеуса — включает тумблер, и любой
		// /warp crystals из гайда подменяется.
		if (RynConfig.preferNucleus && cmd.equalsIgnoreCase("/warp crystals")) {
			cmd = "/warp nucleus";
		}
		// Закрываем всё, а не возвращаем в список: игрок жмёт варп, чтобы
		// оказаться в игре, а не чтобы смотреть на другой наш экран.
		this.minecraft.setScreen(null);
		this.minecraft.player.connection.sendCommand(cmd.replaceFirst("^/", ""));
	}

	private void openBazaar(String name) {
		if (this.minecraft.player == null) return;
		this.minecraft.setScreen(null);
		this.minecraft.player.connection.sendCommand("bz " + name);
	}

	/** Сворачиваемая секция. Пустую не раскрыть — нечего показывать. */
	private int section(GuiGraphicsExtractor ctx, String title, String body, boolean open,
						java.util.function.Consumer<Boolean> setOpen,
						int x, int y, int mouseX, int mouseY) {
		int right = x + WIDTH;
		boolean has = body != null && !body.isBlank();
		String head = (has ? (open ? "▼ " : "▶ ") : "▶ ") + title;
		boolean hover = has && in(mouseX, mouseY, x, y - 2, right, y + 9);
		ctx.text(this.font, head, x, y, has ? (hover ? TEXT : TEXT_DIM) : TEXT_FAINT, true);
		if (!has) {
			String no = Lang.tr("not written yet", "пока не написано");
			ctx.text(this.font, no, right - this.font.width(no), y, TEXT_FAINT, true);
		}
		final int hy = y;
		if (has) zones.add(new Zone(x, hy - 2, right, hy + 9, () -> setOpen.accept(!open)));
		y += 13;

		if (open && has) {
			// drawRich с реальной мышью — иначе подсказки [..](tip:..) в тексте
			// не срабатывают на наведение.
			y = drawRich(ctx, body, x + 8, y, WIDTH - 8, TEXT_DIM, mouseX, mouseY);
			y += 6;
		}
		return y;
	}

	/** Рисует текст с переносами: и по \n, и по ширине. */
	private int drawWrapped(GuiGraphicsExtractor ctx, String text, int x, int y, int maxW, int color) {
		return drawRich(ctx, text, x, y, maxW, color, -1, -1);
	}

	/**
	 * Текст с разметкой: §-цвета, пометки [текст](роль:арг), переносы.
	 *
	 * Кладём слово за словом и сами следим за шириной. Готовый font.split тут
	 * не годится: он ничего не знает про пометки, а нам надо и подсветить их,
	 * и повесить кликзону ровно на их место в строке.
	 */
	private int drawRich(GuiGraphicsExtractor ctx, String text, int x, int y, int maxW,
						 int color, int mouseX, int mouseY) {
		if (text == null || text.isEmpty()) return y;
		for (String para : text.split("\n")) {
			if (para.isEmpty()) { y += 5; continue; }

			// Строка списка выравнивается с отступом, чтобы перенос не уезжал
			// под маркер и список читался списком.
			int indent = para.startsWith("- ") || para.startsWith("  ") ? 10 : 0;
			int cx = x;
			String carry = ""; // цвет, начатый на прошлой строке

			java.util.List<RichText.Part> parts = RichText.parse(para);
			for (int pi = 0; pi < parts.size(); pi++) {
				RichText.Part part = parts.get(pi);
				// В исходнике теги часто стоят вплотную к пунктуации: "(...)(shard:x)."
				// Без этой проверки перенос всегда пихал пробел между ними, и хвост
				// вида ")." съезжал на отдельную строку с фантомным пробелом впереди.
				RichText.Part next = pi + 1 < parts.size() ? parts.get(pi + 1) : null;
				boolean glueToNext = next != null && !next.isTag() && !next.text().isEmpty()
						&& !Character.isWhitespace(next.text().charAt(0));
				if (part.isTag()) {
					String label = tagLabel(part);
					int w = this.font.width(label);
					if (cx > x && cx + w > x + maxW) { y += 10; cx = x + indent; }
					// Подсказка-команда выключена в настройках — имя моба остаётся
					// цветным, но без клика и всплывашки, чтобы не перегружать.
					boolean off = hintDisabled(part);
					boolean hover = !off && mouseX >= 0 && in(mouseX, mouseY, cx, y - 1, cx + w, y + 9);
					// Без подчёркивания: слово и так выделено цветом.
					int tc = hasOwnColor(label) ? 0xFFFFFFFF : tagColor(part);
					ctx.text(this.font, label, cx, y, hover && !hasOwnColor(label) ? TEXT : tc, true);
					if (!off) {
						addTagZone(part, cx, y, w);
						// Подсказку показываем, если она есть — tagTip сам решает,
						// у каких ролей она уместна (пустой массив = не показывать).
						if (hover) {
							// Команда с НЕСКОЛЬКИМИ плашками (напр. Minotaur — 4 тира):
							// показываем одну страницу, колесо листает — иначе не влезает.
							java.util.List<PlaqueView> pls = part.role().equals("cmd")
									? plaquesFor(part.arg()) : java.util.List.of();
							if (pls.size() > 1) {
								if (!part.arg().equals(plaqueArg)) { plaqueArg = part.arg(); plaquePage = 0; }
								if (plaquePage >= pls.size()) plaquePage = pls.size() - 1;
								if (plaquePage < 0) plaquePage = 0;
								hoverCmdArg = part.arg();
								hoverCmdPages = pls.size();
								PlaqueView pv = pls.get(plaquePage);
								tooltip = pagedPlaque(pv, plaquePage, pls.size(), part.arg());
								tooltipHead = pv.skin().isBlank() ? null : pv.skin();
								tooltipX = mouseX;
								tooltipY = mouseY;
							} else {
								String[] tip = tagTip(part);
								if (tip.length > 0) {
									tooltip = tip;
									tooltipX = mouseX;
									tooltipY = mouseY;
									// Голова моба над плашкой, если у команды сохранена текстура.
									tooltipHead = (!pls.isEmpty() && !pls.get(0).skin().isBlank())
											? pls.get(0).skin() : null;
								}
							}
						}
					}
					cx += w + (glueToNext ? 0 : this.font.width(" "));
					continue;
				}
				String[] rawWords = part.text().split(" ", -1);
				int lastIdx = -1;
				for (int wi = 0; wi < rawWords.length; wi++) if (!rawWords[wi].isEmpty()) lastIdx = wi;
				for (int wi = 0; wi < rawWords.length; wi++) {
					String word = rawWords[wi];
					if (word.isEmpty()) continue;
					String draw = carry + word;
					int w = this.font.width(draw);
					if (cx > x + indent && cx + w > x + maxW) { y += 10; cx = x + indent; draw = carry + word; }
					ctx.text(this.font, draw, cx, y, color, true);
					// Последнее слово куска перед тегом без пробела в исходнике —
					// клеим впритык, а не разрываем строку фантомным пробелом.
					boolean glueToTag = wi == lastIdx && !part.text().endsWith(" ")
							&& next != null && next.isTag();
					cx += this.font.width(draw) + (glueToTag ? 0 : this.font.width(" "));
					String c = RichText.lastCode(draw);
					carry = c.isEmpty() && draw.contains("§r") ? "" : (c.isEmpty() ? carry : c);
				}
			}
			y += 10;
		}
		return y;
	}

	private static final int TAG_BZ = 0xFF5FD68A;
	private static final int TAG_TIP = 0xFFB9BCC7;
	private static final int TAG_SHARD = 0xFF5B8DEF;

	private int tagColor(String role) {
		return switch (role) {
			case "bz" -> TAG_BZ;
			case "shard" -> TAG_SHARD;
			default -> TAG_TIP;
		};
	}

	/** Цвет из §-кода прямо в подписи. Пометка красится своим, а не ролевым. */
	private static boolean hasOwnColor(String label) {
		return label != null && label.indexOf('§') >= 0;
	}

	/**
	 * Выключена ли эта пометка-команда настройкой. Гейтим только подсказки
	 * /bestiary и /seacreatureguide — остальные cmd (/recipe, /rng) всегда живые.
	 */
	private static boolean hintDisabled(RichText.Part p) {
		if (!p.role().equals("cmd")) return false;
		String a = p.arg();
		if (a.startsWith("/bestiary")) return !RynConfig.bestiaryHints;
		if (a.startsWith("/seacreatureguide")) return !RynConfig.seaGuideHints;
		return false;
	}

	/** Цвет пометки. У локации свой — из справочника, а не общий для пометок. */
	private int tagColor(RichText.Part p) {
		if (p.role().equals("loc")) {
			LocationDb.Loc l = LocationDb.get(p.arg());
			if (l != null) return codeColor(l.color());
		}
		return tagColor(p.role());
	}

	/** §-код в цвет. Берём палитру Minecraft, чтобы совпадало с игрой. */
	private static int codeColor(String code) {
		if (code == null || code.length() < 2) return 0xFFFFFFFF;
		ChatFormatting f = ChatFormatting.getByCode(code.charAt(1));
		if (f == null || f.getColor() == null) return 0xFFFFFFFF;
		return 0xFF000000 | f.getColor();
	}

	/** Как показать пометку локации: имя из справочника, а не то, что в тексте. */
	private String tagLabel(RichText.Part p) {
		if (p.role().equals("loc")) {
			LocationDb.Loc l = LocationDb.get(p.arg());
			if (l != null) return l.name();
		}
		return p.text();
	}

	private static final String[] NO_TIP = new String[0];

	/** Подсказка пометки. Пустой массив — подсказки нет (не мельтешим). */
	private String[] tagTip(RichText.Part p) {
		return switch (p.role()) {
			// bz:товар — без подсказки; bz:товар|строка|строка — кастомная подсказка
			case "bz" -> {
				String[] parts = p.arg().split("\\|");
				if (parts.length <= 1) yield NO_TIP;
				yield java.util.Arrays.copyOfRange(parts, 1, parts.length);
			}
			case "warp" -> new String[] {Lang.tr("Click — ", "Клик — ") + p.arg()};
			case "cmd" -> cmdTip(p.arg());
			case "loc" -> locTip(p.arg());
			case "tip" -> p.arg().split("\\|");
			default -> NO_TIP; // shard, salts и т.п. — по клику и так ясно
		};
	}

	/**
	 * Подсказка команды. Для /bestiary и /seacreatureguide, если игрок уже
	 * листал бестиарий, показываем снятую плашку моба (типы, статы, лут);
	 * не листал — только строку с командой.
	 */
	/** Плашка для тултипа — из бестиария или Sea Creature Guide, единый вид. */
	private record PlaqueView(java.util.List<String> lines, String skin) {}

	/** Одна страница многостраничной плашки: шапка-навигация + плашка + строка клика. */
	private String[] pagedPlaque(PlaqueView pv, int page, int total, String arg) {
		java.util.List<String> out = new java.util.ArrayList<>();
		out.add(Lang.tr("§8▲ wheel  §7", "§8▲ колесо  §7") + (page + 1) + "/" + total + "  §8▼");
		out.addAll(pv.lines());
		out.add("");
		out.add(Lang.tr("§8Click — ", "§8Клик — ") + cmdExec(arg));
		return out.toArray(new String[0]);
	}

	private String[] cmdTip(String arg) {
		java.util.List<PlaqueView> ps = plaquesFor(arg);
		if (!ps.isEmpty()) {
			java.util.List<String> out = new java.util.ArrayList<>();
			for (int i = 0; i < ps.size(); i++) {
				if (i > 0) out.add(""); // пустая строка между вариантами моба
				out.addAll(ps.get(i).lines());
			}
			out.add("");
			out.add(Lang.tr("§8Click — ", "§8Клик — ") + cmdExec(arg));
			return out.toArray(new String[0]);
		}
		return new String[] {Lang.tr("Click — ", "Клик — ") + cmdExec(arg)};
	}

	/**
	 * Все варианты плашки для команды. /bestiary → статы/лут из бестиария;
	 * /seacreatureguide → требования/шансы из Sea Creature Guide.
	 */
	/** Команда до "|" (что выполнить). После "|" — имя плашки, здесь отбрасываем. */
	private static String cmdExec(String arg) {
		int bar = arg.indexOf('|');
		return bar >= 0 ? arg.substring(0, bar).trim() : arg;
	}

	private java.util.List<PlaqueView> plaquesFor(String arg) {
		// "cmd аргумент" или "cmd аргумент|ИмяПлашки" (когда семейство и моб зовутся
		// по-разному: /bestiary Tentacle открывает семейство, плашка — Hellwisp).
		String exec = cmdExec(arg);
		int bar = arg.indexOf('|');
		String plaqueOverride = bar >= 0 ? arg.substring(bar + 1).trim() : null;
		// "|-" — команда кликабельна, но плашку бестиария не показываем (например
		// Tank Zombie: данжевый моб без внятной плашки).
		if ("-".equals(plaqueOverride)) return java.util.List.of();
		int sp = exec.indexOf(' ');
		if (sp <= 0) return java.util.List.of();
		String cmd = exec.substring(0, sp);
		String mob = plaqueOverride != null ? plaqueOverride : exec.substring(sp + 1).trim();
		java.util.List<PlaqueView> out = new java.util.ArrayList<>();
		if (cmd.equals("/bestiary")) {
			for (BestiaryDb.Plaque p : BestiaryDb.getAll(mob)) out.add(new PlaqueView(p.lines(), p.skin()));
		} else if (cmd.equals("/seacreatureguide")) {
			for (SeaGuideDb.Plaque p : SeaGuideDb.getAll(mob)) out.add(new PlaqueView(p.lines(), p.skin()));
		}
		return out;
	}

	/**
	 * Точки-споты метода для отслеживания: явный coords у метода (конкретный
	 * спот) + loc:-теги с координатами из текста метода (напр. места ловушек).
	 */
	private java.util.List<Waypoints.Spot> methodSpots(ShardInfo.Method m) {
		java.util.List<Waypoints.Spot> out = new java.util.ArrayList<>();
		String spotName = ShardDb.displayName(key);

		// 1. Записанный /srpath spot — САМЫЙ авторитетный: он поставлен руками именно
		//    туда, куда надо (напр. crow/heron у Agatha, а не в loc:murkwater из текста).
		//    Перекрывает loc/coords. Только для методов-«пойти куда-то».
		if (isPhysicalMethod(m.type)) {
			java.util.List<net.minecraft.world.phys.Vec3> pts = NavGraph.spotsForMethod(key, m.type);
			if (!pts.isEmpty()) {
				// У метода не задан свой warp — тогда трек молчал бы вместо телепорта.
				// Берём варп острова, на котором записан сам спот.
				String warp = (m.warp != null && !m.warp.isBlank()) ? m.warp
						: NavGraph.defaultWarp(NavGraph.islandForMethod(key, m.type));
				for (net.minecraft.world.phys.Vec3 c : pts) addSpot(out, c.x, c.y, c.z, spotName, warp, 0xFF5FD68A, false);
				return out;
			}
		}

		// 2. loc-теги (у них есть название локации для подписи), потом явные coords.
		//    Дедуп: и то и другое часто указывает на одно место.
		if (m.text != null) {
			for (RichText.Part p : RichText.parse(m.text)) {
				if (p.isTag() && "loc".equals(p.role())) {
					LocationDb.Loc l = LocationDb.get(p.arg());
					double[] lc = l == null ? null : l.xyz();
					if (lc != null) addSpot(out, lc[0] + 0.5, lc[1] + 0.5, lc[2] + 0.5,
							l.name(), l.effectiveWarp(), colorOf(l), l.landsHere());
				}
			}
		}
		if (m.coords != null && !m.coords.isBlank()) {
			for (String part : m.coords.split(";")) {  // ";" — несколько точек
				double[] c = parseXyz(part);
				// Варп прогоняем через подмену MVP+: у метода он записан строкой, а не
				// loc-ссылкой, и без этого тумблер MVP+ на такие методы не действовал.
				if (c != null) addSpot(out, c[0], c[1], c[2], spotName,
						LocationDb.upgradeWarp(m.warp), 0xFF5FD68A, false);
			}
		}
		return out;
	}

	/** Методы, у которых есть физический спот (куда идти): охота, ловушка, покупка у NPC, сундук. */
	private static boolean isPhysicalMethod(String type) {
		return "hunting".equals(type) || "trap".equals(type)
				|| "purchase".equals(type) || "chest".equals(type);
	}

	/** Добавляет спот, если рядом (<8 блоков) ещё нет — иначе это дубль. */
	private void addSpot(java.util.List<Waypoints.Spot> out, double x, double y, double z,
						 String name, String warp, int color, boolean landsHere) {
		for (Waypoints.Spot s : out) {
			if (Math.abs(s.x() - x) < 8 && Math.abs(s.y() - y) < 8 && Math.abs(s.z() - z) < 8) return;
		}
		out.add(new Waypoints.Spot(x, y, z, name, warp, color, landsHere));
	}

	/**
	 * Куда телепортировать при отслеживании. Спот в приоритете: его warp — это уже
	 * l.effectiveWarp(), т.е. учитывает тумблер MVP+/свитки (напр. /warp drag вместо
	 * /warp end). Сырой m.warp берём лишь запасным — он MVP+ не учитывает, и раньше
	 * из-за него трек всегда слал базовый варп (баг Draconic и т.п.).
	 */
	private String methodWarp(ShardInfo.Method m, java.util.List<Waypoints.Spot> spots) {
		for (Waypoints.Spot s : spots) if (s.warp() != null && !s.warp().isBlank()) return s.warp();
		if (m.warp != null && !m.warp.isBlank()) return LocationDb.upgradeWarp(m.warp);
		return "";
	}

	private static double[] parseXyz(String s) {
		if (s == null || s.isBlank()) return null;
		String[] p = s.trim().split("[ ,]+");
		if (p.length != 3) return null;
		try {
			return new double[] {Double.parseDouble(p[0]), Double.parseDouble(p[1]),
					Double.parseDouble(p[2])};
		} catch (NumberFormatException e) { return null; }
	}

	private static int colorOf(LocationDb.Loc l) {
		String code = l.color();
		if (code == null || code.length() < 2) return 0xFFFFFFFF;
		net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(code.charAt(1));
		return f == null || f.getColor() == null ? 0xFFFFFFFF : 0xFF000000 | f.getColor();
	}

	/**
	 * Подсказка локации. Координаты/заметки в тултипе не дублируем — вейпоинт
	 * при отслеживании и так покажет, куда идти. Варп в тултипе — только у
	 * локаций-островов (без своих coords, напр. galatea/crimson): для подлокаций
	 * (Murkwater Loch, Kiara's и т.п.) тултипа вовсе нет, не мельтешим.
	 */
	private String[] locTip(String key) {
		LocationDb.Loc l = LocationDb.get(key);
		// Нет в справочнике — так и скажем. Промолчать значит оставить игрока
		// гадать, почему подсказка пустая.
		if (l == null) return new String[] {Lang.tr("Location '", "Локация '") + key + Lang.tr("' not in the reference", "' не в справочнике")};
		if (l.hasCoords()) return NO_TIP;
		java.util.List<String> out = new java.util.ArrayList<>();
		String w = l.effectiveWarp();
		out.add(w.isBlank() ? Lang.tr("No warp, walk there", "Варпа нет, добираться ногами") : Lang.tr("Click — ", "Клик — ") + w);
		// Ближний варп доступен, но не задействован (нет MVP+ или свитка) —
		// подскажем, что есть путь короче.
		if (l.hasMvpWarp() && !w.equals(l.warpMvp())) {
			out.add(Lang.tr("§8at MVP+: ", "§8у MVP+: ") + l.warpMvp());
		}
		return out.toArray(new String[0]);
	}

	private void addTagZone(RichText.Part p, int x, int y, int w) {
		switch (p.role()) {
			// товар до | — что открыть на базаре
			case "bz" -> zones.add(new Zone(x, y - 1, x + w, y + 9,
					() -> openBazaar(p.arg().split("\\|")[0])));
			case "shard" -> zones.add(new Zone(x, y - 1, x + w, y + 9, () -> {
				if (ShardDb.shard(p.arg()) != null) {
					this.minecraft.setScreen(new ShardPageScreen(p.arg(), parent));
				}
			}));
			case "warp", "cmd" -> zones.add(new Zone(x, y - 1, x + w, y + 9, () -> warp(cmdExec(p.arg()))));
			case "npc" -> {
				NavGraph.NpcSpot n = NavGraph.npc(p.arg());
				if (n != null) {
					zones.add(new Zone(x, y - 1, x + w, y + 9, () -> {
						String npcWarp = NavGraph.defaultWarp(n.island());
						Waypoints.track(java.util.List.of(
								new Waypoints.Spot(n.x(), n.y(), n.z(), p.arg(), npcWarp, 0xFF5FD68A, false)),
								key, true, false);
						if (!npcWarp.isBlank() && !SkyBlockCheck.onIslandOf(npcWarp)) warp(npcWarp);
						else this.minecraft.setScreen(null);
					}));
				}
			}
			case "loc" -> {
				LocationDb.Loc l = LocationDb.get(p.arg());
				// Клик по локации — метка ТОЛЬКО на неё. Метки всех локаций
				// метода ставит отслеживание, это разные сценарии.
				if (l != null) {
					zones.add(new Zone(x, y - 1, x + w, y + 9, () -> {
						// Ближний варп кидает прямо в точку — метка не нужна.
						// Есть координаты — метка. Нет — просто варп, чужую метку
							// (напр. от «отслеживать») не стираем.
							if (l.landsHere()) Waypoints.clear();
							else if (l.hasCoords()) Waypoints.only(l, key);
						String cmd = l.effectiveWarp();
						// Уже на этом острове — не телепортируем в начало, просто метка.
						if (!cmd.isBlank() && !SkyBlockCheck.onIslandOf(cmd)) warp(cmd);
						else this.minecraft.setScreen(null);
					}));
				}
			}
			default -> { } // tip — только подсказка, кликать нечего
		}
	}

	private void row(GuiGraphicsExtractor ctx, String label, String value,
					 int x, int right, int y, int color) {
		ctx.text(this.font, label, x, y, TEXT_FAINT, true);
		ctx.text(this.font, value, right - this.font.width(value), y, color, true);
	}

	private static String shortUrl(String u) {
		return u.length() > 52 ? u.substring(0, 50) + "…" : u;
	}

	private static String fmt(double v) {
		double a = Math.abs(v);
		if (a >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
		if (a >= 1_000) return String.format("%.1fk", v / 1_000);
		return String.format("%.0f", v);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		int mx = (int) event.x(), my = (int) event.y();

		// Базар: клик — открыть, ПКМ — в топ
		ShardDb.Shard s = ShardDb.shard(key);
		for (Zone z : zones) {
			if (!in(mx, my, z.x1(), z.y1(), z.x2(), z.y2())) continue;
			if (z.action() != null) { z.action().run(); return true; }
			// зона базара
			if (event.button() == 1) {
				this.minecraft.setScreen(new FusionTopScreen());
			} else if (this.minecraft.player != null && s != null) {
				this.onClose();
				this.minecraft.player.connection.sendCommand("bz " + ShardDb.bazaarName(key));
			}
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		// Курсор над многостраничной плашкой бестиария — колесо листает страницы,
		// а саму страницу не скроллим (иначе плашка «убегает»).
		if (hoverCmdPages > 1) {
			plaquePage -= (int) Math.signum(dy); // вверх — предыдущий тир, вниз — следующий
			if (plaquePage < 0) plaquePage = 0;
			if (plaquePage > hoverCmdPages - 1) plaquePage = hoverCmdPages - 1;
			return true;
		}
		int max = Math.max(0, contentH - this.height + 20);
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy) * 12));
		return true;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
