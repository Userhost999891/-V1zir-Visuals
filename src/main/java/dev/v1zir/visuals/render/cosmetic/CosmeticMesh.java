package dev.v1zir.visuals.render.cosmetic;

/**
 * One cosmetic model: indexed triangles in pixels (1/16 block) with x to the wearer's right, y up and -z forward,
 * split into parts that move on their own (a wing, a pet's head or tail), each turning about its own pivot. Built
 * once when first needed and kept.
 */
public final class CosmeticMesh {
	/** Floats per vertex: position, normal, pattern coordinates. */
	public static final int FLOATS = 8;

	// Materials; cosmetic.glsl reads the same numbers.
	public static final int MATTE = 0;
	public static final int SATIN = 1;
	public static final int GOLD = 2;
	public static final int GEM = 3;
	/** Emissive: shines with its own colour, unaffected by the world's light. */
	public static final int GLOW = 4;
	public static final int FEATHER = 5;
	public static final int MEMBRANE = 6;
	public static final int IRIDESCENT = 7;
	/** Emissive flowing energy (animated). */
	public static final int ENERGY = 8;
	/** Night sky with drifting stars (animated). */
	public static final int GALAXY = 9;
	public static final int SILK = 10;
	public static final int KNIT = 11;
	public static final int FUR = 12;
	public static final int EYE = 13;
	public static final int LEAF = 14;
	public static final int BONE = 15;
	public static final int SILVER = 16;
	/** Emissive swirl of light (animated). */
	public static final int SWIRL = 17;

	final float[] data;
	final int[] color;
	final int[] material;
	final int[] indices;
	final Part[] parts;
	/** Bounds over all vertices: min x, y, z, max x, y, z. */
	final float[] bounds;

	CosmeticMesh(float[] data, int[] color, int[] material, int[] indices, Part[] parts, float[] bounds) {
		this.data = data;
		this.color = color;
		this.material = material;
		this.indices = indices;
		this.parts = parts;
		this.bounds = bounds;
	}

	public Part[] parts() {
		return parts;
	}

	public int triangleCount() {
		return indices.length / 3;
	}

	/**
	 * A range of the index list that moves as one piece about (pivotX, pivotY, pivotZ); glow parts are drawn
	 * blended over the scene (translucent wings, a halo's shine) after the solid ones.
	 */
	public record Part(int firstIndex, int indexCount, float pivotX, float pivotY, float pivotZ, boolean glow) {
	}
}
