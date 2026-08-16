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

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Inject(method = "lambda$addLateDebugPass$0", at = @At("TAIL"))
	private void skyryn$waypoints(GpuBufferSlice fog, ResourceHandle<?> main,
								  CameraRenderState camera, Matrix4fc projection, CallbackInfo ci,
								  @Local PoseStack poseStack, @Local MultiBufferSource.BufferSource buffer) {
		Waypoints.captureFrame(camera.pos);
		Waypoints.renderWorld(poseStack, buffer, camera.pos);
		com.ryn.skyryn.waypoint.MobHighlight.renderWorld(poseStack, buffer, camera.pos);
		com.ryn.skyryn.hud.CritterTimer.captureFrame(camera.pos);
		com.ryn.skyryn.hud.CritterTimer.renderWorld(poseStack, buffer, camera.pos);
		com.ryn.skyryn.hud.SafariTracker.captureFrame(camera.pos);
		com.ryn.skyryn.hud.SafariTracker.renderWorld(poseStack, buffer, camera.pos);
		com.ryn.skyryn.waypoint.SafariBiomes.captureFrame(camera.pos);
		com.ryn.skyryn.waypoint.SafariBiomes.renderWorld(poseStack, buffer, camera.pos);
	}
}
