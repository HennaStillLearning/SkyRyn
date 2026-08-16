package com.ryn.skyryn.mixin;

import com.ryn.skyryn.config.RynConfig;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class MissingItemModelMixin {
	@Shadow @Final private ModelManager modelManager;

	@Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
	private void skyryn$vanillaFallback(ItemStackRenderState state, ItemStack stack, ItemDisplayContext ctx,
										Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		int mode = RynConfig.packMode;
		Identifier id = stack.get(DataComponents.ITEM_MODEL);
		if (id == null) return;
		Identifier vanilla = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (vanilla.equals(id)) return;

		boolean replace = mode == RynConfig.PACK_OFF
				? this.modelManager.getItemModel(id) instanceof MissingItemModel
				: stack.getItem() != Items.PAPER;
		if (!replace) return;

		ItemStack copy = stack.copy();
		copy.set(DataComponents.ITEM_MODEL, vanilla);
		((ItemModelResolver) (Object) this).appendItemLayers(state, copy, ctx, level, owner, seed);
		ci.cancel();
	}
}
