package com.ryn.skyryn.dev;

import com.ryn.skyryn.data.VanillaLook;

public final class LookProbe {
	private LookProbe() { }

	public static void register() {
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
			if (!command.trim().equals("srlook")) return true;
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			mc.execute(() -> probe(mc));
			return false;
		});
	}

	private static void probe(net.minecraft.client.Minecraft mc) {
		if (mc.player == null) return;
		net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
		String id = VanillaLook.skyblockId(held);
		VanillaLook.Look look = VanillaLook.byId(id);
		StringBuilder sb = new StringBuilder("§e[SkyRyn]§r таблица: " + VanillaLook.size() + " записей");
		sb.append(", режим пака: ").append(com.ryn.skyryn.config.RynConfig.packMode);
		sb.append("§r | id=").append(id.isEmpty() ? "§c—" : "§a" + id);
		sb.append("§r | база=").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()));
		net.minecraft.resources.Identifier model = held.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
		sb.append("§r | модель=").append(model == null ? "нет" : model);
		sb.append("§r | запись: ").append(look == null ? "§cне найдена"
				: "§a" + look.model() + (look.skin() == null ? "" : " +скин"));
		mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(sb.toString()));
	}
}
