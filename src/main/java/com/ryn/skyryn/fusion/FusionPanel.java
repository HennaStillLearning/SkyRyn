package com.ryn.skyryn.fusion;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import com.ryn.skyryn.config.Lang;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.hud.HuntingFortune;
import com.ryn.skyryn.screen.FusionTopScreen;
import com.ryn.skyryn.screen.ShardListScreen;
import com.ryn.skyryn.screen.ShardPageScreen;
import com.ryn.skyryn.waypoint.SkyBlockCheck;

/**
 * Панель фьюжен-калькулятора рядом с окном "Fusion Box".
 *
 * Панель рисуется в СВОИХ координатах (0,0 — левый верхний угол панели),
 * а на экран её ставит матрица: translate(panelX, panelY) + scale(panelScale).
 * Поэтому мышь перед проверкой кликзон переводится в локальные координаты.
 *
 * Двигается перетаскиванием за заголовок, позиция и масштаб — в /sr.
 */
public class FusionPanel {

	// ===== Палитра =====
	// Сознательно скупая: один акцент на всё интерактивное, три градации серого
	// на текст, и зелёный/красный ТОЛЬКО там, где цвет несёт смысл (профит).
	// Раньше золотой/аква/жёлтый/зелёный конкурировали за внимание, и глазу
	// не за что было зацепиться.
	private static final int BG_TOP      = 0xF21A1A24;
	private static final int BG_BOTTOM   = 0xF2131319;
	private static final int SHADOW      = 0x66000000;
	private static final int BORDER      = 0xFF2E2E3C;
	private static final int ACCENT      = 0xFF5B8DEF;
	private static final int ACCENT_SOFT = 0xFF2A3F63;
	private static final int SURFACE     = 0xFF1F1F29;
	private static final int SURFACE_HI  = 0xFF2A2A38;
	private static final int DIVIDER     = 0xFF262633;

	private static final int TEXT        = 0xFFDDDEE6;
	private static final int TEXT_DIM    = 0xFF9A9CAB;
	private static final int TEXT_FAINT  = 0xFF5E606E;
	private static final int GREEN       = 0xFF5FD68A;
	private static final int RED         = 0xFFE06C6C;
	private static final int WARN        = 0xFFD9A441;

	// Ликвидность — приглушённо: это подсказка, а не главное на экране.
	private static final int LIQ_HI      = 0xFF4A8F63;
	private static final int LIQ_MID     = 0xFF9A8244;
	private static final int LIQ_LOW     = 0xFFA05555;

	private static final int PANEL_WIDTH = 200;
	/** Строка покупки — две строки: "1000x Pest ... 86.19MLang.tr(" and ", " и ")86.19K/шт". */
	private static final int BUY_ROW_H = 20;
	/** Высота закреплённого внизу блока трекера. */
	private static final int TRACKER_H = 86;
	/** Высота шапки — за неё панель таскается. */
	private static final int HEADER_H = 18;
	/** Полоса настроек расчёта, закреплена внизу над трекером. */
	private static final int SETTINGS_H = 18;
	/** Ширина ванильного окна сундука — чтобы встать слева от него. */
	private static final int CONTAINER_W = 176;

	// Поля ввода
	private static String shardInput = "";
	private static String amountInput = "";
	private static String crocInput = "";
	private static String fortuneInput = "";
	private static int activeField = 0; // 0 = ничего, 1 = шард, 2 = количество, 3 = croc, 4 = фортуна
	private static int lastStateVersion = -1; // версия FusionState, под которую синхронизированы поля

	// Что уже Lang.tr("bought", "куплено") (кликнуто) — для галочек
	private static final Set<String> purchased = new HashSet<>();

	// Кликзоны текущего кадра (в ЛОКАЛЬНЫХ координатах панели)
	private static final List<ClickZone> clickZones = new ArrayList<>();
	private static boolean wasMouseDown = false;

	// Перетаскивание
	private static boolean dragging = false;
	private static int dragOffsetX = 0;
	private static int dragOffsetY = 0;

	// Свёрнута до маленькой иконки
	private static boolean collapsed = false;
	private static final int COLLAPSED_SZ = 18;
	private static void toggleCollapse() { collapsed = !collapsed; }

	private static class ClickZone {
		final int x1, y1, x2, y2;
		final Runnable action;
		ClickZone(int x1, int y1, int x2, int y2, Runnable action) {
			this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
			this.action = action;
		}
		boolean contains(double mx, double my) {
			return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
		}
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!isFusionScreen(screen)) return;

			// Тянем свежие цены базара
			BazaarPrices.refreshIfNeeded();

			// Инициализируем поля из сохранённого состояния
			if (FusionState.currentShard != null) {
				shardInput = FusionState.currentShard;
				amountInput = String.valueOf(FusionState.currentAmount);
			}
			crocInput = String.valueOf(RynConfig.crocodileLevel);
			fortuneInput = String.valueOf((int) RynConfig.hunterFortune);

			// В 26.1 отрисовка GUI разделена на фазы — рисуем в extract-фазе.
			ScreenEvents.afterExtract(screen).register((scr, ctx, mouseX, mouseY, tickDelta) -> {
				if (!RynConfig.calculatorEnabled) return;
				renderPanel(ctx, scr, mouseX, mouseY);
				handleMouse(scr, mouseX, mouseY);
			});

