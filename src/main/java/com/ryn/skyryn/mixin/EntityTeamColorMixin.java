package com.ryn.skyryn.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ryn.skyryn.waypoint.MobHighlight;

/**
 * Цвет контура подсвеченного моба. Игра красит обводку цветом команды существа —
 * подменяем его на выбранный в настройках.
 */
@Mixin(Entity.class)
public class EntityTeamColorMixin {

	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void skyryn$outlineColor(CallbackInfoReturnable<Integer> cir) {
		int c = MobHighlight.outlineColor((Entity) (Object) this);
		if (c != 0) cir.setReturnValue(c & 0xFFFFFF);
	}
}
