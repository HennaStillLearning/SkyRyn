package com.ryn.skyryn.waypoint;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import com.ryn.skyryn.data.ShardDb;

/**
 * /srpath — запись навигационного графа острова ногами (см. {@link NavGraph}).
 *
 * Порядок записи: {@code /srpath start}, пройти по коридорам (мод сам ставит узлы
 * каждые ~3.5 блока и связывает близкие в сеть), пометить концы {@code /srpath spot
 * <шард>} и {@code /srpath npc <имя>}, затем {@code /srpath end}. Дальше навигация
 * от любой точки идёт по этому графу.
 */
public class PathRecorder {

	/** Шаг авто-крошек, блоки. */
	private static final double STEP = 3.5;
	/** Авто-связь: новый узел цепляем к ближайшему уже записанному в этом радиусе. */
	private static final double LINK_DIST = 4.5;
	/** ...но только если по высоте близко — не связывать этажи «сквозь пол». */
	private static final double LINK_DY = 2.5;
	/** Пост-обработка на /srpath end: любые два узла ближе этого соединяем ребром,
	 *  если между ними ЧИСТАЯ прямая (не сквозь блоки). Так граф сам обрастает
	 *  срезами — спуски, прыжки, AOTV-перелёты через прогалы. Стены не пробиваем. */
	private static final double CONNECT_RADIUS = 10.0;

	private static final String PREFIX = "§5§l[§dSkyRyn§5§l]§r ";

	private static boolean recording = false;
	private static String islandName = "";
	private static NavGraph.Island isl = null;
	private static int lastNodeId = -1;
	private static Vec3 lastPos = null;
	private static final List<Integer> session = new ArrayList<>();

