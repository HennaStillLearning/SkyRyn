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

/**
 * Ванильный вид предметов, которым его есть куда вернуть.
 *
 * Свои вещи Hypixel задаёт компонентом item_model: у Hyperion там лежит не «алмазный
 * меч», а имя модели из серверного пака. Отсюда два разных перекоса, и лечим мы их
 * по-разному, по режиму {@link RynConfig#packMode}.
 *
 * Режим «не грузить». Пака нет, модель не находится, игра рисует штатную «модель
 * отсутствует» — чёрно-фиолетовый кубик. Подставляем модель самого предмета: у ванильных
 * имя модели совпадает с их id, так что выходит алмазный меч, а не кубик.
 *
 * Режим «гибрид». Пак загружен, и без вмешательства кастомными остаются вообще все
 * предметы — мечи, броня, инструменты. Возвращаем им ванильный вид, а кастомную модель
 * оставляем только тем, кто сделан НА БАЗЕ БУМАГИ: у бумаги своего вида нет, отнимешь
 * модель — останется стопка листов, по которой вещь не опознать. Отсюда и граница:
 * бумагу не трогаем, всё остальное приводим к ванили.
 *
 * Настоящий стак не трогаем: правим копию, которая живёт один кадр.
 */
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
		// Совпало — модель и так ванильная, подставлять нечего.
		if (vanilla.equals(id)) return;

		boolean replace = mode == RynConfig.PACK_OFF
				// Пака нет: чиним только то, что иначе стало бы фиолетовым кубиком.
				? this.modelManager.getItemModel(id) instanceof MissingItemModel
				// Пак есть: своё оставляем бумаге, остальным возвращаем ванильный вид.
				: stack.getItem() != Items.PAPER;
		if (!replace) return;

		ItemStack copy = stack.copy();
		copy.set(DataComponents.ITEM_MODEL, vanilla);
		// У копии модель уже ванильная, так что повторный заход выйдет по проверке выше.
		((ItemModelResolver) (Object) this).appendItemLayers(state, copy, ctx, level, owner, seed);
		ci.cancel();
	}
}
