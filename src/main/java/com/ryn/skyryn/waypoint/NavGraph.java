package com.ryn.skyryn.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class NavGraph {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final double GOAL_SNAP = 12.0;

	public static class Node {
		final int id;
		final double x, y, z;
		Node(int id, double x, double y, double z) { this.id = id; this.x = x; this.y = y; this.z = z; }
		Vec3 vec() { return new Vec3(x, y, z); }
	}

	public static class Island {
		final List<Node> nodes = new ArrayList<>();
		final Map<Integer, Node> byId = new HashMap<>();
		final Map<Integer, List<Integer>> adj = new HashMap<>();
		final Map<String, Integer> spots = new LinkedHashMap<>();
		final Map<String, Integer> npcs = new LinkedHashMap<>();
		int nextId = 0;

		public int addNode(double x, double y, double z) {
			int id = nextId++;
			Node n = new Node(id, x, y, z);
			nodes.add(n); byId.put(id, n); adj.put(id, new ArrayList<>());
			return id;
		}
		public void addEdge(int a, int b) {
			if (a == b || !adj.containsKey(a) || !adj.containsKey(b)) return;
			if (!adj.get(a).contains(b)) { adj.get(a).add(b); adj.get(b).add(a); }
		}
		double dist(int a, int b) {
			Node p = byId.get(a), q = byId.get(b);
			return Math.sqrt(sq(p.x - q.x) + sq(p.y - q.y) + sq(p.z - q.z));
		}
		int nearest(double x, double y, double z, double maxDist, int... exclude) {
			int best = -1; double bestD = maxDist * maxDist;
			outer:
			for (Node n : nodes) {
				for (int e : exclude) if (n.id == e) continue outer;
				double d = sq(n.x - x) + sq(n.y - y) + sq(n.z - z);
				if (d <= bestD) { bestD = d; best = n.id; }
			}
			return best;
		}
		public int nodeCount() { return nodes.size(); }
		public int edgeCount() { int e = 0; for (var l : adj.values()) e += l.size(); return e / 2; }
		public int spotCount() { return spots.size(); }
		public int npcCount() { return npcs.size(); }
	}

	private static final Map<String, Island> ISLANDS = new HashMap<>();

	private static String norm(String island) { return island == null ? "" : island.trim().toLowerCase(); }

	public static Island island(String name) { return ISLANDS.get(norm(name)); }
	public static Island islandOrCreate(String name) {
		return ISLANDS.computeIfAbsent(norm(name), k -> new Island());
	}
	public static Map<String, Island> all() { return ISLANDS; }

	private static double sq(double v) { return v * v; }

	private static volatile Island cIsl = null;
	private static volatile int cGoal = -1;
	private static volatile int cStart = -1;
	private static Map<Integer, Double> cDist = null;
	private static Map<Integer, Integer> cPrev = null;
	private static volatile List<Vec3> cPath = null;

	public static List<Vec3> route(Vec3 from, Vec3 target) {
		Island isl = null; int goal = -1; double bestG = GOAL_SNAP * GOAL_SNAP;
		for (Island i : ISLANDS.values()) {
			for (Node n : i.nodes) {
				double d = sq(n.x - target.x) + sq(n.y - target.y) + sq(n.z - target.z);
				if (d <= bestG) { bestG = d; goal = n.id; isl = i; }
			}
		}
		if (isl == null) return null;

		if (!(isl == cIsl && goal == cGoal && cDist != null)) {
			dijkstraFrom(isl, goal);
			cIsl = isl; cGoal = goal; cStart = -1; cPath = null;
		}

		int start = -1; double best = Double.MAX_VALUE;
		for (Node n : isl.nodes) {
			if (cDist.get(n.id) == null) continue;
			double d2 = sq(n.x - from.x) + sq(n.y - from.y) + sq(n.z - from.z);
			if (d2 < best) { best = d2; start = n.id; }
		}
		if (start < 0) return null;
		if (start == cStart && cPath != null) return cPath;

		List<Vec3> pts = new ArrayList<>();
		Integer cur = start; int guard = 0;
		while (cur != null) {
			pts.add(isl.byId.get(cur).vec());
			if (cur == goal) break;
			cur = cPrev.get(cur);
			if (++guard > isl.nodes.size() + 1) break;
		}
		cStart = start; cPath = pts;
		return pts;
	}

	private static void dijkstraFrom(Island isl, int goal) {
		Map<Integer, Double> d = new HashMap<>();
		Map<Integer, Integer> prev = new HashMap<>();
		PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) ->
				Double.compare(d.getOrDefault(a[0], Double.MAX_VALUE), d.getOrDefault(b[0], Double.MAX_VALUE)));
		d.put(goal, 0.0); pq.add(new int[]{goal});
		while (!pq.isEmpty()) {
			int u = pq.poll()[0];
			double du = d.getOrDefault(u, Double.MAX_VALUE);
			for (int v : isl.adj.getOrDefault(u, List.of())) {
				double nd = du + isl.dist(u, v);
				if (nd < d.getOrDefault(v, Double.MAX_VALUE)) {
					d.put(v, nd); prev.put(v, u); pq.add(new int[]{v});
				}
			}
		}
		cDist = d; cPrev = prev;
	}

	public static void invalidateCache() { cIsl = null; cGoal = -1; cStart = -1; cDist = null; cPrev = null; cPath = null; }

	public static List<Vec3> spotsForShard(String shardKey) {
		return spotsForMethod(shardKey, null);
	}

	private static final Map<String, String> SPOT_ALIAS = Map.of("heron", "crow");

	private static String spotKey(String shardKey) {
		if (shardKey == null) return null;
		String k = shardKey.trim().toLowerCase();
		return SPOT_ALIAS.getOrDefault(k, k);
	}

	public static List<Vec3> spotsForMethod(String shardKey, String methodType) {
		List<Vec3> typed = new ArrayList<>(), untyped = new ArrayList<>();
		if (shardKey == null) return untyped;
		String want = spotKey(shardKey);
		String mt = methodType == null ? null : methodType.toLowerCase();
		for (Island isl : ISLANDS.values()) {
			for (var e : isl.spots.entrySet()) {
				String key = e.getKey();
				int bar = key.indexOf('|');
				String sk = (bar >= 0 ? key.substring(0, bar) : key).trim().toLowerCase();
				if (!sk.equals(want)) continue;
				Node n = isl.byId.get(e.getValue());
				if (n == null) continue;
				String lt = labelType(bar >= 0 ? key.substring(bar + 1).toLowerCase() : "");
				if (mt == null) { untyped.add(n.vec()); continue; }
				if (lt.isEmpty()) untyped.add(n.vec());
				else if (lt.equals(mt)) typed.add(n.vec());
			}
		}
		return !typed.isEmpty() ? typed : untyped;
	}

	private static String labelType(String label) {
		if (label == null) return "";
		if (label.contains("trap")) return "trap";
		if (label.contains("hunt")) return "hunting";
		if (label.contains("chest")) return "chest";
		if (label.contains("buy") || label.contains("purchase") || label.contains("npc")) return "purchase";
		return "";
	}

	public static String islandForMethod(String shardKey, String methodType) {
		if (shardKey == null) return "";
		String want = spotKey(shardKey);
		String mt = methodType == null ? null : methodType.toLowerCase();
		String untypedIsland = "";
		for (var e : ISLANDS.entrySet()) {
			for (String key : e.getValue().spots.keySet()) {
				int bar = key.indexOf('|');
				String sk = (bar >= 0 ? key.substring(0, bar) : key).trim().toLowerCase();
				if (!sk.equals(want)) continue;
				String lt = labelType(bar >= 0 ? key.substring(bar + 1).toLowerCase() : "");
				if (mt != null && lt.equals(mt)) return e.getKey();
				if (lt.isEmpty() && untypedIsland.isEmpty()) untypedIsland = e.getKey();
			}
		}
		return untypedIsland;
	}

	public record NpcSpot(double x, double y, double z, String island) { }

	public static NpcSpot npc(String name) {
		if (name == null) return null;
		for (var e : ISLANDS.entrySet()) {
			Island isl = e.getValue();
			for (var ne : isl.npcs.entrySet()) {
				if (!ne.getKey().equalsIgnoreCase(name)) continue;
				Node n = isl.byId.get(ne.getValue());
				if (n == null) continue;
				return new NpcSpot(n.x, n.y, n.z, e.getKey());
			}
		}
		return null;
	}

	private static final Map<String, String> ISLAND_WARP = Map.of(
			"galatea", "/warp galatea", "crimson", "/warp crimson", "dwarven", "/warp dwarves",
			"end", "/warp end", "spider", "/warp spider", "park", "/warp park",
			"crystal", "/warp crystals", "bayou", "/warp bayou");

	public static String defaultWarp(String island) {
		return ISLAND_WARP.getOrDefault(norm(island), "");
	}

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("skyryn-graph.json"); }

	public static void load() {
		ISLANDS.clear();
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
			JsonObject islands = root.getAsJsonObject("islands");
			if (islands == null) return;
			for (String name : islands.keySet()) {
				JsonObject io = islands.getAsJsonObject(name);
				Island isl = islandOrCreate(name);
				int maxId = -1;
				for (var el : io.getAsJsonArray("nodes")) {
					JsonObject n = el.getAsJsonObject();
					int id = n.get("id").getAsInt();
					Node nd = new Node(id, n.get("x").getAsDouble(), n.get("y").getAsDouble(), n.get("z").getAsDouble());
					isl.nodes.add(nd); isl.byId.put(id, nd); isl.adj.put(id, new ArrayList<>());
					if (id > maxId) maxId = id;
				}
				isl.nextId = maxId + 1;
				if (io.has("edges")) for (var el : io.getAsJsonArray("edges")) {
					JsonArray e = el.getAsJsonArray();
					isl.addEdge(e.get(0).getAsInt(), e.get(1).getAsInt());
				}
				if (io.has("spots")) { JsonObject sp = io.getAsJsonObject("spots");
					for (String k : sp.keySet()) isl.spots.put(k, sp.get(k).getAsInt()); }
				if (io.has("npcs")) { JsonObject np = io.getAsJsonObject("npcs");
					for (String k : np.keySet()) isl.npcs.put(k, np.get(k).getAsInt()); }
			}
			int nodes = ISLANDS.values().stream().mapToInt(Island::nodeCount).sum();
			com.ryn.skyryn.config.SkyLog.d("NavGraph загружен: островов " + ISLANDS.size() + ", узлов " + nodes);
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("Ошибка чтения skyryn-graph.json: " + e);
		}
	}

	public static void save() {
		try {
			JsonObject root = new JsonObject();
			JsonObject islands = new JsonObject();
			for (var en : ISLANDS.entrySet()) {
				Island isl = en.getValue();
				if (isl.nodes.isEmpty()) continue;
				JsonObject io = new JsonObject();
				JsonArray nodes = new JsonArray();
				for (Node n : isl.nodes) {
					JsonObject no = new JsonObject();
					no.addProperty("id", n.id); no.addProperty("x", round(n.x));
					no.addProperty("y", round(n.y)); no.addProperty("z", round(n.z));
					nodes.add(no);
				}
				io.add("nodes", nodes);
				JsonArray edges = new JsonArray();
				java.util.Set<Long> seen = new java.util.HashSet<>();
				for (var e : isl.adj.entrySet()) for (int v : e.getValue()) {
					int a = e.getKey(), b = v; if (a > b) { int t = a; a = b; b = t; }
					long key = ((long) a << 32) | (b & 0xffffffffL);
					if (seen.add(key)) { JsonArray pair = new JsonArray(); pair.add(a); pair.add(b); edges.add(pair); }
				}
				io.add("edges", edges);
				JsonObject sp = new JsonObject(); isl.spots.forEach(sp::addProperty); io.add("spots", sp);
				JsonObject np = new JsonObject(); isl.npcs.forEach(np::addProperty); io.add("npcs", np);
				islands.add(en.getKey(), io);
			}
			root.add("islands", islands);
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(root));
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.d("Ошибка записи skyryn-graph.json: " + e);
		}
	}

	private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
