package com.ryn.skyryn.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Доступ к геометрии окна контейнера (leftPos/topPos/imageWidth/imageHeight) —
 * чтобы прикреплять свёрнутые иконки калькулятора/бокса точно к краям /hb.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos") int skyryn$leftPos();
	@Accessor("topPos") int skyryn$topPos();
	@Accessor("imageWidth") int skyryn$imageWidth();
	@Accessor("imageHeight") int skyryn$imageHeight();
}
