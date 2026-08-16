package com.ryn.skyryn.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import com.ryn.skyryn.data.ShardDb;

/**
 * /srspot &lt;шард&gt; [метка спота] — записывает текущие координаты + зону из
 * скорборда в config/skyryn-spots.json. Порядок такой: добежать до точки спавна
 * моба и ввести команду с именем шарда — координаты, где стоишь, и станут спотом,
 * куда ведёт метка при отслеживании. Имя шарда матчится по базе, остаток — метка
 * места (напр. "Tangleburg's Path"). Заодно пишем сырой скорборд — по нему
 * строим карту зона -> остров.
 *
 * Команду ловим через ALLOW_COMMAND (перехват до отправки на сервер), а не через
 * fabric command API — в этой сборке его клиентские классы не резолвятся.
 */
public class SpotRecorder {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("skyryn-spots.json");
	}

	public static void register() {
		ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
			String c = command.trim();
			if (c.equals("srspot") || c.startsWith("srspot ")) {
				String args = c.length() > 6 ? c.substring(6).trim() : "";
				String[] parts = args.split("\\s+", 2);
				String sub = parts[0].toLowerCase();
				if (sub.equals("list")) {
					list(parts.length > 1 ? parts[1].trim() : "");
				} else if (sub.equals("remove") || sub.equals("delete")) {
					remove(parts.length > 1 ? parts[1].trim() : "");
				} else {
					record(args);
				}
				return false; // не отправляем на сервер — это наша команда
			}
			return true;
		});
	}

	/** Наш префикс чата — фиолетовый, чтобы не путать с зелёным SkyBlocker. */
	private static final String PREFIX = "§5§l[§dSkyRyn§5§l]§r ";

	private static void record(String args) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		// Форматы (оба работают):
		//   /srspot шард варп метка   — пробелами (варп распознаём по списку)
		//   /srspot шард ; варп ; метка — явно через ";"
		// Варп задаёт остров надёжно (скорборд у нас пока не читается).
		String shard, warp = "", label = "";
		if (args.contains(";")) {
			String[] parts = args.split(";", 3);
			shard = parts[0].trim().toLowerCase();
			if (parts.length > 1) warp = normWarp(parts[1].trim());
			if (parts.length > 2) label = parts[2].trim();
		} else {
			// шард — самое длинное имя-ключ, с которого начинается ввод
			String lower = args.toLowerCase();
			shard = "";
			for (String k : ShardDb.allShards()) {
				if ((lower.equals(k) || lower.startsWith(k + " ")) && k.length() > shard.length()) shard = k;
			}
			String rest = shard.isEmpty() ? args : args.substring(shard.length()).trim();
			// следующее слово — варп? тогда остальное метка
			String[] rw = rest.split("\\s+", 2);
			if (rw.length >= 1 && SkyBlockCheck.isWarp(rw[0])) {
				warp = normWarp(rw[0]);
				label = rw.length > 1 ? rw[1].trim() : "";
			} else {
				label = rest;
			}
		}

		Vec3 p = mc.player.position();
		String coords = fmt(p.x) + " " + fmt(p.y) + " " + fmt(p.z);
		String area = SkyBlockCheck.currentArea();
		// Остров: из варпа (надёжно), иначе из скорборда (пока может быть пусто).
		String island = warp.isBlank() ? SkyBlockCheck.currentIsland() : SkyBlockCheck.islandOfWarp(warp);

		JsonObject entry = new JsonObject();
		entry.addProperty("shard", shard);
		entry.addProperty("warp", warp);
		entry.addProperty("label", label);
		entry.addProperty("coords", coords);
		entry.addProperty("area", area);
		entry.addProperty("island", island);
		entry.addProperty("sidebar", SkyBlockCheck.sidebarDump());

		JsonArray arr = readAll();
		arr.add(entry);
		try {
			writeAll(arr);
		} catch (Exception e) {
			say(mc, "§cНе смог записать: " + e.getMessage());
			return;
		}

		// В чат коорды через " / ", иначе SkyBlocker распознаёт тройку чисел и
		// лезет со своим "Click to display waypoint". В файл пишется как "X Y Z".
		String show = coords.replace(" ", " §7/§b ");
		String areaShow = " §7зона '§f" + (area.isBlank() ? "—" : area) + "§7'";
		if (shard.isEmpty()) {
			say(mc, "§eЗаписал §7(шард не распознан): §f" + args + " §7@ §b" + show + areaShow);
		} else {
			say(mc, "§fСпот для §d" + shard + (label.isBlank() ? "" : " §7(§f" + label + "§7)")
					+ " §7@ §b" + show
					+ (warp.isBlank() ? "" : " §7варп §f" + warp)
					+ areaShow + " §7остров '§f" + (island.isBlank() ? "?" : island) + "§7'");
		}
	}

	/** Читает массив спотов из файла; пусто, если файла нет или он битый. */
	private static JsonArray readAll() {
		try {
			Path f = file();
			if (!Files.exists(f)) return new JsonArray();
			var parsed = JsonParser.parseString(Files.readString(f));
			return parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
		} catch (Exception e) {
			return new JsonArray();
		}
	}

	private static void writeAll(JsonArray arr) throws Exception {
		Path f = file();
		Files.createDirectories(f.getParent());
		Files.writeString(f, GSON.toJson(arr));
	}

	/** Одна строка спота для чата: "#12 §fnessie §7(Murkwater Loch) @ -651 89 35 §7остров 'galatea'". */
	private static String describe(int index1based, JsonObject e) {
		String shard = e.has("shard") ? e.get("shard").getAsString() : "";
		String label = e.has("label") ? e.get("label").getAsString() : "";
		String coords = e.has("coords") ? e.get("coords").getAsString() : "";
		String island = e.has("island") ? e.get("island").getAsString() : "";
		return "§7#" + index1based + " §f" + (shard.isEmpty() ? "§c(без шарда)" : shard)
				+ (label.isBlank() ? "" : " §7(§f" + label + "§7)")
				+ " §7@ §b" + coords
				+ (island.isBlank() ? "" : " §7остров '§f" + island + "§7'");
	}

	/** /srspot list [шард] — без аргумента: последние 15 записей, с шардом: все его споты. */
	private static void list(String shardFilter) {
		Minecraft mc = Minecraft.getInstance();
		JsonArray arr = readAll();
		if (arr.isEmpty()) {
			say(mc, "§7Споты не записаны.");
			return;
		}

		String filter = shardFilter.toLowerCase();
		java.util.List<Integer> matched = new java.util.ArrayList<>();
		for (int i = 0; i < arr.size(); i++) {
			JsonObject e = arr.get(i).getAsJsonObject();
			String shard = e.has("shard") ? e.get("shard").getAsString() : "";
			if (filter.isEmpty() || shard.equalsIgnoreCase(filter)) matched.add(i);
		}

		if (matched.isEmpty()) {
			say(mc, "§7Нет спотов для §f" + shardFilter);
			return;
		}

		java.util.List<Integer> toShow = matched;
		boolean truncated = false;
		if (filter.isEmpty() && matched.size() > 15) {
			toShow = matched.subList(matched.size() - 15, matched.size());
			truncated = true;
		}

		say(mc, "§7Всего споты: §f" + arr.size()
				+ (filter.isEmpty() ? "" : " §7(шард §f" + shardFilter + "§7: " + matched.size() + ")")
				+ (truncated ? " §7— показаны последние 15" : ""));
		for (int i : toShow) {
			say(mc, describe(i + 1, arr.get(i).getAsJsonObject()));
		}
	}

	/** /srspot remove <номер|last> — номер как в выводе /srspot list (1-based). */
	private static void remove(String arg) {
		Minecraft mc = Minecraft.getInstance();
		if (arg.isBlank()) {
			say(mc, "§cУкажи номер: §7/srspot remove <номер|last>");
			return;
		}

		JsonArray arr = readAll();
		if (arr.isEmpty()) {
			say(mc, "§7Споты не записаны.");
			return;
		}

		int idx; // 0-based
		if (arg.equalsIgnoreCase("last")) {
			idx = arr.size() - 1;
		} else {
			try {
				idx = Integer.parseInt(arg.trim()) - 1;
			} catch (NumberFormatException e) {
				say(mc, "§cНе номер: §f" + arg);
				return;
			}
		}
		if (idx < 0 || idx >= arr.size()) {
			say(mc, "§cНет спота #" + (idx + 1) + " §7(всего " + arr.size() + ")");
			return;
		}

		JsonObject removed = arr.get(idx).getAsJsonObject();
		arr.remove(idx);
		try {
			writeAll(arr);
		} catch (Exception e) {
			say(mc, "§cНе смог сохранить: " + e.getMessage());
			return;
		}
		say(mc, "§7Удалил " + describe(idx + 1, removed));
	}

	/** "galatea" / "/warp galatea" -> "/warp galatea". */
	private static String normWarp(String w) {
		if (w.isBlank()) return "";
		w = w.replaceFirst("^/", "").trim();
		if (w.toLowerCase().startsWith("warp ")) return "/" + w;
		return "/warp " + w;
	}

	private static void say(Minecraft mc, String msg) {
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal(PREFIX + msg));
	}

	/** Координата с 2 знаками, без хвостовых нулей: -553.50 -> -553.5, 89.00 -> 89. */
	private static String fmt(double v) {
		String s = String.format(java.util.Locale.US, "%.2f", v);
		if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s;
	}
}
