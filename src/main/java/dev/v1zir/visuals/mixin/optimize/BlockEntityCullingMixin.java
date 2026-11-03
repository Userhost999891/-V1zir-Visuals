package dev.v1zir.visuals.mixin.optimize;

import dev.v1zir.visuals.optimize.DistanceCulling;
import dev.v1zir.visuals.optimize.OcclusionCuller;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block entity culling: hidden block entities are not submitted, and while one is, its camera distance is
 * known to the sign text and banner pattern culling inside its renderer. Renderers that draw outside their
 * block (beacon beams, gateways, structure outlines) are never culled.
 */
@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityCullingMixin {
	@Shadow
	private Vec3d cameraPos;

	@Shadow
	public abstract <S extends BlockEntityRenderState> BlockEntityRenderer<?, S> getByRenderState(S state);

	@Inject(
		method = "render(Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		at = @At("HEAD"), cancellable = true
	)
	private void v1zir$cull(BlockEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
		BlockPos pos = state.pos;
		if (pos == null || cameraPos == null) return;
		BlockEntityRenderer<?, ?> renderer = getByRenderState(state);
		if (renderer != null && !renderer.rendersOutsideBoundingBox() && OcclusionCuller.hidden(pos, cameraPos.x, cameraPos.y, cameraPos.z)) {
			ci.cancel();
			return;
		}
		DistanceCulling.blockEntity(pos.getSquaredDistanceFromCenter(cameraPos.x, cameraPos.y, cameraPos.z));
	}

	@Inject(
		method = "render(Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		at = @At("RETURN")
	)
	private void v1zir$done(BlockEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
		DistanceCulling.blockEntity(-1.0);
	}
}
