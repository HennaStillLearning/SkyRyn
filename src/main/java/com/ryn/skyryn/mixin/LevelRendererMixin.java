package com.ryn.skyryn.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.ryn.skyryn.waypoint.Waypoints;

/**
 * Рисуем ЛУЧ метки там же, где игра рисует debug-гизмо.
 *
 * Два твёрдых факта из логов сложились:
 *   1. lambda$addLateDebugPass$0 ВЫЗЫВАЕТСЯ даже при Sodium (renderWorld
 *      логировался). Fabric COLLECT_SUBMITS при Sodium НЕ вызывается вовсе.
 *   2. Гизмо в этой лямбде — это ПРИМИТИВЫ (боксы, линии), и они видны.
 *      Значит луч (тоже линия-примитив) в той же точке нарисуется.
 *
 * Раньше в этой точке я рисовал ТЕКСТ (он особый и не проходил), а луч
 * рисовал в мёртвой Fabric-фазе. Комбинацию «миксин + луч» не пробовал —
 * вот она.
 *
 * @Local ловит живой BufferSource лямбды, CameraRenderState в аргументе даёт
 * позицию камеры. Waypoints рисует линии в этот буфер и флашит сам.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

	@Inject(method = "lambda$addLateDebugPass$0", at = @At("TAIL"))
	private void skyryn$waypoints(GpuBufferSlice fog, ResourceHandle<?> main,
								  CameraRenderState camera, Matrix4fc projection, CallbackInfo ci,
								  @Local PoseStack poseStack, @Local MultiBufferSource.BufferSource buffer) {
		// Кадр для проекции подписей на HUD (view×projection берём с камеры внутри).
		Waypoints.captureFrame(camera.pos);
		Waypoints.renderWorld(poseStack, buffer, camera.pos);
		// Рамка на блоке под выпавшим шардом. Мобов обводит контуром сама игра.
		com.ryn.skyryn.waypoint.MobHighlight.renderWorld(poseStack, buffer, camera.pos);
		// Honey tracker: метки деревьев (+ кадр для их подписей).
		com.ryn.skyryn.hud.CritterTimer.captureFrame(camera.pos);
		com.ryn.skyryn.hud.CritterTimer.renderWorld(poseStack, buffer, camera.pos);
		// Critter Safari: голограмма Wumpa + вейпоинты квест-предметов.
		com.ryn.skyryn.hud.SafariTracker.captureFrame(camera.pos);
		com.ryn.skyryn.hud.SafariTracker.renderWorld(poseStack, buffer, camera.pos);
		// Critter Safari: контур-стены записанных биомов (/srbiome).
		com.ryn.skyryn.waypoint.SafariBiomes.captureFrame(camera.pos);
		com.ryn.skyryn.waypoint.SafariBiomes.renderWorld(poseStack, buffer, camera.pos);
	}
}
