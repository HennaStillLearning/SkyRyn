package com.ryn.skyryn.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.ryn.skyryn.mixin.ContainerScreenAccessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ryn.skyryn.config.ConfigManager;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import com.ryn.skyryn.waypoint.SkyBlockCheck;

public class SafariTracker {
	private static final Pattern FLOOR_DROP = Pattern.compile("floor drop! you found (.+?) on the ground", Pattern.CASE_INSENSITIVE);
	private static final Pattern CAPTURE = Pattern.compile("capture! you caught (?:a |an )?(.+?) and gained (.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern HUNT_XP = Pattern.compile("\\+([\\d,]+) hunting experience", Pattern.CASE_INSENSITIVE);
	private static final Pattern DOOM_SUMMON = Pattern.compile("summoned a doomspiral", Pattern.CASE_INSENSITIVE);
	private static final Pattern PARTY_SENDER = Pattern.compile("^party\\s*>\\s*(.*?):\\s*(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SUMMARY = Pattern.compile("safari reward summary", Pattern.CASE_INSENSITIVE);
	private static final Pattern HOTSPOT = Pattern.compile("hunting hotspot is the (.+?) biome", Pattern.CASE_INSENSITIVE);
	private static final Pattern ITEM_QTY = Pattern.compile("^(.*?)\\s*x\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern WUMPA_AWOKEN = Pattern.compile("the wumpa has awoken", Pattern.CASE_INSENSITIVE);
	private static final Pattern GATE_OPEN = Pattern.compile("the chamber opens|chamber opens|the door at the back", Pattern.CASE_INSENSITIVE);
	private static final Pattern STARTED_WITH = Pattern.compile("you started with (?:a |an )?(.+?)[!.]", Pattern.CASE_INSENSITIVE);
	private static final Pattern GEM_PLACED = Pattern.compile("you placed the .+? gem on its podium", Pattern.CASE_INSENSITIVE);
	private static final Pattern ESSENCE_BAR = Pattern.compile("\\+([\\d,]+)x? (?:safari|forest) essence", Pattern.CASE_INSENSITIVE);
	private static final Pattern SHARD_REWARD = Pattern.compile("\\+([\\d,]+) shards?\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern HUNT_XP_REWARD = Pattern.compile("\\+([\\d,]+) hunting exp\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern LOOTSHARE = Pattern.compile(
			"loot share! you received (?:an? |(\\d+)x )?.+? shard from \\S+ catching (?:an? )?(.+?)!", Pattern.CASE_INSENSITIVE);

	private static boolean inSafari = false;
	private static boolean visitActive = false;
	private static int floorDrops = 0;
	private static long huntingXp = 0;
	private static long forestEssence = 0;
	private static int shardsCaught = 0;
	private static int floorShards = 0;
	private static int lootshareShards = 0;
	private static int startCapsuleTotal = 0;
	private static long visitStartAt = 0;
	private static final Map<String, Integer> captures = new LinkedHashMap<>();
	private static final Map<String, Integer> floorItems = new LinkedHashMap<>();
	private static long doomSummonAt = 0;

	private static int sessRuns = 0, sessFloorDrops = 0, sessCaptures = 0, sessShards = 0, sessCapsSpent = 0, sessLootshare = 0;
	private static long sessEssence = 0, sessTimeMs = 0, sessHuntXp = 0;
	private static int sessTickets = 0;
	private static final Map<String, Integer> runShards = new LinkedHashMap<>();
	private static final Map<String, Integer> runLs = new LinkedHashMap<>();
	private static final Map<String, Integer> sessShardsMap = new LinkedHashMap<>();
	private static final Map<String, Integer> sessLsMap = new LinkedHashMap<>();
	private static String lastTicket = "";
	private static String currentHotspot = "";

	private static boolean wumpaAwoken = false, wumpaCaught = false;
	private static boolean doomSummoned = false, doomCaught = false;
	private static boolean gateOpen = false, gateCleared = false;
	private static int gemsPlaced = 0;
	private static int gemzieCaught = 0;
	private static final java.util.List<String> ICY_MOBS = java.util.List.of(
			"strongarm", "troodon", "polaris", "billygoat", "mantis shrimp", "nozzlenose", "tepid", "shuddersquid");
	private static final java.util.Set<String> caughtIcy = new java.util.LinkedHashSet<>();
	private static int troodonShards = 0;
	private static boolean entrySent = false;
	private static final java.util.List<String> entryGiven = new java.util.ArrayList<>();

	private static boolean partyOn() { return RynConfig.safariParty || RynConfig.safariSolo; }

	private static boolean msgOn(String key, boolean def) {
		return RynConfig.flag((RynConfig.safariSolo ? "msg." : "pmsg.") + key, def);
	}

	private record ScreenMsg(String text, long expireAt) { }
	private static final java.util.List<ScreenMsg> screenMsgs = new java.util.ArrayList<>();
	private static void showOnScreen(String msg) { screenMsgs.add(new ScreenMsg(msg, System.currentTimeMillis() + 4000)); }

	private static long lastEssenceAt = 0;
	private static String lastEssenceStr = "";

	private static void parseActionBarEssence(String s) {
		Matcher m = ESSENCE_BAR.matcher(s);
		while (m.find()) {
			String key = m.group().toLowerCase();
			long now = System.currentTimeMillis();
			if (key.equals(lastEssenceStr) && now - lastEssenceAt < 1200) { lastEssenceAt = now; continue; }
			lastEssenceStr = key; lastEssenceAt = now;
			forestEssence += parseInt(m.group(1));
		}
	}

	private static String tookTail() {
		return (msgOn("times", true) && visitStartAt > 0)
				? " " + Lang.tr("(took ", "(заняло ") + fmtTime(System.currentTimeMillis() - visitStartAt) + ")" : "";
	}

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!RynConfig.safariTracker) return;
			String s = strip(message.getString());
			if (s == null) return;
			if (overlay) { if (inSafari) parseActionBarEssence(s); return; }

			if (RynConfig.safariParty) {
				Matcher pm = PARTY_SENDER.matcher(s);
				if (pm.find()) {
					String pc = pm.group(2).trim();
					if (pc.toLowerCase().startsWith("#")) { handlePartyCommand(pc.toLowerCase()); return; }
					String biome = biomeFromAnnounce(pc);
					if (biome != null) com.ryn.skyryn.waypoint.SafariBiomes.recordPartyBiome(lastWord(pm.group(1)), biome);
				}
			}

			if (SUMMARY.matcher(s).find()) { if (visitActive) { rewardEssence = 0; pendingExitAt = System.currentTimeMillis() + 3000; long b = SkyBlockCheck.safariEssence(); if (b >= 0) lastEssenceBal = b; } return; }
			if (pendingExitAt > 0) {
				Matcher rm = ESSENCE_BAR.matcher(s);
				if (rm.find() && s.toLowerCase().contains("safari essence")) rewardEssence = parseInt(rm.group(1));
				Matcher rs = SHARD_REWARD.matcher(s);
				if (rs.find()) rewardShards = parseInt(rs.group(1));
				Matcher rhx = HUNT_XP_REWARD.matcher(s);
				if (rhx.find()) rewardHuntXp = parseNum(rhx.group(1));
			}
			Matcher hot = HOTSPOT.matcher(s);
			if (hot.find()) { currentHotspot = cap(hot.group(1).trim()); return; }

			if (!inSafari) return;

			Matcher sw = STARTED_WITH.matcher(s);
			if (sw.find()) { entryGiven.add(cap(sw.group(1).trim())); return; }

			Matcher fd = FLOOR_DROP.matcher(s);
			if (fd.find()) {
				floorDrops++;
				String found = fd.group(1).trim();
				String low = found.toLowerCase();
				Matcher xp = HUNT_XP.matcher(found);
				if (xp.find()) huntingXp += parseNum(xp.group(1));
				else if (low.contains("essence")) {  }
				else {
					Matcher q = ITEM_QTY.matcher(found);
					String name = q.matches() ? q.group(1).trim() : found;
					int qty = q.matches() ? parseInt(q.group(2)) : 1;
					floorItems.merge(name, qty, Integer::sum);
					if (low.contains("shard")) { shardsCaught += qty; floorShards += qty; addShardCount(name.replaceAll("(?i)\\s*shard$", ""), qty, false); }
				}
				return;
			}
			if (BELL_RUNG.matcher(s).find()) { markBellHere(); return; }
			if (s.toUpperCase().contains("SPARKLING") && s.toLowerCase().contains("you caught")) {
				RynConfig.setInt("spark.life", RynConfig.getInt("spark.life", 0) + 1);
				ConfigManager.save();
			}
			Matcher cap = CAPTURE.matcher(s);
			if (cap.find()) {
				String critter = cap.group(1).trim();
				captures.merge(critter, 1, Integer::sum);
				String gained = cap.group(2).toLowerCase();
				if (gained.contains("shard")) { int gc = gainCount(gained); shardsCaught += gc; addShardCount(critter, gc, false); }
				String cl = critter.toLowerCase();
				for (String icy : ICY_MOBS) if (cl.contains(icy)) caughtIcy.add(icy);
				checkWumpaReady();
				if (cl.contains("troodon") && gained.contains("shard")) troodonShards += gainCount(gained);
				if (cl.contains("doomspiral")) { doomCaught = true; onDoomCaptured(); }
				else if (cl.contains("wumpa")) { wumpaCaught = true; onWumpaCaptured(); }
				else if (cl.contains("gemzie")) { if (gemzieCaught < 3) gemzieCaught++; checkGateCleared(); }
				return;
			}
			Matcher ls = LOOTSHARE.matcher(s);
			if (ls.find()) {
				int ln = ls.group(1) != null ? parseInt(ls.group(1)) : 1;
				lootshareShards += ln;
				String mob = ls.group(2).toLowerCase();
				addShardCount(mob, ln, true);
				for (String icy : ICY_MOBS) if (mob.contains(icy)) caughtIcy.add(icy);
				checkWumpaReady();
				if (mob.contains("troodon")) troodonShards += ls.group(1) != null ? parseInt(ls.group(1)) : 1;
				if (mob.contains("gemzie")) { if (gemzieCaught < 3) gemzieCaught++; checkGateCleared(); }
				else if (mob.contains("wumpa")) wumpaCaught = true;
				else if (mob.contains("doomspiral")) doomCaught = true;
				return;
			}
			if (GEM_PLACED.matcher(s).find()) { if (gemsPlaced < 3) gemsPlaced++; return; }
			if (WUMPA_AWOKEN.matcher(s).find()) { onWumpaAwoken(); return; }
			if (GATE_OPEN.matcher(s).find()) { onGateOpen(); return; }
			if (DOOM_SUMMON.matcher(s).find()) { onDoomSummoned(); return; }
			Matcher xp = HUNT_XP.matcher(s);
			if (xp.find()) huntingXp += parseNum(xp.group(1));
		});

		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (RynConfig.safariTracker) drainPartyQueue(mc);
			if (RynConfig.safariTracker && SkyBlockCheck.onSkyBlock()) com.ryn.skyryn.fusion.BazaarPrices.refreshIfNeeded();
			String isl = SkyBlockCheck.onSkyBlock() ? SkyBlockCheck.currentIsland() : "";
			boolean inZone = "crittersafari".equals(isl);
			int[] cc0 = capsuleCounts();
			int capNow = cc0[0] + cc0[1];
			if (inZone && !visitActive && prevCapsuleTotal == 0 && capNow > 0) {
				visitActive = true; inSafari = true; reset();
				pendingEntryMsgAt = System.currentTimeMillis() + 2000;
			} else if (!inZone && visitActive && pendingExitAt == 0 && isl != null && !isl.isBlank()) {
				endVisit(false);
			}
			prevCapsuleTotal = capNow;
			if (visitActive) { long eb = SkyBlockCheck.safariEssence(); if (eb >= 0) lastEssenceBal = eb; }
			if (pendingExitAt > 0 && System.currentTimeMillis() >= pendingExitAt) {
				pendingExitAt = 0;
				long barEssence = forestEssence;
				long eb2 = SkyBlockCheck.safariEssence();
				if (eb2 >= 0) lastEssenceBal = eb2;
				scoreEssence = (essenceStart >= 0 && lastEssenceBal >= 0) ? lastEssenceBal - essenceStart : -1;
				if (scoreEssence >= 0) forestEssence = scoreEssence;
				else if (rewardEssence > 0) forestEssence = rewardEssence;
				StringBuilder sb = new StringBuilder("[SkyRyn] shards наш=" + shardsCaught + " reward=" + rewardShards
						+ " | essence бар=" + barEssence + " reward=" + rewardEssence + " score=" + scoreEssence + " |");
				runShards.forEach((k, v) -> sb.append(' ').append(k).append('x').append(v));
				runLs.forEach((k, v) -> sb.append(" ls:").append(k).append('x').append(v));
				com.ryn.skyryn.config.SkyLog.d(sb.toString());
				endVisit(true);
			}
			if (pendingEntryMsgAt > 0 && System.currentTimeMillis() >= pendingEntryMsgAt) tryEntryMessage();
			if (visitActive && !gateOpen && gemsPlacedWorld() >= 3) onGateOpen();
			if (visitActive && entrySent) {
				int[] cc = capsuleCounts();
				long now = System.currentTimeMillis();
				if (cc[0] + cc[1] == 0) {
					if (capsuleZeroAt == 0) capsuleZeroAt = now;
					else if (!capsuleOutSent && now - capsuleZeroAt > 10000) {
						capsuleOutSent = true;
						if (partyOn()) sendParty(Lang.tr("Out of capsules!", "Капсулы кончились!"));
					}
				} else { capsuleZeroAt = 0; capsuleOutSent = false; }
			}
			if (visitActive && msgOn("biome", true)) {
				String cb = com.ryn.skyryn.waypoint.SafariBiomes.currentBiome();
				long now = System.currentTimeMillis();
				if (!cb.equals(lastAnnouncedBiome) && now - lastBiomeAnnounceAt > 3000) {
					if (!cb.isEmpty() && partyOn())
						sendParty(Lang.tr("Entered ", "Вошёл в ") + com.ryn.skyryn.waypoint.SafariBiomes.currentColored());
					lastAnnouncedBiome = cb;
					lastBiomeAnnounceAt = now;
				}
			}
			if (inSafari) scanSparkling(mc);
			if (visitActive && mc.player != null) {
				String cb = com.ryn.skyryn.waypoint.SafariBiomes.currentBiome();
				long now = System.currentTimeMillis();
				if (!cb.isEmpty() && RynConfig.flag("dupe." + cb, !cb.equals("forest")) && now - lastDupeAt > 30000) {
					java.util.List<String> names = com.ryn.skyryn.waypoint.SafariBiomes.playersInList(cb);
					if (names.size() >= 2) {
						String me = mc.getUser().getName();
						String first = names.stream().min(String::compareToIgnoreCase).orElse(me);
						if (first.equalsIgnoreCase(me)) {
							lastDupeAt = now;
							if (partyOn()) sendParty("§c⚠ " + Lang.tr("Dupe in ", "Дюп в ") + cb
									+ " (" + String.join(", ", names) + ")");
						}
					}
				}
			}
		});

		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_CHAT.register(msg -> {
			String low = msg.trim().toLowerCase();
			if (!low.startsWith("#")) return true;
			String r = commandResult(low);
			if (r == null) return true;
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Safari] " + r));
			return false;
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyryn", "safari-tracker"),
				(ctx, tick) -> renderHud(ctx));

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
			String title = screen.getTitle().getString();
			if (title == null || !title.toLowerCase().contains("safari")) return;
			ScreenMouseEvents.allowMouseClick(screen).register((scr, event) -> {
				String t = ticketAt(cs, (int) event.x(), (int) event.y());
				if (t != null) { lastTicket = t; com.ryn.skyryn.config.SkyLog.d("тикет распознан: " + t + " (меню '" + title + "')"); }
				return true;
			});
		});
	}

	private static String ticketAt(AbstractContainerScreen<?> cs, int mx, int my) {
		if (!(cs instanceof ContainerScreenAccessor acc)) return null;
		int left = acc.skyryn$leftPos(), top = acc.skyryn$topPos();
		for (Slot s : cs.getMenu().slots) {
			if (!s.hasItem()) continue;
			int sx = left + s.x, sy = top + s.y;
			if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
				String n = s.getItem().getHoverName().getString().toLowerCase();
				com.ryn.skyryn.config.SkyLog.d("клик по предмету: '" + n + "'");
				return ticketTier(n);
			}
		}
		return null;
	}

	private static String ticketTier(String n) {
		if (!n.contains("ticket") && !n.contains("safari")) return null;
		if (n.contains("first-class") || n.contains("first class")) return "First-Class";
		if (n.contains("premium")) return "Premium";
		if (n.contains("economy")) return "Economy";
		if (n.contains("basic")) return "Basic";
		return null;
	}

	private static long pendingEntryMsgAt = 0;
	private static long pendingExitAt = 0;
	private static long rewardEssence = 0;
	private static int rewardShards = 0;
	private static long rewardHuntXp = 0;
	private static long essenceStart = -1;
	private static long lastEssenceBal = -1;
	private static long scoreEssence = -1;
	private static int prevCapsuleTotal = 0;
	private static String lastAnnouncedBiome = "";
	private static long lastBiomeAnnounceAt = 0;
	private static long lastDupeAt = 0;
	private static long capsuleZeroAt = 0;
	private static boolean capsuleOutSent = false;

	private static void reset() {
		floorDrops = 0; huntingXp = 0; forestEssence = 0; shardsCaught = 0; floorShards = 0; lootshareShards = 0;
		captures.clear(); floorItems.clear(); doomSummonAt = 0;
		wumpaAwoken = false; wumpaCaught = false; doomSummoned = false; doomCaught = false;
		gateOpen = false; gateCleared = false; gemsPlaced = 0; gemzieCaught = 0;
		caughtIcy.clear(); troodonShards = 0;
		runShards.clear(); runLs.clear();
		currentHotspot = ""; entryGiven.clear();
		startCapsuleTotal = 0; visitStartAt = 0; entrySent = false;
		pendingExitAt = 0; rewardEssence = 0; rewardShards = 0; rewardHuntXp = 0; essenceStart = -1; lastEssenceBal = -1; scoreEssence = -1;
		lastAnnouncedBiome = ""; lastBiomeAnnounceAt = 0;
		capsuleZeroAt = 0; capsuleOutSent = false;
		lastEssenceStr = ""; lastEssenceAt = 0;
	}

	private static void tryEntryMessage() {
		if (entrySent || !visitActive) return;
		entrySent = true;
		pendingEntryMsgAt = 0;
		int[] c = capsuleCounts();
		startCapsuleTotal = c[0] + c[1];
		visitStartAt = System.currentTimeMillis();
		essenceStart = SkyBlockCheck.safariEssence();
		lastEssenceBal = essenceStart;
		com.ryn.skyryn.config.SkyLog.d("вход: essenceStart=" + essenceStart + " | сайдбар: " + SkyBlockCheck.sidebarDump());
		if (partyOn() && msgOn("entry", true)) sendEntryMessage();
	}

	private static void endVisit(boolean withSummary) {
		if (!visitActive) { inSafari = false; return; }
		if (withSummary) sendExitSummary();
		visitActive = false;
		inSafari = false;
		pendingEntryMsgAt = 0;
		visitStartAt = 0;
	}

	private static void sendEntryMessage() {
		int[] c = capsuleCounts();
		StringBuilder sb = new StringBuilder("Capsules: ").append(c[0]).append(" | ").append(c[1]).append(" Masterful");
		if (!currentHotspot.isEmpty()) sb.append(" | HS: ").append(currentHotspot);
		boolean spark = com.ryn.skyryn.data.SafariPerks.hasSparkling() || hasItemContaining("sparkling");
		sb.append(" | Sparkling necklace: ").append(spark ? "✓" : "✗");
		sendParty(sb.toString());
	}

	private static boolean hasItemContaining(String sub) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;
		var inv = mc.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st != null && !st.isEmpty() && st.getHoverName().getString().toLowerCase().contains(sub)) return true;
		}
		return false;
	}

	private static void sendExitSummary() {
		int caps = capturesTotal();
		int[] cc = capsuleCounts();
		int spent = Math.max(0, startCapsuleTotal - (cc[0] + cc[1]));
		long dt = visitStartAt > 0 ? System.currentTimeMillis() - visitStartAt : 0;
		sessRuns++; sessFloorDrops += floorDrops; sessCaptures += caps; sessShards += shardsCaught;
		sessCapsSpent += spent; sessEssence += forestEssence; sessTimeMs += dt; sessLootshare += lootshareShards;
		sessHuntXp += rewardHuntXp;
		sessTickets++;
		runShards.forEach((k, v) -> sessShardsMap.merge(k, v, Integer::sum));
		runLs.forEach((k, v) -> sessLsMap.merge(k, v, Integer::sum));
		runShards.forEach((k, v) -> RynConfig.lifeShards.merge(k, v, Integer::sum));
		runLs.forEach((k, v) -> RynConfig.lifeShards.merge(k, v, Integer::sum));
		captures.forEach((k, v) -> RynConfig.lifeCaptures.merge(k.toLowerCase(), v, Integer::sum));
		RynConfig.lifeEssence += forestEssence;
		RynConfig.lifeTimeMs += dt;
		RynConfig.lifeHuntXp += rewardHuntXp;
		RynConfig.lifeRuns++;
		ConfigManager.save();
		if (partyOn() && msgOn("exit", true)) {
			String ess = scoreEssence >= 0 ? String.valueOf(scoreEssence) : "?";
			sendParty("Capsules used: " + spent + " | Essence " + ess
					+ " | Captured: " + caps + " | Shards: " + shardsCaught
					+ " | Lootshare: " + lootshareShards + " | Time: " + fmtTime(dt));
		}
	}

	public static com.google.gson.JsonObject trackerToJson() {
		com.google.gson.JsonObject o = new com.google.gson.JsonObject();
		o.addProperty("runs", sessRuns);
		o.addProperty("floorDrops", sessFloorDrops);
		o.addProperty("captures", sessCaptures);
		o.addProperty("shards", sessShards);
		o.addProperty("capsSpent", sessCapsSpent);
		o.addProperty("lootshare", sessLootshare);
		o.addProperty("tickets", sessTickets);
		o.addProperty("essence", sessEssence);
		o.addProperty("timeMs", sessTimeMs);
		o.addProperty("huntXp", sessHuntXp);
		com.google.gson.JsonObject sm = new com.google.gson.JsonObject();
		sessShardsMap.forEach(sm::addProperty);
		o.add("shardsMap", sm);
		com.google.gson.JsonObject lm = new com.google.gson.JsonObject();
		sessLsMap.forEach(lm::addProperty);
		o.add("lsMap", lm);
		return o;
	}
	public static void trackerFromJson(com.google.gson.JsonObject o) {
		if (o == null) return;
		sessRuns = o.has("runs") ? o.get("runs").getAsInt() : 0;
		sessFloorDrops = o.has("floorDrops") ? o.get("floorDrops").getAsInt() : 0;
		sessCaptures = o.has("captures") ? o.get("captures").getAsInt() : 0;
		sessShards = o.has("shards") ? o.get("shards").getAsInt() : 0;
		sessCapsSpent = o.has("capsSpent") ? o.get("capsSpent").getAsInt() : 0;
		sessLootshare = o.has("lootshare") ? o.get("lootshare").getAsInt() : 0;
		sessTickets = o.has("tickets") ? o.get("tickets").getAsInt() : 0;
		sessEssence = o.has("essence") ? o.get("essence").getAsLong() : 0;
		sessTimeMs = o.has("timeMs") ? o.get("timeMs").getAsLong() : 0;
		sessHuntXp = o.has("huntXp") ? o.get("huntXp").getAsLong() : 0;
		sessShardsMap.clear();
		if (o.has("shardsMap")) for (var en : o.getAsJsonObject("shardsMap").entrySet()) sessShardsMap.put(en.getKey(), en.getValue().getAsInt());
		sessLsMap.clear();
		if (o.has("lsMap")) for (var en : o.getAsJsonObject("lsMap").entrySet()) sessLsMap.put(en.getKey(), en.getValue().getAsInt());
	}
	public static void resetTracker() {
		sessRuns = 0; sessFloorDrops = 0; sessCaptures = 0; sessShards = 0; sessCapsSpent = 0; sessLootshare = 0; sessTickets = 0;
		sessEssence = 0; sessTimeMs = 0; sessHuntXp = 0;
		sessShardsMap.clear(); sessLsMap.clear();
		ConfigManager.save();
	}

	public static void resetEverything() {
		RynConfig.lifeShards.clear();
		RynConfig.lifeCaptures.clear();
		RynConfig.lifeEssence = 0; RynConfig.lifeTimeMs = 0; RynConfig.lifeHuntXp = 0; RynConfig.lifeRuns = 0;
		resetTracker();
	}

	private static String statsText() {
		int[] c = capsuleCounts();
		int used = Math.max(0, startCapsuleTotal - (c[0] + c[1]));
		long dt = visitStartAt > 0 ? System.currentTimeMillis() - visitStartAt : 0;
		return Lang.tr("Capsules used: ", "Капсул использовано: ") + used + "/" + startCapsuleTotal
				+ " | " + Lang.tr("Captured: ", "Поймано: ") + capturesTotal()
				+ " | " + Lang.tr("Shards: ", "Шардов: ") + shardsCaught
				+ " | Lootshare: " + lootshareShards
				+ " | " + Lang.tr("Essence: ", "Эссенция: ") + forestEssence
				+ " | " + Lang.tr("Time: ", "Время: ") + fmtTime(dt);
	}

	private static boolean atBiome(String b) { return b.equals(com.ryn.skyryn.waypoint.SafariBiomes.currentBiome()); }

	private static void onDoomSummoned() {
		if (doomSummoned) return;
		doomSummoned = true; doomSummonAt = System.currentTimeMillis();
		if (partyOn() && atBiome("haunted") && RynConfig.flag("doom.ann.summoned", true)) sendParty("Doomspiral " + Lang.tr("summoned!", "призван!") + tookTail());
	}
	private static void onWumpaAwoken() {
		if (wumpaAwoken) return;
		wumpaAwoken = true;
		if (partyOn() && atBiome("icy") && RynConfig.flag("wumpa.ann.awoken", true)) sendParty("Wumpa " + Lang.tr("awoken!", "пробудился!") + tookTail());
	}

	private static void checkWumpaReady() {
		if (wumpaAwoken || wumpaCaught || caughtIcy.size() < ICY_MOBS.size()) return;
		onWumpaAwoken();
	}
	private static void onGateOpen() {
		if (gateOpen) return;
		gateOpen = true;
		if (RynConfig.flag("gate.ann.open", true) && partyOn() && atBiome("cavern")) sendParty(Lang.tr("Gate open!", "Ворота открыты!") + tookTail());
	}
	private static void checkGateCleared() {
		if (gemzieCaught < 3 || gateCleared) return;
		gateCleared = true;
		if (RynConfig.flag("gate.ann.cleared", true) && partyOn() && atBiome("cavern")) sendParty(Lang.tr("Gate cleared!", "Врата зачищены!") + tookTail());
	}
	private static void onDoomCaptured() {
		if (partyOn() && RynConfig.flag("doom.ann.defeated", true)) sendParty("Doomspiral " + Lang.tr("defeated!", "повержен!") + defeatTail(true));
		doomSummonAt = 0;
	}
	private static void onWumpaCaptured() {
		if (partyOn() && RynConfig.flag("wumpa.ann.defeated", true)) sendParty("Wumpa " + Lang.tr("defeated!", "повержен!") + defeatTail(false));
	}

	private static String defeatTail(boolean doom) {
		if (!msgOn("times", true) || visitStartAt <= 0) return "";
		long dt = System.currentTimeMillis() - visitStartAt;
		long best = doom ? RynConfig.doomPbMs : RynConfig.wumpaPbMs;
		String pb = pbNote(dt, best);
		best = bestPb(dt, best);
		if (doom) RynConfig.doomPbMs = best; else RynConfig.wumpaPbMs = best;
		ConfigManager.save();
		return " " + Lang.tr("took ", "заняло ") + fmtTime(dt) + pb;
	}
	private static String pbNote(long dt, long pb) {
		if (!msgOn("pb", true)) return "";
		return (pb < 0 || dt < pb) ? Lang.tr(" (new PB!)", " (новый рекорд!)") : "";
	}
	private static long bestPb(long dt, long pb) { return (pb < 0 || dt < pb) ? dt : pb; }
	private static String fmtTime(long ms) {
		long s = ms / 1000;
		return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
	}
	private static String fmtDur(long ms) {
		long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60;
		return h > 0 ? h + "h " + m + "m" : (s / 60) + "m " + (s % 60) + "s";
	}
	private static int capturesTotal() { return captures.values().stream().mapToInt(Integer::intValue).sum(); }

	private static void addShardCount(String name, int qty, boolean ls) {
		String key = com.ryn.skyryn.data.ShardDb.keyByName(name);
		if (key == null) return;
		(ls ? runLs : runShards).merge(key, qty, Integer::sum);
	}
	private static final String[] ESSENCE_IDS = { "ESSENCE_SAFARI", "SAFARI_ESSENCE" };
	private static com.ryn.skyryn.fusion.BazaarPrices.Price essencePrice() {
		for (String id : ESSENCE_IDS) { var p = com.ryn.skyryn.fusion.BazaarPrices.get(id); if (p != null) return p; }
		return null;
	}
	private static long[] valueBoth(Map<String, Integer> shards, long essence) {
		if (com.ryn.skyryn.fusion.BazaarPrices.unavailable() || !com.ryn.skyryn.fusion.BazaarPrices.isLoaded()) return null;
		double is = 0, so = 0;
		for (var en : shards.entrySet()) {
			String bz = com.ryn.skyryn.data.ShardDb.bazaarId(en.getKey());
			var p = bz == null ? null : com.ryn.skyryn.fusion.BazaarPrices.get(bz);
			if (p != null) { is += p.sellOffer * en.getValue(); so += p.instaBuy * en.getValue(); }
		}
		if (essence > 0) { var pe = essencePrice(); if (pe != null) { is += pe.sellOffer * essence; so += pe.instaBuy * essence; } }
		return new long[]{ Math.round(is), Math.round(so) };
	}

	private static long liveTimeMs() { return sessTimeMs + (visitStartAt > 0 ? System.currentTimeMillis() - visitStartAt : 0); }
	private static long perHrLive(double amount) { double h = liveTimeMs() / 3600000.0; return h > 0 ? Math.round(amount / h) : 0; }
	private static long currentRunEssence() { return (essenceStart >= 0 && lastEssenceBal >= 0) ? Math.max(0, lastEssenceBal - essenceStart) : 0; }
	private static String profitText(long p, long ms) {
		if (p < 0) return "§8" + PRICES;
		if (ms <= 0) return "§8" + Lang.tr("calc…", "счёт…");
		double h = ms / 3600000.0;
		return "§a" + fmt(Math.round(p / h)) + "§7/hr";
	}

	private static long runProfitSingle() {
		if (!visitActive) return -1;
		long[] a = valueBoth(new LinkedHashMap<>(runShards), 0);
		long[] b = valueBoth(new LinkedHashMap<>(runLs), 0);
		long[] c = valueBoth(new LinkedHashMap<>(), currentRunEssence());
		if (a == null) return -1;
		return RynConfig.safariSellOffer ? a[1] + b[1] + c[1] : a[0] + b[0] + c[0];
	}

	private static long liveProfitSingle() {
		Map<String, Integer> own = new LinkedHashMap<>(sessShardsMap);
		Map<String, Integer> ls = new LinkedHashMap<>(sessLsMap);
		if (visitActive) { runShards.forEach((k, v) -> own.merge(k, v, Integer::sum)); runLs.forEach((k, v) -> ls.merge(k, v, Integer::sum)); }
		long ess = sessEssence + (visitActive ? currentRunEssence() : 0);
		long[] a = valueBoth(own, 0), b = valueBoth(ls, 0), c = valueBoth(new LinkedHashMap<>(), ess);
		if (a == null) return -1;
		return RynConfig.safariSellOffer ? a[1] + b[1] + c[1] : a[0] + b[0] + c[0];
	}
	private static long lifeShardsTotal() { return RynConfig.lifeShards.values().stream().mapToLong(Integer::longValue).sum(); }
	private static long lifeCapturesTotal() { return RynConfig.lifeCaptures.values().stream().mapToLong(Integer::longValue).sum(); }
	private static long lifeProfitSingle() {
		long[] b = valueBoth(RynConfig.lifeShards, RynConfig.lifeEssence);
		return b == null ? -1 : (RynConfig.safariSellOffer ? b[1] : b[0]);
	}

	private static String fmtFull(long n) { return String.format("%,d", n); }

	public static final String[][] COMMANDS = {
			{ "#profit #pr",                 "Coins per hour, session",           "Коины в час за сессию" },
			{ "#profitr #prr",               "Coins per hour, current run",       "Коины в час за текущий заход" },
			{ "#essence #es",                "Essence per hour, session",         "Эссенция в час за сессию" },
			{ "#essencer #esr",              "Essence per hour, current run",     "Эссенция в час за текущий заход" },
			{ "#shards #sh",                 "Shards per hour, session",          "Шарды в час за сессию" },
			{ "#shardsr #shr",               "Shards per hour, current run",      "Шарды в час за текущий заход" },
			{ "#capture #ct",                "Catches per hour; #ct <mob> — that mob only",
					"Поимки в час; #ct <моб> — только по нему" },
			{ "#capturer #ctr",              "Catches per hour, current run",     "Поимки в час за текущий заход" },
			{ "#exp #ex",                    "Hunting exp per hour, session",     "Опыт охоты в час за сессию" },
			{ "#expr #exr",                  "Hunting exp per hour, current run", "Опыт охоты в час за текущий заход" },
			{ "#total #tl",                  "Profit for all time",               "Профит за всё время" },
			{ "#essencet #essencetotal #et", "Essence for all time",              "Эссенция за всё время" },
			{ "#shardstotal #shtl",          "Shards for all time",               "Шарды за всё время" },
			{ "#capturetotal #cttl",         "Catches for all time",              "Поимок за всё время" },
			{ "#expall #ext",                "Hunting exp for all time",          "Опыт охоты за всё время" },
			{ "#critterplaytime #cpt",       "Time in safari, session",           "Время в сафари за сессию" },
			{ "#cptall",                     "Time in safari for all time",       "Время в сафари за всё время" },
			{ "#perks",                      "Your safari perks — open the perks shop once so the mod can read them",
					"Твои перки сафари — открой магазин перков, чтобы мод их прочитал" },
			{ "#stats",                      "Everything about the current run",  "Всё о текущем заходе" },
			{ "#help",                       "The list of commands in chat",      "Список команд в чат" },
	};

	private static final String HELP = "#pr #es #sh #ct #exp (session) | #prr #esr #shr #ctr #expr (run) "
			+ "| #tl #et #shtl #cttl #expall (all time) | #cpt #cptall | #ct <mob> | #perks #stats #help";
	private static final String PRICES = Lang.tr("prices…", "цены…");

	public static java.util.List<String> commandLines() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (String[] c : COMMANDS) {
			String[] a = c[0].split(" ");
			StringBuilder sb = new StringBuilder("§b").append(a[0]);
			if (a.length > 1) sb.append(" §8").append(String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
			out.add(sb.append(" §7— ").append(Lang.tr(c[1], c[2])).toString());
		}
		return out;
	}

	private static long resetArmedAt = 0;
	public static void resetEverythingConfirm() {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (now - resetArmedAt > 5000) {
			resetArmedAt = now;
			say(mc, "§c" + Lang.tr("This wipes EVERYTHING, all-time totals included. Click again within 5 seconds to confirm.",
					"Это сотрёт ВСЁ, включая итоги за всё время. Нажми ещё раз в течение 5 секунд, чтобы подтвердить."));
			return;
		}
		resetArmedAt = 0;
		resetEverything();
		say(mc, Lang.tr("Everything is reset.", "Всё обнулено."));
	}

	private static void say(Minecraft mc, String msg) {
		if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[Safari]§r " + msg));
	}

	public static void printCommands() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		say(mc, Lang.tr("party commands:", "команды пати:"));
		for (String s : commandLines())
			mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  " + s));
	}

	private static String canon(String cmd) {
		for (String[] c : COMMANDS) {
			String[] a = c[0].split(" ");
			for (String s : a) if (s.equals(cmd)) return a[0];
		}
		return null;
	}

	private static long runTimeMs() { return visitStartAt > 0 ? System.currentTimeMillis() - visitStartAt : 0; }

	private static String runRate(double amount, boolean coins) {
		long ms = runTimeMs();
		if (!visitActive || ms <= 0) return "—";
		long v = Math.round(amount / (ms / 3600000.0));
		return (coins ? fmt(v) : fmtFull(v)) + "/hr";
	}

	private static final boolean COMMANDS_OFF = false;
	private static String commandResult(String body) {
		if (COMMANDS_OFF) return null;
		String b = body.trim();
		int sp = b.indexOf(' ');
		String cmd = sp < 0 ? b : b.substring(0, sp);
		if (cmd.equals("#scan")) return scanNearby();
		String c = canon(cmd);
		if (c == null) return null;
		String arg = sp < 0 ? "" : b.substring(sp + 1).trim();

		switch (c) {
			case "#capturetotal": return Lang.tr("Total caught: ", "Всего поймано: ") + fmtFull(lifeCapturesTotal());
			case "#shardstotal": return Lang.tr("Total shards: ", "Всего шардов: ") + fmtFull(lifeShardsTotal());
			case "#essencet": return Lang.tr("Total essence: ", "Всего эссенции: ") + fmtFull(RynConfig.lifeEssence);
			case "#expall": return Lang.tr("Total exp: ", "Всего опыта: ") + fmtFull(RynConfig.lifeHuntXp);
			case "#total": {
				long v = lifeProfitSingle();
				return Lang.tr("Runs: ", "Заходов: ") + RynConfig.lifeRuns
						+ " | " + Lang.tr("Time: ", "Время: ") + fmtDur(RynConfig.lifeTimeMs)
						+ " | " + Lang.tr("Total Profit: ", "Профит всего: ") + (v < 0 ? PRICES : fmt(v));
			}
			case "#cptall": return Lang.tr("Total playtime: ", "Всего в сафари: ") + fmtDur(RynConfig.lifeTimeMs);
			case "#critterplaytime": return Lang.tr("Playtime: ", "Время сессии: ") + fmtDur(liveTimeMs());

			case "#profitr": {
				if (!visitActive) return Lang.tr("Run: ", "Заход: ") + "—";
				long p = runProfitSingle();
				return Lang.tr("Run: ", "Заход: ") + (p < 0 ? PRICES : runRate(p, true));
			}
			case "#essencer": return Lang.tr("Essence (run): ", "Эссенция (заход): ") + runRate(currentRunEssence(), false);
			case "#shardsr": return Lang.tr("Shards (run): ", "Шарды (заход): ") + runRate(shardsCaught, false);
			case "#capturer": return Lang.tr("Captures (run): ", "Поимки (заход): ") + runRate(capturesTotal(), false);
			case "#expr": return Lang.tr("Exp (run): ", "Опыт (заход): ") + runRate(huntingXp, false);

			case "#profit": { long p = liveProfitSingle(); return p < 0 ? PRICES : Lang.tr("Profit: ", "Профит: ") + fmt(perHrLive(p)) + "/hr"; }
			case "#essence": return Lang.tr("Essence: ", "Эссенция: ") + fmtFull(perHrLive(sessEssence + (visitActive ? currentRunEssence() : 0))) + "/hr";
			case "#shards": return Lang.tr("Shards: ", "Шарды: ") + fmtFull(perHrLive(sessShards + (visitActive ? shardsCaught : 0))) + "/hr";
			case "#exp": return Lang.tr("Exp: ", "Опыт: ") + fmtFull(perHrLive(sessHuntXp + (visitActive ? huntingXp : 0))) + "/hr";
			default: break;
		}
		if (c.equals("#capture")) {
			if (arg.isEmpty()) return Lang.tr("Captures: ", "Поймано: ") + fmtFull(perHrLive(sessCaptures + (visitActive ? capturesTotal() : 0))) + "/hr";
			String key = arg.toLowerCase();
			int live = 0;
			for (var en : captures.entrySet()) if (en.getKey().equalsIgnoreCase(arg)) live += en.getValue();
			int base = com.ryn.skyryn.data.BestiaryDb.killsLive(arg);
			if (base >= 0) return cap(arg) + Lang.tr(" caught: ", " поймано: ") + fmtFull(base + live);
			int own = RynConfig.lifeCaptures.getOrDefault(key, 0) + live;
			if (own > 0) return cap(arg) + Lang.tr(" caught: ", " поймано: ") + fmtFull(own);
			return cap(arg) + Lang.tr(": no count yet — open the Critter Safari menu with this mob once",
					": пока нечего показать — открой меню Critter Safari с этим мобом");
		}
		if (c.equals("#perks")) return "Perks: " + com.ryn.skyryn.data.SafariPerks.list();
		if (c.equals("#stats")) return statsText();
		if (c.equals("#help")) return HELP;
		return null;
	}

	private static String scanNearby() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return "no world";
		Vec3 p = mc.player.position();
		StringBuilder log = new StringBuilder("[SkyRyn] SCAN @" + fmtPos(p) + "\n");
		int n = 0;
		for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player) continue;
			double d = e.position().distanceTo(p);
			if (d > 8) continue;
			String extra = "";
			if (e instanceof net.minecraft.world.entity.item.ItemEntity ie)
				extra = " item='" + strip(ie.getItem().getHoverName().getString()) + "' x" + ie.getItem().getCount();
			String nm = e.getCustomName() != null ? strip(e.getCustomName().getString()) : "";
			log.append(String.format(java.util.Locale.US, "  %.1fm %s%s%s @%s%n", d,
					net.minecraft.world.entity.EntityType.getKey(e.getType()),
					nm.isEmpty() ? "" : " name='" + nm + "'", extra, fmtPos(e.position())));
			n++;
		}
		if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
			var bs = mc.level.getBlockState(bhr.getBlockPos());
			log.append("  LOOK block=").append(bs).append(" @").append(bhr.getBlockPos());
		}
		com.ryn.skyryn.config.SkyLog.d(log.toString());
		return "scan: " + n + Lang.tr(" entities -> log", " сущностей -> лог");
	}
	private static String fmtPos(Vec3 v) { return String.format(java.util.Locale.US, "%.1f,%.1f,%.1f", v.x, v.y, v.z); }
	private static void handlePartyCommand(String body) {
		String r = commandResult(body);
		if (r != null) sendParty(r);
	}

	private static final Pattern BELL_RUNG =
			Pattern.compile("rang the bell|bell has already been rung", Pattern.CASE_INSENSITIVE);

	private static void markBellHere() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		int best = -1;
		double bd = 12 * 12;
		for (int i = 0; i < BELLS.length; i++) {
			if (RynConfig.flag("bell." + i, false)) continue;
			double d = mc.player.distanceToSqr(BELLS[i][0] + 0.5, BELLS[i][1], BELLS[i][2] + 0.5);
			if (d < bd) { bd = d; best = i; }
		}
		if (best < 0) return;
		RynConfig.setFlag("bell." + best, true);
		ConfigManager.save();
		int left = 0;
		for (int i = 0; i < BELLS.length; i++) if (!RynConfig.flag("bell." + i, false)) left++;
		showAnnounce(Announce.BELL, Lang.tr("Bell ", "Колокол ") + (best + 1) + "/7",
				left == 0 ? Lang.tr("all seven — go to «Hunter» Tobias!", "все семь — иди к «Hunter» Tobias!")
						: Lang.tr("left: ", "осталось: ") + left);
	}

	private static final java.util.Set<Integer> sparkSeen = new java.util.HashSet<>();
	private static long lastSparkScan = 0;
	private static int sparkRun = 0;

	private static void scanSparkling(Minecraft mc) {
		if (!RynConfig.flag("sparkling.ann", true) || mc.level == null || mc.player == null) return;
		if (System.currentTimeMillis() - lastSparkScan < 1000) return;
		lastSparkScan = System.currentTimeMillis();
		for (net.minecraft.world.entity.Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(48))) {
			var n = e.getCustomName();
			if (n == null) continue;
			String s = strip(n.getString());
			if (s == null || !s.toUpperCase().contains("SPARKLING")) continue;
			if (!sparkSeen.add(e.getId())) continue;
			sparkRun++;
			String who = s.replaceAll("(?i)\\[?lv\\.?\\s*\\d+\\]?", "")
					.replaceAll("(?i)sparkling", "").replaceAll("[\\d,/❤]+", "").trim();
			showAnnounce(Announce.SPARKLING, Announce.text(Announce.SPARKLING, "SPARKLING!"),
					who.isEmpty() ? Lang.tr("a sparkling critter nearby!", "рядом sparkling-криттер!") : who);
			if (partyOn()) sendParty("SPARKLING " + (who.isEmpty() ? "critter" : who) + "!");
		}
		if (sparkSeen.size() > 512) sparkSeen.clear();
	}

	public static int sparklingRun() { return sparkRun; }
	public static int sparklingLife() { return RynConfig.getInt("spark.life", 0); }

	private static String annId = "", annBig = "", annSub = "";
	private static long annAt = -100000;

	private static void showAnnounce(String id, String big, String sub) {
		annId = id; annBig = big; annSub = sub;
		annAt = System.currentTimeMillis();
	}

	private static void drawAnnounce(GuiGraphicsExtractor ctx, Minecraft mc) {
		if (annBig.isEmpty()) return;
		long showMs = Announce.showMs(annId);
		long dt = System.currentTimeMillis() - annAt;
		if (dt < 0 || dt > showMs) return;
		Announce.draw(ctx, mc.font, annId, annBig, annSub, Math.round((1f - dt / (float) showMs) * 255));
	}

	private static final java.util.ArrayDeque<String> partyQueue = new java.util.ArrayDeque<>();
	private static long lastPartySend = 0;

	private static void sendParty(String msg) {
		if (RynConfig.safariSolo) {
			showOnScreen(msg);
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null)
				mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[Safari]§r " + msg));
			return;
		}
		partyQueue.add(msg);
	}

	private static void drainPartyQueue(Minecraft mc) {
		if (partyQueue.isEmpty()) return;
		long now = System.currentTimeMillis();
		if (now - lastPartySend < 2200) return;
		lastPartySend = now;
		String m = partyQueue.poll().replaceAll("§.", "");
		if (mc.player != null && mc.getConnection() != null)
			mc.getConnection().sendCommand("pc " + m);
	}

	private static final java.util.List<String> QUEST_ITEMS = java.util.List.of(
			"yogi berry", "bag of seeds", "wriggleworm", "icebreaker",
			"purple gem", "lime gem", "orange gem", "soothing incense", "shining coin");

	private static String questItemsText() {
		Minecraft mc = Minecraft.getInstance();
		java.util.LinkedHashMap<String, Integer> found = new java.util.LinkedHashMap<>();
		if (mc.player != null) {
			var inv = mc.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack st = inv.getItem(i);
				if (st == null || st.isEmpty()) continue;
				String n = st.getHoverName().getString();
				String low = n.toLowerCase();
				for (String q : QUEST_ITEMS) if (low.contains(q)) { found.merge(cap(q), st.getCount(), Integer::sum); break; }
			}
		}
		if (found.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (var e : found.entrySet()) { if (sb.length() > 0) sb.append(", "); sb.append(e.getValue()).append("x ").append(e.getKey()); }
		return sb.toString();
	}
	private static String cap(String s) {
		String[] w = s.split(" ");
		StringBuilder sb = new StringBuilder();
		for (String x : w) { if (sb.length() > 0) sb.append(' '); sb.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1)); }
		return sb.toString();
	}

	private static int[] capsuleCounts() {
		Minecraft mc = Minecraft.getInstance();
		int normal = 0, master = 0;
		if (mc.player != null) {
			var inv = mc.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack st = inv.getItem(i);
				if (st == null || st.isEmpty()) continue;
				String n = st.getHoverName().getString().toLowerCase();
				if (n.contains("masterful critter capsule")) master += st.getCount();
				else if (n.contains("critter capsule")) normal += st.getCount();
			}
		}
		return new int[]{ normal, master };
	}

	private static void renderHud(GuiGraphicsExtractor ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

		drawAnnounce(ctx, mc);

		if (!screenMsgs.isEmpty()) {
			long now = System.currentTimeMillis();
			screenMsgs.removeIf(m -> now >= m.expireAt());
			int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
			int x = Announce.px(sw, Announce.SAFARI), yy = Announce.py(sh, Announce.SAFARI);
			int step = Math.round(11 * Announce.scale(Announce.SAFARI));
			for (ScreenMsg m : screenMsgs) {
				Announce.draw(ctx, mc.font, Announce.SAFARI, m.text(), null, 255, x, yy);
				yy += step;
			}
		}

		if (RynConfig.questHighlight && inSafari) {
			String held = heldQuestItem();
			if (held != null) {
				ctx.text(mc.font, "§e" + questHint(held), RynConfig.safariHudX, RynConfig.safariHudY + plaqueH + 2, 0xFFFFFFFF, true);
			}
		}

		if (haveFrame && inSafari && !worldLabels.isEmpty()) {
			int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
			for (WorldLabel wl : worldLabels) {
				float rx = (float) (wl.x - camPos.x), ry = (float) (wl.y - camPos.y), rz = (float) (wl.z - camPos.z);
				Vector4f clip = VP.transform(new Vector4f(rx, ry, rz, 1f));
				if (clip.w <= 0.05f) continue;
				float ndcx = clip.x / clip.w, ndcy = clip.y / clip.w;
				if (ndcx < -1.2f || ndcx > 1.2f || ndcy < -1.2f || ndcy > 1.2f) continue;
				int px = Math.round((ndcx * 0.5f + 0.5f) * sw), py = Math.round((1f - (ndcy * 0.5f + 0.5f)) * sh);
				int dist = (int) Math.round(Math.sqrt((double) rx * rx + ry * ry + rz * rz));
				String t = wl.text + " §7" + dist + "m";
				ctx.text(mc.font, t, px - mc.font.width(t) / 2, py, 0xFFFFFFFF, true);
			}
		}

		if (RynConfig.safariTracker && inSafari) drawPlaque(ctx, mc.font);

		int wt = RynConfig.getInt("wumpa.tracker", 0);
		boolean wtShow = inSafari && !wumpaCaught && !caughtIcy.isEmpty() && caughtIcy.size() < ICY_MOBS.size()
				&& (wt == 0 || (wt == 2 && "icy".equals(com.ryn.skyryn.waypoint.SafariBiomes.currentBiome())));
		if (wtShow) {
			int wx = RynConfig.safariHudX, wy = RynConfig.safariHudY + plaqueH + 14;
			ctx.text(mc.font, "§c§lWumpa §7(" + caughtIcy.size() + "/" + ICY_MOBS.size() + ")", wx, wy, 0xFFFFFFFF, true); wy += 10;
			for (String icy : ICY_MOBS) { if (caughtIcy.contains(icy)) continue; ctx.text(mc.font, "§c ✗ §7" + cap(icy), wx + 4, wy, 0xFFFFFFFF, true); wy += 10; }
		}
	}

	private static int plaqueW = 120, plaqueH = 60;
	public static int plaqueW() { return plaqueW; }
	public static int plaqueH() { return plaqueH; }

	public static void drawPlaque(GuiGraphicsExtractor ctx, Font font) {
		float s = Math.max(0.5f, RynConfig.safariHudScale);
		ctx.pose().pushMatrix();
		ctx.pose().translate(RynConfig.safariHudX, RynConfig.safariHudY);
		ctx.pose().scale(s, s);
		int y = 0, maxW = 0;
		int[] cap = capsuleCounts();
		java.util.List<String> lines = new java.util.ArrayList<>();
		lines.add("§6§lCritter Safari" + (RynConfig.flag("tr.ticket", true) && !lastTicket.isEmpty() ? " §7(" + lastTicket + ")" : ""));
		lines.add("§7" + Lang.tr("Floor drops: §f", "Floor drop: §f") + floorDrops);
		lines.add("§7" + Lang.tr("Captures: §f", "Поймано: §f") + capturesTotal());
		int sm = RynConfig.getInt("tr.shards", 0);
		if (sm != 3) lines.add("§7" + Lang.tr("Shards: §f", "Шардов: §f")
				+ (sm == 1 ? lootshareShards : sm == 2 ? (shardsCaught + lootshareShards) : shardsCaught));
		if (RynConfig.flag("tr.floorshards", true)) lines.add("§7" + Lang.tr("Floor shards: §f", "Floor-шарды: §f") + floorShards);
		if (RynConfig.flag("tr.sparkling", true) && (sparkRun > 0 || sparklingLife() > 0))
			lines.add("§6" + Lang.tr("Sparkling: §f", "Sparkling: §f") + sparkRun + " §7/ §f" + sparklingLife());
		if (RynConfig.flag("tr.essence", true)) {
			long liveEss = (essenceStart >= 0 && lastEssenceBal >= 0) ? lastEssenceBal - essenceStart : -1;
			lines.add("§7" + Lang.tr("Essence: §f", "Эссенция: §f") + (liveEss >= 0 ? fmtFull(liveEss) : "?"));
		}
		if (RynConfig.flag("tr.capsules", true)) lines.add("§7" + Lang.tr("Capsules: §f", "Капсулы: §f") + cap[0] + " §7/ §f" + cap[1] + "§7 masterful");
		if (RynConfig.flag("tr.profit", true)) {
			int scope = RynConfig.getInt("tr.profitScope", 0);
			if (scope != 1) lines.add("§7" + Lang.tr("Profit: ", "Профит: ")
					+ profitText(liveProfitSingle(), liveTimeMs()));
			if (scope != 0) lines.add("§7" + Lang.tr("Run: ", "Заход: ")
					+ profitText(runProfitSingle(), visitStartAt > 0 ? System.currentTimeMillis() - visitStartAt : 0));
		}
		if (RynConfig.flag("tr.time", true) && visitStartAt > 0)
			lines.add("§7" + Lang.tr("Time: §f", "Время: §f") + fmtTime(System.currentTimeMillis() - visitStartAt));
		String cb = com.ryn.skyryn.waypoint.SafariBiomes.currentColored();
		lines.add("§7" + Lang.tr("Biome: ", "Биом: ") + (cb.isEmpty() ? "§8" + Lang.tr("Plaza", "площадь") : cb));
		if (RynConfig.flag("tr.hotspot", true) && !currentHotspot.isEmpty())
			lines.add("§b" + Lang.tr("Hotspot: §f", "Хотспот: §f") + currentHotspot);
		if (!entryGiven.isEmpty())
			lines.add("§7" + Lang.tr("Given: §f", "Выдано: §f") + String.join("§7,§f ", entryGiven));
		String quest = RynConfig.flag("tr.quest", true) ? questItemsText() : "";
		if (!quest.isEmpty()) { lines.add("§7" + Lang.tr("Quest:", "Квест:")); for (String q : wrapList(font, quest, 150)) lines.add("§8  " + q); }
		if (wumpaCaught) lines.add("§a✔ Wumpa");
		else if (wumpaAwoken) lines.add("§c" + Lang.tr("Wumpa awake", "Wumpa проснулся"));
		if (doomCaught) lines.add("§a✔ Doomspiral");
		else if (doomSummoned) lines.add("§5" + Lang.tr("Doomspiral active", "Doomspiral активен"));
		if (gateCleared) lines.add("§a✔ " + Lang.tr("Gate cleared", "Врата зачищены"));
		else if (gateOpen) lines.add("§5Gemzie §f" + gemzieCaught + "§7/3");
		else { int gp = gemsPlacedWorld(); if (gp > 0) lines.add("§b" + Lang.tr("Gate gems", "Врата самоцветы") + " §f" + gp + "§7/3"); }
		for (String ln : lines) maxW = Math.max(maxW, font.width(ln));
		if (RynConfig.colors.containsKey("plaqueBg"))
			ctx.fill(-3, -3, maxW + 3, lines.size() * 10 + 1, 0xC0000000 | (RynConfig.color("plaqueBg", 0) & 0xFFFFFF));
		int textCol = RynConfig.color("plaqueText", 0xFFFFFFFF);
		for (String ln : lines) { ctx.text(font, ln, 0, y, textCol, true); y += 10; }
		ctx.pose().popMatrix();
		plaqueW = Math.max(60, Math.round(maxW * s));
		plaqueH = Math.max(20, Math.round(y * s));
	}

	private static java.util.List<String> wrapList(Font font, String csv, int maxW) {
		java.util.List<String> out = new java.util.ArrayList<>();
		String line = "";
		for (String p : csv.split(", ")) {
			String test = line.isEmpty() ? p : line + ", " + p;
			if (font.width("§8" + test) > maxW && !line.isEmpty()) { out.add(line); line = p; }
			else line = test;
		}
		if (!line.isEmpty()) out.add(line);
		return out;
	}

	private static final Matrix4f VP = new Matrix4f();
	private static Vec3 camPos = Vec3.ZERO;
	private static boolean haveFrame = false;

	private static final double[] WUMPA_SPOT = { -113, 80, -74 };
	private static final double[] DOOM_SPOT = { -5.5, 46, -24.5 };
	private static final double[] GATE_SPOT = { -141, 62, 51 };
	private static final double[] BIRDFEEDER_SPOT = { -1, 89, 44 };

	private static final double[][] BELLS = {
			{ -68, 66, -43 },
			{ -96, 46, -57 },
			{ -4, 96, -42 },
			{ 47, 55, -7 },
			{ -30, 125, 59 },
			{ -90, 109, 16 },
			{ -50, 81, 0 },
	};
	private static final double[][] HIDEONWALL_SPOTS = {
			{16.4, 71, -57.3}, {-3.411, 71, -82.276}, {-22.278, 71, -64.012}, {-23.006, 71, -73.364},
			{15.322, 79, -51.240}, {16.828, 80, -71.861}, {4.260, 79, -84.239}, {-22.251, 79, -57.048} };
	private static final double[][] INCENSE_ALTAR = {
			{-5.495, 47, -19.480}, {-10.498, 47, -24.496}, {-5.486, 47, -29.518}, {-0.483, 47, -24.422} };

	private static boolean iceHere(double[] c) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return true;
		var st = mc.level.getBlockState(net.minecraft.core.BlockPos.containing(c[0], c[1], c[2]));
		return !st.isAir() && st.getFluidState().isEmpty();
	}

	private static final double[][] ICE_SPOTS = { {-108.373, 89, -26.524}, {-131.044, 78, -61.046}, {-103.300, 80, -94.519} };
	private static final double[][] GEM_SPOTS = { {-139.5, 62, 56.547}, {-136.450, 62, 51.5}, {-139.5, 62, 46.480} };

	private record WorldLabel(double x, double y, double z, String text) { }
	private static final java.util.List<WorldLabel> worldLabels = new java.util.ArrayList<>();

	private static double[][] questSpots(String q) {
		return switch (q) {
			case "icebreaker" -> ICE_SPOTS;
			case "purple gem" -> new double[][]{ GEM_SPOTS[0] };
			case "lime gem" -> new double[][]{ GEM_SPOTS[1] };
			case "orange gem" -> new double[][]{ GEM_SPOTS[2] };
			case "shining coin" -> new double[][]{ {-5, 60, -52} };
			case "soothing incense" -> INCENSE_ALTAR;
			default -> new double[0][];
		};
	}

	private static double[] gemSpot(String gem) {
		return switch (gem) {
			case "purple gem" -> GEM_SPOTS[0];
			case "lime gem" -> GEM_SPOTS[1];
			case "orange gem" -> GEM_SPOTS[2];
			default -> null;
		};
	}
	private static int gemColor(String gem) {
		return switch (gem) {
			case "purple gem" -> 0xFFC050FF;
			case "lime gem" -> 0xFF80FF40;
			case "orange gem" -> 0xFFFF8020;
			default -> 0xFF55E0FF;
		};
	}
	private static String gemCode(String gem) {
		return switch (gem) { case "purple gem" -> "§d"; case "lime gem" -> "§a"; case "orange gem" -> "§6"; default -> "§b"; };
	}

	private static boolean gemHere(double[] c) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return false;
		return !mc.level.getBlockState(net.minecraft.core.BlockPos.containing(c[0], c[1], c[2])).isAir();
	}

	private static int gemsPlacedWorld() {
		int n = 0;
		for (double[] c : GEM_SPOTS) if (gemHere(c)) n++;
		return n;
	}

	private static boolean candleLit(double[] c) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return false;
		var st = mc.level.getBlockState(net.minecraft.core.BlockPos.containing(c[0], c[1], c[2]));
		return st.getBlock() instanceof net.minecraft.world.level.block.AbstractCandleBlock
				&& net.minecraft.world.level.block.AbstractCandleBlock.isLit(st);
	}

	private static String questHint(String held) {
		if (held.equals("icebreaker")) return Lang.tr("Break the ice", "Разрушьте лёд");
		if (held.equals("shining coin")) return Lang.tr("Throw into the water in the mansion basement", "Бросьте в воду в подвале особняка");
		if (held.equals("soothing incense")) return Lang.tr("Click to light a candle", "Кликните, чтобы зажечь свечу");
		if (held.endsWith("gem")) return Lang.tr("Place on the pedestal at the gate", "Установите в пьедестал у ворот");
		return Lang.tr("Place in the Birdfeeder", "Поместите в Birdfeeder");
	}

	private static String heldQuestItem() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		String n = mc.player.getMainHandItem().getHoverName().getString().toLowerCase();
		for (String q : QUEST_ITEMS) if (n.contains(q)) return q;
		return null;
	}

	public static void captureFrame(Vec3 cam) {
		Minecraft.getInstance().gameRenderer.getMainCamera().getViewRotationProjectionMatrix(VP);
		camPos = cam;
		haveFrame = true;
	}

	public static void renderWorld(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam) {
		worldLabels.clear();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		if (!RynConfig.safariTracker || !inSafari) return;

		VertexConsumer vc = buf.getBuffer(RenderTypes.lines());
		PoseStack.Pose e = ps.last();

		int wm = RynConfig.getInt("wumpa.mode", 2);
		if (wm == 2 || (wm == 1 && !wumpaCaught)) {
			double wx = WUMPA_SPOT[0] + 0.5, wy = WUMPA_SPOT[1], wz = WUMPA_SPOT[2] + 0.5;
			int wcol = wumpaCaught ? 0x50E070 : RynConfig.color("mk.wumpa", 0xFF4040);
			String wst = wumpaCaught ? "§a" + Lang.tr("Defeated", "Повержен")
					: wumpaAwoken ? "§c" + Lang.tr("Awoken", "Проснулся") : "§7" + Lang.tr("Sleeping", "Спит");
			marker(vc, e, cam, wx, wy, wz, wcol, !wumpaCaught);
			String cnt = (!wumpaCaught && !wumpaAwoken) ? " §7(" + caughtIcy.size() + "/" + ICY_MOBS.size() + ")" : "";
			worldLabels.add(new WorldLabel(wx, wy + 1.6, wz, "§b§lWumpa " + wst + cnt));
		}
		int dm = RynConfig.getInt("doom.mode", 2);
		if (dm == 2 || (dm == 1 && !doomCaught)) {
			double dx = DOOM_SPOT[0], dy = DOOM_SPOT[1], dz = DOOM_SPOT[2];
			int dcol = doomCaught ? 0x50E070 : RynConfig.color("mk.doom", 0x9040FF);
			String dst = doomCaught ? "§a" + Lang.tr("Defeated", "Повержен")
					: doomSummoned ? "§c" + Lang.tr("Summoned", "Призван") : "§7" + Lang.tr("Sleeping", "Спит");
			marker(vc, e, cam, dx, dy, dz, dcol, !doomCaught);
			worldLabels.add(new WorldLabel(dx, dy + 1.6, dz, "§5§lDoomspiral " + dst));
		}
		int gm = RynConfig.getInt("gate.mode", 2);
		if (gm == 2 || (gm == 1 && !gateCleared)) {
			double gx = GATE_SPOT[0] + 0.5, gy = GATE_SPOT[1], gz = GATE_SPOT[2] + 0.5;
			if (gateCleared) {
				marker(vc, e, cam, gx, gy, gz, 0x50E070, false);
				worldLabels.add(new WorldLabel(gx, gy + 1.6, gz, "§a§lGate " + Lang.tr("Cleared", "Зачищены")));
			} else if (!gateOpen) {
				marker(vc, e, cam, gx, gy, gz, RynConfig.color("mk.gate", 0x55E0FF), true);
				worldLabels.add(new WorldLabel(gx, gy + 1.6, gz, "§b§lGate §7(" + gemsPlacedWorld() + "/3)"));
			} else {
				marker(vc, e, cam, gx, gy, gz, RynConfig.color("mk.gate", 0xB060FF), true);
				worldLabels.add(new WorldLabel(gx, gy + 1.6, gz, "§5§lGemzie §7(" + gemzieCaught + "/3)"));
			}
		}
		if (RynConfig.flag("qh.yogi", true) || RynConfig.flag("qh.seeds", true) || RynConfig.flag("qh.wriggle", true)) {
			double bx = BIRDFEEDER_SPOT[0] + 0.5, by = BIRDFEEDER_SPOT[1], bz = BIRDFEEDER_SPOT[2] + 0.5;
			marker(vc, e, cam, bx, by, bz, RynConfig.color("mk.birdfeeder", 0xFFD24A), true);
			worldLabels.add(new WorldLabel(bx, by + 1.6, bz, "§6§lBirdfeeder"));
		}

		if (RynConfig.flag("bells.show", true)) {
			for (int i = 0; i < BELLS.length; i++) {
				if (RynConfig.flag("bell." + i, false)) continue;
				double[] c = BELLS[i];
				double bx = c[0] + 0.5, by = c[1], bz = c[2] + 0.5;
				marker(vc, e, cam, bx, by, bz, RynConfig.color("mk.bell", 0xFFE04A), true);
				worldLabels.add(new WorldLabel(bx, by + 1.6, bz, "§6§lBell §7" + (i + 1) + "/7"));
			}
		}

		if (RynConfig.flag("haunted.hwguess", false) && "haunted".equals(com.ryn.skyryn.waypoint.SafariBiomes.currentBiome())) {
			for (double[] c : HIDEONWALL_SPOTS) {
				if (mc.player.distanceToSqr(c[0] + 0.5, c[1], c[2] + 0.5) < 25) continue;
				marker(vc, e, cam, c[0] + 0.5, c[1], c[2] + 0.5, 0xC060FF, false);
				worldLabels.add(new WorldLabel(c[0] + 0.5, c[1] + 1.4, c[2] + 0.5, "§5Hideonwall?"));
			}
		}

		if (RynConfig.questHighlight && RynConfig.flag("qh.candle", true)
				&& "soothing incense".equals(heldQuestItem())
				&& "haunted".equals(com.ryn.skyryn.waypoint.SafariBiomes.currentBiome())) renderIncenseAltar(vc, e, cam);

		if (RynConfig.questHighlight) {
			String held = heldQuestItem();
			if (held != null) {
				if (held.endsWith("gem") && RynConfig.flag("qh." + held.split(" ")[0], true)) {
					double[] c = gemSpot(held);
					if (c != null && !gemHere(c) && !gateOpen) {
						marker(vc, e, cam, c[0], c[1], c[2], gemColor(held), true);
						worldLabels.add(new WorldLabel(c[0], c[1] + 1.6, c[2], gemCode(held) + cap(held)));
					}
				} else if (held.equals("icebreaker") && RynConfig.flag("qh.ice", true)) {
					for (double[] c : ICE_SPOTS) {
						if (!iceHere(c)) continue;
						marker(vc, e, cam, c[0], c[1], c[2], RynConfig.color("mk.ice", 0x8BE0FF), true);
						worldLabels.add(new WorldLabel(c[0], c[1] + 1.6, c[2], "§bIcebreaker"));
					}
				} else if (held.equals("shining coin") && RynConfig.flag("qh.coin", true)) {
					for (double[] c : questSpots(held)) {
						double qx = c[0] + 0.5, qy = c[1], qz = c[2] + 0.5;
						marker(vc, e, cam, qx, qy, qz, 0x55E0FF, true);
						worldLabels.add(new WorldLabel(qx, qy + 1.6, qz, "§b" + cap(held)));
					}
				}
			}
		}
		buf.endBatch();
	}

	private static void renderIncenseAltar(VertexConsumer vc, PoseStack.Pose e, Vec3 cam) {
		int lit = 0;
		for (double[] c : INCENSE_ALTAR) if (candleLit(c)) lit++;
		for (double[] c : INCENSE_ALTAR) {
			if (candleLit(c)) continue;
			markerFilled(vc, e, cam, c[0], c[1] - 1, c[2], RynConfig.color("mk.candle", 0xC050FF));
			worldLabels.add(new WorldLabel(c[0], c[1] + 1.0, c[2],
					"§d" + Lang.tr("Candle", "Свеча") + " §7(" + lit + "/4)"));
		}
	}

	private static void markerFilled(VertexConsumer vc, PoseStack.Pose e, Vec3 cam, double wx, double wy, double wz, int rgb) {
		marker(vc, e, cam, wx, wy, wz, rgb, true);
	}

	private static void marker(VertexConsumer vc, PoseStack.Pose e, Vec3 cam, double wx, double wy, double wz, int rgb, boolean beam) {
		float x = (float) (wx - cam.x), y = (float) (wy - cam.y), z = (float) (wz - cam.z);
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		float top = beam ? 64f : 4f;
		line(vc, e, x, y - 1, z, x, y + top, z, r, g, b, 16f);
	}

	private static void line(VertexConsumer vc, PoseStack.Pose e,
							 float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b) {
		line(vc, e, x1, y1, z1, x2, y2, z2, r, g, b, 4f);
	}

	private static void line(VertexConsumer vc, PoseStack.Pose e,
							 float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, float w) {
		float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len > 0) { nx /= len; ny /= len; nz /= len; }
		vc.addVertex(e, x1, y1, z1).setNormal(e, nx, ny, nz).setColor(r, g, b, 255).setLineWidth(w);
		vc.addVertex(e, x2, y2, z2).setNormal(e, nx, ny, nz).setColor(r, g, b, 255).setLineWidth(w);
	}

	private static int gainCount(String gainedLow) {
		Matcher m = Pattern.compile("(\\d+)\\s*x").matcher(gainedLow);
		return m.find() ? parseInt(m.group(1)) : 1;
	}

	private static String lastWord(String s) {
		if (s == null) return "";
		String[] w = s.trim().split("\\s+");
		return w.length == 0 ? "" : w[w.length - 1];
	}
	private static final String[] SAFARI_BIOMES = { "icy", "haunted", "cavern", "forest" };
	private static String biomeFromAnnounce(String msg) {
		if (msg == null) return null;
		String low = msg.toLowerCase();
		if (!low.startsWith("entered") && !low.startsWith("вошёл")) return null;
		for (String b : SAFARI_BIOMES) if (low.contains(b)) return b;
		return null;
	}

	private static long parseNum(String s) { try { return Long.parseLong(s.replace(",", "")); } catch (Exception e) { return 0; } }
	private static int parseInt(String s) { try { return Integer.parseInt(s.replace(",", "")); } catch (Exception e) { return 1; } }
	private static String fmt(double v) {
		if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
		if (v >= 1_000) return String.format("%.1fk", v / 1_000);
		return String.format("%.0f", v);
	}
	private static String strip(String s) { return s == null ? null : s.replaceAll("§.", ""); }
}
