package dev.v1zir.visuals.render.world;

/**
 * What the effects decided about an entity for one frame, carried on its render state from the
 * moment vanilla extracts it until it is drawn (EntityRenderStateMixin, EntityRenderManagerMixin).
 */
public interface MarkedRenderState {
	/** Chams colour (ARGB) of the entity this frame, 0 for none. */
	int v1zir$chams();

	void v1zir$setChams(int argb);

	/** The entity is not drawn at all this frame (Kill Effects shattered its body). */
	boolean v1zir$hidden();

	void v1zir$setHidden(boolean hidden);
}
