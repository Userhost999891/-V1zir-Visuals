package dev.v1zir.visuals.render.knife;

/**
 * One knife model: indexed triangles in centimetres (x along the knife from the butt to the tip, y towards the
 * spine, z across the thickness, the point the hand grips at the origin), split into parts that move on their own:
 * the blade of a folding knife, the two handles of a balisong. Built once per knife type and kept.
 */
public final class KnifeMesh {
	/** Floats per vertex: position, normal, pattern coordinates (blade plane, cm). */
	public static final int FLOATS = 8;

	// Materials; knife.glsl reads the same numbers.
	/** Blade flats: take the finish. */
	public static final int BLADE = 0;
	/** Primary bevel: takes the finish, coated finishes wear through to polished steel here. */
	public static final int BEVEL = 1;
	/** The honed cutting edge: a thin line of polished steel. */
	public static final int EDGE = 2;
	public static final int STEEL = 3;
	/** Blackened (oxide) steel. */
	public static final int DARK = 4;
	public static final int BRASS = 5;
	public static final int WOOD = 6;
	/** G10, polymer and rubber; tinted by the vertex colour. */
	public static final int POLYMER = 7;
	/** Paracord wrap. */
	public static final int CORD = 8;
	/** Stacked leather washers. */
	public static final int LEATHER = 9;

	final float[] data;
	final int[] material;
	final int[] color;
	final int[] indices;
	final Part[] parts;

	KnifeMesh(float[] data, int[] material, int[] color, int[] indices, Part[] parts) {
		this.data = data;
		this.material = material;
		this.color = color;
		this.indices = indices;
		this.parts = parts;
	}

	public int vertexCount() {
		return material.length;
	}

	public int triangleCount() {
		return indices.length / 3;
	}

	public Part[] parts() {
		return parts;
	}

	/**
	 * A range of the index list that moves as one piece, turning around (pivotX, pivotY) about the z axis:
	 * KnifePose gives each part an angle.
	 */
	public record Part(int firstIndex, int indexCount, float pivotX, float pivotY) {
	}
}
