package com.ryn.skyryn.mixin;

import com.ryn.skyryn.config.RynConfig;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Обход серверного ресурспака (режим RynConfig.PACK_OFF).
 *
 * Hypixel на SkyBlock кикает, если ОТКЛОНИТЬ пак (ответ DECLINED). Но проверяет он
 * только статус-ответ клиента, а не то, применили ли мы пак на самом деле. Поэтому
 * отвечаем «принял» (ACCEPTED) и «успешно загрузил» (SUCCESSFULLY_LOADED) — сервер
 * доволен и пускает, — а сам обработчик отменяем: пак не качается и не применяется,
 * текстуры/модели остаются ванильными. Именно так работают «server pack bypass»-моды.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ResourcePackBypassMixin {

	@Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
	private void skyryn$bypassServerPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
		if (RynConfig.packMode != RynConfig.PACK_OFF) return;
		ClientCommonPacketListenerImpl self = (ClientCommonPacketListenerImpl) (Object) this;
		self.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
		self.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
		ci.cancel();
	}
}
