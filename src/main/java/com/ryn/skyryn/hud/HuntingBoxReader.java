package com.ryn.skyryn.hud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.data.ShardDb;
import com.ryn.skyryn.data.ShardIcons;
import com.ryn.skyryn.fusion.FusionPanel;
import com.ryn.skyryn.mixin.ContainerScreenAccessor;

public class HuntingBoxReader {
	public static void register() {
		net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
				(stack, context, type, lines) -> {
			if (!RynConfig.flag("peek.recipes", true)) return;
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.hasControlDown() || mc.hasShiftDown()
					|| !(mc.screen instanceof AbstractContainerScreen<?>)) return;
			String t = mc.screen.getTitle() == null ? "" : mc.screen.getTitle().getString();
			if (!isBox(t) && !isAttributeMenu(t)) return;
			if (shardOfStack(stack) == null) return;
			lines.add(net.minecraft.network.chat.Component.literal(isAttributeMenu(t)
					? "§8" + Lang.tr("Shift — made from your box", "Shift — из чего собрать в боксе")
					: "§8" + Lang.tr("Ctrl — fuses into · Shift — made from",
					"Ctrl — во что идёт · Shift — из чего собрать")));
		});

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			String title = screen.getTitle().getString();
			if (title == null) return;
			if (isAttributeMenu(title)) {
				ScreenEvents.afterExtract(screen).register(
						(scr, ctx, mx, my, delta) -> recipePeek(ctx, cs, mx, my));
				net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen)
						.register((scr, mxs, mys, hor, ver) -> !peekScroll(ver));
				return;
			}
			if (!isBox(title)) return;

			ScreenEvents.afterExtract(screen).register((scr, ctx, mx, my, delta) -> {
				captureIcons(cs);
				countStock(cs, title);
				highlight(ctx, cs);
				recipePeek(ctx, cs, mx, my);
			});

			net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen)
					.register((scr, mxs, mys, hor, ver) -> !peekScroll(ver));
		});
	}

	private static final java.util.regex.Pattern PAGE =
			java.util.regex.Pattern.compile("\\((\\d+)\\s*/\\s*(\\d+)\\)");

	private static void countStock(AbstractContainerScreen<?> screen, String title) {
		if (title == null || !(title.contains("Hunting Box") || title.contains("Fusion Box"))) return;
		java.util.Map<String, Integer> counts = new java.util.HashMap<>();
		int boxSlots = screen.getMenu().slots.size() - 36;
		for (int i = 0; i < boxSlots; i++) {
			Slot slot = screen.getMenu().slots.get(i);
			if (!slot.hasItem()) continue;
			String key = matchShard(clean(slot.getItem().getHoverName().getString()));
			if (key == null) continue;
			counts.merge(key, amountOf(slot.getItem()), Integer::sum);
		}
		java.util.regex.Matcher m = PAGE.matcher(title);
		int page = 1, total = 0;
		if (m.find()) {
			try {
				page = Integer.parseInt(m.group(1));
				total = Integer.parseInt(m.group(2));
			} catch (NumberFormatException ignored) { }
		}
		com.ryn.skyryn.fusion.ShardStock.putPage(page, total, counts);
	}

	private static int amountOf(net.minecraft.world.item.ItemStack st) {
		var lore = st.get(net.minecraft.core.component.DataComponents.LORE);
		if (lore != null) {
			for (net.minecraft.network.chat.Component line : lore.lines()) {
				String s = clean(line.getString());
				java.util.regex.Matcher m = AMOUNT.matcher(s);
				if (m.find()) {
					try { return Integer.parseInt(m.group(1).replace(",", "")); } catch (NumberFormatException ignored) { }
				}
			}
			for (net.minecraft.network.chat.Component line : lore.lines()) {
				java.util.regex.Matcher m = LOOSE_AMOUNT.matcher(clean(line.getString()));
				if (m.find()) {
					try { return Integer.parseInt(m.group(1).replace(",", "")); } catch (NumberFormatException ignored) { }
				}
			}
		}
		return st.getCount();
	}

	private static final java.util.regex.Pattern LOOSE_AMOUNT =
			java.util.regex.Pattern.compile("(?i)(?:^|\\s)([\\d,]{1,9})\\s+shards?");

	private static final java.util.regex.Pattern AMOUNT =
			java.util.regex.Pattern.compile("(?i)(?:amount|hunting box|in storage|owned|stored|you have)\\s*:\\s*([\\d,]+)");

	private static final int PEEK_BG = 0xF21A1A24, PEEK_BORDER = 0xFF2E2E3C;
	private static final int PEEK_TITLE = 0xFFF2F3F7, PEEK_DIM = 0xFF9A9CAB, PEEK_ACCENT = 0xFF5B8DEF;
	private static final int PEEK_MAX = 6;

	private static boolean isBox(String title) {
		return title.contains("Hunting Box") || title.contains("Fusion Box")
				|| title.contains("Shard Fusion");
	}

	private static boolean isAttributeMenu(String title) { return title.contains("Attribute Menu"); }

	private static final java.util.regex.Pattern ATTR_SOURCE =
			java.util.regex.Pattern.compile("Source:\\s*(.+?)\\s+Shard\\s*\\(([A-Za-z]\\d+)\\)");

	private static String shardOfStack(net.minecraft.world.item.ItemStack stack) {
		String byName = matchShard(clean(stack.getHoverName().getString()));
		if (byName != null) return byName;
		var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
		if (lore == null) return null;
		for (net.minecraft.network.chat.Component line : lore.lines()) {
			java.util.regex.Matcher m = ATTR_SOURCE.matcher(clean(line.getString()));
			if (!m.find()) continue;
			String key = ShardDb.byId(m.group(2));
			if (key == null) {
				String name = m.group(1).trim().toLowerCase();
				if (ShardDb.shard(name) != null) key = name;
			}
			return key;
		}
		return null;
	}

	private static void recipePeek(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> screen, int mx, int my) {
		if (!RynConfig.flag("peek.recipes", true)) return;
		if (!(screen instanceof ContainerScreenAccessor acc)) return;

		String key = hoveredShard(screen, acc, mx, my);
		if (key == null) { peekKey = null; return; }
		if (!key.equals(peekKey)) { peekKey = key; peekScroll = 0; }

		var mc = net.minecraft.client.Minecraft.getInstance();
		String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
		boolean attr = isAttributeMenu(title);
		boolean ctrl = mc.hasControlDown() && !attr, shift = mc.hasShiftDown();
		if (!ctrl && !shift) return;

		java.util.List<String> rows = ctrl ? fusableRows(key) : makeableRows(key);
		int have = com.ryn.skyryn.fusion.ShardStock.owned(key);

		java.util.List<String> lines = new java.util.ArrayList<>();
		lines.add("§f" + ShardDb.displayName(key) + " §8× " + have
				+ " §7· " + (ctrl ? Lang.tr("fuses into", "во что идёт") : Lang.tr("made from", "из чего собрать")));
		if (rows.isEmpty()) {
			lines.add("§8" + (ctrl
					? Lang.tr("nothing to fuse from your box", "из бокса собрать нечего")
					: Lang.tr("not enough shards in the box for it", "в боксе не хватает шардов на него")));
			if (com.ryn.skyryn.fusion.ShardStock.freshPages().isEmpty())
				lines.add("§8" + Lang.tr("open the Hunting Box first — the mod has not seen your shards yet",
						"открой Hunting Box — мод ещё не видел твой запас"));
		} else {
			peekScroll = Math.max(0, Math.min(peekScroll, Math.max(0, rows.size() - PEEK_MAX)));
			int to = Math.min(rows.size(), peekScroll + PEEK_MAX);
			for (int i = peekScroll; i < to; i++) lines.add(rows.get(i));
			if (rows.size() > PEEK_MAX)
				lines.add("§8" + (peekScroll + 1) + "–" + to + " " + Lang.tr("of", "из") + " " + rows.size()
						+ " §7· " + Lang.tr("wheel to scroll", "колесо — прокрутка"));
		}
		drawPeek(ctx, lines, mx, my);
	}

	private static java.util.List<String> fusableRows(String key) {
		java.util.List<String> out = new java.util.ArrayList<>();
		int per = ShardDb.INPUT_PER_FUSION;
		int have = com.ryn.skyryn.fusion.ShardStock.owned(key);
		if (have < per) return out;

		java.util.Map<String, java.util.List<ShardDb.Recipe>> byPair = new java.util.LinkedHashMap<>();
		for (ShardDb.Recipe r : ShardDb.usedIn(key)) {
			String pairKey = r.firstClick().toLowerCase() + "|" + r.secondClick().toLowerCase();
			byPair.computeIfAbsent(pairKey, k -> new java.util.ArrayList<>()).add(r);
		}

		java.util.List<Object[]> ranked = new java.util.ArrayList<>();
		for (var e : byPair.entrySet()) {
			ShardDb.Recipe sample = e.getValue().get(0);
			String first = sample.firstClick(), second = sample.secondClick();
			String partner = first.equalsIgnoreCase(key) ? second : first;
			int havePartner = com.ryn.skyryn.fusion.ShardStock.owned(partner);
			boolean self = first.equalsIgnoreCase(second);
			int fusions = self ? have / (per * 2) : Math.min(have / per, havePartner / per);
			if (fusions <= 0) continue;

			StringBuilder sb = new StringBuilder("§a" + per + "x " + ShardDb.displayName(first)
					+ " §7» §a" + per + "x " + ShardDb.displayName(second) + " §7= §f");
			boolean firstOut = true;
			for (ShardDb.Recipe r : e.getValue()) {
				if (!firstOut) sb.append("§7, §f");
				sb.append(r.qty * fusions).append("x ").append(ShardDb.displayName(r.output));
				firstOut = false;
			}
			ranked.add(new Object[]{ sb.toString(), fusions });
		}
		ranked.sort((a, b) -> (Integer) b[1] - (Integer) a[1]);
		for (Object[] r : ranked) out.add((String) r[0]);
		return out;
	}

	private static java.util.List<String> makeableRows(String key) {
		java.util.List<Object[]> ranked = new java.util.ArrayList<>();
		int per = ShardDb.INPUT_PER_FUSION;

		for (ShardDb.Recipe r : ShardDb.recipesFor(key)) {
			String first = r.firstClick(), second = r.secondClick();
			int haveFirst = com.ryn.skyryn.fusion.ShardStock.owned(first);
			int haveSecond = com.ryn.skyryn.fusion.ShardStock.owned(second);
			boolean self = first.equalsIgnoreCase(second);
			int fusions = self ? haveFirst / (per * 2) : Math.min(haveFirst / per, haveSecond / per);
			if (fusions <= 0) continue;

			String row = "§a" + per + "x " + ShardDb.displayName(first)
					+ " §7» §a" + per + "x " + ShardDb.displayName(second)
					+ " §7= §f" + (fusions * r.qty) + "x " + ShardDb.displayName(key);
			ranked.add(new Object[]{ row, fusions * r.qty });
		}
		ranked.sort((a, b) -> (Integer) b[1] - (Integer) a[1]);

		java.util.List<String> out = new java.util.ArrayList<>();
		for (Object[] r : ranked) out.add((String) r[0]);
		return out;
	}

	private static String peekKey = null;
	private static int peekScroll = 0;

	public static boolean peekScroll(double ver) {
		if (!RynConfig.flag("peek.recipes", true) || peekKey == null) return false;
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (!mc.hasControlDown() && !mc.hasShiftDown()) return false;
		peekScroll -= (int) Math.signum(ver);
		if (peekScroll < 0) peekScroll = 0;
		return true;
	}

	private static String hoveredShard(AbstractContainerScreen<?> screen, ContainerScreenAccessor acc, int mx, int my) {
		int left = acc.skyryn$leftPos(), top = acc.skyryn$topPos();
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) continue;
			int sx = left + slot.x, sy = top + slot.y;
			if (mx < sx || mx >= sx + 16 || my < sy || my >= sy + 16) continue;
			return shardOfStack(slot.getItem());
		}
		return null;
	}

	private static void drawPeek(GuiGraphicsExtractor ctx, java.util.List<String> lines, int mx, int my) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		var font = mc.font;
		int w = 0;
		for (String s : lines) w = Math.max(w, font.width(s));
		w += 10;
		int h = lines.size() * 10 + 6;
		int x = mx + 14, y = my + 14;
		if (x + w > mc.getWindow().getGuiScaledWidth() - 2) x = Math.max(2, mx - 14 - w);
		if (y + h > mc.getWindow().getGuiScaledHeight() - 2) y = Math.max(2, my - 14 - h);

		ctx.fill(x, y, x + w, y + h, PEEK_BG);
		ctx.fill(x, y, x + w, y + 1, PEEK_ACCENT);
		ctx.fill(x, y + h - 1, x + w, y + h, PEEK_BORDER);
		ctx.fill(x, y, x + 1, y + h, PEEK_BORDER);
		ctx.fill(x + w - 1, y, x + w, y + h, PEEK_BORDER);
		int ty = y + 4;
		for (String s : lines) { ctx.text(font, s, x + 5, ty, PEEK_TITLE, true); ty += 10; }
	}

	private static void captureIcons(AbstractContainerScreen<?> screen) {
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) continue;
			String key = matchShard(clean(slot.getItem().getHoverName().getString()));
			if (key != null) ShardIcons.put(key, slot.getItem());
		}
	}

	private static void highlight(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> screen) {
		if (!RynConfig.highlightFuseInputs) return;
		if (!(screen instanceof ContainerScreenAccessor acc)) return;
		java.util.Set<String> hl = FusionPanel.highlightInputs;
		if (hl.isEmpty()) return;
		int left = acc.skyryn$leftPos(), top = acc.skyryn$topPos();
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) continue;
			String key = matchShard(clean(slot.getItem().getHoverName().getString()));
			if (key == null || !hl.contains(key)) continue;
			int sx = left + slot.x, sy = top + slot.y;
			int g = 0xFF3FE05F;
			ctx.fill(sx - 1, sy - 1, sx + 17, sy, g);
			ctx.fill(sx - 1, sy + 16, sx + 17, sy + 17, g);
			ctx.fill(sx - 1, sy, sx, sy + 16, g);
			ctx.fill(sx + 16, sy, sx + 17, sy + 16, g);
			ctx.fill(sx, sy, sx + 16, sy + 16, 0x4033C059);
		}
	}

	private static String matchShard(String name) {
		if (name == null || name.isBlank()) return null;
		String n = name.trim();
		if (ShardDb.shard(n) != null) return n.toLowerCase();

		int sp = n.lastIndexOf(' ');
		if (sp > 0 && n.substring(sp + 1).matches("[IVXL]+")) {
			String base = n.substring(0, sp);
			if (ShardDb.shard(base) != null) return base.toLowerCase();
		}
		return null;
	}

	private static String clean(String s) {
		return s == null ? "" : s.replaceAll("§.", "").trim();
	}
}
