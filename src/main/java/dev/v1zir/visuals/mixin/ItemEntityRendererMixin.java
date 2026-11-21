package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.v1zir.visuals.module.impl.world.ItemPhysicsModule;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Physics. Only method heads are hooked: render remembers the pose it starts from, and the
 * first static stack draw it makes swaps vanilla's bob and spin for the physics pose. Whichever of
 * the stack draw overloads render calls, the first one entered applies it once.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	private static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V";
	private static final String STACK = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;)V";
	private static final String STACK_BOX = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/Box;)V";
	private static final String RENDER_STACK = "renderStack(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;)V";

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
	private void v1zir$updatePhysics(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
		ItemPhysicsModule.update(entity, state);
	}

	@Inject(method = RENDER, at = @At("HEAD"))
	private void v1zir$beginPhysics(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
									CameraRenderState camera, CallbackInfo ci) {
		ItemPhysicsModule.begin(state, matrices);
	}

	@Inject(method = RENDER, at = @At("RETURN"))
	private void v1zir$endPhysics(CallbackInfo ci) {
		ItemPhysicsModule.end();
	}

	@Inject(method = {STACK, STACK_BOX, RENDER_STACK}, at = @At("HEAD"))
	private static void v1zir$applyPhysics(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices,
										   @Local(argsOnly = true) ItemStackEntityRenderState state) {
		ItemPhysicsModule.apply(matrices, state);
	}
}
