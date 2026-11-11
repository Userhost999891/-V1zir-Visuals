package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.render.world.MarkedRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The per-frame marks of an entity (chams colour, hidden body), from extraction to drawing. */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements MarkedRenderState {
	@Unique
	private int v1zir$chams;
	@Unique
	private boolean v1zir$hidden;

	@Override
	public int v1zir$chams() {
		return v1zir$chams;
	}

	@Override
	public void v1zir$setChams(int argb) {
		v1zir$chams = argb;
	}

	@Override
	public boolean v1zir$hidden() {
		return v1zir$hidden;
	}

	@Override
	public void v1zir$setHidden(boolean hidden) {
		v1zir$hidden = hidden;
	}
}
