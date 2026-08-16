package com.ryn.skyryn.waypoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

import com.ryn.skyryn.config.RynConfig;

public class MobHighlight {
	public record MobDef(String key, String label, int color, String zone, Predicate<Entity> typeMatch, String group) { }

	public static final List<String> GROUPS = List.of("Cavern", "Forest", "Haunted", "Icy");
	public static final String TORRHUS = "Torrhus Canyon", OTHER = "Other",
			GALATEA = "Galatea", SAFARI_NPC = "Safari NPC";

	public static final List<MobDef> MOBS = build();

	private static List<MobDef> build() {
		List<MobDef> m = new java.util.ArrayList<>();
		m.add(new MobDef("hideonsun", "§eHideonsun§r", 0xFFFFD24A, "torrhus",
				e -> e instanceof net.minecraft.world.entity.monster.Shulker, TORRHUS));
		for (String k : new String[]{ "beeheemoth", "blue jay", "bunbun", "drybark", "dustybit", "ember",
				"firefox", "goldolot", "grizzly", "groundhog", "hivethief", "honeybuzz", "mountain goat",
				"pangolin", "parched", "pollendart", "puck", "sepialot", "solar", "timil", "water snake" })
			m.add(crit(k, 0xFFE0C060, TORRHUS));
		m.add(new MobDef("hunter", "§eHunter NPC§r", 0xFFFFD24A, "",
				e -> e instanceof net.minecraft.world.entity.player.Player
						&& e.getName().getString().toLowerCase().startsWith("hunter "), SAFARI_NPC));
		m.add(new MobDef("cinderbat", "§6Cinderbat§r", 0xFFFF7A33, "crimson",
				e -> e instanceof net.minecraft.world.entity.ambient.Bat, OTHER));
		m.add(new MobDef("hideonleaf", "§aHideonleaf§r", 0xFF5FD68A, "galatea",
				e -> e instanceof net.minecraft.world.entity.monster.Shulker, GALATEA));
		m.add(crit("murkbat", 0xFF3FD0C0, GALATEA));
		m.add(crit("honeyhog", 0xFFE0C060, GALATEA));
		m.add(crit("stag beetle", 0xFFB08040, GALATEA));
		m.add(crit("honeymite", 0xFFE0C060, GALATEA));
		m.add(crit("woodlouse", 0xFFB08040, GALATEA));
		m.add(crit("hewver", 0xFF7FD060, GALATEA));
		m.add(new MobDef("invisibug", "§bInvisibug§r", 0xFF9BE0FF, "galatea",
				MobHighlight::isInvisibugMarker, GALATEA));
		for (String k : new String[]{ "cavernfish", "flitter", "driftling", "chuckwalla",
				"snoozle", "gemzie", "scrappy", "rockmite" })
			m.add(crit(k, 0xFF55E0FF, "Cavern"));
		m.add(new MobDef("shyworm", "§fShyworm§r", 0xFF55E0FF, "",
				e -> nearNamed(e, "shyworm") || isSlimeStack(e), "Cavern"));
		m.add(new MobDef("hideonfloor", "§bHideonfloor§r", 0xFF4AC0E0, "critter safari",
				e -> e instanceof net.minecraft.world.entity.monster.Shulker && !isPurpleShulker(e), "Forest"));
		for (String k : new String[]{ "foxtrot", "bluebird", "honeybug", "treefrog", "woodchucker", "fluffling", "parakeet", "macaw" })
			m.add(crit(k, 0xFF50E070, "Forest"));
		m.add(new MobDef("bloodbat", "§cBloodbat§r", 0xFFFF4A4A, "critter safari",
				e -> e instanceof net.minecraft.world.entity.ambient.Bat && !nearNamed(e, "flitter")
						&& inBiome(e, "haunted"), "Haunted"));
		m.add(new MobDef("duplico", "§5Duplico§r", 0xFFB060FF, "",
				e -> (typeIs(e, "interaction") && isDuplicoBox(e)) || nearNamed(e, "duplico"), "Haunted"));
		for (String k : new String[]{ "areita", "gazer", "litterbug", "hideyho", "solsnatcher",
				"gimmiegold", "hideonwall" })
			m.add(crit(k, 0xFFB060FF, "Haunted"));
		for (String k : new String[]{ "strongarm", "tepid", "polaris", "shuddersquid", "billygoat",
				"mantis shrimp", "nozzlenose", "troodon" })
			m.add(crit(k, 0xFF9BE0FF, "Icy"));
		return List.copyOf(m);
	}

