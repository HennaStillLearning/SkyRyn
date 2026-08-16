package com.ryn.skyryn.mixin;

import com.ryn.skyryn.config.FilteredServerPack;
import com.ryn.skyryn.config.RynConfig;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Pack.class)
public abstract class ServerPackFilterMixin {
	@Inject(method = "open", at = @At("RETURN"), cancellable = true)
	private void skyryn$filterServerPack(CallbackInfoReturnable<PackResources> cir) {
		if (RynConfig.packMode != RynConfig.PACK_HYBRID) return;
		Pack self = (Pack) (Object) this;
		if (!self.getId().startsWith("server/")) return;
		PackResources res = cir.getReturnValue();
		if (res == null || res instanceof FilteredServerPack) return;
		FilteredServerPack.forget();
		cir.setReturnValue(new FilteredServerPack(res));
	}
}
