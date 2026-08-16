package com.ryn.skyryn.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.ryn.skyryn.data.ShardAttribute;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.data.ShardProgress;
import com.ryn.skyryn.data.StatFilter;
import com.ryn.skyryn.fusion.BazaarPrices;
import net.minecraft.world.item.ItemStack;

/**
 * /sr shards — все шарды игры сеткой карточек.
 *
 * Клик по карточке открывает страницу шарда — это будущий гайд.
 * Здесь показываем только то, что игроку и правда нужно: имя, редкость,
 * семью, источник и прогресс аттрибута. Внутренние id (C3, U21) и
 * "direct or fuse" не пишем — это шум.
 */
public class ShardListScreen extends Screen {

	static final int BG          = 0xF0141419;
	static final int CARD        = 0xFF1A1A24;
	static final int CARD_HOVER  = 0xFF23232F;
	static final int BORDER      = 0xFF2E2E3C;
	static final int ACCENT      = 0xFF5B8DEF;
	static final int ACCENT_SOFT = 0xFF2A3F63;
	// Белый как у SkyHanni: серый текст на тёмном фоне читается тяжело,
	// а список шардов — то, что разглядывают долго.
	static final int TEXT        = 0xFFFFFFFF;
	static final int TEXT_DIM    = 0xFFC8CAD4;
	static final int TEXT_FAINT  = 0xFF9096A6;
	static final int TRACK       = 0xFF23232F;
	static final int GOLD        = 0xFFFFD24A;
	static final int GREEN       = 0xFF5FD68A;

	/** Цвета редкостей — как в игре, чтобы глаз узнавал без чтения. */
	static int rarityColor(String r) {
		if (r == null) return TEXT_DIM;
		return switch (r.toLowerCase()) {
			case "common" -> 0xFFB9BCC7;
			case "uncommon" -> 0xFF5FD68A;
			case "rare" -> 0xFF5B8DEF;
			case "epic" -> 0xFFB061E0;
			case "legendary" -> 0xFFE0A040;
			default -> TEXT_DIM;
		};
	}

	static String rarityShort(String r) {
		if (r == null || r.isEmpty()) return "?";
		return r.substring(0, 1).toUpperCase();
	}

	// Карточка выросла: на ней теперь видно, что шард даёт. Ради этого сюда
	// и заходят, а раньше за этим надо было кликать в каждый шард.
	private static final int CARD_W = 208;
	private static final int CARD_H = 58;
	private static final int GAP = 6;

	// Хаб-режим: /sr shards открывается как окно с плитками-иконками по скиллам
	// (Combat, Mining, …). Клик по плитке ведёт в шарды этого скилла. Старая
	// плоская сетка никуда не делась — переключается тумблером в шапке.
	private static final int TILE_W = 150;
	private static final int TILE_H = 78;
	private static final int TGAP = 8;
	private static final int VIEW_HUB = 0;   // окно с иконками скиллов (старт)
	private static final int VIEW_SKILL = 1; // список шардов одного скилла (есть «← назад»)
	private static final int VIEW_FLAT = 2;  // старая плоская сетка всех шардов (тумблер)
	/** Порядок плиток; реально показываем лишь те скиллы, что есть в данных. */
	private static final String[] SKILL_ORDER = {
			"Global", "Combat", "Hunting", "Mining", "Farming",
			"Foraging", "Fishing", "Taming", "Enchanting"
	};

	/** Порядок: по имени / уровень ↑ / уровень ↓. Семья — отдельный ФИЛЬТР, не сортировка. */
	private static final int SORT_NAME = 0;
	private static final int SORT_LEVEL_UP = 1;
	private static final int SORT_LEVEL_DOWN = 2;
	private static final int SORT_MODES = 3;

	private String search = "";
	private boolean searchFocused = false;
	/** Ctrl+A выделил всё: следующий ввод/вставка заменяет строку целиком. */
	private boolean searchAllSelected = false;
	private String rarityFilter = null;
	private String sourceFilter = null;
	private int sort = SORT_NAME;
	/** Показывать только не открытые — уровень 0. */
	private boolean onlyMissing = false;
	/** Фильтр по стату (null — все). Раскрыт ли список. */
	private String statFilter = null;
	private boolean statOpen = false;
	/** Экранная зона кнопки «стат» — считается при отрисовке, нужна кликам и списку. */
	private int statX = 0, statW = 0;
	/** Фильтр по семье (null — все) — как источник, но дропдауном (семей много). */
	private String familyFilter = null;
	private boolean familyOpen = false;
	private int famX = 0, famW = 0;
	private static final int STAT_FY = 52;
	private int scroll = 0;

	/** Режим восстанавливается из конфига в конструкторе (хаб или плоская сетка). */
	private int viewMode = VIEW_HUB;
	private int toggleX = 0, toggleW = 0;
	/** Экранные координаты поля поиска — считаются при отрисовке (в хабе оно по центру). */
	private int searchX = 24, searchW = 160;
	/** Скилл -> сколько у него шардов; считается один раз в init. */
	private final java.util.Map<String, Integer> skillCount = new java.util.LinkedHashMap<>();
	/** Скилл -> % прокачки: сумма уровней аттрибутов / (10 × число шардов с аттрибутом). */
	private final java.util.Map<String, Integer> skillPct = new java.util.LinkedHashMap<>();
	/** Скилл -> сколько у него шардов с прокачиваемым аттрибутом. */
	private final java.util.Map<String, Integer> skillAttrCount = new java.util.LinkedHashMap<>();
	/** Плитки хаба в порядке показа (пересечение SKILL_ORDER с реальными данными). */
	private final List<String> hubSkills = new ArrayList<>();
	/** Экранные зоны чипов «последних просмотренных» — считаются при отрисовке, нужны кликам. */
	private final java.util.List<int[]> recentChipRects = new java.util.ArrayList<>();
	private final java.util.List<int[]> recentXRects = new java.util.ArrayList<>();
	private final java.util.List<String> recentKeys = new java.util.ArrayList<>();