	private static MobDef crit(String key, int argb, String group) {
		return new MobDef(key, "§f" + capWords(key), argb, "", e -> typeIs(e, key) || nearNamed(e, key), group);
	}

	private static boolean typeIs(Entity e, String key) {
		return EntityType.getKey(e.getType()).toString().endsWith(":" + key.replace(' ', '_'));
	}
	private static boolean isSlimeStack(Entity e) {
		if (!(e instanceof net.minecraft.world.entity.monster.Slime) || !e.isInvisible()) return false;
		if (Math.abs(e.getBoundingBox().getXsize() - 1.04) > 0.1) return false;
		int near = 0;
		for (Entity o : e.level().getEntities(e, e.getBoundingBox().inflate(2.5, 2.0, 2.5)))
			if (o instanceof net.minecraft.world.entity.monster.Slime && o.isInvisible() && ++near >= 2) return true;
		return false;
	}

	private static boolean isInvisibugMarker(Entity e) {
		if (!(e instanceof net.minecraft.world.entity.decoration.ArmorStand st)) return false;
		if (!st.isInvisible() || st.getCustomName() != null) return false;
		AABB bb = st.getBoundingBox();
		if (bb.getXsize() > 0.02 || bb.getYsize() > 0.02) return false;
		for (net.minecraft.world.entity.EquipmentSlot s : net.minecraft.world.entity.EquipmentSlot.values())
			if (!st.getItemBySlot(s).isEmpty()) return false;
		for (Entity o : e.level().getEntities(e, bb.inflate(3.0, 3.0, 3.0)))
			if (isModelStand(o)) return false;
		return true;
	}

	private static boolean isDuplicoBox(Entity e) {
		AABB bb = e.getBoundingBox();
		return Math.abs(bb.getXsize() - 1.10) < 0.06 && Math.abs(bb.getYsize() - 1.10) < 0.06;
	}

