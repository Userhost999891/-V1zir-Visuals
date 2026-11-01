package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.render.world.MarkedRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kill Effects: a body that shattered is not drawn at all (no model, armour or held items). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	private static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V";

	@Inject(method = RENDER, at = @At("HEAD"), cancellable = true)
	private void v1zir$hideShattered(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera,
									 CallbackInfo ci) {
		if (((MarkedRenderState) state).v1zir$hidden()) ci.cancel();
	}
}
