package dev.v1zir.visuals.render.cosmetic;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A cape as cloth, simulated: a grid of points about a pixel apart hanging from the shoulders, moved by Verlet steps
 * under gravity, air drag and a light wind, held to its size by distance constraints (along, across, diagonally and
 * over every second point, so it folds softly instead of creasing) and kept off the back, the legs and the arms.
 * <p>
 * The points live in the wearer's body space (the cape mesh's space: pixels, x to the wearer's right, y up, z back,
 * the neck at the origin). Between frames they are carried over by how the body moved and turned in the world, so
 * they keep their place there: the cloth trails behind a run, swings out when the wearer turns, lifts in a fall and
 * settles when they stop. Air drag acts against the cloth's motion through the world, which is what lifts it when
 * running; gravity and the wind are turned into body space each frame. Written for V1zir.
 */
public final class CapeCloth {
	static final int COLS = 11;
	static final int ROWS = 17;
	static final int COUNT = COLS * ROWS;
	/** Rows of the collision profiles, a pixel apart down from the neck. */
	static final int FLOOR_ROWS = 24;
	/** Pixels of the model per block (the player model is drawn at 15/16). */
	static final float PX_PER_BLOCK = 16f / 0.9375f;

	private static final float GRAVITY = 150f;
	/** Air drag per second, against the cloth's speed through the world. */
	private static final float DRAG = 1.5f;
	private static final float STEP = 1f / 120f;
	private static final int MAX_STEPS = 5;
	private static final int ITERATIONS = 8;
	/** How far above the shoulders' level the cloth may lift, per row down it (a little past level at most). */
	private static final float LIFT_LIMIT = 0.18f;

	// Current and previous positions, body space.
	final float[] pos = new float[COUNT * 3];
	private final float[] prev = new float[COUNT * 3];
	private final float[] rest = new float[COUNT * 3];
	// Constraints: pairs of point indices, rest lengths and stiffness.
	private final int[] linkA;
	private final int[] linkB;
	private final float[] linkRest;
	private final float[] linkStiff;
	/**
	 * How far back (z) the cloth must stay at each row down from the neck: in the middle (torso and legs) and at the
	 * wearer's right and left sides (also the arms). Set each frame from the model's pose.
	 */
	final float[] floorMid = new float[FLOOR_ROWS];
	final float[] floorRight = new float[FLOOR_ROWS];
	final float[] floorLeft = new float[FLOOR_ROWS];
	/** With wings on: how far back the cloth may lift near the top (it stays under them), NaN without. */
	float ceiling = Float.NaN;

	private final Matrix4f lastToWorld = new Matrix4f();
	private final Matrix4f carry = new Matrix4f();
	private final Matrix4f toBody = new Matrix4f();
	private final Vector3f v = new Vector3f();
	private final Vector3f gravity = new Vector3f();
	private final Vector3f wind = new Vector3f();
	private double lastX;
	private double lastY;
	private double lastZ;
	private long lastNanos;
	private float lastStep = STEP;
	private float time;
	private boolean started;

