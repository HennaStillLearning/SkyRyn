package com.ryn.skyryn.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ryn.skyryn.waypoint.MobHighlight;

/**
 * Контур вокруг выбранных мобов. Игра сама обводит светящихся существ по силуэту —
 * мы лишь говорим ей, что наш моб светится. Цвет обводки берётся из
 * {@link EntityTeamColorMixin}.
 *
 * Светится только моб, до которого есть прямая видимость: ванильное свечение
 * рисуется поверх блоков, и без этой проверки вышел бы воллхак.
 */
@Mixin(Minecraft.class)
public class MinecraftGlowMixin {

	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void skyryn$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (MobHighlight.glowing(entity)) cir.setReturnValue(true);
	}
}