	private List<String> shards = new ArrayList<>();
	private final List<String> sources = new ArrayList<>();
	private final List<String> families = new ArrayList<>();

	public ShardListScreen() {
		super(Component.literal(Lang.tr("SkyRyn — Shards", "SkyRyn — Шарды")));
		// Запомненный выбор: плоская сетка или окно иконок скиллов.
		viewMode = com.ryn.skyryn.config.RynConfig.shardsFlatView ? VIEW_FLAT : VIEW_HUB;
	}

	@Override
	protected void init() {
		BazaarPrices.refreshIfNeeded();
		Set<String> src = new LinkedHashSet<>();
		java.util.TreeSet<String> fam = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (String k : ShardDb.allShards()) {
			ShardDb.Shard s = ShardDb.shard(k);
			if (s == null) continue;
			if (s.source != null && !s.source.isEmpty()) src.add(s.source);
			if (s.family != null && !s.family.isEmpty()) fam.add(s.family);
		}
		sources.clear();
		sources.addAll(src);
		families.clear();
		families.addAll(fam);

		// Счётчик шардов и % прокачки по скиллам.
		skillCount.clear();
		skillPct.clear();
		skillAttrCount.clear();
		java.util.Map<String, int[]> prog = new java.util.HashMap<>(); // скилл -> {got, max}
		for (String k : ShardDb.allShards()) {
			ShardDb.Shard s = ShardDb.shard(k);
			if (s == null || s.source == null || s.source.isEmpty()) continue;
			skillCount.merge(s.source, 1, Integer::sum);
			if (!s.hasAttribute()) continue;
			skillAttrCount.merge(s.source, 1, Integer::sum);
			int[] p = prog.computeIfAbsent(s.source, kk -> new int[2]);
			p[1] += ShardAttribute.MAX_LEVEL;
			int lvl = ShardProgress.displayLevel(k); // -1 (неизвестно) считаем как 0
			if (lvl > 0) p[0] += lvl;
		}
		for (var e : prog.entrySet()) {
			int[] p = e.getValue();
			skillPct.put(e.getKey(), p[1] > 0 ? Math.round(100f * p[0] / p[1]) : 0);
		}
		hubSkills.clear();
		for (String sk : SKILL_ORDER) if (skillCount.containsKey(sk)) hubSkills.add(sk);
		// Источник не из списка порядка — в конец, чтобы ни один шард не пропал из хаба.
		for (String sk : sources) if (!hubSkills.contains(sk)) hubSkills.add(sk);

		rebuild();
	}

