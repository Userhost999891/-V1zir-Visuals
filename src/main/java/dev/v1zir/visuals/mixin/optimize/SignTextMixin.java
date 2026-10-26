package dev.v1zir.visuals.mixin.optimize;

import dev.v1zir.visuals.optimize.DistanceCulling;
import net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.SignBlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Distance culling: far signs (and hanging signs) keep their board, not the text nobody could read. */
@Mixin(AbstractSignBlockEntityRenderer.class)
public abstract class SignTextMixin {
	@Inject(
		method = "renderText(Lnet/minecraft/client/render/block/entity/state/SignBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Z)V",
		at = @At("HEAD"), cancellable = true
	)
	private void v1zir$farText(SignBlockEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, boolean front, CallbackInfo ci) {
		if (DistanceCulling.signTextHidden()) ci.cancel();
	}
}
