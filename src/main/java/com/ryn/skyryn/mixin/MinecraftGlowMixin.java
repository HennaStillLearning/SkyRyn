package com.ryn.skyryn.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ryn.skyryn.waypoint.MobHighlight;

@Mixin(Minecraft.class)
public class MinecraftGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void skyryn$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (MobHighlight.glowing(entity)) cir.setReturnValue(true);
	}
}
