package dev.v1zir.visuals.render.world;

import dev.v1zir.visuals.util.ColorUtil;

import java.util.function.IntUnaryOperator;

/** Helpers that turn simple shapes into glowing quads. */
public final class Draw3D {
	private Draw3D() {
	}

	/** Flat ring lying on the ground; colorAt receives the segment index (0..segments). */
	public static void flatRing(WorldEffectRenderer.Mesh mesh, double cx, double y, double cz, double radius, double width,
								int segments, IntUnaryOperator colorAt) {
		double inner = Math.max(0, radius - width);
		double outer = radius + width;
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
			int col0 = colorAt.applyAsInt(i);
			int col1 = colorAt.applyAsInt(i + 1);
			mesh.vertex(cx + c0 * inner, y, cz + s0 * inner, -1f, 0f, col0);
			mesh.vertex(cx + c0 * outer, y, cz + s0 * outer, 1f, 0f, col0);
			mesh.vertex(cx + c1 * outer, y, cz + s1 * outer, 1f, 1f, col1);
			mesh.vertex(cx + c1 * inner, y, cz + s1 * inner, -1f, 1f, col1);
		}
	}

	/** Vertical glowing band around a column (target ESP ring). */
	public static void bandRing(WorldEffectRenderer.Mesh mesh, double cx, double y, double cz, double radius, double halfHeight,
								int segments, IntUnaryOperator colorAt) {
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			double x0 = cx + Math.cos(a0) * radius, z0 = cz + Math.sin(a0) * radius;
			double x1 = cx + Math.cos(a1) * radius, z1 = cz + Math.sin(a1) * radius;
			int col0 = colorAt.applyAsInt(i);
			int col1 = colorAt.applyAsInt(i + 1);
			mesh.vertex(x0, y - halfHeight, z0, -1f, 0f, col0);
			mesh.vertex(x0, y + halfHeight, z0, 1f, 0f, col0);
			mesh.vertex(x1, y + halfHeight, z1, 1f, 1f, col1);
			mesh.vertex(x1, y - halfHeight, z1, -1f, 1f, col1);
		}
	}

	/** Camera facing glowing line between two points. */
	public static void line(WorldEffectRenderer.Mesh mesh, double x0, double y0, double z0, double x1, double y1, double z1,
							float width, int color0, int color1) {
		// Perpendicular to both the line and the view direction.
		double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
		double vx = (x0 + x1) * 0.5 - WorldEffectRenderer.camX;
		double vy = (y0 + y1) * 0.5 - WorldEffectRenderer.camY;
		double vz = (z0 + z1) * 0.5 - WorldEffectRenderer.camZ;
		double px = dy * vz - dz * vy;
		double py = dz * vx - dx * vz;
		double pz = dx * vy - dy * vx;
		double len = Math.sqrt(px * px + py * py + pz * pz);
		if (len < 1e-6) return;
		px = px / len * width;
		py = py / len * width;
		pz = pz / len * width;
		mesh.vertex(x0 - px, y0 - py, z0 - pz, -1f, 0f, color0);
		mesh.vertex(x0 + px, y0 + py, z0 + pz, 1f, 0f, color0);
		mesh.vertex(x1 + px, y1 + py, z1 + pz, 1f, 1f, color1);
		mesh.vertex(x1 - px, y1 - py, z1 - pz, -1f, 1f, color1);
	}

	/**
	 * 0 within {@code near} blocks of the camera, 1 from {@code far} on (smooth in between). Effects
	 * fade out in front of the eyes, where their quads would otherwise fill the screen.
	 */
	public static float cameraFade(double x, double y, double z, float near, float far) {
		double dx = x - WorldEffectRenderer.camX;
		double dy = y - WorldEffectRenderer.camY;
		double dz = z - WorldEffectRenderer.camZ;
		float t = (float) ((Math.sqrt(dx * dx + dy * dy + dz * dz) - near) / (far - near));
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3f - 2f * t);
	}

	/**
	 * Ring colours that wave between two colours around the circle. One instance is kept and set
	 * again every frame, so drawing rings allocates nothing.
	 */
	public static final class Wave implements IntUnaryOperator {
		private int from;
		private int to;
		private float phase;
		private int alpha;
		private int segments;

		/** alpha is 0..1; phase turns the wave around the ring (radians). */
		public Wave set(int from, int to, float phase, float alpha, int segments) {
			this.from = from;
			this.to = to;
			this.phase = phase;
			this.alpha = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
			this.segments = segments;
			return this;
		}

		@Override
		public int applyAsInt(int i) {
			float k = (float) (Math.sin(i * Math.PI * 2 / segments + phase) * 0.5 + 0.5);
			return ColorUtil.withAlpha(ColorUtil.lerp(from, to, k), alpha);
		}
	}
}
