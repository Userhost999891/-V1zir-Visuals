package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.module.impl.world.ItemPhysicsModule;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the Item Physics tilt of the frame from updateRenderState to render. */
@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsModule.Posed {
	@Unique
	private float v1zir$tilt = Float.NaN;

	@Override
	public float v1zir$tilt() {
		return v1zir$tilt;
	}

	@Override
	public void v1zir$setTilt(float tilt) {
		v1zir$tilt = tilt;
	}
}
