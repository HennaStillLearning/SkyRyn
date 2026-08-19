package com.ryn.skyryn.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VanillaLook {
	private VanillaLook() { }

	public record Look(Identifier model, String skin) { }

	private static final Map<String, Look> LOOKS = new HashMap<>();
	private static final Map<String, ResolvableProfile> PROFILES = new HashMap<>();

	public static int size() { return LOOKS.size(); }

	public static void load() {
		LOOKS.clear();
		PROFILES.clear();
		JsonObject root;
		try (InputStream in = VanillaLook.class.getResourceAsStream("/skyryn/item-fallback.json")) {
			if (in == null) {
				com.ryn.skyryn.config.SkyLog.warn("item-fallback.json нет в ресурсах");
				return;
			}
			root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			com.ryn.skyryn.config.SkyLog.warn("item-fallback.json не прочитан: " + e);
			return;
		}
		for (String id : root.keySet()) {
			try {
				JsonObject rec = root.getAsJsonObject(id);
				Identifier model = Identifier.tryParse(rec.get("item").getAsString());
				if (model == null) continue;
				String skin = rec.has("skin") ? rec.get("skin").getAsString() : null;
				LOOKS.put(id, new Look(model, skin));
			} catch (Exception e) {
				com.ryn.skyryn.config.SkyLog.warn("item-fallback: запись " + id + " пропущена (" + e + ")");
			}
		}
		com.ryn.skyryn.config.SkyLog.d("Старый вид предметов: записей " + LOOKS.size());
	}

	public static Look of(ItemStack stack) {
		return LOOKS.get(skyblockId(stack));
	}

	public static Look byId(String id) {
		return id == null ? null : LOOKS.get(id);
	}

	public static String skyblockId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return "";
		try {
			return data.copyTag().getStringOr("id", "");
		} catch (Exception e) {
			return "";
		}
	}

	public static ResolvableProfile profile(ItemStack stack, Look look) {
		if (look == null || look.skin() == null) return null;
		String id = skyblockId(stack);
		if (id.isEmpty()) return null;
		if (PROFILES.containsKey(id)) return PROFILES.get(id);
		ResolvableProfile prof = null;
		try {
			GameProfile gp = new GameProfile(
					UUID.nameUUIDFromBytes(("skyryn:" + id).getBytes(StandardCharsets.UTF_8)), "skyryn");
			gp.properties().put("textures", new Property("textures", look.skin()));
			prof = ResolvableProfile.createResolved(gp);
		} catch (Throwable t) {
			com.ryn.skyryn.config.SkyLog.warn("Скин для " + id + " не собрался: " + t);
		}
		PROFILES.put(id, prof);
		return prof;
	}
}