	private void rebuild() {
		shards.clear();
		String q = search.toLowerCase().trim();
		for (String k : ShardDb.allShards()) {
			ShardDb.Shard s = ShardDb.shard(k);
			if (s == null) continue;
			// Ищем и по имени шарда, и по названию аттрибута: помнить, что
			// "Bucket Lover" — это Coralot, никто не обязан.
			if (!q.isEmpty() && !s.name.toLowerCase().contains(q)
					&& !(s.attrTitle != null && s.attrTitle.toLowerCase().contains(q))) continue;
			if (rarityFilter != null && !rarityFilter.equalsIgnoreCase(s.rarity)) continue;
			if (sourceFilter != null && !sourceFilter.equalsIgnoreCase(s.source)) continue;
			// Lang.tr("Unopened", "Не открытые") — уровень 0. Пока Attribute Menu не открывали,
			// displayLevel честно отдаёт -1, и список будет пуст: это правильно,
			// нам просто неоткуда знать.
			if (onlyMissing && ShardProgress.displayLevel(k) != 0) continue;
			if (statFilter != null && !StatFilter.matches(s, statFilter)) continue;
			if (familyFilter != null && !familyFilter.equalsIgnoreCase(s.family)) continue;
			shards.add(k);
		}
		shards.sort(comparator());
		// Скролл НЕ сбрасываем в 0, а лишь ужимаем в допустимый диапазон: тогда
		// возврат со страницы шарда (re-init) сохраняет позицию списка, а не
		// швыряет в начало. При смене фильтра, если позиция стала за пределом,
		// клампнется вниз сама.
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	/** Неизвестный уровень всегда в конце: сортировать по тому, чего не знаем, нельзя. */
	private java.util.Comparator<String> comparator() {
		java.util.Comparator<String> byName =
				(a, b) -> ShardDb.displayName(a).compareToIgnoreCase(ShardDb.displayName(b));
		if (sort == SORT_NAME) return byName;
		return (a, b) -> {
			int la = ShardProgress.displayLevel(a), lb = ShardProgress.displayLevel(b);
			if (la < 0 && lb < 0) return byName.compare(a, b);
			if (la < 0) return 1;
			if (lb < 0) return -1;
			int cmp = sort == SORT_LEVEL_UP ? Integer.compare(la, lb) : Integer.compare(lb, la);
			return cmp != 0 ? cmp : byName.compare(a, b);
		};
	}

	private String sortName() {
		return switch (sort) {
			case SORT_LEVEL_UP -> Lang.tr("level ↑", "уровень ↑");
			case SORT_LEVEL_DOWN -> Lang.tr("level ↓", "уровень ↓");
			default -> Lang.tr("by name", "по имени");
		};
	}

	private int gridX() { return 24; }
	private int gridTop() { return 84; }
	private int gridBottom() { return this.height - 16; }
	private int cols() { return Math.max(1, (this.width - 48 + GAP) / (CARD_W + GAP)); }
	private int visibleRows() { return Math.max(1, (gridBottom() - gridTop() + GAP) / (CARD_H + GAP)); }
	private int maxScroll() {
		int rows = (shards.size() + cols() - 1) / cols();
		return Math.max(0, rows - visibleRows());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, this.width, this.height, BG);
		int x = gridX();

		ctx.text(this.font, Lang.tr("SHARDS", "ШАРДЫ"), x, 18, TEXT, true);

		// Навигация справа-сверху зависит от режима:
		//  хаб     → «сетка» (к старой плоской сетке всех шардов)
		//  скилл   → «← назад» (обратно в хаб иконок)
		//  плоская → «по скиллам» (в хаб иконок)
		String tg = switch (viewMode) {
			case VIEW_SKILL -> Lang.tr("← back", "← назад");
			case VIEW_FLAT -> Lang.tr("by skill", "по скиллам");
			default -> Lang.tr("grid view", "сетка");
		};
		toggleW = this.font.width(tg) + 12;
		toggleX = this.width - 24 - toggleW;
		boolean tgHover = in(mouseX, mouseY, toggleX, 16, toggleX + toggleW, 30);
		ctx.fill(toggleX, 16, toggleX + toggleW, 30, tgHover ? CARD_HOVER : CARD);
		ctx.fill(toggleX, 16, toggleX + toggleW, 17, ACCENT);
		ctx.text(this.font, tg, toggleX + 6, 20, TEXT, true);

		// Счётчик слева от навигации.
		String count = viewMode == VIEW_HUB
				? visibleHub().size() + Lang.tr(" skills", " скиллов")
				: shards.size() + Lang.tr(" of ", " из ") + ShardDb.allShards().size();
		ctx.text(this.font, count, toggleX - 8 - this.font.width(count), 18, TEXT_FAINT, true);

		// Поиск: в хабе — по центру, иначе — слева. Координаты помним для кликов.
		if (viewMode == VIEW_HUB) { searchW = 220; searchX = (this.width - searchW) / 2; }
		else { searchW = 160; searchX = x; }
		drawSearch(ctx);

		// Фильтр редкости — не в хабе (там нет ни фильтров, ни сортировок).
		if (viewMode != VIEW_HUB) {
			int bx = searchX + searchW + 12;
			bx = chip(ctx, Lang.tr("all", "все"), rarityFilter == null, bx, 32, mouseX, mouseY);
			for (String r : new String[] {"common", "uncommon", "rare", "epic", "legendary"}) {
				int w = this.font.width(rarityShort(r)) + 12;
				boolean active = r.equals(rarityFilter);
				boolean hover = in(mouseX, mouseY, bx, 32, bx + w, 46);
				ctx.fill(bx, 32, bx + w, 46, active ? ACCENT_SOFT : (hover ? CARD_HOVER : CARD));
				ctx.text(this.font, rarityShort(r), bx + 6, 36, rarityColor(r), true);
				bx += w + 4;
			}
		}

		// Ряд источников — только в старой плоской сетке. В режиме скилла на его
		// месте подпись, какой скилл открыт; в хабе — ничего (навигация иконками).
		int fy = 52;
		if (viewMode == VIEW_FLAT) {
			int fx = x;
			fx = chip(ctx, Lang.tr("all", "все"), sourceFilter == null, fx, fy, mouseX, mouseY);
			for (String s : sources) {
				int w = this.font.width(s) + 12;
				boolean active = s.equals(sourceFilter);
				boolean hover = in(mouseX, mouseY, fx, fy, fx + w, fy + 14);
				ctx.fill(fx, fy, fx + w, fy + 14, active ? ACCENT_SOFT : (hover ? CARD_HOVER : CARD));
				ctx.text(this.font, s, fx + 6, fy + 3, active ? TEXT : TEXT_DIM, true);
				fx += w + 4;
			}
		} else if (viewMode == VIEW_SKILL && sourceFilter != null) {
			try { ctx.item(skillIcon(sourceFilter), x, fy - 2); } catch (Exception ignored) { }
			ctx.text(this.font, skillLabel(sourceFilter), x + 20, fy + 3, TEXT, true);
		}

		// Сортировки/фильтры справа — только не в хабе.
		if (viewMode != VIEW_HUB) {
		// Сортировка и «не открытые» — справа, чтобы не мешались с фильтрами
		String sortLabel = sortName();
		int sw2 = this.font.width(sortLabel) + 12;
		int missW = this.font.width(Lang.tr("unopened", "не открытые")) + 12;
		int mx2 = this.width - 24 - missW;
		int sx2 = mx2 - sw2 - 4;
		boolean sHover = in(mouseX, mouseY, sx2, fy, sx2 + sw2, fy + 14);
		ctx.fill(sx2, fy, sx2 + sw2, fy + 14, sort != SORT_NAME ? ACCENT_SOFT : (sHover ? CARD_HOVER : CARD));
		ctx.text(this.font, sortLabel, sx2 + 6, fy + 3, sort != SORT_NAME ? TEXT : TEXT_DIM, true);

		boolean mHover = in(mouseX, mouseY, mx2, fy, mx2 + missW, fy + 14);
		ctx.fill(mx2, fy, mx2 + missW, fy + 14, onlyMissing ? ACCENT_SOFT : (mHover ? CARD_HOVER : CARD));
		ctx.text(this.font, Lang.tr("unopened", "не открытые"), mx2 + 6, fy + 3, onlyMissing ? TEXT : TEXT_DIM, true);

		// Кнопка-дропдаун «стат» слева от сортировки
		StatFilter.Stat curStat = StatFilter.byId(statFilter);
		String statLabel = Lang.tr("stat: ", "стат: ")
				+ (curStat == null ? Lang.tr("all", "все") : curStat.label());
		int stW = this.font.width(statLabel) + 12;
		statX = sx2 - stW - 4;
		boolean stHover = in(mouseX, mouseY, statX, fy, statX + stW, fy + 14);
		ctx.fill(statX, fy, statX + stW, fy + 14,
				statFilter != null ? ACCENT_SOFT : (stHover ? CARD_HOVER : CARD));
		ctx.text(this.font, statLabel, statX + 6, fy + 3, statFilter != null ? TEXT : TEXT_DIM, true);
		statW = stW;

		// Кнопка-дропдаун «семья» слева от стата
		String famLabel = Lang.tr("family: ", "семья: ")
				+ (familyFilter == null ? Lang.tr("all", "все") : familyFilter);
		int fW = this.font.width(famLabel) + 12;
		famX = statX - fW - 4;
		boolean famHover = in(mouseX, mouseY, famX, fy, famX + fW, fy + 14);
		ctx.fill(famX, fy, famX + fW, fy + 14,
				familyFilter != null ? ACCENT_SOFT : (famHover ? CARD_HOVER : CARD));
		ctx.text(this.font, famLabel, famX + 6, fy + 3, familyFilter != null ? TEXT : TEXT_DIM, true);
		famW = fW;
		}

		if (viewMode == VIEW_HUB) {
			drawHub(ctx, mouseX, mouseY);
			drawRecent(ctx, mouseX, mouseY);
		} else {
			if (shards.isEmpty()) {
				ctx.text(this.font, Lang.tr("Nothing found", "Ничего не нашлось"), x, gridTop() + 8, TEXT_FAINT, true);
			} else {
				int cols = cols();
				int start = scroll * cols;
				int shownCards = Math.min(visibleRows() * cols, shards.size() - start);
				for (int i = 0; i < shownCards; i++) {
					String key = shards.get(start + i);
					int cx = x + (i % cols) * (CARD_W + GAP);
					int cy = gridTop() + (i / cols) * (CARD_H + GAP);
					drawCard(ctx, key, cx, cy, in(mouseX, mouseY, cx, cy, cx + CARD_W, cy + CARD_H));
				}
				if (maxScroll() > 0) {
					String s = (scroll + 1) + "/" + (maxScroll() + 1);
					ctx.text(this.font, s, this.width - 24 - this.font.width(s), this.height - 12, TEXT_FAINT, true);
				}
			}
			ctx.text(this.font, Lang.tr("click — open shard · wheel — scroll", "клик — открыть шард · колесо — прокрутка"), x, this.height - 12, TEXT_FAINT, true);
		}

		// Выпадающие списки — последними, поверх сетки, ВСЕГДА (даже при пустом
		// результате — иначе открытый список невидим и клик кажется нерабочим).
		if (statOpen) drawStatList(ctx, mouseX, mouseY);
		if (familyOpen) drawFamilyList(ctx, mouseX, mouseY);
	}

