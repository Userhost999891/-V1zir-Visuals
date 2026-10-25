package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.impl.visuals.NoRenderModule;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Render: far armour stands (often server holograms) are culled before a render state is built for them. */
@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
	/** x, y and z are the camera position the world is rendered from. */
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void v1zir$farArmorStands(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (!(entity instanceof ArmorStandEntity)) return;
		NoRenderModule noRender = NoRenderModule.active();
		if (noRender != null && noRender.hidesArmorStand(entity.squaredDistanceTo(x, y, z))) cir.setReturnValue(false);
	}
}