			// Перехват клавиш для полей ввода.
			// В 26.1 лямбда даёт (screen, KeyEvent) — код клавиши берём через .key().
			ScreenKeyboardEvents.allowKeyPress(screen).register((scr, keyEvent) -> {
				if (!RynConfig.calculatorEnabled) return true;
				if (activeField == 0) return true; // поле не активно — всё в игру (в т.ч. бинды)

				int key = keyEvent.key();

				// Esc — снимаем фокус с поля и пропускаем клавишу дальше (закрытие меню и т.п.)
				if (key == GLFW.GLFW_KEY_ESCAPE) {
					activeField = 0;
					return true;
				}

				// Обрабатываем ТОЛЬКО клавиши, которые реально нужны полю ввода.
				// Всё остальное (бинды SkyHanni, хоткеи) пропускаем в игру.
				boolean isInputKey =
						key == GLFW.GLFW_KEY_BACKSPACE
						|| key == GLFW.GLFW_KEY_ENTER
						|| key == GLFW.GLFW_KEY_TAB
						|| (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
						|| (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)
						|| key == GLFW.GLFW_KEY_SPACE;

				if (!isInputKey) {
					return true; // не наша клавиша — отдаём игре (бинды работают)
				}

				handleKey(key);
				return false; // наша клавиша — забираем себе
			});

			// Колесо над панелью — прокрутка длинного рецепта (панель не растягивается).
			ScreenMouseEvents.allowMouseScroll(screen).register((scr, mxs, mys, hor, ver) -> {
				if (!RynConfig.calculatorEnabled || collapsed) return true;
				int maxScroll = Math.max(0, lastFullH - lastDrawnH);
				if (maxScroll <= 0) return true;                    // всё влезает — колесо не наше
				if (!overPanel(scr, mxs, mys)) return true;
				scrollY -= (int) Math.signum(ver) * 18;
				scrollY = Math.max(0, Math.min(scrollY, maxScroll));
				return false;                                        // прокрутили — в игру не пускаем
			});
		});
	}

	/** Пишет ли игрок сейчас в поле панели — тогда бинды не трогаем. */
	public static boolean isTyping() {
		return RynConfig.calculatorEnabled && activeField != 0;
	}

	private static boolean isFusionScreen(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?>)) return false;
		if (!SkyBlockCheck.onSkyBlock()) return false;
		String title = screen.getTitle().getString();
		// И на машине фьюза, и на /hb: там калькулятор соседствует с гайдом «что сфьюзить».
		return title != null && (title.contains("Fusion Box") || title.contains("Hunting Box"));
	}

	// ===== Позиция и размер панели =====

	/** Результат текущего кадра — считаем один раз, используем и для высоты, и для отрисовки. */
	private static FusionCalculator.Result frameResult = null;
	/** Кэш тяжёлого расчёта: пересобираем только при смене запроса/цен/настроек. */
	private static FusionCalculator.Result cachedResult = null;
	private static String cachedResultSig = null;
	/** Итог ironman-плана (время фарма/флаги) — считаем вместе с деревом, не каждый кадр. */
	private static double cachedIronTotal = 0;
	private static boolean cachedIronUnfarmable, cachedIronBuy;
	/** Входы текущего фьюза калькулятора — читает HuntingBoxReader для подсветки слотов. */
	public static java.util.Set<String> highlightInputs = java.util.Set.of();
	private static java.util.Set<String> cachedHighlight = java.util.Set.of();
	/** Высота панели последнего кадра — нужна мыши, которая обрабатывается после отрисовки.
	 *  Теперь это ВИДИМАЯ (обрезанная) высота, а не полная. */
	private static int lastPanelH = 200;
	/** Прокрутка контента (в контент-координатах, до масштаба). */
	private static int scrollY = 0;
	private static int lastFullH = 0;    // полная высота контента текущего кадра
	private static int lastDrawnH = 0;   // видимая высота (обрезанная под экран)

	/** Сколько подсказок автодополнения показываем сейчас. */
	private static int suggestionCount() {
		if (activeField != 1 || shardInput.isEmpty()) return 0;
		String q = shardInput.toLowerCase();
		int n = 0;
		for (String c : ShardDb.allCraftable()) {
			if (c.startsWith(q) && ++n >= 5) break;
		}
		return n;
	}

	/**
	 * Сколько строк займёт шаг. Входы влезают в одну строку — рисуем одной,
	 * не влезают — в две. Раньше строка просто обрезалась на середине слова.
	 */
	private static int stepLines(Font tr, FusionCalculator.Step s) {
		return tr.width(stepInputs(s)) <= PANEL_WIDTH - 34 ? 2 : 3;
	}

	/**
	 * Части ввода шага: "1000x Chill", "1000x Pest". Self-fusion (Sun Fish +
	 * Sun Fish) в inputs схлопнут в один ключ — разворачиваем в ДВА слагаемых,
	 * иначе игрок видит "30x Sun Fish → Galaxy Fish" и думает, что это баг.
	 */
	private static java.util.List<String> stepInputParts(FusionCalculator.Step s) {
		java.util.List<String> parts = new java.util.ArrayList<>();
		if (s.selfFuse && s.inputs.size() == 1) {
			Map.Entry<String, Integer> e = s.inputs.entrySet().iterator().next();
			int half = e.getValue() / 2;
			String nm = ShardDb.displayName(e.getKey());
			parts.add(half + "x " + nm);
			parts.add(half + "x " + nm);
			return parts;
		}
		for (Map.Entry<String, Integer> e : s.inputs.entrySet()) {
			parts.add(e.getValue() + "x " + ShardDb.displayName(e.getKey()));
		}
		return parts;
	}

	/** "1000x Chill + 1000x Pest" — входы шага одной строкой. */
	private static String stepInputs(FusionCalculator.Step s) {
		return String.join(" + ", stepInputParts(s));
	}

	/** Высота панели считается из содержимого — чтобы шаги не налезали на трекер. */
	/** Уникальные рецепты шарда (без дублей a+b / b+a) — для фолбэка без цен. */
	private static java.util.List<ShardDb.Recipe> uniqueRecipes(String shard) {
		java.util.List<ShardDb.Recipe> out = new java.util.ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (ShardDb.Recipe r : ShardDb.recipesFor(shard)) {
			String k = r.a.compareTo(r.b) <= 0 ? r.a + "|" + r.b : r.b + "|" + r.a;
			if (seen.add(k)) out.add(r);
		}
		return out;
	}

	private static final int FALLBACK_CAP = 8; // сколько рецептов показываем максимум

	/** Высота фолбэк-секции (список рецептов). Должна совпадать с drawBazaarFallback. */
	private static int fallbackHeight(String shard) {
		int h = 12; // строка-заголовок
		ShardDb.Shard s = ShardDb.shard(shard);
		if (s != null && s.direct) h += 10;
		java.util.List<ShardDb.Recipe> rs = uniqueRecipes(shard);
		h += Math.min(FALLBACK_CAP, rs.size()) * 10;
		if (rs.size() > FALLBACK_CAP) h += 10;
		if (rs.isEmpty() && (s == null || !s.direct)) h += 10;
		return h;
	}

	/** Скорость прямой добычи коротким текстом. */
	private static String fmtRate(double r) {
		return r >= 100 ? String.valueOf(Math.round(r))
				: String.format(java.util.Locale.ROOT, "%.1f", r);
	}

	/**
	 * Рисует рецепты шарда, когда цен базара нет (нет сети / API лёг). Обычный
	 * расчёт «дешёвого пути» без цен невозможен, но показать ЧТО из чего делается
	 * и ловится ли напрямую — можно и полезно.
	 */
	private static void drawBazaarFallback(GuiGraphicsExtractor ctx, Font tr, String shard,
										   int textX, int rightX, int y, double mx, double my) {
		boolean off = BazaarPrices.unavailable();
		ctx.text(tr, off ? Lang.tr("Bazaar unavailable — recipes:", "Базар недоступен — рецепты:")
						 : Lang.tr("Loading prices — recipes:", "Цены грузятся — рецепты:"),
				textX, y, off ? WARN : TEXT_FAINT, true);
		y += 12;

		ShardDb.Shard s = ShardDb.shard(shard);
		if (s != null && s.direct) {
			String rate = s.rate > 0 ? " (~" + fmtRate(s.rate) + Lang.tr("/h", "/ч") + ")" : "";
			ctx.text(tr, "• " + Lang.tr("hunt directly", "ловится напрямую") + rate,
					textX + 4, y, GREEN, true);
			y += 10;
		}

		java.util.List<ShardDb.Recipe> rs = uniqueRecipes(shard);
		int shown = 0;
		for (ShardDb.Recipe r : rs) {
			if (shown >= FALLBACK_CAP) break;
			String line = "• " + ShardDb.displayName(r.a) + "  +  " + ShardDb.displayName(r.b);
			ctx.text(tr, fit(tr, line, rightX - textX - 6), textX + 4, y, TEXT, true);
			y += 10;
			shown++;
		}
		if (rs.size() > FALLBACK_CAP) {
			ctx.text(tr, "  +" + (rs.size() - FALLBACK_CAP) + Lang.tr(" more", " ещё"),
					textX + 4, y, TEXT_FAINT, true);
			y += 10;
		}
		if (rs.isEmpty() && (s == null || !s.direct)) {
			ctx.text(tr, Lang.tr("no fusion recipe", "рецепта фьюза нет"), textX + 4, y, TEXT_FAINT, true);
			y += 10;
		}
	}

	private static int panelHeight(Screen screen) {
		if (collapsed) return COLLAPSED_SZ;   // свёрнуто — маленькая иконка
		Font tr = Minecraft.getInstance().font;
		int h = HEADER_H + 8;        // шапка + разделитель
		h += 14;                     // поле шарда
		int sug = suggestionCount();
		if (sug > 0) h += sug * 10 + 2;
		h += 15;                     // поле количества
		h += 19;                     // кнопки

		if (frameResult == null) {
			// Базар не загрузился: вместо залипшего "Loading" показываем рецепты.
			// Эта ветка рендерится только когда шард выбран и у него есть рецепт
			// (иначе секции результата вообще нет — см. drawContent).
			String shard = FusionState.currentShard;
			if (shard != null && ShardDb.hasRecipe(shard)) h += fallbackHeight(shard);
		} else {
			h += 12 + frameResult.shoppingList.size() * BUY_ROW_H;
			h += 10;
			h += 12;
			for (FusionCalculator.Step s : frameResult.steps) h += stepLines(tr, s) * 10;
			h += 10;
			if (RynConfig.ironman) h += 11 + 11 + 12;                 // время фарма + слэк + XP
			else h += 11 + 11 + 11 + 12 + 11 + 11;                    // cost/sale/profit/XP/пометка/продаж-нед
		}
		h += SETTINGS_H;             // полоса настроек — всегда внизу
		if (RynConfig.fusionTrackerEnabled) h += TRACKER_H;
		h += 8;                      // нижний отступ
		return h;
	}

	/**
	 * Масштаб, при котором панель влезает в экран целиком.
	 *
	 * Раньше высота просто обрезалась по экрану, а настройки с трекером всё
	 * равно рисовались от низа обрезанной панели — и наезжали на итоги, стоило
	 * дереву фьюза стать длиннее (например при переключении на buy-offer).
	 *
	 * Теперь вместо обрезки ужимаем всю панель. Мельче — зато ничего не
	 * перекрывает друг друга и видно целиком.
	 */
	private static float effectiveScale(Screen screen, int naturalH) {
		float want = RynConfig.panelScale;
		if (naturalH <= 0) return want;
		float fit = (screen.height - 8f) / naturalH;
		return Math.min(want, fit);
	}

	/** Левый край окна контейнера (точно — через миксин; иначе оценка по 176). */
	private static int containerLeft(Screen s) {
		if (s instanceof com.ryn.skyryn.mixin.ContainerScreenAccessor a) return a.skyryn$leftPos();
		return (s.width - CONTAINER_W) / 2;
	}
	/** Верх окна контейнера. */
	private static int containerTop(Screen s) {
		if (s instanceof com.ryn.skyryn.mixin.ContainerScreenAccessor a) return a.skyryn$topPos();
		return 20;
	}

	/** X панели. Свёрнуто — иконка ПРИКРЕПЛЕНА слева от контейнера (не таскается);
	 *  развёрнуто — сохранённая позиция (свободно таскается) либо авто слева. */
	private static int panelX(Screen screen) {
		if (collapsed) {
			int sz = (int) (COLLAPSED_SZ * lastScale);
			return Math.max(2, containerLeft(screen) - sz - 1);   // вплотную к левому краю /hb
		}
		int w = (int) (PANEL_WIDTH * lastScale);
		if (RynConfig.panelX >= 0) return Math.max(0, Math.min(RynConfig.panelX, screen.width - w));
		return Math.max(4, containerLeft(screen) - w - 10);
	}

	/** Y панели. Свёрнуто — по верху контейнера; развёрнуто — сохранённая либо центр. */
	private static int panelY(Screen screen) {
		if (collapsed) return containerTop(screen);
		int h = (int) (lastPanelH * lastScale);
		if (RynConfig.panelY >= 0) return Math.max(0, Math.min(RynConfig.panelY, Math.max(0, screen.height - h)));
		return Math.max(4, (screen.height - h) / 2);
	}

	private static void handleKey(int key) {
		if (activeField == 1) {
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!shardInput.isEmpty()) shardInput = shardInput.substring(0, shardInput.length() - 1);
			} else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_TAB) {
				activeField = 2; // переход к количеству
			} else {
				String ch = keyToChar(key);
				if (ch != null) shardInput += ch;
			}
		} else if (activeField == 2) {
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!amountInput.isEmpty()) amountInput = amountInput.substring(0, amountInput.length() - 1);
			} else if (key == GLFW.GLFW_KEY_ENTER) {
				applyInput();
				activeField = 0;
			} else if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
				amountInput += (char) ('0' + (key - GLFW.GLFW_KEY_0));
			}
		} else if (activeField == 3) {
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!crocInput.isEmpty()) crocInput = crocInput.substring(0, crocInput.length() - 1);
				applyCroc();
			} else if (key == GLFW.GLFW_KEY_ENTER) {
				applyCroc();
				activeField = 0;
			} else if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
				if (crocInput.length() < 2) crocInput += (char) ('0' + (key - GLFW.GLFW_KEY_0));
				applyCroc();
			}
		} else if (activeField == 4) {
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!fortuneInput.isEmpty()) fortuneInput = fortuneInput.substring(0, fortuneInput.length() - 1);
				applyFortune();
			} else if (key == GLFW.GLFW_KEY_ENTER) {
				applyFortune();
				activeField = 0;
			} else if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
				if (fortuneInput.length() < 4) fortuneInput += (char) ('0' + (key - GLFW.GLFW_KEY_0));
				applyFortune();
			}
		}
	}

	/** Переводит код клавиши в букву (a-z, пробел). Для поиска шарда. */
	private static String keyToChar(int key) {
		if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
			return String.valueOf((char) ('a' + (key - GLFW.GLFW_KEY_A)));
		}
		if (key == GLFW.GLFW_KEY_SPACE) return " ";
		return null;
	}

	/** Полностью сбрасывает ветку фьюза: поля, расчёт и галочки покупок. */
	private static void clearAll() {
		FusionState.set(null, 0);
		shardInput = "";
		amountInput = "";
		activeField = 0;
		purchased.clear();
	}

	/**
	 * Применяет введённую Hunter Fortune. Обычно её читает {@link HuntingFortune} из
	 * Stats Breakdown, но в ironman время фарма считается именно по ней, а меню со
	 * статами открывают не каждый заход — поэтому число можно вбить руками прямо здесь.
	 */
	private static void applyFortune() {
		int v = 0;
		if (!fortuneInput.isEmpty()) {
			try { v = Integer.parseInt(fortuneInput); } catch (NumberFormatException e) { return; }
		}
		v = Math.max(0, Math.min(5000, v));
		if (Math.abs(v - RynConfig.hunterFortune) > 0.01f) {
			RynConfig.hunterFortune = v;
			HuntingFortune.readAt = System.currentTimeMillis();   // число свежее: его только что ввели
			ConfigManager.save();
			cachedResultSig = null;   // время фарма зависит от фортуны — пересчитать
		}
	}

	/** Применяет введённый уровень Crocodile: 0..10, мусор игнорируем. */
	private static void applyCroc() {
		int lvl = 0;
		if (!crocInput.isEmpty()) {
			try { lvl = Integer.parseInt(crocInput); } catch (NumberFormatException e) { return; }
		}
		lvl = Math.max(0, Math.min(RynConfig.CROCODILE_MAX, lvl));
		if (lvl != RynConfig.crocodileLevel) {
			RynConfig.crocodileLevel = lvl;
			ConfigManager.save(); // чтобы не слетал после перезахода
			cachedResultSig = null;   // сразу пересчитать под новый уровень Crocodile
		}
	}

	/** Применяет введённые шард+количество к состоянию. */
	private static void applyInput() {
		String shard = shardInput.trim().toLowerCase();
		int amount;
		try { amount = Integer.parseInt(amountInput.trim()); } catch (Exception e) { amount = 0; }
		if (ShardDb.hasRecipe(shard) && amount > 0) {
			FusionState.set(shard, amount);
			lastStateVersion = FusionState.version; // это наш ввод — не перезагружать поля
			purchased.clear(); // новый расчёт — сбрасываем галочки
		}
	}

	/** Клики и перетаскивание. Мышь переводим в локальные координаты панели. */
	private static void handleMouse(Screen screen, int mouseX, int mouseY) {
		long window = Minecraft.getInstance().getWindow().handle();
		boolean mouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		// Тот же масштаб, что и на отрисовке: панель могла ужаться под экран,
		// и если считать мышь по настроечному, клики уедут мимо кнопок.
		float s = lastScale;
		int px = panelX(screen);
		int py = panelY(screen);
		double lmx = (mouseX - px) / s;
		double visY = (mouseY - py) / s;                       // видимая координата в панели
		double lmy = visY + (collapsed ? 0 : scrollY);          // координата контента (в ней клик-зоны)
		boolean overPanel = lmx >= 0 && lmx <= PANEL_WIDTH && visY >= 0 && visY <= lastPanelH;

		if (mouseDown && !wasMouseDown) {
			boolean hitZone = false;
			if (overPanel) {
				// Кликзоны СНАЧАЛА, перенос — потом (иначе кнопки шапки превращались бы в drag).
				for (ClickZone z : clickZones) {
					if (z.contains(lmx, lmy)) { z.action.run(); hitZone = true; break; }
				}
				// Клик по пустому месту шапки — тащим панель (свёрнуто там иконка, не тащим).
				if (!hitZone && !collapsed && lmy >= 0 && lmy <= HEADER_H) {
					dragging = true;
					dragOffsetX = mouseX - px;
					dragOffsetY = mouseY - py;
				}
			}
			// Клик мимо всех полей и кнопок — снимаем фокус, чтобы буквы не летели в поиск.
			if (!hitZone && !dragging && activeField != 0) activeField = 0;
		}

		if (dragging) {
			if (mouseDown) {
				RynConfig.panelX = mouseX - dragOffsetX;
				RynConfig.panelY = mouseY - dragOffsetY;
			} else {
				dragging = false;
				ConfigManager.save(); // отпустили — запомнили позицию
			}
		}

		wasMouseDown = mouseDown;
	}

	// ===== Примитивы отрисовки =====

	/** Прямоугольник со «скруглёнными» углами — срезаем по пикселю. */
	private static void roundRect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1 + 1, y1, x2 - 1, y2, color);
		ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	/** Рамка в 1px со скруглением. */
	private static void roundOutline(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1 + 1, y1, x2 - 1, y1 + 1, color);  // верх
		ctx.fill(x1 + 1, y2 - 1, x2 - 1, y2, color);  // низ
		ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);  // лево
		ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);  // право
	}

	/** Текст, выровненный по правому краю. */
	private static void textRight(GuiGraphicsExtractor ctx, Font tr, String s, int rightX, int y, int color) {
		ctx.text(tr, s, rightX - tr.width(s), y, color, true);
	}

	/** Строка "подпись — значение": подпись слева приглушённо, значение справа. */
	private static void statRow(GuiGraphicsExtractor ctx, Font tr, String label, String value,
								int x, int rightX, int y, int valueColor) {
		ctx.text(tr, label, x, y, TEXT_DIM, true);
		textRight(ctx, tr, value, rightX, y, valueColor);
	}

	private static boolean inBox(double mx, double my, int x1, int y1, int x2, int y2) {
		return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
	}

	/** Кнопка со скруглением и ховером. */
	private static void button(GuiGraphicsExtractor ctx, Font tr, String label,
							   int x, int y, int w, int h, double mx, double my,
							   int base, int hover, int borderColor) {
		boolean isHover = inBox(mx, my, x, y, x + w, y + h);
		roundRect(ctx, x, y, x + w, y + h, isHover ? hover : base);
		roundOutline(ctx, x, y, x + w, y + h, borderColor);
		ctx.text(tr, label, x + (w - tr.width(label)) / 2, y + (h - 8) / 2, TEXT, true);
	}

	/** Кнопка-чип с кликзоной. Возвращает X, где начинается следующая. */
	private static int chip(GuiGraphicsExtractor ctx, Font tr, String label, boolean active,
							int x, int y, double mx, double my, Runnable action) {
		int w = tr.width(label) + 10;
		boolean hover = inBox(mx, my, x, y, x + w, y + 12);
		roundRect(ctx, x, y, x + w, y + 12, hover || active ? SURFACE_HI : SURFACE);
		roundOutline(ctx, x, y, x + w, y + 12, hover || active ? ACCENT : BORDER);
		ctx.text(tr, label, x + 5, y + 2, hover || active ? TEXT : TEXT_DIM, true);
		clickZones.add(new ClickZone(x, y, x + w, y + 12, action));
		return x + w + 4;
	}

	/**
	 * Открывает наш экран. Через schedule: setScreen прямо из отрисовки — верный
	 * способ уронить кадр на полпути.
	 *
	 * Fusion Box при этом закроется — экран в игре может быть только один.
	 */
	private static void openScreen(Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		mc.schedule(() -> mc.setScreen(screen));
	}

	/** Все шарды-входы дерева текущего фьюза (для подсветки слотов в /hb и Fusion Box). */
	private static java.util.Set<String> inputSet(FusionCalculator.Result r) {
		java.util.Set<String> s = new java.util.HashSet<>();
		if (r != null) for (FusionCalculator.Step st : r.steps) s.addAll(st.inputs.keySet());
		return s;
	}

	private static void renderPanel(GuiGraphicsExtractor ctx, Screen screen, int mouseX, int mouseY) {
		clickZones.clear();
		Font tr = Minecraft.getInstance().font;

		// Состояние сменили извне (кнопка «в калькулятор» из гайда) — подтянуть поля ввода.
		if (FusionState.version != lastStateVersion) {
			lastStateVersion = FusionState.version;
			shardInput = FusionState.currentShard == null ? "" : FusionState.currentShard;
			amountInput = FusionState.currentAmount > 0 ? String.valueOf(FusionState.currentAmount) : "";
			activeField = 0;
			scrollY = 0;   // новый расчёт — прокрутку в начало
		}

		// Дерево фьюза строит FusionCalculator (денежный путь) для ОБОИХ профилей.
		// Ironman-время считается отдельным планом (кэш, не каждый кадр); тяжёлый
		// расчёт кэшируем по сигнатуре.
		frameResult = null;
		String shard = FusionState.currentShard;
		if (shard != null && ShardDb.hasRecipe(shard)) {
			BazaarPrices.refreshIfNeeded();
			if (BazaarPrices.isLoaded()) {
				String sig = FusionState.version + "|" + BazaarPrices.version() + "|" + RynConfig.useInstaBuy
						+ "|" + RynConfig.crocodileLevel + "|" + RynConfig.bazaarTaxPercent()
						+ "|" + RynConfig.ironman + "|" + RynConfig.hunterFortune;
				if (!sig.equals(cachedResultSig)) {
					cachedResult = FusionCalculator.calculate(shard, FusionState.currentAmount,
							FusionState.forcedTopA, FusionState.forcedTopB);
					cachedResultSig = sig;
					cachedHighlight = inputSet(cachedResult);
					// Ironman: время фарма считаем тут же (кэш), не каждый кадр.
					if (RynConfig.ironman) {
						FusionPlanner.Plan p = FusionPlanner.plan(shard, FusionState.currentAmount, true);
						cachedIronTotal = p.total;
						cachedIronUnfarmable = p.hasUnfarmable;
						cachedIronBuy = p.hasBuy;
					}
				}
				frameResult = cachedResult;
			}
		}
		// Подсветка слотов бокса = входы текущего рецепта калькулятора (меняется с рецептом).
		highlightInputs = frameResult != null ? cachedHighlight : java.util.Set.of();

		int fullH = panelHeight(screen);
		int drawnH;
		if (collapsed) {
			drawnH = fullH; scrollY = 0;
		} else {
			// Не растягиваем панель на весь экран: видимую высоту ограничиваем, лишнее — скроллом.
			int maxH = Math.max(80, (int) ((screen.height - 8) / RynConfig.panelScale));
			drawnH = Math.min(fullH, maxH);
			int maxScroll = Math.max(0, fullH - drawnH);
			scrollY = Math.max(0, Math.min(scrollY, maxScroll));
		}
		lastFullH = fullH;
		lastDrawnH = drawnH;
		lastPanelH = drawnH;     // позиция/мышь — по ВИДИМОЙ высоте

		float s = effectiveScale(screen, drawnH);
		lastScale = s;
		int px = panelX(screen);
		int py = panelY(screen);
		double mx = (mouseX - px) / s;                             // мышь в локальных координатах
		double my = (mouseY - py) / s + (collapsed ? 0 : scrollY);  // ...с учётом прокрутки контента

		ctx.pose().pushMatrix();
		ctx.pose().translate(px, py);
		ctx.pose().scale(s, s);
		if (collapsed) {
			drawContent(ctx, tr, drawnH, mx, my);
		} else {
			drawFrame(ctx, drawnH);                                // каркас — по видимой высоте (фиксирован)
			// Контент рисуем со сдвигом прокрутки, обрезая scissor'ом по видимому окну.
			// enableScissor учитывает текущий pose (translate+scale) — координаты локальные.
			ctx.enableScissor(0, 2, PANEL_WIDTH, drawnH - 1);
			ctx.pose().pushMatrix();
			ctx.pose().translate(0, -scrollY);
			drawContent(ctx, tr, fullH, mx, my);
			ctx.pose().popMatrix();
			ctx.disableScissor();
			drawScrollbar(ctx, drawnH, fullH);
		}
		ctx.pose().popMatrix();
	}

	/** Каркас панели: тень, фон-градиент, рамка, акцентная полоса — по видимой высоте h. */
	private static void drawFrame(GuiGraphicsExtractor ctx, int h) {
		int right = PANEL_WIDTH;
		roundRect(ctx, 3, 3, right + 3, h + 3, SHADOW);
		ctx.fillGradient(1, 0, right - 1, h, BG_TOP, BG_BOTTOM);
		ctx.fill(0, 1, 1, h - 1, BG_TOP);
		ctx.fill(right - 1, 1, right, h - 1, BG_BOTTOM);
		roundOutline(ctx, 0, 0, right, h, BORDER);
		ctx.fill(1, 1, right - 1, 2, ACCENT);
	}

	/** Полоска-скроллбар справа, если контент выше видимой области. */
	private static void drawScrollbar(GuiGraphicsExtractor ctx, int drawnH, int fullH) {
		int maxScroll = fullH - drawnH;
		if (maxScroll <= 0) return;
		int top = 4, bot = drawnH - 4, trackH = bot - top;
		if (trackH < 12) return;
		int thumbH = Math.max(12, (int) ((long) trackH * drawnH / fullH));
		int thumbY = top + (int) ((long) (trackH - thumbH) * scrollY / maxScroll);
		int x = PANEL_WIDTH - 3;
		ctx.fill(x, top, x + 2, bot, SHADOW);
		ctx.fill(x, thumbY, x + 2, thumbY + thumbH, ACCENT);
	}

	/** Мышь над видимой областью панели (в экранных координатах). */
	private static boolean overPanel(Screen screen, double sx, double sy) {
		double lx = (sx - panelX(screen)) / lastScale;
		double ly = (sy - panelY(screen)) / lastScale;
		return lx >= 0 && lx <= PANEL_WIDTH && ly >= 0 && ly <= lastPanelH;
	}

	/** Масштаб последнего кадра — мышь считается после отрисовки, ей нужен тот же. */
	private static float lastScale = 1f;

	/** Рисует панель в локальных координатах: (0,0) — её левый верхний угол. */
	private static void drawContent(GuiGraphicsExtractor ctx, Font tr, int panelH, double mx, double my) {
		// Свёрнуто — маленькая плашка-иконка (клик разворачивает).
		if (collapsed) {
			int sz = COLLAPSED_SZ;
			boolean hov = inBox(mx, my, 0, 0, sz, sz);
			roundRect(ctx, 2, 2, sz + 2, sz + 2, SHADOW);
			roundRect(ctx, 0, 0, sz, sz, hov ? SURFACE_HI : SURFACE);
			roundOutline(ctx, 0, 0, sz, sz, hov ? ACCENT : BORDER);
			// Рисуем мини-иконку калькулятора: «экран» + сетка кнопок.
			int col = hov ? TEXT : TEXT_DIM;
			ctx.fill(4, 4, sz - 4, 7, col);
			for (int ry : new int[] { 10, 14 })
				for (int rx : new int[] { 5, 9, 13 })
					ctx.fill(rx, ry, rx + 2, ry + 2, col);
			clickZones.add(new ClickZone(0, 0, sz, sz, FusionPanel::toggleCollapse));
			return;
		}

		// Каркас панели (тень/фон/рамка/акцент) рисует drawFrame по ВИДИМОЙ высоте —
		// здесь только контент, который может скроллиться под обрезкой.
		int right = PANEL_WIDTH;
		int textX = 10;
		int rightX = right - 10;
		int y = 5;

		// ===== Шапка: кнопки экранов слева, возраст цен справа =====
		// Названия мода тут нет намеренно: панель и так наша, а место в шапке
		// полезнее отдать кнопкам. За пустое место шапки панель таскается.
		y = 3;
		int navX = chip(ctx, tr, Lang.tr("Shards", "Шарды"), false, textX, y, mx, my,
				() -> openScreen(new ShardListScreen()));
		navX = chip(ctx, tr, Lang.tr("Top", "Топ"), false, navX, y, mx, my,
				() -> openScreen(new FusionTopScreen()));
		// Кнопка сворачивания панели до иконки.
		chip(ctx, tr, "▾", false, navX, y, mx, my, FusionPanel::toggleCollapse);

		String age = BazaarPrices.ageText();
		int aW = tr.width(age) + 6;
		boolean aHover = inBox(mx, my, rightX - aW, y, rightX, y + 12);
		textRight(ctx, tr, age, rightX, y + 2, aHover ? ACCENT : TEXT_FAINT);
		clickZones.add(new ClickZone(rightX - aW, y, rightX, y + 12, BazaarPrices::forceRefresh));

		y = HEADER_H;
		ctx.fill(textX, y, rightX, y + 1, DIVIDER);
		y += 6;

		// ===== Поле Lang.tr("Shard", "Шард") =====
		int boxW = PANEL_WIDTH - 18 - 15; // оставляем место под крестик
		int shardBoxY = y;
		drawInputBox(ctx, tr, textX, y, boxW, shardInput, Lang.tr("shard", "шард"), activeField == 1, () -> activeField = 1);

		// Крестик очистки справа от поля шарда
		int xBtnX = textX + boxW + 3;
		boolean xHover = inBox(mx, my, xBtnX, shardBoxY, xBtnX + 12, shardBoxY + 12);
		roundRect(ctx, xBtnX, shardBoxY, xBtnX + 12, shardBoxY + 12, xHover ? SURFACE_HI : SURFACE);
		roundOutline(ctx, xBtnX, shardBoxY, xBtnX + 12, shardBoxY + 12, xHover ? RED : BORDER);
		ctx.text(tr, "✕", xBtnX + 3, shardBoxY + 2, xHover ? TEXT : TEXT_FAINT, true);
		clickZones.add(new ClickZone(xBtnX, shardBoxY, xBtnX + 12, shardBoxY + 12, () -> {
			shardInput = "";
			activeField = 0; // крестик — снимаем фокус, чтобы буквы не летели в поиск
		}));
		y += 14;

		// ===== Автодополнение =====
		if (activeField == 1 && !shardInput.isEmpty()) {
			String q = shardInput.toLowerCase();
			int shown = 0;
			for (String craftable : ShardDb.allCraftable()) {
				if (!craftable.startsWith(q)) continue;
				if (shown >= 5) break; // не растягиваем список бесконечно
				final String suggestion = craftable;
				boolean hover = inBox(mx, my, textX, y, textX + boxW, y + 10);
				if (hover) roundRect(ctx, textX, y, textX + boxW, y + 10, ACCENT_SOFT);
				ctx.text(tr, craftable, textX + 4, y + 1, hover ? TEXT : TEXT_DIM, true);
				clickZones.add(new ClickZone(textX, y, textX + boxW, y + 10, () -> {
					shardInput = suggestion;
					activeField = 2;
				}));
				y += 10;
				shown++;
			}
			y += 2;
		}

		// ===== Поле Lang.tr("Amount", "Количество") + кнопка =====
		drawInputBox(ctx, tr, textX, y, boxW, amountInput, Lang.tr("amount", "кол-во"), activeField == 2, () -> activeField = 2);
		y += 15;

		int clearW = 40;
		int calcW = (rightX - textX) - clearW - 4;
		button(ctx, tr, Lang.tr("Calculate", "Рассчитать"), textX, y, calcW, 13, mx, my, ACCENT_SOFT, ACCENT, ACCENT);
		clickZones.add(new ClickZone(textX, y, textX + calcW, y + 13, FusionPanel::applyInput));

		int clearX = textX + calcW + 4;
		button(ctx, tr, "Clear", clearX, y, clearW, 13, mx, my, SURFACE, SURFACE_HI, BORDER);
		clickZones.add(new ClickZone(clearX, y, clearX + clearW, y + 13, FusionPanel::clearAll));
		y += 18;

		// ===== Результат =====
		String shard = FusionState.currentShard;
		if (shard == null || !ShardDb.hasRecipe(shard)) {
			renderBottom(ctx, tr, textX, rightX, panelH, mx, my);
			return;
		}
		if (frameResult == null) {
			// Базар недоступен/грузится — не залипаем на "Loading", показываем
			// рецепты шарда из данных (+ ловится ли напрямую). Ценам это не нужно.
			drawBazaarFallback(ctx, tr, shard, textX, rightX, y, mx, my);
			renderBottom(ctx, tr, textX, rightX, panelH, mx, my);
			return;
		}

		FusionCalculator.Result res = frameResult;

		boolean iron = RynConfig.ironman;
		sectionHeader(ctx, tr, iron ? Lang.tr("FARM", "ФАРМ") : Lang.tr("BUY", "КУПИТЬ"), textX, y);
		y += 12;

		for (Map.Entry<String, Integer> e : res.shoppingList.entrySet()) {
			String shardKey = e.getKey();
			boolean bought = purchased.contains(shardKey.toLowerCase());
			boolean hover = inBox(mx, my, textX, y - 1, rightX, y + BUY_ROW_H - 2);

			// Подсветка строки под курсором
			if (hover && !bought) roundRect(ctx, textX - 3, y - 2, rightX, y + BUY_ROW_H - 3, SURFACE_HI);

			// Индикатор ликвидности — вертикальная полоска слева
			int liqColor = TEXT_FAINT;
			String iid = ShardDb.bazaarId(shardKey);
			if (iid != null) {
				BazaarPrices.Price pr = BazaarPrices.get(iid);
				if (pr != null) {
					if (pr.sellVolume > 500_000) liqColor = LIQ_HI;
					else if (pr.sellVolume > 100_000) liqColor = LIQ_MID;
					else liqColor = LIQ_LOW;
				}
			}
			ctx.fill(textX, y, textX + 2, y + 8, bought ? TEXT_FAINT : liqColor);

			// Первая строка: количество + название слева, общая цена справа
			final int qty = e.getValue();
			final String dispName = ShardDb.displayName(shardKey);
			String label = (bought ? "✔ " : "") + qty + "x " + dispName;
			double unit = FusionCalculator.unitBuyPrice(shardKey);
			// Ironman: без цен (фармим, не покупаем) — имя во всю ширину.
			int labelW = (iron || unit == Double.MAX_VALUE)
					? (rightX - textX - 6) : (rightX - textX - 6) - (tr.width(fmt(unit * qty)) + 6);
			ctx.text(tr, fit(tr, label, labelW), textX + 6, y, bought ? TEXT_FAINT : TEXT, true);

			if (!iron && unit != Double.MAX_VALUE) {
				textRight(ctx, tr, fmt(unit * qty), rightX, y, bought ? TEXT_FAINT : TEXT_DIM);
				// Вторая строка: цена за штуку, приглушённо
				ctx.text(tr, fmt(unit) + Lang.tr("/pc", "/шт"), textX + 6, y + 9, TEXT_FAINT, true);
			}

			final String bz = ShardDb.bazaarName(shardKey);
			final String keyLower = shardKey.toLowerCase();
			clickZones.add(new ClickZone(textX, y - 1, rightX, y + BUY_ROW_H - 2, () -> {
				// Ironman: базара нет, вести на страницу шарда — оттуда видно
				// способы добычи и можно отслеживать. Обычному игроку — базар.
				if (RynConfig.ironman) {
					openScreen(new ShardPageScreen(keyLower, null));
				} else {
					BazaarHint.remember(dispName, qty);
					openBazaar(bz);
					purchased.add(keyLower);
				}
			}));
			y += BUY_ROW_H;
		}

		y += 3;
		ctx.fill(textX, y, rightX, y + 1, DIVIDER);
		y += 5;

		// ===== Шаги фьюжена =====
		// Список покупок не объясняет, ЧТО с этим делать. Показываем всю ветку
		// в порядке выполнения: сначала промежуточные шарды, целевой — последним.
		sectionHeader(ctx, tr, Lang.tr("STEPS", "ШАГИ"), textX, y);
		y += 12;

		int stepNo = 1;
		for (FusionCalculator.Step st : res.steps) {
			ctx.text(tr, stepNo + ".", textX, y, TEXT_FAINT, true);

			// Входы: одной строкой, если влезают, иначе переносим — обрезать нельзя,
			// "400x Praying ManLang.tr(" instead of ", " вместо ")Praying Mantis" хуже, чем лишняя строка.
			String inputs = stepInputs(st);
			int inX = textX + 12;
			int availW = rightX - inX;
			if (stepLines(tr, st) == 2) {
				ctx.text(tr, fit(tr, inputs, availW), inX, y, TEXT, true);
				y += 10;
			} else {
				boolean first = true;
				for (String part : stepInputParts(st)) {
					String line = (first ? "" : "+ ") + part;
					ctx.text(tr, fit(tr, line, availW), inX, y, TEXT, true);
					y += 10;
					first = false;
				}
			}

			ctx.text(tr, fit(tr, "→ " + st.outputAmount + "x " + ShardDb.displayName(st.output), availW),
					inX, y, TEXT_DIM, true);
			y += 10;
			stepNo++;
		}

		y += 2;
		ctx.fill(textX, y, rightX, y + 1, DIVIDER);
		y += 7;

		// ===== Итог =====
		if (iron) {
			// Ironman: денег нет — только время фарма (движок времени FusionPlanner).
			String t = cachedIronTotal > 0 ? fmtHours(cachedIronTotal) : "—";
			if (cachedIronUnfarmable) t += Lang.tr(" + boss/dungeon", " + боссы/данж");
			statRow(ctx, tr, Lang.tr("Farm time", "Время фарма"), "~" + t, textX, rightX, y, TEXT_DIM);
			y += 11;
			if (cachedIronBuy) { statRow(ctx, tr, "", Lang.tr("🛒 some from NPC", "🛒 часть у NPC"), textX, rightX, y, GREEN); y += 11; }
		} else {
			statRow(ctx, tr, Lang.tr("Cost", "Стоимость"), fmt(res.totalCost), textX, rightX, y, TEXT_DIM);
			y += 11;
			statRow(ctx, tr, Lang.tr("Sale", "Продажа"), fmt(res.sellRevenue), textX, rightX, y, TEXT_DIM);
			y += 11;
			statRow(ctx, tr, Lang.tr("Profit", "Профит"), fmt(res.profit), textX, rightX, y, res.profit >= 0 ? GREEN : RED);
			y += 11;
		}

		// XP за всю ветку, включая промежуточные фьюзы — их обычно большинство.
		// Без wisdom считать нечего: множитель у каждого свой.
		int fusions = FusionXp.fusionsInBranch(res.steps);
		if (RynConfig.huntingWisdom > 0) {
			statRow(ctx, tr, "XP (" + fusions + Lang.tr(" fusions)", " фьюзов)"), fmt(FusionXp.forBranch(res.steps)),
					textX, rightX, y, TEXT_DIM);
		} else {
			statRow(ctx, tr, "XP (" + fusions + Lang.tr(" fusions)", " фьюзов)"), Lang.tr("enter wisdom", "укажи wisdom"), textX, rightX, y, TEXT_FAINT);
		}
		y += 12;

		// Reptile-пометка: этот путь тратит reptile-шарды, значит выход зависит от уровня
		// Crocodile. Одного слова достаточно — длинное пояснение в панели только мешает.
		if (fusionUsesReptile(res)) {
			ctx.text(tr, "Reptile", textX, y, WARN, true);
			y += 11;
		}

		// Если цене целевого шарда верить нельзя — профит выше посчитан по ней же,
		// поэтому честнее сказать об этом прямо здесь, а не только в /rftop.
		String bid = ShardDb.bazaarId(shard);
		BazaarPrices.Price tp = bid == null ? null : BazaarPrices.get(bid);
		// Ликвидность: сколько шардов раскупают за неделю — можно ли реально продать.
		if (!RynConfig.ironman && tp != null) {
			statRow(ctx, tr, Lang.tr("Sold/week", "Продаж/нед"), fmt(tp.sellMovingWeek), textX, rightX, y, TEXT_DIM);
			y += 11;
		}
		if (tp != null && tp.warning().isBad()) {
			ctx.text(tr, fit(tr, "⚠ " + tp.warning().tag(), rightX - textX), textX, y, WARN, true);
		}

		renderBottom(ctx, tr, textX, rightX, panelH, mx, my);
	}

	/** Всё, что прибито к низу панели: настройки расчёта и трекер. */
	private static void renderBottom(GuiGraphicsExtractor ctx, Font tr, int textX, int rightX,
									 int panelH, double mx, double my) {
		renderSettings(ctx, tr, textX, rightX, panelH, mx, my);
		renderTracker(ctx, tr, textX, rightX, panelH, mx, my);
	}

	/**
	 * Обрезает строку по ширине с многоточием. Страховка: длинное имя шарда
	 * не должно вылезать за панель, даже если я где-то не угадал с раскладкой.
	 */
	private static String fit(Font tr, String s, int maxW) {
		if (tr.width(s) <= maxW) return s;
		return tr.plainSubstrByWidth(s, maxW - tr.width("…")) + "…";
	}

	/** Заголовок секции: мелкий, приглушённый, без крика. */
	private static void sectionHeader(GuiGraphicsExtractor ctx, Font tr, String s, int x, int y) {
		ctx.text(tr, s, x, y, TEXT_FAINT, true);
	}

	/** Тратит ли путь фьюза reptile-шарды (тогда выход множится на Crocodile). */
	private static boolean fusionUsesReptile(FusionCalculator.Result r) {
		if (r == null) return false;
		for (FusionCalculator.Step st : r.steps)
			for (String in : st.inputs.keySet()) {
				ShardDb.Shard sh = ShardDb.shard(in);
				if (sh != null && sh.reptile) return true;
			}
		return false;
	}

	/**
	 * Настройки расчёта — закреплены внизу, над трекером.
	 *
	 * Внизу они потому, что так у skyshards: сверху то, что вводишь каждый раз,
	 * снизу то, что выставил один раз и забыл. Раньше они занимали лучшее место
	 * на панели, хотя трогаешь их раз в месяц.
	 */
	private static void renderSettings(GuiGraphicsExtractor ctx, Font tr, int textX, int rightX,
									   int panelH, double mx, double my) {
		int y = panelH - (RynConfig.fusionTrackerEnabled ? TRACKER_H : 0) - SETTINGS_H;
		int x = textX;

		// insta-buy — только для normal (в ironman базара нет).
		if (!RynConfig.ironman) {
			String mode = RynConfig.useInstaBuy ? "insta-buy" : "buy-offer";
			int mW = tr.width(mode) + 10;
			boolean mHover = inBox(mx, my, x, y, x + mW, y + 12);
			roundRect(ctx, x, y, x + mW, y + 12, mHover ? SURFACE_HI : SURFACE);
			roundOutline(ctx, x, y, x + mW, y + 12, mHover ? ACCENT : BORDER);
			ctx.text(tr, mode, x + 5, y + 2, mHover ? TEXT : TEXT_DIM, true);
			clickZones.add(new ClickZone(x, y, x + mW, y + 12, () -> {
				RynConfig.useInstaBuy = !RynConfig.useInstaBuy;
				ConfigManager.save();
				cachedResultSig = null;   // сразу пересчитать, не заставляя перезабивать
			}));
			x += mW + 6;
		}

		// Hunter Fortune — только в ironman: там от неё зависит время фарма, а в normal
		// считаются деньги, и число ни на что не влияет. Обычно его приносит HuntingFortune
		// из Stats Breakdown, но меню открывают не каждый заход — здесь можно вбить руками.
		if (RynConfig.ironman) {
			ctx.text(tr, "fortune", x, y + 2, TEXT_FAINT, true);
			int fBoxX = x + tr.width("fortune") + 3;
			drawInputBox(ctx, tr, fBoxX, y, 30, fortuneInput, "0", activeField == 4, () -> activeField = 4);
			x = fBoxX + 30 + 6;
		}

		// Crocodile — уровень вводится с клавиатуры.
		ctx.text(tr, "crocodile", x, y + 2, TEXT_FAINT, true);
		int crocBoxX = x + tr.width("crocodile") + 3;
		drawInputBox(ctx, tr, crocBoxX, y, 22, crocInput, "0", activeField == 3, () -> activeField = 3);

		// Профиль — только показ (переключается в /sr), без тумблера.
		String prof = RynConfig.ironman ? "ironman" : "normal";
		ctx.text(tr, prof, rightX - tr.width(prof), y + 2, RynConfig.ironman ? WARN : TEXT_FAINT, true);
	}

	/** Трекер сессии — закреплён у нижнего края панели. */
	private static void renderTracker(GuiGraphicsExtractor ctx, Font tr, int textX, int rightX,
									  int panelH, double mx, double my) {
		if (!RynConfig.fusionTrackerEnabled) return;

		int y = panelH - TRACKER_H;

		ctx.fill(textX, y, rightX, y + 1, DIVIDER);
		y += 6;

		// Заголовок + переключатель режима справа
		sectionHeader(ctx, tr, Lang.tr("TRACKER", "ТРЕКЕР"), textX, y);
		String mode = "[" + RynConfig.trackerModeName() + "]";
		int mw = tr.width(mode);
		boolean mHover = inBox(mx, my, rightX - mw - 3, y - 1, rightX, y + 9);
		ctx.text(tr, mode, rightX - mw, y, mHover ? TEXT : TEXT_DIM, true);
		clickZones.add(new ClickZone(rightX - mw - 3, y - 1, rightX, y + 9, () -> {
			RynConfig.cycleTrackerMode();
			ConfigManager.save();
		}));
		y += 12;

		// Значения зависят от режима. Профит во всех режимах считается по целевому
		// шарду за вычетом себестоимости материалов — см. FusionTracker.
		String fusions, shards, xp;
		double profit;
		switch (RynConfig.trackerMode) {
			case RynConfig.TRACKER_TOTAL -> {
				fusions = String.valueOf(FusionTracker.totalFusions);
				shards = String.valueOf(FusionTracker.totalShardsObtained);
				xp = fmt(FusionTracker.totalFusionXp);
				profit = FusionTracker.totalProfit();
			}
			case RynConfig.TRACKER_PER_HOUR -> {
				fusions = String.format("%.0f", FusionTracker.fusionsPerHour());
				shards = String.format("%.0f", FusionTracker.shardsPerHour());
				xp = fmt(FusionTracker.fusionXpPerHour());
				profit = FusionTracker.profitPerHour();
			}
			default -> {
				fusions = String.valueOf(FusionTracker.sessionFusions);
				shards = String.valueOf(FusionTracker.sessionShardsObtained);
				xp = fmt(FusionTracker.sessionFusionXp);
				profit = FusionTracker.sessionProfit();
			}
		}

		statRow(ctx, tr, Lang.tr("Fusions", "Фьюзов"), fusions, textX, rightX, y, TEXT_DIM);
		y += 10;
		statRow(ctx, tr, Lang.tr("Shards", "Шардов"), shards, textX, rightX, y, TEXT_DIM);
		y += 10;
		statRow(ctx, tr, "Fusion XP", xp, textX, rightX, y, TEXT_DIM);
		y += 10;
		statRow(ctx, tr, Lang.tr("Profit", "Профит"), fmt(profit), textX, rightX, y, profit >= 0 ? GREEN : RED);
		y += 14;

		// Кнопка сброса сессии
		int w = rightX - textX;
		button(ctx, tr, Lang.tr("Reset session", "Сброс сессии"), textX, y, w, 12, mx, my, SURFACE, SURFACE_HI, BORDER);
		clickZones.add(new ClickZone(textX, y, textX + w, y + 12, FusionTracker::resetSession));
	}

	/** Форматирует число в k/M для читаемости. */
	private static String fmt(double v) {
		double a = Math.abs(v);
		if (a >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
		if (a >= 1_000) return String.format("%.1fk", v / 1_000);
		return String.format("%.0f", v);
	}

	/** Часы → «Xм» / «X.Yч» (для ironman-времени фарма). */
	private static String fmtHours(double hours) {
		if (hours < 1) return Math.max(1, Math.round(hours * 60)) + Lang.tr(" min", " мин");
		return String.format("%.1f", hours) + Lang.tr(" h", " ч");
	}

	/** Рисует поле ввода с плейсхолдером. Клик активирует поле. */
	private static void drawInputBox(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w,
									 String value, String placeholder, boolean active, Runnable onClick) {
		int h = 12;
		roundRect(ctx, x, y, x + w, y + h, active ? SURFACE_HI : SURFACE);
		roundOutline(ctx, x, y, x + w, y + h, active ? ACCENT : BORDER);

		if (value.isEmpty() && !active) {
			ctx.text(tr, placeholder, x + 4, y + 2, TEXT_FAINT, true);
		} else {
			ctx.text(tr, value, x + 4, y + 2, TEXT, true);
		}

		// Мигающая каретка вместо статичного подчёркивания
		if (active && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = x + 5 + tr.width(value);
			ctx.fill(cx, y + 2, cx + 1, y + h - 2, ACCENT);
		}

		clickZones.add(new ClickZone(x, y, x + w, y + h, onClick));
	}

	private static void openBazaar(String bazaarName) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.connection.sendCommand("bz " + bazaarName);
		}
	}
}
