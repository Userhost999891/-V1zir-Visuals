package dev.v1zir.visuals.render;

/**
 * Where Rain Effects' puddles lie, on the CPU: the same noise and water level as rPuddleShape and
 * rPuddleLevel in reflect.glsl, so a splash knows whether it lands in standing water. The noise
 * cells are hashed with integers (pcg2d) and interpolated in single precision like the shader, so
 * both agree everywhere but on the last fraction of a shoreline.
 */
public final class Puddles {
	private static final int M = 1664525;
	private static final int C = 1013904223;

	private Puddles() {
	}

	/**
	 * How much water stands at world position (x, z): 0 on dry ground, 1 inside a puddle.
	 * {@code wetness} is the shader's (RainEffectsModule#wetness); the camera position picks the same
	 * 4096-block wrap of the noise as the shader.
	 */
	public static float at(double x, double z, double camX, double camZ, float wetness) {
		float px = (float) (x - 4096.0 * Math.floorDiv((long) Math.floor(camX), 4096L));
		float pz = (float) (z - 4096.0 * Math.floorDiv((long) Math.floor(camZ), 4096L));
		float shape = 0.68f * noise(px * 0.3f, pz * 0.3f) + 0.24f * noise(px * 0.9f + 17.3f, pz * 0.9f + 17.3f)
			+ 0.08f * noise(px * 2.6f + 5.7f, pz * 2.6f + 5.7f);
		float level = 0.8f + (0.67f - 0.8f) * clamp((wetness - 0.25f) / 0.75f) - 0.07f * Math.max(wetness - 1f, 0f);
		return smoothstep(level, level + 0.012f, shape);
	}

	/** rCellNoise: value noise with smooth interpolation between the hashed corners of each cell. */
	private static float noise(float x, float y) {
		float ix = (float) Math.floor(x);
		float iy = (float) Math.floor(y);
		float fx = x - ix;
		float fy = y - iy;
		fx = fx * fx * (3f - 2f * fx);
		fy = fy * fy * (3f - 2f * fy);
		int cx = (int) ix + 8192;
		int cy = (int) iy + 8192;
		float a = hash(cx, cy);
		float b = hash(cx + 1, cy);
		float d = hash(cx, cy + 1);
		float e = hash(cx + 1, cy + 1);
		float bottom = a + (b - a) * fx;
		float top = d + (e - d) * fx;
		return bottom + (top - bottom) * fy;
	}

	/** rCellHash: pcg2d on unsigned 32-bit integers (Java's int arithmetic wraps the same way). */
	private static float hash(int x, int y) {
		x = x * M + C;
		y = y * M + C;
		x += y * M;
		y += x * M;
		x ^= x >>> 16;
		y ^= y >>> 16;
		x += y * M;
		y += x * M;
		x ^= x >>> 16;
		y ^= y >>> 16;
		return ((x ^ y) >>> 8) / 16777216f;
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private static float smoothstep(float edge0, float edge1, float v) {
		float t = clamp((v - edge0) / (edge1 - edge0));
		return t * t * (3f - 2f * t);
	}
}
