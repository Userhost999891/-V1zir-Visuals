package dev.v1zir.visuals.render.world;

import net.minecraft.client.render.VertexConsumer;

import java.util.Arrays;

/**
 * Collects the quads a model renders: four corners and the unit face normal each, in the space of
 * the matrices it is rendered with (camera relative for entities in the world). Colour, texture and
 * light are dropped. The buffer grows when needed and is reused, so once it is large enough
 * capturing allocates nothing.
 */
public final class ModelCapture implements VertexConsumer {
	/** Floats per quad: corners 0 to 3 (x, y, z each), then the unit normal. */
	public static final int STRIDE = 15;

	private float[] quads = new float[STRIDE * 256];
	private int count;
	private int corner;
	private float normalX, normalY, normalZ;

	/** Forgets everything captured so far. */
	public void clear() {
		count = 0;
		corner = 0;
	}

	/** Quads captured since the last clear. */
	public int count() {
		return count;
	}

	/** The captured quads, STRIDE floats each; only the first count() are valid. */
	public float[] quads() {
		return quads;
	}

	@Override
	public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
		normal(nx, ny, nz);
		vertex(x, y, z);
	}

	@Override
	public VertexConsumer vertex(float x, float y, float z) {
		if (corner == 0 && (count + 1) * STRIDE > quads.length) quads = Arrays.copyOf(quads, quads.length * 2);
		int o = count * STRIDE + corner * 3;
		quads[o] = x;
		quads[o + 1] = y;
		quads[o + 2] = z;
		if (++corner == 4) {
			corner = 0;
			float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
			// Degenerate faces (zero sized cuboid sides) are dropped.
			if (length > 1e-4f) {
				int n = count * STRIDE + 12;
				quads[n] = normalX / length;
				quads[n + 1] = normalY / length;
				quads[n + 2] = normalZ / length;
				count++;
			}
		}
		return this;
	}

	@Override
	public VertexConsumer normal(float x, float y, float z) {
		normalX = x;
		normalY = y;
		normalZ = z;
		return this;
	}

	@Override
	public VertexConsumer color(int red, int green, int blue, int alpha) {
		return this;
	}

	@Override
	public VertexConsumer color(int argb) {
		return this;
	}

	@Override
	public VertexConsumer texture(float u, float v) {
		return this;
	}

	@Override
	public VertexConsumer overlay(int u, int v) {
		return this;
	}

	@Override
	public VertexConsumer light(int u, int v) {
		return this;
	}

	@Override
	public VertexConsumer lineWidth(float width) {
		return this;
	}
}