	private static String capWords(String s) {
		String[] w = s.split(" ");
		StringBuilder sb = new StringBuilder();
		for (String x : w) { if (sb.length() > 0) sb.append(' '); sb.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1)); }
		return sb.toString();
	}

	static boolean isModelStand(Entity e) {
		if (!(e instanceof net.minecraft.world.entity.decoration.ArmorStand st)) return false;
		if (st.getCustomName() != null) return false;
		for (net.minecraft.world.entity.EquipmentSlot s : net.minecraft.world.entity.EquipmentSlot.values())
			if (!st.getItemBySlot(s).isEmpty()) return true;
		return false;
	}

	private static boolean inBiome(Entity e, String biome) {
		if (!SafariBiomes.any()) return true;
		String b = SafariBiomes.biomeAt(e.getX(), e.getZ());
		return b.isEmpty() || b.equalsIgnoreCase(biome);
	}

	private static boolean isPurpleShulker(Entity e) {
		if (!(e instanceof net.minecraft.world.entity.monster.Shulker sh)) return false;
		net.minecraft.world.item.DyeColor col = sh.getColor();
		return col == null || col == net.minecraft.world.item.DyeColor.PURPLE;
	}

	private static boolean nameContains(Entity e, String sub) {
		var n = e.getCustomName();
		return n != null && n.getString().toLowerCase().contains(sub);
	}

	private static boolean nearNamed(Entity e, String sub) {
		if (nameContains(e, sub)) return true;
		for (Entity pass : e.getPassengers()) if (nameContains(pass, sub)) return true;
		AABB area = e.getBoundingBox().inflate(2.5, 4.0, 2.5);
		for (Entity o : e.level().getEntities(e, area)) if (nameContains(o, sub)) return true;
		return false;
	}

	public static MobDef def(String key) {
		if (key == null) return null;
		for (MobDef d : MOBS) if (d.key().equalsIgnoreCase(key)) return d;
		return null;
	}

	private static final float MAX_DIST = 80f;

	public static boolean enabled() {
		return RynConfig.mobHighlightEnabled && (!RynConfig.highlightMobs.isEmpty() || !RynConfig.customMobs.isEmpty());
	}

	public static final MobDef SPARKLING = new MobDef("sparkling", "§6Sparkling§r", 0xFFFFA020, "",
			e -> nearNamed(e, "sparkling"), OTHER);

	private static MobDef match(Entity ent, String island, String area) {
		boolean player = ent instanceof net.minecraft.world.entity.player.Player;
		if (player) return playerMatch(ent);
		if (RynConfig.flag("sparkling.hl", true) && SPARKLING.typeMatch().test(ent)) return SPARKLING;
		for (MobDef d : MOBS) {
			if (!RynConfig.hasHighlightMob(d.key())) continue;
			if (d.key().equals("hunter")) continue;
			String z = d.zone();
			if (!z.isEmpty() && !z.equals(island) && !area.contains(z)) continue;
			if (d.typeMatch().test(ent)) return d;
		}
		return matchCustom(ent);
	}

	private static MobDef playerMatch(Entity ent) {
		MobDef h = def("hunter");
		if (h == null || !RynConfig.hasHighlightMob("hunter")) return null;
		return h.typeMatch().test(ent) ? h : null;
	}

	private static MobDef matchCustom(Entity ent) {
		String type = EntityType.getKey(ent.getType()).toString();
		for (RynConfig.CustomMob c : RynConfig.customMobs) {
			if (!RynConfig.hasHighlightMob(c.key())) continue;
			if (!c.entityType().isBlank() && !type.equals(c.entityType())) continue;
			if (!c.namePart().isBlank() && !nearNamed(ent, c.namePart().toLowerCase())) continue;
			if (c.entityType().isBlank() && c.namePart().isBlank()) continue;
			return new MobDef(c.key(), c.label(), c.color(), "", e -> true, "Custom");
		}
		return null;
	}

	private record Cached(long at, MobDef def) { }
	private static final java.util.Map<Integer, Cached> CACHE = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Cached> OUTLINE = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Cached> STICKY = new java.util.HashMap<>();
	private static final long STICKY_MS = 300_000;
	private static long lastSweep = 0;

	private static MobDef mobOf(Entity ent) {
		if (ent == null) return null;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ent == mc.player) return null;
		if (ent instanceof net.minecraft.world.entity.Display) return null;
		if (ent instanceof net.minecraft.world.entity.decoration.ArmorStand && !isInvisibugMarker(ent)) return null;

		long now = System.currentTimeMillis();
		Cached c = CACHE.get(ent.getId());
		if (c != null && now - c.at() < 500) return c.def();
		MobDef d = match(ent, SkyBlockCheck.currentIsland(), SkyBlockCheck.currentArea().toLowerCase());
		if (d != null) STICKY.put(ent.getId(), new Cached(now, d));
		else {
			Cached s = STICKY.get(ent.getId());
			if (s != null && now - s.at() < STICKY_MS) d = s.def();
		}
		CACHE.put(ent.getId(), new Cached(now, d));
		return d;
	}

	public static MobDef outlineDef(Entity ent) {
		if (ent == null || !enabled()) return null;
		long now = System.currentTimeMillis();
		if (now - lastSweep > 5000) {
			lastSweep = now;
			CACHE.entrySet().removeIf(e -> now - e.getValue().at() > 5000);
			OUTLINE.entrySet().removeIf(e -> now - e.getValue().at() > 5000);
			markerBody.entrySet().removeIf(e -> now - e.getValue() > 5000);
			STICKY.entrySet().removeIf(e -> now - e.getValue().at() > STICKY_MS);
		}
		Cached c = OUTLINE.get(ent.getId());
		if (c != null && now - c.at() < 500) return c.def();
		MobDef d = resolve(ent);
		OUTLINE.put(ent.getId(), new Cached(now, d));
		return d;
	}

	private static MobDef resolve(Entity ent) {
		MobDef d = mobOf(ent);
		if (d != null) {
			if (!ent.isInvisible()) return d;
			if (hasMarkerCarrier(ent)) { markerBody.put(ent.getId(), System.currentTimeMillis()); return d; }
			return chosenCarrier(ent) == null ? d : null;
		}
		if (!isCarrier(ent) || isMarkerStand(ent)) return null;
		Entity host = ent.getVehicle();
		if (needsCarrier(host)) { d = mobOf(host); if (d != null) return d; }
		for (Entity o : ent.level().getEntities(ent, ent.getBoundingBox().inflate(1.5, 2.0, 1.5))) {
			if (!needsCarrier(o)) continue;
			d = mobOf(o);
			if (d == null) continue;
			if (hasMarkerCarrier(o)) return null;
			return chosenCarrier(o) == ent ? d : null;
		}
		return null;
	}

	private static boolean needsCarrier(Entity host) {
		return host != null && host.isInvisible();
	}

	private static boolean isCarrier(Entity e) {
		return e instanceof net.minecraft.world.entity.Display || isModelStand(e);
	}

	private static boolean isMarkerStand(Entity e) {
		return isModelStand(e) && e.getBoundingBox().getYsize() < 0.05;
	}

	private static final java.util.Map<Integer, Long> markerBody = new java.util.HashMap<>();

	private static boolean hasMarkerCarrier(Entity host) {
		for (Entity o : host.level().getEntities(host, host.getBoundingBox().inflate(1.5, 2.0, 1.5)))
			if (isMarkerStand(o)) return true;
		return false;
	}

	private static Entity chosenCarrier(Entity host) {
		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity o : host.level().getEntities(host, host.getBoundingBox().inflate(1.5, 2.0, 1.5))) {
			if (!isCarrier(o)) continue;
			double score = o.position().distanceToSqr(host.position())
					+ (o instanceof net.minecraft.world.entity.Display ? 0 : 1000);
			if (score < bestScore) { bestScore = score; best = o; }
		}
		return best;
	}

	private static boolean boxed(Entity ent, MobDef d) {
		if (isModelStand(ent)) return RynConfig.getInt("hl.stand", 0) == 0;
		if (d == null) return false;
		if (d.key().equals("duplico") || d.key().equals("invisibug")) return true;
		Long at = markerBody.get(ent.getId());
		return at != null && System.currentTimeMillis() - at < 2000;
	}

	public static boolean glowing(Entity ent) {
		MobDef d = outlineDef(ent);
		if (d == null || boxed(ent, d)) return false;
		return inSight(ent);
	}

	public static int outlineColor(Entity ent) {
		MobDef d = outlineDef(ent);
		if (d == null || !inSight(ent)) return 0;
		return RynConfig.color("mob." + d.key(), d.color());
	}

	private record Sight(long at, boolean ok) { }
	private static final java.util.Map<Integer, Sight> SIGHT = new java.util.HashMap<>();

	public static boolean inSight(Entity ent) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return false;
		long now = System.currentTimeMillis();
		Sight s = SIGHT.get(ent.getId());
		if (s != null && now - s.at() < 200) return s.ok();
		Vec3 eye = mc.gameRenderer.getMainCamera().position();
		boolean ok = ent.position().distanceToSqr(eye) <= MAX_DIST * MAX_DIST && visible(mc.level, eye, ent);
		if (SIGHT.size() > 512) SIGHT.clear();
		SIGHT.put(ent.getId(), new Sight(now, ok));
		return ok;
	}

	public static void renderWorld(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		if (enabled()) {
			VertexConsumer mv = buf.getBuffer(RenderTypes.debugQuads());
			PoseStack.Pose mp = ps.last();
			boolean any = false;
			for (Entity ent : mc.level.entitiesForRendering()) {
				MobDef d = outlineDef(ent);
				if (d == null || !boxed(ent, d)) continue;
				if (ent.position().distanceToSqr(cam) > MAX_DIST * MAX_DIST || !inSight(ent)) continue;
				AABB bb = ent.getBoundingBox();
				double cx = bb.getCenter().x, cz = bb.getCenter().z;
				double top = bb.maxY + 0.1;
				double bot = isModelStand(ent) ? Math.max(bb.minY, top - 1.3) : bb.minY - 0.1;
				if (bb.getYsize() < 0.05) { bot = bb.minY - 0.4; top = bb.minY + 0.6; }
				int col = RynConfig.color("mob." + d.key(), d.color());
				Waypoints.filledBox(mv, mp,
						(float) (cx - 0.55 - cam.x), (float) (bot - cam.y), (float) (cz - 0.55 - cam.z),
						(float) (cx + 0.55 - cam.x), (float) (top - cam.y), (float) (cz + 0.55 - cam.z),
						(col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, 110);
				any = true;
			}
			if (any) buf.endBatch();
		}

		if (!RynConfig.floorDropHighlight) return;

		VertexConsumer vc = buf.getBuffer(RenderTypes.debugQuads());
		PoseStack.Pose e = ps.last();

		java.util.Map<Long, Integer> perBlock = new java.util.HashMap<>();
		for (Entity ent : mc.level.entitiesForRendering()) {
			if (!(ent instanceof net.minecraft.world.entity.Display.ItemDisplay)) continue;
			if (!isFloorDrop(mc.level, ent)) continue;
			Vec3 c = ent.position();
			if (c.distanceTo(cam) > MAX_DIST) continue;
			perBlock.merge(net.minecraft.core.BlockPos.asLong(
					(int) Math.floor(c.x), (int) Math.floor(c.y - 0.1), (int) Math.floor(c.z)), 1, Integer::sum);
		}
		for (var en : perBlock.entrySet()) {
			if (en.getValue() < 2) continue;
			net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.of(en.getKey());
			double bx = bp.getX(), by = bp.getY(), bz = bp.getZ();
			int fc = RynConfig.color("floordrop", 0xFFFFEE55);
			double g = 0.02;
			Waypoints.filledBox(vc, e,
					(float) (bx - g - cam.x), (float) (by - g - cam.y), (float) (bz - g - cam.z),
					(float) (bx + 1 + g - cam.x), (float) (by + 1 + g - cam.y), (float) (bz + 1 + g - cam.z),
					(fc >> 16) & 0xFF, (fc >> 8) & 0xFF, fc & 0xFF, 110);
		}
		buf.endBatch();
	}

	private static boolean isFloorDrop(Level level, Entity ent) {
		if (ent.getVehicle() != null) return false;
		Vec3 c = ent.position();
		net.minecraft.core.BlockPos under = net.minecraft.core.BlockPos.containing(c.x, c.y - 0.1, c.z);
		if (level.getBlockState(under).isAir()) return false;
		AABB near = ent.getBoundingBox().inflate(1.0, 1.5, 1.0);
		for (Entity o : level.getEntities(ent, near))
			if (o instanceof net.minecraft.world.entity.LivingEntity
					&& !(o instanceof net.minecraft.world.entity.decoration.ArmorStand)
					&& !(o instanceof net.minecraft.world.entity.player.Player)) return false;
		return true;
	}

	private static boolean visible(Level level, Vec3 eye, Entity ent) {
		AABB bb = ent.getBoundingBox();
		Vec3 c = bb.getCenter();
		double ix = Math.min(bb.getXsize(), 1.0) * 0.35, iz = Math.min(bb.getZsize(), 1.0) * 0.35;
		Vec3[] pts = {
				c,
				new Vec3(c.x, bb.maxY - 0.05, c.z), new Vec3(c.x, bb.minY + 0.05, c.z),
				new Vec3(c.x - ix, c.y, c.z - iz), new Vec3(c.x + ix, c.y, c.z + iz),
				new Vec3(c.x - ix, c.y, c.z + iz), new Vec3(c.x + ix, c.y, c.z - iz) };
		Vec3 toEye = eye.subtract(c);
		Vec3 nudge = toEye.lengthSqr() > 1.0E-4 ? toEye.normalize().scale(0.15) : Vec3.ZERO;
		for (Vec3 p : pts) if (clear(level, eye, p.add(nudge), ent)) return true;
		return false;
	}

	private static boolean clear(Level level, Vec3 eye, Vec3 target, Entity ent) {
		if (paintingBlocks(level, eye, target, ent)) return false;
		BlockHitResult hit = level.clip(new ClipContext(eye, target,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ent));
		if (hit.getType() == HitResult.Type.MISS) return true;
		return hit.getLocation().distanceToSqr(eye) >= target.distanceToSqr(eye) - 0.25;
	}

	private static final java.util.List<AABB> PAINTINGS = new java.util.ArrayList<>();
	private static long lastPaintScan = 0;

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		long now = System.currentTimeMillis();
		if (now - lastPaintScan < 500) return;
		lastPaintScan = now;
		PAINTINGS.clear();
		if (!RynConfig.mobHighlightEnabled) return;
		AABB near = mc.player.getBoundingBox().inflate(64);
		for (Entity e : mc.level.getEntities(mc.player, near))
			if (e instanceof net.minecraft.world.entity.decoration.painting.Painting)
				PAINTINGS.add(e.getBoundingBox());
	}

	private static boolean paintingBlocks(Level level, Vec3 eye, Vec3 target, Entity ent) {
		for (AABB bb : PAINTINGS) {
			if (bb.clip(eye, target).isPresent()) return true;
		}
		return false;
	}

}
