package com.ryn.skyryn.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import com.ryn.skyryn.SkyRynClient;
import com.ryn.skyryn.data.BestiaryDb;
import com.ryn.skyryn.data.SeaGuideDb;
import com.ryn.skyryn.data.ShardProgress;
import com.ryn.skyryn.fusion.FusionTracker;
import com.ryn.skyryn.hud.HuntingTracker;

public class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("skyryn.json");
	}

	private static Path legacyPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("rynfusion.json");
	}

	public static void load() {
		try {
			Path path = configPath();
			if (!Files.exists(path)) {
				Path old = legacyPath();
				if (Files.exists(old)) {
					Files.copy(old, path);
					com.ryn.skyryn.config.SkyLog.d("Перенёс настройки из rynfusion.json");
				} else {
					RynConfig.allFeaturesOff();
					save();
					return;
				}
			}
			String json = Files.readString(path);
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();

			if (root.has("lang")) RynConfig.lang = root.get("lang").getAsString();
			if (root.has("shardsFlatView")) RynConfig.shardsFlatView = root.get("shardsFlatView").getAsBoolean();
			if (root.has("recentShards") && root.get("recentShards").isJsonArray()) {
				RynConfig.recentShards.clear();
				for (var e : root.getAsJsonArray("recentShards")) RynConfig.recentShards.add(e.getAsString());
			}

			if (root.has("calculatorEnabled")) RynConfig.calculatorEnabled = root.get("calculatorEnabled").getAsBoolean();
			if (root.has("fusionTrackerEnabled")) RynConfig.fusionTrackerEnabled = root.get("fusionTrackerEnabled").getAsBoolean();
			if (root.has("huntingTrackerEnabled")) RynConfig.huntingTrackerEnabled = root.get("huntingTrackerEnabled").getAsBoolean();

			if (root.has("useInstaBuy")) RynConfig.useInstaBuy = root.get("useInstaBuy").getAsBoolean();
			if (root.has("bazaarHintEnabled")) RynConfig.bazaarHintEnabled = root.get("bazaarHintEnabled").getAsBoolean();
			if (root.has("bazaarFlipperLevel")) RynConfig.bazaarFlipperLevel = root.get("bazaarFlipperLevel").getAsInt();
			if (root.has("crocodileLevel")) RynConfig.crocodileLevel = root.get("crocodileLevel").getAsInt();
			if (root.has("huntingWisdom")) RynConfig.huntingWisdom = root.get("huntingWisdom").getAsFloat();
			if (root.has("hunterFortune")) RynConfig.hunterFortune = root.get("hunterFortune").getAsFloat();
			if (root.has("boxBoardEnabled")) RynConfig.boxBoardEnabled = root.get("boxBoardEnabled").getAsBoolean();
			if (root.has("highlightFuseInputs")) RynConfig.highlightFuseInputs = root.get("highlightFuseInputs").getAsBoolean();
			if (root.has("blockServerPack"))
				RynConfig.packMode = root.get("blockServerPack").getAsBoolean() ? RynConfig.PACK_OFF : RynConfig.PACK_HYBRID;
			if (root.has("packMode")) RynConfig.packMode = root.get("packMode").getAsInt();
			if (RynConfig.packMode != RynConfig.PACK_OFF) RynConfig.packMode = RynConfig.PACK_HYBRID;
			if (root.has("boxGuideX")) RynConfig.boxGuideX = root.get("boxGuideX").getAsInt();
			if (root.has("boxGuideY")) RynConfig.boxGuideY = root.get("boxGuideY").getAsInt();
			if (root.has("boxGuideSlot")) RynConfig.boxGuideSlot = root.get("boxGuideSlot").getAsInt();
			if (root.has("excludeChameleon")) RynConfig.excludeChameleon = root.get("excludeChameleon").getAsBoolean();
			if (root.has("excludeWoodenBait")) RynConfig.excludeWoodenBait = root.get("excludeWoodenBait").getAsBoolean();
			if (root.has("frogPet")) RynConfig.frogPet = root.get("frogPet").getAsBoolean();
			if (root.has("huntHudX")) RynConfig.huntHudX = root.get("huntHudX").getAsInt();
			if (root.has("huntHudY")) RynConfig.huntHudY = root.get("huntHudY").getAsInt();
			if (root.has("huntHudScale")) RynConfig.huntHudScale = root.get("huntHudScale").getAsFloat();
			if (root.has("safariHudX")) RynConfig.safariHudX = root.get("safariHudX").getAsInt();
			if (root.has("safariHudY")) RynConfig.safariHudY = root.get("safariHudY").getAsInt();
			if (root.has("safariHudScale")) RynConfig.safariHudScale = root.get("safariHudScale").getAsFloat();
			if (root.has("colors") && root.get("colors").isJsonObject()) {
				RynConfig.colors.clear();
				for (var en : root.getAsJsonObject("colors").entrySet())
					RynConfig.colors.put(en.getKey(), en.getValue().getAsInt());
			}
			if (root.has("flags") && root.get("flags").isJsonObject()) {
				RynConfig.flags.clear();
				for (var en : root.getAsJsonObject("flags").entrySet())
					RynConfig.flags.put(en.getKey(), en.getValue().getAsBoolean());
			}
			if (root.has("texts") && root.get("texts").isJsonObject()) {
				RynConfig.texts.clear();
				for (var en : root.getAsJsonObject("texts").entrySet())
					RynConfig.texts.put(en.getKey(), en.getValue().getAsString());
			}
			if (root.has("ints") && root.get("ints").isJsonObject()) {
				RynConfig.ints.clear();
				for (var en : root.getAsJsonObject("ints").entrySet())
					RynConfig.ints.put(en.getKey(), en.getValue().getAsInt());
			}
			if (root.has("safariLife") && root.get("safariLife").isJsonObject()) {
				var life = root.getAsJsonObject("safariLife");
				RynConfig.lifeShards.clear();
				if (life.has("shards")) for (var en : life.getAsJsonObject("shards").entrySet()) RynConfig.lifeShards.put(en.getKey(), en.getValue().getAsInt());
				RynConfig.lifeCaptures.clear();
				if (life.has("captures")) for (var en : life.getAsJsonObject("captures").entrySet()) RynConfig.lifeCaptures.put(en.getKey(), en.getValue().getAsInt());
				if (life.has("essence")) RynConfig.lifeEssence = life.get("essence").getAsLong();
				if (life.has("timeMs")) RynConfig.lifeTimeMs = life.get("timeMs").getAsLong();
				if (life.has("runs")) RynConfig.lifeRuns = life.get("runs").getAsInt();
				if (life.has("huntXp")) RynConfig.lifeHuntXp = life.get("huntXp").getAsLong();
			}
			if (root.has("mobKills") && root.get("mobKills").isJsonObject())
				for (var en : root.getAsJsonObject("mobKills").entrySet()) { try { com.ryn.skyryn.data.BestiaryDb.putKills(en.getKey(), en.getValue().getAsInt()); } catch (Exception ignored) { } }
			if (root.has("mobKillsSnap") && root.get("mobKillsSnap").isJsonObject())
				for (var en : root.getAsJsonObject("mobKillsSnap").entrySet()) { try { com.ryn.skyryn.data.BestiaryDb.putCapSnap(en.getKey(), en.getValue().getAsInt()); } catch (Exception ignored) { } }
			if (root.has("safariSellOffer")) RynConfig.safariSellOffer = root.get("safariSellOffer").getAsBoolean();
			if (root.has("safariTrack") && root.get("safariTrack").isJsonObject())
				com.ryn.skyryn.hud.SafariTracker.trackerFromJson(root.getAsJsonObject("safariTrack"));
			if (root.has("huntTrackerMode")) RynConfig.huntTrackerMode = root.get("huntTrackerMode").getAsInt();
			if (root.has("huntInstaSell")) RynConfig.huntInstaSell = root.get("huntInstaSell").getAsBoolean();
			if (root.has("huntCountLootShare")) RynConfig.huntCountLootShare = root.get("huntCountLootShare").getAsBoolean();
			if (root.has("huntCountSalt")) RynConfig.huntCountSalt = root.get("huntCountSalt").getAsBoolean();
			if (root.has("huntCountCharm")) RynConfig.huntCountCharm = root.get("huntCountCharm").getAsBoolean();
			if (root.has("huntCountNaga")) RynConfig.huntCountNaga = root.get("huntCountNaga").getAsBoolean();
			if (root.has("huntIdleSeconds")) RynConfig.huntIdleSeconds = root.get("huntIdleSeconds").getAsInt();
			if (root.has("huntShowSources")) RynConfig.huntShowSources = root.get("huntShowSources").getAsBoolean();
			if (root.has("huntPauseAnnounce")) RynConfig.huntPauseAnnounce = root.get("huntPauseAnnounce").getAsBoolean();
			if (root.has("ironman")) RynConfig.ironman = root.get("ironman").getAsBoolean();
			if (root.has("mvpPlus")) RynConfig.mvpPlus = root.get("mvpPlus").getAsBoolean();
			if (root.has("preferNucleus")) RynConfig.preferNucleus = root.get("preferNucleus").getAsBoolean();
			if (root.has("routeBeam")) RynConfig.routeBeam = root.get("routeBeam").getAsBoolean();
			if (root.has("bestiaryHints")) RynConfig.bestiaryHints = root.get("bestiaryHints").getAsBoolean();
			if (root.has("seaGuideHints")) RynConfig.seaGuideHints = root.get("seaGuideHints").getAsBoolean();
			if (root.has("mobHighlightEnabled")) RynConfig.mobHighlightEnabled = root.get("mobHighlightEnabled").getAsBoolean();
			if (root.has("timberAlert")) RynConfig.timberAlert = root.get("timberAlert").getAsBoolean();
			if (root.has("petalfallAlert")) RynConfig.petalfallAlert = root.get("petalfallAlert").getAsBoolean();
			if (root.has("customMobs") && root.get("customMobs").isJsonArray()) {
				RynConfig.customMobs.clear();
				for (var e : root.getAsJsonArray("customMobs")) {
					if (!e.isJsonObject()) continue;
					JsonObject o = e.getAsJsonObject();
					RynConfig.customMobs.add(new RynConfig.CustomMob(
							str(o, "key"), str(o, "label"), str(o, "entityType"), str(o, "namePart"),
							o.has("color") ? o.get("color").getAsInt() : 0xFFFFD24A));
				}
			}
			if (root.has("floorDropHighlight")) RynConfig.floorDropHighlight = root.get("floorDropHighlight").getAsBoolean();
			if (root.has("woodpeckerAlert")) RynConfig.woodpeckerAlert = root.get("woodpeckerAlert").getAsBoolean();
			if (root.has("critterTimer")) RynConfig.critterTimer = root.get("critterTimer").getAsBoolean();
			if (root.has("safariTracker")) RynConfig.safariTracker = root.get("safariTracker").getAsBoolean();
			if (root.has("safariParty")) RynConfig.safariParty = root.get("safariParty").getAsBoolean();
			if (root.has("questHighlight")) RynConfig.questHighlight = root.get("questHighlight").getAsBoolean();
			if (root.has("hotspotAnnounce")) RynConfig.hotspotAnnounce = root.get("hotspotAnnounce").getAsBoolean();
			if (root.has("safariTestChat") && root.get("safariTestChat").getAsBoolean()) RynConfig.safariSolo = true;
			if (root.has("safariSolo")) RynConfig.safariSolo = root.get("safariSolo").getAsBoolean();
			if (root.has("safariTimings")) RynConfig.safariTimings = root.get("safariTimings").getAsBoolean();
			if (root.has("doomPbMs")) RynConfig.doomPbMs = root.get("doomPbMs").getAsLong();
			if (root.has("wumpaPbMs")) RynConfig.wumpaPbMs = root.get("wumpaPbMs").getAsLong();
			if (root.has("gateAnnounce")) RynConfig.gateAnnounce = root.get("gateAnnounce").getAsBoolean();
			if (root.has("lmWumpa")) RynConfig.lmWumpa = root.get("lmWumpa").getAsBoolean();
			if (root.has("lmDoom")) RynConfig.lmDoom = root.get("lmDoom").getAsBoolean();
			if (root.has("lmGate")) RynConfig.lmGate = root.get("lmGate").getAsBoolean();
			if (root.has("lmBirdfeeder")) RynConfig.lmBirdfeeder = root.get("lmBirdfeeder").getAsBoolean();
			if (root.has("biomeEnterMsg")) RynConfig.biomeEnterMsg = root.get("biomeEnterMsg").getAsBoolean();
			if (root.has("dupeWarn")) RynConfig.dupeWarn = root.get("dupeWarn").getAsBoolean();
			if (root.has("dupeWarnForest")) RynConfig.dupeWarnForest = root.get("dupeWarnForest").getAsBoolean();
			if (root.has("highlightMobs") && root.get("highlightMobs").isJsonArray()) {
				RynConfig.highlightMobs.clear();
				for (var e : root.getAsJsonArray("highlightMobs")) RynConfig.highlightMobs.add(e.getAsString());
			}
			if (root.has("scrolls") && root.get("scrolls").isJsonArray()) {
				RynConfig.scrolls.clear();
				for (var e : root.getAsJsonArray("scrolls")) RynConfig.scrolls.add(e.getAsString());
			}

			if (root.has("attrLevels") && root.get("attrLevels").isJsonObject()) {
				JsonObject levels = root.getAsJsonObject("attrLevels");
				for (String k : levels.keySet()) {
					try { ShardProgress.setLevel(k, levels.get(k).getAsInt()); }
					catch (Exception ignored) { }
				}
			}

			if (root.has("bestiary") && root.get("bestiary").isJsonObject()) {
				BestiaryDb.fromJson(root.getAsJsonObject("bestiary"));
			}
			if (root.has("seaguide") && root.get("seaguide").isJsonObject()) {
				SeaGuideDb.fromJson(root.getAsJsonObject("seaguide"));
			}

			RynConfig.clearBatches();
			if (root.has("batches") && root.get("batches").isJsonObject()) {
				JsonObject b = root.getAsJsonObject("batches");
				for (String k : b.keySet()) {
					try { RynConfig.setBatch(k, b.get(k).getAsInt()); } catch (Exception ignored) { }
				}
			}

			if (root.has("panelX")) RynConfig.panelX = root.get("panelX").getAsInt();
			if (root.has("panelY")) RynConfig.panelY = root.get("panelY").getAsInt();
			if (root.has("panelScale")) RynConfig.panelScale = root.get("panelScale").getAsFloat();
			if (root.has("trackerMode")) RynConfig.trackerMode = root.get("trackerMode").getAsInt();

			if (root.has("tracker")) {
				JsonObject t = root.getAsJsonObject("tracker");
				FusionTracker.totalFusions = t.has("totalFusions") ? t.get("totalFusions").getAsLong() : 0;
				FusionTracker.totalShardsObtained = t.has("totalShards") ? t.get("totalShards").getAsLong() : 0;
				FusionTracker.totalFusionXp = t.has("totalFusionXp") ? t.get("totalFusionXp").getAsDouble() : 0;
				FusionTracker.totalSpent = t.has("totalSpent") ? t.get("totalSpent").getAsDouble() : 0;
				FusionTracker.totalEarned = t.has("totalEarned") ? t.get("totalEarned").getAsDouble() : 0;
			}

			if (root.has("hunt")) {
				JsonObject h = root.getAsJsonObject("hunt");
				HuntingTracker.totalShards = h.has("totalShards") ? h.get("totalShards").getAsLong() : 0;
				HuntingTracker.totalXp = h.has("totalXp") ? h.get("totalXp").getAsDouble() : 0;
				HuntingTracker.totalCaught.clear();
				if (h.has("caught") && h.get("caught").isJsonObject()) {
					JsonObject c = h.getAsJsonObject("caught");
					for (String k : c.keySet()) {
						try { HuntingTracker.totalCaught.put(k, c.get(k).getAsInt()); } catch (Exception ignored) { }
					}
				}
			}
			com.ryn.skyryn.config.SkyLog.d("Config loaded");
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("Config load error: " + e.getMessage());
		}
	}

	public static void save() {
		try {
			JsonObject root = new JsonObject();
			root.addProperty("lang", RynConfig.lang);
			root.addProperty("shardsFlatView", RynConfig.shardsFlatView);
			JsonArray recent = new JsonArray();
			for (String s : RynConfig.recentShards) recent.add(s);
			root.add("recentShards", recent);
			root.addProperty("calculatorEnabled", RynConfig.calculatorEnabled);
			root.addProperty("fusionTrackerEnabled", RynConfig.fusionTrackerEnabled);
			root.addProperty("huntingTrackerEnabled", RynConfig.huntingTrackerEnabled);
			root.addProperty("useInstaBuy", RynConfig.useInstaBuy);
			root.addProperty("bazaarHintEnabled", RynConfig.bazaarHintEnabled);
			root.addProperty("bazaarFlipperLevel", RynConfig.bazaarFlipperLevel);
			root.addProperty("crocodileLevel", RynConfig.crocodileLevel);
			root.addProperty("huntingWisdom", RynConfig.huntingWisdom);
			root.addProperty("hunterFortune", RynConfig.hunterFortune);
			root.addProperty("boxBoardEnabled", RynConfig.boxBoardEnabled);
			root.addProperty("highlightFuseInputs", RynConfig.highlightFuseInputs);
			root.addProperty("packMode", RynConfig.packMode);
			root.addProperty("boxGuideX", RynConfig.boxGuideX);
			root.addProperty("boxGuideY", RynConfig.boxGuideY);
			root.addProperty("boxGuideSlot", RynConfig.boxGuideSlot);
			root.addProperty("excludeChameleon", RynConfig.excludeChameleon);
			root.addProperty("excludeWoodenBait", RynConfig.excludeWoodenBait);
			root.addProperty("frogPet", RynConfig.frogPet);
			root.addProperty("huntHudX", RynConfig.huntHudX);
			root.addProperty("huntHudY", RynConfig.huntHudY);
			root.addProperty("huntHudScale", RynConfig.huntHudScale);
			root.addProperty("safariHudX", RynConfig.safariHudX);
			root.addProperty("safariHudY", RynConfig.safariHudY);
			root.addProperty("safariHudScale", RynConfig.safariHudScale);
			com.google.gson.JsonObject colObj = new com.google.gson.JsonObject();
			for (var en : RynConfig.colors.entrySet()) colObj.addProperty(en.getKey(), en.getValue());
			root.add("colors", colObj);
			com.google.gson.JsonObject flagObj = new com.google.gson.JsonObject();
			for (var en : RynConfig.flags.entrySet()) flagObj.addProperty(en.getKey(), en.getValue());
			root.add("flags", flagObj);
			com.google.gson.JsonObject intObj = new com.google.gson.JsonObject();
			for (var en : RynConfig.ints.entrySet()) intObj.addProperty(en.getKey(), en.getValue());
			root.add("ints", intObj);
			com.google.gson.JsonObject txtObj = new com.google.gson.JsonObject();
			for (var en : RynConfig.texts.entrySet()) txtObj.addProperty(en.getKey(), en.getValue());
			root.add("texts", txtObj);
			com.google.gson.JsonObject life = new com.google.gson.JsonObject();
			com.google.gson.JsonObject lsh = new com.google.gson.JsonObject();
			for (var en : RynConfig.lifeShards.entrySet()) lsh.addProperty(en.getKey(), en.getValue());
			life.add("shards", lsh);
			com.google.gson.JsonObject lcap = new com.google.gson.JsonObject();
			for (var en : RynConfig.lifeCaptures.entrySet()) lcap.addProperty(en.getKey(), en.getValue());
			life.add("captures", lcap);
			life.addProperty("essence", RynConfig.lifeEssence);
			life.addProperty("timeMs", RynConfig.lifeTimeMs);
			life.addProperty("runs", RynConfig.lifeRuns);
			life.addProperty("huntXp", RynConfig.lifeHuntXp);
			root.add("safariLife", life);
			JsonObject mobKills = new JsonObject();
			for (var en : com.ryn.skyryn.data.BestiaryDb.allKills().entrySet()) mobKills.addProperty(en.getKey(), en.getValue());
			root.add("mobKills", mobKills);
			JsonObject mobKillsSnap = new JsonObject();
			for (var en : com.ryn.skyryn.data.BestiaryDb.allCapSnaps().entrySet()) mobKillsSnap.addProperty(en.getKey(), en.getValue());
			root.add("mobKillsSnap", mobKillsSnap);
			root.addProperty("safariSellOffer", RynConfig.safariSellOffer);
			root.add("safariTrack", com.ryn.skyryn.hud.SafariTracker.trackerToJson());
			root.addProperty("huntTrackerMode", RynConfig.huntTrackerMode);
			root.addProperty("huntInstaSell", RynConfig.huntInstaSell);
			root.addProperty("huntCountLootShare", RynConfig.huntCountLootShare);
			root.addProperty("huntCountSalt", RynConfig.huntCountSalt);
			root.addProperty("huntCountCharm", RynConfig.huntCountCharm);
			root.addProperty("huntCountNaga", RynConfig.huntCountNaga);
			root.addProperty("huntIdleSeconds", RynConfig.huntIdleSeconds);
			root.addProperty("huntShowSources", RynConfig.huntShowSources);
			root.addProperty("huntPauseAnnounce", RynConfig.huntPauseAnnounce);
			root.addProperty("ironman", RynConfig.ironman);
			root.addProperty("mvpPlus", RynConfig.mvpPlus);
			root.addProperty("preferNucleus", RynConfig.preferNucleus);
			root.addProperty("routeBeam", RynConfig.routeBeam);
			root.addProperty("bestiaryHints", RynConfig.bestiaryHints);
			root.addProperty("seaGuideHints", RynConfig.seaGuideHints);
			JsonArray scr = new JsonArray();
			for (String s : RynConfig.scrolls) scr.add(s);
			root.add("scrolls", scr);

			root.addProperty("mobHighlightEnabled", RynConfig.mobHighlightEnabled);
			root.addProperty("floorDropHighlight", RynConfig.floorDropHighlight);
			root.addProperty("woodpeckerAlert", RynConfig.woodpeckerAlert);
			root.addProperty("timberAlert", RynConfig.timberAlert);
			root.addProperty("petalfallAlert", RynConfig.petalfallAlert);
			JsonArray cms = new JsonArray();
			for (RynConfig.CustomMob c : RynConfig.customMobs) {
				JsonObject o = new JsonObject();
				o.addProperty("key", c.key());
				o.addProperty("label", c.label());
				o.addProperty("entityType", c.entityType());
				o.addProperty("namePart", c.namePart());
				o.addProperty("color", c.color());
				cms.add(o);
			}
			root.add("customMobs", cms);
			root.addProperty("critterTimer", RynConfig.critterTimer);
			root.addProperty("safariTracker", RynConfig.safariTracker);
			root.addProperty("safariParty", RynConfig.safariParty);
			root.addProperty("questHighlight", RynConfig.questHighlight);
			root.addProperty("hotspotAnnounce", RynConfig.hotspotAnnounce);
			root.addProperty("safariSolo", RynConfig.safariSolo);
			root.addProperty("safariTimings", RynConfig.safariTimings);
			root.addProperty("doomPbMs", RynConfig.doomPbMs);
			root.addProperty("wumpaPbMs", RynConfig.wumpaPbMs);
			root.addProperty("gateAnnounce", RynConfig.gateAnnounce);
			root.addProperty("lmWumpa", RynConfig.lmWumpa);
			root.addProperty("lmDoom", RynConfig.lmDoom);
			root.addProperty("lmGate", RynConfig.lmGate);
			root.addProperty("lmBirdfeeder", RynConfig.lmBirdfeeder);
			root.addProperty("biomeEnterMsg", RynConfig.biomeEnterMsg);
			root.addProperty("dupeWarn", RynConfig.dupeWarn);
			root.addProperty("dupeWarnForest", RynConfig.dupeWarnForest);
			JsonArray hl = new JsonArray();
			for (String s : RynConfig.highlightMobs) hl.add(s);
			root.add("highlightMobs", hl);

			JsonObject levels = new JsonObject();
			for (java.util.Map.Entry<String, Integer> e : ShardProgress.allLevels().entrySet()) {
				levels.addProperty(e.getKey(), e.getValue());
			}
			root.add("attrLevels", levels);

			JsonObject bestiary = new JsonObject();
			for (java.util.Map.Entry<String, BestiaryDb.Plaque> e : BestiaryDb.all().entrySet()) {
				JsonObject o = new JsonObject();
				JsonArray arr = new JsonArray();
				for (String line : e.getValue().lines()) arr.add(line);
				o.add("lines", arr);
				o.addProperty("skin", e.getValue().skin());
				bestiary.add(e.getKey(), o);
			}
			root.add("bestiary", bestiary);

			JsonObject seaguide = new JsonObject();
			for (java.util.Map.Entry<String, SeaGuideDb.Plaque> e : SeaGuideDb.all().entrySet()) {
				JsonObject o = new JsonObject();
				JsonArray arr = new JsonArray();
				for (String line : e.getValue().lines()) arr.add(line);
				o.add("lines", arr);
				o.addProperty("skin", e.getValue().skin());
				seaguide.add(e.getKey(), o);
			}
			root.add("seaguide", seaguide);

			JsonObject batches = new JsonObject();
			for (java.util.Map.Entry<String, Integer> e : RynConfig.allBatches().entrySet()) {
				batches.addProperty(e.getKey(), e.getValue());
			}
			root.add("batches", batches);
			root.addProperty("panelX", RynConfig.panelX);
			root.addProperty("panelY", RynConfig.panelY);
			root.addProperty("panelScale", RynConfig.panelScale);
			root.addProperty("trackerMode", RynConfig.trackerMode);

			JsonObject t = new JsonObject();
			t.addProperty("totalFusions", FusionTracker.totalFusions);
			t.addProperty("totalShards", FusionTracker.totalShardsObtained);
			t.addProperty("totalFusionXp", FusionTracker.totalFusionXp);
			t.addProperty("totalSpent", FusionTracker.totalSpent);
			t.addProperty("totalEarned", FusionTracker.totalEarned);
			root.add("tracker", t);

			JsonObject h = new JsonObject();
			h.addProperty("totalShards", HuntingTracker.totalShards);
			h.addProperty("totalXp", HuntingTracker.totalXp);
			JsonObject caught = new JsonObject();
			for (java.util.Map.Entry<String, Integer> e : HuntingTracker.totalCaught.entrySet()) {
				caught.addProperty(e.getKey(), e.getValue());
			}
			h.add("caught", caught);
			root.add("hunt", h);

			Path path = configPath();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root));
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("Config save error: " + e.getMessage());
		}
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}
}