	private void drawFamilyList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		int w = Math.max(famW, 150), x = famX, y = STAT_FY + 16;
		int rows = families.size() + 1; // +1 — «все»
		int maxRows = Math.min(rows, (this.height - y - 8) / 13); // не вылезаем за экран
		ctx.fill(x, y, x + w, y + maxRows * 13 + 2, 0xF01A1A24);
		ctx.fill(x, y, x + w, y + 1, ACCENT);
		int iy = y + 2;
		for (int i = 0; i < maxRows; i++) {
			boolean hover = in(mouseX, mouseY, x, iy, x + w, iy + 13);
			if (hover) ctx.fill(x, iy, x + w, iy + 13, ACCENT_SOFT);
			boolean isAll = i == 0;
			String label = isAll ? Lang.tr("all", "все") : families.get(i - 1);
			boolean active = isAll ? familyFilter == null : families.get(i - 1).equals(familyFilter);
			ctx.text(this.font, label, x + 6, iy + 3, active || hover ? TEXT : TEXT_DIM, true);
			iy += 13;
		}
	}

	private void drawStatList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		int w = 130, x = statX, y = STAT_FY + 16;
		java.util.List<StatFilter.Stat> stats = StatFilter.stats();
		int rows = stats.size() + 1; // +1 — строка «все»
		ctx.fill(x, y, x + w, y + rows * 13 + 2, 0xF01A1A24);
		ctx.fill(x, y, x + w, y + 1, ACCENT);
		int iy = y + 2;
		for (int i = 0; i < rows; i++) {
			boolean hover = in(mouseX, mouseY, x, iy, x + w, iy + 13);
			if (hover) ctx.fill(x, iy, x + w, iy + 13, ACCENT_SOFT);
			boolean isAll = i == 0;
			String label = isAll ? Lang.tr("all", "все") : stats.get(i - 1).label();
			boolean active = isAll ? statFilter == null : stats.get(i - 1).id().equals(statFilter);
			ctx.text(this.font, label, x + 6, iy + 3, active || hover ? TEXT : TEXT_DIM, true);
			iy += 13;
		}
	}

	private int chip(GuiGraphicsExtractor ctx, String label, boolean active,
					 int x, int y, int mouseX, int mouseY) {
		int w = this.font.width(label) + 12;
		boolean hover = in(mouseX, mouseY, x, y, x + w, y + 14);
		ctx.fill(x, y, x + w, y + 14, active ? ACCENT_SOFT : (hover ? CARD_HOVER : CARD));
		ctx.text(this.font, label, x + 6, y + 3, active ? TEXT : TEXT_DIM, true);
		return x + w + 4;
	}

	private int hubCols() {
		int fit = (this.width - 48 + TGAP) / (TILE_W + TGAP);
		return Math.max(1, Math.min(Math.max(1, hubSkills.size()), Math.max(1, fit)));
	}

	private void drawSearch(GuiGraphicsExtractor ctx) {
		ctx.fill(searchX, 32, searchX + searchW, 46, searchFocused ? ACCENT_SOFT : CARD);
		ctx.fill(searchX, 32, searchX + searchW, 33, searchFocused ? ACCENT : BORDER);
		String shown = search.isEmpty() && !searchFocused ? Lang.tr("search...", "поиск...") : search;
		ctx.text(this.font, shown, searchX + 5, 36,
				search.isEmpty() && !searchFocused ? TEXT_FAINT : TEXT, true);
		if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = searchX + 6 + this.font.width(search);
			ctx.fill(cx, 35, cx + 1, 43, ACCENT);
		}
	}

	/** Плитки хаба с учётом поиска: если игрок ввёл шард — остаются лишь скиллы,
	 *  в которых этот шард есть. Пустой поиск — все скиллы. */
	private List<String> visibleHub() {
		String q = search.toLowerCase().trim();
		if (q.isEmpty()) return hubSkills;
		java.util.Set<String> hit = new java.util.HashSet<>();
		for (String k : ShardDb.allShards()) {
			ShardDb.Shard s = ShardDb.shard(k);
			if (s == null || s.source == null) continue;
			if (s.name.toLowerCase().contains(q)
					|| (s.attrTitle != null && s.attrTitle.toLowerCase().contains(q))) {
				hit.add(s.source);
			}
		}
		List<String> out = new ArrayList<>();
		for (String sk : hubSkills) if (hit.contains(sk)) out.add(sk);
		return out;
	}

	private void drawHub(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		List<String> vis = visibleHub();
		if (vis.isEmpty()) {
			ctx.text(this.font, Lang.tr("Nothing found", "Ничего не нашлось"),
					gridX(), gridTop() + 8, TEXT_FAINT, true);
			return;
		}
		int x = gridX(), cols = hubCols();
		for (int i = 0; i < vis.size(); i++) {
			String src = vis.get(i);
			int cx = x + (i % cols) * (TILE_W + TGAP);
			int cy = gridTop() + (i / cols) * (TILE_H + TGAP);
			drawTile(ctx, src, cx, cy, in(mouseX, mouseY, cx, cy, cx + TILE_W, cy + TILE_H));
		}
	}


	/** «Последний раз вы смотрели:» — до 3 последних шардов, каждый с крестиком. */
	private void drawRecent(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		recentChipRects.clear(); recentXRects.clear(); recentKeys.clear();
		java.util.List<String> keys = new java.util.ArrayList<>();
		for (String k : RynConfig.recentShards) if (ShardDb.shard(k) != null) keys.add(k);
		if (keys.isEmpty()) return;

		// Нижний правый угол: подпись сверху, чипы в ряд, прижаты к правому краю.
		int gap = 6, cy = this.height - 24, total = 0;
		int[] wds = new int[keys.size()];
		for (int i = 0; i < keys.size(); i++) {
			ShardDb.Shard s = ShardDb.shard(keys.get(i));
			wds[i] = 8 + this.font.width(s.name) + 8 + this.font.width("✕") + 6;
			total += wds[i] + (i > 0 ? gap : 0);
		}
		int startX = this.width - 16 - total;
		ctx.text(this.font, Lang.tr("Recently viewed:", "Последний раз вы смотрели:"),
				startX, cy - 13, TEXT_FAINT, true);
		int cx = startX;
		for (int i = 0; i < keys.size(); i++) {
			String k = keys.get(i);
			ShardDb.Shard s = ShardDb.shard(k);
			int chipW = wds[i], nameW = this.font.width(s.name);
			boolean cardHover = in(mouseX, mouseY, cx, cy, cx + chipW, cy + 16);
			ctx.fill(cx, cy, cx + chipW, cy + 16, cardHover ? CARD_HOVER : CARD);
			ctx.fill(cx, cy, cx + 2, cy + 16, rarityColor(s.rarity)); // полоска редкости
			ctx.text(this.font, s.name, cx + 8, cy + 4, rarityColor(s.rarity), true);
			int xx = cx + 8 + nameW + 8;
			boolean xHover = in(mouseX, mouseY, xx - 3, cy, cx + chipW, cy + 16);
			ctx.text(this.font, "✕", xx, cy + 4, xHover ? 0xFFE0605F : TEXT_FAINT, true);
			recentChipRects.add(new int[]{cx, cy, cx + chipW, cy + 16});
			recentXRects.add(new int[]{xx - 3, cy, cx + chipW, cy + 16});
			recentKeys.add(k);
			cx += chipW + gap;
		}
	}

	private void drawTile(GuiGraphicsExtractor ctx, String src, int x, int y, boolean hover) {
		ctx.fill(x, y, x + TILE_W, y + TILE_H, hover ? CARD_HOVER : CARD);
		if (hover) {
			ctx.fill(x, y, x + TILE_W, y + 1, ACCENT);
			ctx.fill(x, y + TILE_H - 1, x + TILE_W, y + TILE_H, ACCENT);
			ctx.fill(x, y, x + 1, y + TILE_H, ACCENT);
			ctx.fill(x + TILE_W - 1, y, x + TILE_W, y + TILE_H, ACCENT);
		} else {
			ctx.fill(x, y, x + TILE_W, y + 1, BORDER);
		}

		// % прокачки скилла — маленьким в правом верхнем углу, зелёным на 100%.
		Integer pct = skillPct.get(src);
		if (pct != null) {
			String p = pct + "%";
			ctx.text(this.font, p, x + TILE_W - 6 - this.font.width(p), y + 5,
					pct >= 100 ? GREEN : TEXT_FAINT, true);
		}

		try { ctx.item(skillIcon(src), x + TILE_W / 2 - 8, y + 12); } catch (Exception ignored) { }
		String name = skillLabel(src);
		ctx.text(this.font, name, x + TILE_W / 2 - this.font.width(name) / 2, y + 36, TEXT, true);
		int n = skillAttrCount.getOrDefault(src, 0);
		String cnt = n + Lang.tr(" attributes", " аттрибутов");
		ctx.text(this.font, cnt, x + TILE_W / 2 - this.font.width(cnt) / 2, y + 50, TEXT_FAINT, true);

		// Тонкая полоска прогресса по нижней кромке — ненавязчиво.
		if (pct != null && pct > 0) {
			int bw = TILE_W - 12;
			int fill = (int) (bw * Math.min(1.0, pct / 100.0));
			ctx.fill(x + 6, y + TILE_H - 5, x + 6 + bw, y + TILE_H - 4, TRACK);
			ctx.fill(x + 6, y + TILE_H - 5, x + 6 + fill, y + TILE_H - 4, pct >= 100 ? GREEN : ACCENT);
		}
	}

	/** Иконка-предмет под скилл (в духе Hypixel). */
	private static ItemStack skillIcon(String src) {
		net.minecraft.world.item.Item it = switch (src) {
			case "Combat" -> net.minecraft.world.item.Items.DIAMOND_SWORD;
			case "Hunting" -> net.minecraft.world.item.Items.LEAD;
			case "Fishing" -> net.minecraft.world.item.Items.FISHING_ROD;
			case "Foraging" -> net.minecraft.world.item.Items.OAK_SAPLING;
			case "Mining" -> net.minecraft.world.item.Items.DIAMOND_PICKAXE;
			case "Farming" -> net.minecraft.world.item.Items.GOLDEN_HOE;
			case "Taming" -> net.minecraft.world.item.Items.BONE;
			case "Enchanting" -> net.minecraft.world.item.Items.ENCHANTED_BOOK;
			default -> net.minecraft.world.item.Items.NETHER_STAR; // Global и прочее
		};
		return new ItemStack(it);
	}

	private static String skillLabel(String src) {
		return switch (src) {
			case "Global" -> Lang.tr("Global", "Общее");
			case "Combat" -> Lang.tr("Combat", "Бой");
			case "Hunting" -> Lang.tr("Hunting", "Охота");
			case "Fishing" -> Lang.tr("Fishing", "Рыбалка");
			case "Foraging" -> Lang.tr("Foraging", "Собирательство");
			case "Mining" -> Lang.tr("Mining", "Добыча");
			case "Farming" -> Lang.tr("Farming", "Ферма");
			case "Taming" -> Lang.tr("Taming", "Приручение");
			case "Enchanting" -> Lang.tr("Enchanting", "Зачарование");
			default -> src;
		};
	}

	private void drawCard(GuiGraphicsExtractor ctx, String key, int x, int y, boolean hover) {
		ShardDb.Shard s = ShardDb.shard(key);
		if (s == null) return;

		ctx.fill(x, y, x + CARD_W, y + CARD_H, hover ? CARD_HOVER : CARD);
		// Полоска редкости слева — цвет узнаётся без чтения
		ctx.fill(x, y, x + 2, y + CARD_H, rarityColor(s.rarity));
		if (hover) {
			ctx.fill(x, y, x + CARD_W, y + 1, ACCENT);
			ctx.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, ACCENT);
			ctx.fill(x + CARD_W - 1, y, x + CARD_W, y + CARD_H, ACCENT);
		}

		int lvl = ShardProgress.displayLevel(key);

		ctx.text(this.font, fit(s.name, CARD_W - 34), x + 7, y + 5, rarityColor(s.rarity), true);
		String r = rarityShort(s.rarity);
		ctx.text(this.font, r, x + CARD_W - 8 - this.font.width(r), y + 5, rarityColor(s.rarity), true);

		// Уровень аттрибута. "?" — Attribute Menu ещё не открывали, и врать про
		// ноль нельзя: не открыт и неизвестен — разные вещи.
		if (!s.hasAttribute()) {
			String no = Lang.tr("no levels", "без уровней");
			ctx.text(this.font, no, x + CARD_W - 20 - this.font.width(no), y + 5, TEXT_FAINT, true);
		} else {
			String lv = lvl < 0 ? "?" : lvl + "/10";
			ctx.text(this.font, lv, x + CARD_W - 20 - this.font.width(lv), y + 5,
					lvl < 0 ? TEXT_FAINT : (lvl >= 10 ? GREEN : TEXT_DIM), true);
		}

		// Аттрибут: название и что даёт. Ради этого сюда и заходят — раньше за
		// этим приходилось кликать в каждый шард по очереди.
		if (s.attrTitle != null && !s.attrTitle.isEmpty()) {
			ctx.text(this.font, fit(s.attrTitle, CARD_W - 14), x + 7, y + 17, GOLD, true);
		}
		String attrDesc = s.attrDescShown();
		if (attrDesc != null && !attrDesc.isEmpty()) {
			String d = ShardAttribute.range(attrDesc);
			int ty = y + 28;
			for (var line : this.font.split(Component.literal(d), CARD_W - 14)) {
				if (ty > y + CARD_H - 12) break; // в карточку больше не влезет
				ctx.text(this.font, line, x + 7, ty, TEXT_DIM, true);
				ty += 9;
			}
		}

		// Полоска уровня
		if (s.hasAttribute() && lvl > 0) {
			int bw = CARD_W - 14;
			int fill = (int) (bw * Math.min(1.0, lvl / (double) ShardAttribute.MAX_LEVEL));
			ctx.fill(x + 7, y + CARD_H - 7, x + 7 + bw, y + CARD_H - 6, TRACK);
			ctx.fill(x + 7, y + CARD_H - 7, x + 7 + fill, y + CARD_H - 6, rarityColor(s.rarity));
		}
	}

	private String fit(String s, int maxW) {
		if (this.font.width(s) <= maxW) return s;
		return this.font.plainSubstrByWidth(s, maxW - this.font.width("…")) + "…";
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!searchFocused) return super.charTyped(event);
		// codepoint() — это int. "строка += int" дописывает ЧИСЛО: набирал "abc",
		// в поле появлялось "979899". Переводим код в символ явно.
		if (searchAllSelected) { search = ""; searchAllSelected = false; }
		search += Character.toString(event.codepoint());
		rebuild();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchFocused) {
			int k = event.key();
			// Ctrl+A/C/X/V — как в любом поле ввода (event сам знает про Mac-quirk).
			if (event.isSelectAll()) { searchAllSelected = !search.isEmpty(); return true; }
			if (event.isCopy()) { if (!search.isEmpty()) this.minecraft.keyboardHandler.setClipboard(search); return true; }
			if (event.isCut()) {
				if (!search.isEmpty()) this.minecraft.keyboardHandler.setClipboard(search);
				search = ""; searchAllSelected = false; rebuild(); return true;
			}
			if (event.isPaste()) {
				String clip = sanitizeClip(this.minecraft.keyboardHandler.getClipboard());
				search = searchAllSelected ? clip : search + clip;
				searchAllSelected = false; rebuild(); return true;
			}
			if (k == GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; searchAllSelected = false; return true; }
			if (k == GLFW.GLFW_KEY_BACKSPACE) {
				if (searchAllSelected) { search = ""; searchAllSelected = false; rebuild(); }
				else if (!search.isEmpty()) { search = search.substring(0, search.length() - 1); rebuild(); }
				return true;
			}
			if (k == GLFW.GLFW_KEY_ENTER) { searchFocused = false; return true; }
			return true;
		}
		return super.keyPressed(event);
	}

	/** Буфер обмена: одна строка, без управляющих символов, до 64 знаков. */
	static String sanitizeClip(String s) {
		if (s == null) return "";
		int nl = s.indexOf('\n'); if (nl >= 0) s = s.substring(0, nl);
		s = s.replaceAll("\\p{Cntrl}", "").trim();
		return s.length() > 64 ? s.substring(0, 64) : s;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		int mx = (int) event.x(), my = (int) event.y();
		int x = gridX();

		// Хаб-режим: активны только поиск, навигация и плитки скиллов.
		if (viewMode == VIEW_HUB) {
			// «сетка» — уйти в старую плоскую сетку всех шардов (запоминаем выбор).
			if (in(mx, my, toggleX, 16, toggleX + toggleW, 30)) {
				sourceFilter = null; viewMode = VIEW_FLAT; scroll = 0;
				com.ryn.skyryn.config.RynConfig.shardsFlatView = true;
				com.ryn.skyryn.config.ConfigManager.save();
				rebuild();
				return true;
			}
			if (in(mx, my, searchX, 32, searchX + searchW, 46)) { searchFocused = true; return true; }
			searchFocused = false;
			List<String> vis = visibleHub();
			int cols = hubCols();
			for (int i = 0; i < vis.size(); i++) {
				int cx = x + (i % cols) * (TILE_W + TGAP);
				int cy = gridTop() + (i / cols) * (TILE_H + TGAP);
				if (in(mx, my, cx, cy, cx + TILE_W, cy + TILE_H)) {
					sourceFilter = vis.get(i); // войти в шарды этого скилла
					viewMode = VIEW_SKILL;
					scroll = 0;
					rebuild();
					return true;
				}
			}
			// Последние просмотренные: крестик — убрать, тело чипа — открыть шард.
			for (int i = 0; i < recentKeys.size(); i++) {
				int[] xr = recentXRects.get(i);
				if (in(mx, my, xr[0], xr[1], xr[2], xr[3])) {
					com.ryn.skyryn.config.RynConfig.removeRecent(recentKeys.get(i));
					com.ryn.skyryn.config.ConfigManager.save();
					return true;
				}
				int[] cr = recentChipRects.get(i);
				if (in(mx, my, cr[0], cr[1], cr[2], cr[3])) {
					this.minecraft.setScreen(new ShardPageScreen(recentKeys.get(i), this));
					return true;
				}
			}
			return true; // клик мимо в хабе — ничего
		}

		// Навигация справа-сверху: из скилла и из плоской сетки — назад в хаб.
		if (in(mx, my, toggleX, 16, toggleX + toggleW, 30)) {
			boolean wasFlat = viewMode == VIEW_FLAT;
			if (viewMode == VIEW_SKILL) sourceFilter = null; // из скилла — назад ко всем
			viewMode = VIEW_HUB; scroll = 0;
			if (wasFlat) { // из плоской сетки в хаб — запомнить выбор
				com.ryn.skyryn.config.RynConfig.shardsFlatView = false;
				com.ryn.skyryn.config.ConfigManager.save();
			}
			rebuild();
			return true;
		}

		// Открытые списки ловят клик первым — иначе выбор уйдёт в карточку под ними.
		if (statOpen) {
			int lw = 130, ly = STAT_FY + 16;
			java.util.List<StatFilter.Stat> stats = StatFilter.stats();
			int rows = stats.size() + 1;
			for (int i = 0; i < rows; i++) {
				if (in(mx, my, statX, ly + i * 13, statX + lw, ly + i * 13 + 13)) {
					statFilter = i == 0 ? null : stats.get(i - 1).id();
					statOpen = false;
					rebuild();
					return true;
				}
			}
			statOpen = false; // клик мимо списка — просто закрыть
			return true;
		}
		if (familyOpen) {
			int lw = Math.max(famW, 150), ly = STAT_FY + 16;
			int rows = families.size() + 1;
			int maxRows = Math.min(rows, (this.height - ly - 8) / 13);
			for (int i = 0; i < maxRows; i++) {
				if (in(mx, my, famX, ly + i * 13, famX + lw, ly + i * 13 + 13)) {
					familyFilter = i == 0 ? null : families.get(i - 1);
					familyOpen = false;
					rebuild();
					return true;
				}
			}
			familyOpen = false;
			return true;
		}
		// Кнопки дропдаунов открываем ИЗ ЛЮБОГО состояния (в т.ч. с фокусом в
		// поиске) — раньше при непустом поиске список статов было не открыть.
		if (in(mx, my, statX, STAT_FY, statX + statW, STAT_FY + 14)) {
			statOpen = true; familyOpen = false; searchFocused = false;
			return true;
		}
		if (in(mx, my, famX, STAT_FY, famX + famW, STAT_FY + 14)) {
			familyOpen = true; statOpen = false; searchFocused = false;
			return true;
		}

		if (in(mx, my, searchX, 32, searchX + searchW, 46)) { searchFocused = true; return true; }
		searchFocused = false;

		// Редкость
		int bx = searchX + searchW + 12;
		int w0 = this.font.width(Lang.tr("all", "все")) + 12;
		if (in(mx, my, bx, 32, bx + w0, 46)) { rarityFilter = null; rebuild(); return true; }
		bx += w0 + 4;
		for (String r : new String[] {"common", "uncommon", "rare", "epic", "legendary"}) {
			int w = this.font.width(rarityShort(r)) + 12;
			if (in(mx, my, bx, 32, bx + w, 46)) {
				rarityFilter = r.equals(rarityFilter) ? null : r;
				rebuild();
				return true;
			}
			bx += w + 4;
		}

		// Сортировка и «не открытые»
		int fy = 52;
		int missW = this.font.width(Lang.tr("unopened", "не открытые")) + 12;
		int mx2 = this.width - 24 - missW;
		int sw2 = this.font.width(sortName()) + 12;
		int sx2 = mx2 - sw2 - 4;
		if (in(mx, my, mx2, fy, mx2 + missW, fy + 14)) {
			onlyMissing = !onlyMissing;
			rebuild();
			return true;
		}
		if (in(mx, my, sx2, fy, sx2 + sw2, fy + 14)) {
			sort = (sort + 1) % SORT_MODES;
			rebuild();
			return true;
		}

		// Источник — чипы есть только в плоской сетке.
		if (viewMode == VIEW_FLAT) {
			int fx = x;
			if (in(mx, my, fx, fy, fx + w0, fy + 14)) { sourceFilter = null; rebuild(); return true; }
			fx += w0 + 4;
			for (String s : sources) {
				int w = this.font.width(s) + 12;
				if (in(mx, my, fx, fy, fx + w, fy + 14)) {
					sourceFilter = s.equals(sourceFilter) ? null : s;
					rebuild();
					return true;
				}
				fx += w + 4;
			}
		}

		// Карточки
		int cols = cols();
		int start = scroll * cols;
		int shown = Math.min(visibleRows() * cols, Math.max(0, shards.size() - start));
		for (int i = 0; i < shown; i++) {
			int cx = x + (i % cols) * (CARD_W + GAP);
			int cy = gridTop() + (i / cols) * (CARD_H + GAP);
			if (in(mx, my, cx, cy, cx + CARD_W, cy + CARD_H)) {
				this.minecraft.setScreen(new ShardPageScreen(shards.get(start + i), this));
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(dy)));
		return true;
	}

	static boolean in(int mx, int my, int x1, int y1, int x2, int y2) {
		return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
