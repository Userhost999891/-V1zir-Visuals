package dev.v1zir.visuals.render.world;

/** Implemented by modules that draw 3D effects after the world. */
public interface WorldEffect {
	void render3D(float tickDelta);
}
