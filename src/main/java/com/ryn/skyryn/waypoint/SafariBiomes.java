package com.ryn.skyryn.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SafariBiomes {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String PREFIX = "§5§l[§dSkyRyn§5§l]§r ";

	private static final Map<String, List<double[]>> POLYS = new LinkedHashMap<>();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("skyryn-safari-biomes.json");
	}

	public static void register() {
		load();
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyryn", "safari-biomes"),
				(ctx, tick) -> renderHud(ctx));
	}

	public static String currentBiome() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return "";
		return biomeAt(mc.player.getX(), mc.player.getZ());
	}

	public static String biomeAt(double x, double z) {
		for (var e : POLYS.entrySet()) {
			List<double[]> poly = e.getValue();
			if (poly.size() >= 3 && inPoly(x, z, poly)) return e.getKey();
		}
		return "";
	}

	public static boolean any() { return !POLYS.isEmpty(); }

	private static boolean inPoly(double x, double z, List<double[]> poly) {
		boolean in = false;
		int n = poly.size();
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double xi = poly.get(i)[0], zi = poly.get(i)[1];
			double xj = poly.get(j)[0], zj = poly.get(j)[1];
			boolean intersect = ((zi > z) != (zj > z))
					&& (x < (xj - xi) * (z - zi) / (zj - zi) + xi);
			if (intersect) in = !in;
		}
		return in;
	}

	private static final Matrix4f VP = new Matrix4f();
	private static Vec3 camPos = Vec3.ZERO;
	private static boolean haveFrame = false;

	private record BLabel(double x, double y, double z, String text) { }
	private static final List<BLabel> labels = new ArrayList<>();

	private static int color(String n) {
		return switch (n) {
			case "icy" -> 0x60D0FF;
			case "haunted" -> 0xB060FF;
			case "cavern" -> 0xFFB040;
			case "forest" -> 0x50E070;
			default -> 0x404040 | (n.hashCode() & 0xBFBFBF);
		};
	}

	public static void captureFrame(Vec3 cam) {
		Minecraft.getInstance().gameRenderer.getMainCamera().getViewRotationProjectionMatrix(VP);
		camPos = cam;
		haveFrame = true;
	}

	public static void renderWorld(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam) {
		labels.clear();
		if (POLYS.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (!SkyBlockCheck.onSkyBlock() || !"crittersafari".equals(SkyBlockCheck.currentIsland())) return;

		VertexConsumer vc = buf.getBuffer(RenderTypes.lines());
		PoseStack.Pose e = ps.last();
		double baseY = mc.player.getY();
		String cur = currentBiome();

		for (var en : POLYS.entrySet()) {
			List<double[]> poly = en.getValue();
			if (poly.size() < 2) continue;
			int col = color(en.getKey());
			int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
			boolean isCur = en.getKey().equals(cur);
			int n = poly.size();

			if (isCur || !com.ryn.skyryn.config.RynConfig.flag("bm." + en.getKey(), true)) continue;
			double cx = 0, cz = 0;
			for (double[] v : poly) { cx += v[0]; cz += v[1]; }
			cx /= n; cz /= n;
			String who = playersIn(mc, en.getKey());
			float mx = (float) (cx - cam.x), mz = (float) (cz - cam.z);
			line(vc, e, mx, (float) (baseY - 2 - cam.y), mz, mx, (float) (baseY + 64 - cam.y), mz, r, g, b, 8f);
			labels.add(new BLabel(cx, baseY + 2.4, cz,
					(isCur ? "§l" : "") + colorCode(en.getKey()) + capName(en.getKey())
							+ (who.isEmpty() ? "" : " §7(" + who + ")") + (isCur ? " §e◄" : "")));
		}
		buf.endBatch();
	}

	private static String playersIn(Minecraft mc, String biome) {
		return String.join(", ", playersInList(biome));
	}

	private static final Map<String, String> PARTY_BIOME = new java.util.HashMap<>();
	private static final Map<String, Long> PARTY_AT = new java.util.HashMap<>();
	private static final long PARTY_TTL = 5 * 60 * 1000L;

	public static void recordPartyBiome(String name, String biome) {
		if (name == null || name.isBlank() || biome == null || biome.isBlank()) return;
		PARTY_BIOME.put(name, biome);
		PARTY_AT.put(name, System.currentTimeMillis());
	}

	public static java.util.List<String> playersInList(String biome) {
		java.util.List<String> names = new java.util.ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) for (var pl : mc.level.players()) {
			if (pl.getUUID().version() != 4) continue;
			if (biome.equals(biomeAt(pl.getX(), pl.getZ()))) {
				String nm = pl.getName().getString();
				if (!names.contains(nm)) names.add(nm);
			}
		}
		long now = System.currentTimeMillis();
		for (var e : PARTY_BIOME.entrySet()) {
			if (!biome.equals(e.getValue())) continue;
			Long at = PARTY_AT.get(e.getKey());
			if (at == null || now - at > PARTY_TTL) continue;
			if (!names.contains(e.getKey())) names.add(e.getKey());
		}
		return names;
	}

	private static String colorCode(String n) {
		return switch (n) {
			case "icy" -> "§b"; case "haunted" -> "§5"; case "cavern" -> "§6"; case "forest" -> "§a"; default -> "§f";
		};
	}

	private static String capName(String n) { return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1); }

	public static String currentColored() {
		String b = currentBiome();
		return b.isEmpty() ? "" : colorCode(b) + capName(b);
	}

	private static void renderHud(GuiGraphicsExtractor ctx) {
		if (!haveFrame || labels.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		for (BLabel l : labels) {
			float rx = (float) (l.x - camPos.x), ry = (float) (l.y - camPos.y), rz = (float) (l.z - camPos.z);
			Vector4f clip = VP.transform(new Vector4f(rx, ry, rz, 1f));
			if (clip.w <= 0.05f) continue;
			float ndcx = clip.x / clip.w, ndcy = clip.y / clip.w;
			if (ndcx < -1.2f || ndcx > 1.2f || ndcy < -1.2f || ndcy > 1.2f) continue;
			int px = Math.round((ndcx * 0.5f + 0.5f) * sw), py = Math.round((1f - (ndcy * 0.5f + 0.5f)) * sh);
			ctx.text(mc.font, l.text, px - mc.font.width(l.text) / 2, py, 0xFFFFFFFF, true);
		}
	}

	private static void line(VertexConsumer vc, PoseStack.Pose e,
							 float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, float width) {
		float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len > 0) { nx /= len; ny /= len; nz /= len; }
		vc.addVertex(e, x1, y1, z1).setNormal(e, nx, ny, nz).setColor(r, g, b, 255).setLineWidth(width);
		vc.addVertex(e, x2, y2, z2).setNormal(e, nx, ny, nz).setColor(r, g, b, 255).setLineWidth(width);
	}

	private static void load() {
		POLYS.clear();
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			var parsed = JsonParser.parseString(Files.readString(f));
			if (!parsed.isJsonObject()) return;
			for (var e : parsed.getAsJsonObject().entrySet()) {
				if (!e.getValue().isJsonArray()) continue;
				List<double[]> poly = new java.util.ArrayList<>();
				for (var vtx : e.getValue().getAsJsonArray()) {
					JsonArray a = vtx.getAsJsonArray();
					if (a.size() >= 2) poly.add(new double[]{ a.get(0).getAsDouble(), a.get(1).getAsDouble() });
				}
				if (!poly.isEmpty()) POLYS.put(e.getKey().toLowerCase(), poly);
			}
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("skyryn-safari-biomes.json не прочитан: " + e);
		}
	}

	private static double round(double v) { return Math.round(v * 10.0) / 10.0; }

	private static void say(Minecraft mc, String msg) {
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal(PREFIX + msg));
	}
}
