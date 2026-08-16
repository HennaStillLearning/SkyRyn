package com.ryn.skyryn.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ryn.skyryn.hud.CritterTimer;

@Mixin(Gui.class)
public class GuiSubtitleMixin {
	@Inject(method = "setSubtitle", at = @At("HEAD"))
	private void skyryn$subtitle(Component subtitle, CallbackInfo ci) {
		if (subtitle != null) CritterTimer.onSubtitle(subtitle.getString());
	}

	@Inject(method = "setTitle", at = @At("HEAD"), require = 0)
	private void skyryn$title(Component title, CallbackInfo ci) {
		if (title != null) CritterTimer.onSubtitle(title.getString());
	}

	@Inject(method = "setOverlayMessage", at = @At("HEAD"), require = 0)
	private void skyryn$overlay(Component message, boolean animate, CallbackInfo ci) {
		if (message != null) CritterTimer.onSubtitle(message.getString());
	}
}
