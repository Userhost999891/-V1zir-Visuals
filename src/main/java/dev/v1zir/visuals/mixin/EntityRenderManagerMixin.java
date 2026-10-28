package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.impl.visuals.EntityGlowModule;
import dev.v1zir.visuals.module.impl.visuals.NoRenderModule;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No Render: far armour stands (often server holograms) are culled before a render state is built
 * for them. Entity Glow: the finished render state of an outlined entity takes its colour.
 */
@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
	/** x, y and z are the camera position the world is rendered from. */
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void v1zir$farArmorStands(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (!(entity instanceof ArmorStandEntity)) return;
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.hidesArmorStand(entity.squaredDistanceTo(x, y, z))) cir.setReturnValue(false);
	}

	/** Runs after every renderer's updateRenderState, so nothing overwrites the colour afterwards. */
	@Inject(method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;", at = @At("RETURN"))
	private void v1zir$glowColor(Entity entity, float tickProgress, CallbackInfoReturnable<EntityRenderState> cir) {
		EntityGlowModule glow = EntityGlowModule.active();
		if (glow == null) return;
		int color = glow.color(entity);
		if (color != 0) cir.getReturnValue().outlineColor = color;
	}
}
