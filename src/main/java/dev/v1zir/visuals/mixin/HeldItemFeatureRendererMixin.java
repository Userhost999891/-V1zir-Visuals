package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.v1zir.visuals.render.knife.KnifeRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Knives in third person: a player's held sword is drawn as the knife instead, at the hand transform vanilla has
 * just set up for the item (the player's own model in F5, and other players when the module shows them).
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
	@WrapOperation(method = "renderItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V"))
	private void v1zir$knife(ItemRenderState itemState, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, int outline,
							 Operation<Void> original, @Local(argsOnly = true) ArmedEntityRenderState state, @Local(argsOnly = true) ItemStack stack) {
		if (!KnifeRenderer.thirdPerson(state, stack, matrices, queue, light)) original.call(itemState, matrices, queue, light, overlay, outline);
	}
}