	CapeCloth() {
		for (int r = 0; r < ROWS; r++) {
			float half = half(r);
			for (int c = 0; c < COLS; c++) {
				int i = (r * COLS + c) * 3;
				rest[i] = half * (2f * c / (COLS - 1) - 1f);
				rest[i + 1] = CosmeticModels.CAPE_TOP - r;
				rest[i + 2] = CosmeticModels.CAPE_Z;
			}
		}
		// Along (1 and 2 down), across (1 and 2 over, and the whole width), and both diagonals: a cape is stiffer
		// across than along, so it swings and curls down its length but does not crumple sideways.
		int n = (ROWS - 1) * COLS + ROWS * (COLS - 1) + (ROWS - 2) * COLS + ROWS * (COLS - 2) + ROWS + 2 * (ROWS - 1) * (COLS - 1);
		linkA = new int[n];
		linkB = new int[n];
		linkRest = new float[n];
		linkStiff = new float[n];
		int k = 0;
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				int i = r * COLS + c;
				if (r + 1 < ROWS) k = link(k, i, i + COLS, 1f);
				if (c + 1 < COLS) k = link(k, i, i + 1, 1f);
				if (r + 2 < ROWS) k = link(k, i, i + 2 * COLS, 0.75f);
				if (c + 2 < COLS) k = link(k, i, i + 2, 0.9f);
				if (c == 0) k = link(k, i, i + COLS - 1, 1f);
				if (r + 1 < ROWS && c + 1 < COLS) {
					k = link(k, i, i + COLS + 1, 0.5f);
					k = link(k, i + 1, i + COLS, 0.5f);
				}
			}
		}
		reset();
	}

	private int link(int k, int a, int b, float stiffness) {
		linkA[k] = a;
		linkB[k] = b;
		float dx = rest[a * 3] - rest[b * 3], dy = rest[a * 3 + 1] - rest[b * 3 + 1], dz = rest[a * 3 + 2] - rest[b * 3 + 2];
		linkRest[k] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		linkStiff[k] = stiffness;
		return k + 1;
	}

	/** The cape's half width at row s down from the shoulders: a little narrower than the arms at the top, flaring. */
	static float half(float s) {
		return CosmeticModels.CAPE_HALF_TOP + (CosmeticModels.CAPE_HALF_BOTTOM - CosmeticModels.CAPE_HALF_TOP) * s / (ROWS - 1);
	}

	/** Hanging straight, at rest. */
	void reset() {
		System.arraycopy(rest, 0, pos, 0, pos.length);
		System.arraycopy(rest, 0, prev, 0, prev.length);
		for (int i = 0; i < FLOOR_ROWS; i++) {
			floorMid[i] = -99f;
			floorRight[i] = -99f;
			floorLeft[i] = -99f;
		}
	}

	/**
	 * Advances the cloth to now (System.nanoTime). toWorld: body space to the wearer's world space (pixels of the
	 * model, world axes, the feet at the origin); x, y, z: where the wearer is in the world (blocks).
	 */
	void update(long now, Matrix4f toWorld, double x, double y, double z) {
		if (started && now == lastNanos) return;
		float dt = started ? (now - lastNanos) / 1.0e9f : 0f;
		double dx = (x - lastX) * PX_PER_BLOCK, dy = (y - lastY) * PX_PER_BLOCK, dz = (z - lastZ) * PX_PER_BLOCK;
		boolean jump = !started || dt > 0.5f || dx * dx + dy * dy + dz * dz > 80.0 * 80.0;
		lastNanos = now;
		lastX = x;
		lastY = y;
		lastZ = z;
		if (jump) {
			// First seen, back after a while, or teleported: hang at rest.
			started = true;
			reset();
			lastToWorld.set(toWorld);
			return;
		}
		// Keep the points where they were in the world: from the old body space to the world, back by the move,
		// then into the new body space.
		toWorld.invert(toBody);
		carry.set(toBody).translate((float) -dx, (float) -dy, (float) -dz).mul(lastToWorld);
		if (turnedTooFar(carry)) {
			reset();
			lastToWorld.set(toWorld);
			return;
		}
		for (int i = 0; i < COUNT; i++) {
			int o = i * 3;
			carry.transformPosition(v.set(pos[o], pos[o + 1], pos[o + 2]));
			pos[o] = v.x;
			pos[o + 1] = v.y;
			pos[o + 2] = v.z;
			carry.transformPosition(v.set(prev[o], prev[o + 1], prev[o + 2]));
			prev[o] = v.x;
			prev[o + 1] = v.y;
			prev[o + 2] = v.z;
		}
		lastToWorld.set(toWorld);
		// Gravity and a light, slowly changing wind, from the world into body space.
		time += dt;
		toBody.transformDirection(gravity.set(0f, -GRAVITY, 0f));
		float gust = 0.6f + 0.4f * (float) Math.sin(time * 0.37f);
		toBody.transformDirection(wind.set((float) Math.sin(time * 0.9f) * 9f + (float) Math.sin(time * 2.3f) * 4f, 0f,
			(float) Math.cos(time * 0.7f) * 7f + 3f).mul(gust));
		int steps = Math.max(1, Math.min(MAX_STEPS, (int) Math.ceil(dt / STEP)));
		float h = Math.min(dt, MAX_STEPS * STEP) / steps;
		for (int s = 0; s < steps; s++) step(h);
	}

	/** Whether the body turned more than about 100 degrees between frames (another view of the wearer): start over. */
	private static boolean turnedTooFar(Matrix4f m) {
		// The trace of the rotation part is 1 + 2 cos(angle).
		float trace = m.m00() + m.m11() + m.m22();
		return trace < 1f + 2f * (float) Math.cos(Math.toRadians(100.0));
	}

	private void step(float h) {
		if (h <= 0f) return;
		// Verlet with the time corrected for a changing step, damped by air drag against the motion through the world.
		float ratio = h / lastStep;
		float keep = (float) Math.exp(-DRAG * h);
		float ax = gravity.x + wind.x, ay = gravity.y + wind.y, az = gravity.z + wind.z;
		float hh = h * (h + lastStep) * 0.5f;
		for (int i = COLS; i < COUNT; i++) {
			int o = i * 3;
			float px = pos[o], py = pos[o + 1], pz = pos[o + 2];
			pos[o] = px + (px - prev[o]) * ratio * keep + ax * hh;
			pos[o + 1] = py + (py - prev[o + 1]) * ratio * keep + ay * hh;
			pos[o + 2] = pz + (pz - prev[o + 2]) * ratio * keep + az * hh;
			prev[o] = px;
			prev[o + 1] = py;
			prev[o + 2] = pz;
		}
		lastStep = h;
		for (int it = 0; it < ITERATIONS; it++) {
			for (int k = 0; k < linkA.length; k++) satisfy(k);
			pin();
			collide();
		}
	}

	private void satisfy(int k) {
		int a = linkA[k] * 3, b = linkB[k] * 3;
		float dx = pos[b] - pos[a], dy = pos[b + 1] - pos[a + 1], dz = pos[b + 2] - pos[a + 2];
		float d2 = dx * dx + dy * dy + dz * dz;
		if (d2 < 1e-10f) return;
		float d = (float) Math.sqrt(d2);
		float diff = (d - linkRest[k]) / d * linkStiff[k];
		// The top row is pinned: a link to it moves only the other end.
		boolean pinA = linkA[k] < COLS, pinB = linkB[k] < COLS;
		if (pinA && pinB) return;
		float wa = pinA ? 0f : pinB ? 1f : 0.5f;
		float wb = pinB ? 0f : pinA ? 1f : 0.5f;
		pos[a] += dx * diff * wa;
		pos[a + 1] += dy * diff * wa;
		pos[a + 2] += dz * diff * wa;
		pos[b] -= dx * diff * wb;
		pos[b + 1] -= dy * diff * wb;
		pos[b + 2] -= dz * diff * wb;
	}

	private void pin() {
		System.arraycopy(rest, 0, pos, 0, COLS * 3);
	}

	/** Off the back, the legs and the arms; never above the shoulders. */
	private void collide() {
		for (int i = COLS; i < COUNT; i++) {
			int o = i * 3;
			float x = pos[o], y = pos[o + 1];
			int r = i / COLS;
			float limit = CosmeticModels.CAPE_TOP - 0.4f + LIFT_LIMIT * r;
			if (y > limit) pos[o + 1] = y = limit;
			float down = Math.max(0f, -y);
			if (down > FLOOR_ROWS - 1.001f) continue;
			int row = (int) down;
			float f = down - row;
			if (!Float.isNaN(ceiling)) {
				float max = ceiling + Math.max(0f, down - 10f) * 0.6f;
				if (pos[o + 2] > max) pos[o + 2] = max;
			}
			float[] floor = x > 3.6f ? floorRight : x < -3.6f ? floorLeft : floorMid;
			float min = floor[row] + (floor[row + 1] - floor[row]) * f;
			if (Math.abs(x) <= 5.6f && pos[o + 2] < min) pos[o + 2] = min;
		}
	}

	/**
	 * The cloth's surface at every grid point, for drawing: position, then the unit tangents across (x at rest) and
	 * up (y at rest) and the normal to the back (z at rest), 12 floats a point.
	 */
	void frames(float[] out) {
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				int i = r * COLS + c;
				int o = i * 12;
				int p = i * 3;
				out[o] = pos[p];
				out[o + 1] = pos[p + 1];
				out[o + 2] = pos[p + 2];
				int left = r * COLS + Math.max(0, c - 1), right = r * COLS + Math.min(COLS - 1, c + 1);
				int up = Math.max(0, r - 1) * COLS + c, down = Math.min(ROWS - 1, r + 1) * COLS + c;
				float tx = pos[right * 3] - pos[left * 3], ty = pos[right * 3 + 1] - pos[left * 3 + 1], tz = pos[right * 3 + 2] - pos[left * 3 + 2];
				float ux = pos[up * 3] - pos[down * 3], uy = pos[up * 3 + 1] - pos[down * 3 + 1], uz = pos[up * 3 + 2] - pos[down * 3 + 2];
				float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz), ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
				if (tl < 1e-6f || ul < 1e-6f) {
					tx = 1f;
					ty = tz = 0f;
					ux = uz = 0f;
					uy = 1f;
				} else {
					tx /= tl;
					ty /= tl;
					tz /= tl;
					ux /= ul;
					uy /= ul;
					uz /= ul;
				}
				// The normal to the back: across x up.
				float nx = ty * uz - tz * uy, ny = tz * ux - tx * uz, nz = tx * uy - ty * ux;
				float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (nl < 1e-6f) {
					nx = ny = 0f;
					nz = 1f;
				} else {
					nx /= nl;
					ny /= nl;
					nz /= nl;
				}
				// Up again, square to the other two, so the frame stays orthonormal on a sheared cell.
				ux = ny * tz - nz * ty;
				uy = nz * tx - nx * tz;
				uz = nx * ty - ny * tx;
				out[o + 3] = tx;
				out[o + 4] = ty;
				out[o + 5] = tz;
				out[o + 6] = ux;
				out[o + 7] = uy;
				out[o + 8] = uz;
				out[o + 9] = nx;
				out[o + 10] = ny;
				out[o + 11] = nz;
			}
		}
	}
}
