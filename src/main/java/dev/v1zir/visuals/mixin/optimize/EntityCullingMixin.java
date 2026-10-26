package dev.v1zir.visuals.mixin.optimize;

import dev.v1zir.visuals.optimize.DistanceCulling;
import dev.v1zir.visuals.optimize.OcclusionCuller;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.ItemFrameEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity culling and the entity half of distance culling. shouldRender runs before a render state is built,
 * so a skipped entity costs nothing further; x, y and z are the camera position.
 */
@Mixin(EntityRenderManager.class)
public abstract class EntityCullingMixin {
	/** Unnamed far armour stands (named ones are holograms whose text must stay). */
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void v1zir$farArmorStand(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof ArmorStandEntity && !entity.hasCustomName() && DistanceCulling.armorStandHidden(entity.squaredDistanceTo(x, y, z))) {
			cir.setReturnValue(false);
		}
	}

	/** Only entities vanilla would draw (in view, in range) are tested, so off-screen ones cost the culler nothing. */
	@Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
	private void v1zir$occluded(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && OcclusionCuller.hidden(entity, x, y, z)) cir.setReturnValue(false);
	}

	/** A far item frame keeps its frame (and map) but not the item model in it. */
	@Inject(
		method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/render/state/CameraRenderState;DDDLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
		at = @At("HEAD")
	)
	private void v1zir$framedItem(EntityRenderState state, CameraRenderState camera, double x, double y, double z, MatrixStack matrices,
								  OrderedRenderCommandQueue queue, CallbackInfo ci) {
		if (state instanceof ItemFrameEntityRenderState frame && DistanceCulling.framedItemHidden(frame.squaredDistanceToCamera)) {
			frame.itemRenderState.clear();
		}
	}
}