	public static void register() {
		NavGraph.load();

		ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
			String c = command.trim();
			if (c.equals("srpath") || c.startsWith("srpath ")) {
				String args = c.length() > 6 ? c.substring(6).trim() : "";
				handle(args);
				return false; // наша команда, на сервер не шлём
			}
			return true;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!recording || isl == null) return;
			var p = client.player;
			if (p == null) return;
			Vec3 pos = p.position();
			// Цепляем ЛЮБОЙ шаг: спрыгнул/подпрыгнул/AOTV/этерварп — всё это реальный
			// проход, и должно стать ребром-шорткатом, а не резаться «защитой».
			if (lastPos == null || pos.distanceToSqr(lastPos) >= STEP * STEP) addNode(pos, true);
		});
	}

	private static void handle(String args) {
		String[] parts = args.split("\\s+", 2);
		String sub = parts[0].toLowerCase();
		String rest = parts.length > 1 ? parts[1].trim() : "";
		switch (sub) {
			case "start" -> start();
			case "end", "stop" -> end();
			case "cancel" -> cancel();
			case "spot" -> spot(rest);
			case "npc" -> npc(rest);
			case "list" -> list();
			default -> help();
		}
	}

	private static void start() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		String island = SkyBlockCheck.currentIsland();
		if (island == null || island.isBlank()) {
			say("§cНе могу определить остров (скорборд/зона не читаются). Встань на острове и повтори.");
			return;
		}
		recording = true;
		islandName = island;
		isl = NavGraph.islandOrCreate(island);
		lastNodeId = -1; lastPos = null; session.clear();
		addNode(mc.player.position(), true); // первый узел там, где стоишь
		say("§aЗапись графа §f" + island + "§a. Иди по коридорам — узлы ставятся сами. "
				+ "Концы помечай §f/srpath spot <шард>§a и §f/srpath npc <имя>§a, потом §f/srpath end§a.");
	}

	private static void end() {
		if (!recording) { say("§7Запись не идёт."); return; }
		recording = false;
		int shortcuts = densify();          // достроить срезы по чистым линиям
		NavGraph.save();
		NavGraph.invalidateCache();
		say("§aГраф §f" + islandName + "§a сохранён: узлов " + isl.nodeCount()
				+ ", рёбер " + isl.edgeCount() + " §7(+" + shortcuts + " срезов авто)§a, спотов "
				+ isl.spotCount() + ", NPC " + isl.npcCount());
		isl = null; lastNodeId = -1; lastPos = null; session.clear();
	}

	/** Достраивает срезы: соединяет близкие узлы, между которыми ЧИСТАЯ прямая
	 *  (спуски/прыжки/AOTV-перелёты), не пробивая стены. Зовётся на /srpath end. */
	private static int densify() {
		net.minecraft.world.level.Level level = Minecraft.getInstance().level;
		if (level == null || isl == null) return 0;
		java.util.List<NavGraph.Node> nodes = isl.nodes;
		int added = 0;
		for (int i = 0; i < nodes.size(); i++) {
			NavGraph.Node a = nodes.get(i);
			for (int j = i + 1; j < nodes.size(); j++) {
				NavGraph.Node b = nodes.get(j);
				double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
				if (dx * dx + dy * dy + dz * dz > CONNECT_RADIUS * CONNECT_RADIUS) continue;
				if (isl.adj.get(a.id).contains(b.id)) continue; // уже связаны
				if (clearLine(level, a, b)) { isl.addEdge(a.id, b.id); added++; }
			}
		}
		return added;
	}

	/** Чистая ли прямая между узлами: не проходит сквозь твёрдый блок (семплим по 0.5). */
	private static boolean clearLine(net.minecraft.world.level.Level level, NavGraph.Node a, NavGraph.Node b) {
		double ax = a.x, ay = a.y + 0.5, az = a.z, bx = b.x, by = b.y + 0.5, bz = b.z;
		double dist = Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz));
		int steps = Math.max(1, (int) Math.ceil(dist / 0.5));
		for (int s = 1; s < steps; s++) {
			double t = (double) s / steps;
			net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(
					ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t);
			if (!level.hasChunkAt(p)) return false;                  // чанк не загружен — не рискуем
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false; // блок на пути
		}
		return true;
	}

	private static void cancel() {
		if (!recording) { say("§7Запись не идёт."); return; }
		// Убираем узлы, добавленные в этой сессии (рёбра с ними тоже вычищаем).
		for (int id : session) {
			NavGraph.Node n = isl.byId.remove(id);
			if (n != null) isl.nodes.remove(n);
			List<Integer> nb = isl.adj.remove(id);
			if (nb != null) for (int v : nb) { var l = isl.adj.get(v); if (l != null) l.remove((Integer) id); }
		}
		recording = false;
		say("§eЗапись отменена — убрано " + session.size() + " узлов, ничего не сохранено.");
		isl = null; lastNodeId = -1; lastPos = null; session.clear();
	}

	/** Ставит узел; chain=true — соединяет с предыдущим по цепочке хода. */
	private static int addNode(Vec3 pos, boolean chain) {
		int id = isl.addNode(pos.x, pos.y, pos.z);
		session.add(id);
		if (chain && lastNodeId >= 0) isl.addEdge(lastNodeId, id);
		// Авто-связь с ближайшим уже записанным узлом (кроме себя и предыдущего).
		int near = isl.nearest(pos.x, pos.y, pos.z, LINK_DIST, id, lastNodeId);
		if (near >= 0 && Math.abs(isl.byId.get(near).y - pos.y) <= LINK_DY) isl.addEdge(id, near);
		lastNodeId = id; lastPos = pos;
		return id;
	}

	private static void spot(String rest) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (isl == null && !ensureIslandForTag()) return;
		// шард — самый длинный ключ, с которого начинается ввод; остаток — метка
		String lower = rest.toLowerCase();
		String shard = "";
		for (String k : ShardDb.allShards())
			if ((lower.equals(k) || lower.startsWith(k + " ")) && k.length() > shard.length()) shard = k;
		if (shard.isEmpty()) { say("§cШард не распознан: §f" + rest); return; }
		String label = rest.substring(shard.length()).trim();
		int id = tagNode(mc.player.position());
		String key = label.isBlank() ? shard : shard + "|" + label;
		isl.spots.put(key, id);
		NavGraph.save(); NavGraph.invalidateCache();
		say("§aСпот §d" + shard + (label.isBlank() ? "" : " §7(" + label + ")") + " §a→ узел #" + id);
	}

	private static void npc(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (name.isBlank()) { say("§cУкажи имя: §f/srpath npc Rosemary"); return; }
		if (isl == null && !ensureIslandForTag()) return;
		int id = tagNode(mc.player.position());
		isl.npcs.put(name, id);
		NavGraph.save(); NavGraph.invalidateCache();
		say("§aNPC §f" + name + " §a→ узел #" + id);
	}

	/** Узел-конец: в записи — часть цепочки; вне записи — цепляем к ближайшему узлу графа. */
	private static int tagNode(Vec3 pos) {
		if (recording) return addNode(pos, true);
		int id = isl.addNode(pos.x, pos.y, pos.z);
		int near = isl.nearest(pos.x, pos.y, pos.z, 8.0, id);
		if (near >= 0) isl.addEdge(id, near);
		else say("§eУзел изолирован — рядом нет графа. Запиши путь /srpath start и веди сюда.");
		return id;
	}

	/** Вне записи spot/npc нужно к какому-то острову — берём текущий. */
	private static boolean ensureIslandForTag() {
		String island = SkyBlockCheck.currentIsland();
		if (island == null || island.isBlank()) { say("§cНе могу определить остров."); return false; }
		islandName = island; isl = NavGraph.islandOrCreate(island);
		return true;
	}

	private static void list() {
		if (NavGraph.all().isEmpty()) { say("§7Граф пуст. Начни: §f/srpath start"); return; }
		say("§7Записанные острова:");
		for (var e : NavGraph.all().entrySet()) {
			NavGraph.Island i = e.getValue();
			if (i.nodeCount() == 0) continue;
			say("§f" + e.getKey() + " §7— узлов " + i.nodeCount() + ", рёбер " + i.edgeCount()
					+ ", спотов " + i.spotCount() + ", NPC " + i.npcCount());
		}
	}

	private static void help() {
		say("§7/srpath §fstart §7— начать запись острова");
		say("§7/srpath §fspot <шард> [метка] §7· §fnpc <имя> §7— пометить конец");
		say("§7/srpath §fend §7· §fcancel §7· §flist");
	}

	private static void say(String msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal(PREFIX + msg));
	}

	public static boolean isRecording() { return recording; }
}
