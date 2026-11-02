package dev.v1zir.visuals.render.knife;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The first-person knife: plays the draw, inspect and attack clips on top of the resting pose, with breathing and a
 * lag behind the camera, and turns the result into matrices: the grip (where the fist holds the knife), the knife
 * (which can leave the grip: spins round a finger, tosses), its moving parts, and the arm, placed so that its fist
 * sits on the grip. Matrices are in view space (blocks) for a right hand; KnifeViewModel mirrors them for a left one.
 * <p>
 * Plain arithmetic on reused objects: no allocation per frame, no game state.
 */
public final class KnifeAnimator {
	/** Centimetres to blocks: the handle about the size of the blocky fist that holds it. */
	public static final float SCALE = 0.046f;
	private static final float DEG = (float) (Math.PI / 180.0);
	/** Inspect fades out over this when an attack or a switch interrupts it, seconds. */
	private static final float CANCEL = 0.16f;
	/** A new attack cross-fades from the previous one over this, seconds. */
	private static final float BLEND = 0.09f;

	public final Matrix4f grip = new Matrix4f();
	public final Matrix4f knife = new Matrix4f();
	/** The arm's model space: what PlayerEntityRenderer.renderRightArm expects on the matrix stack. */
	public final Matrix4f arm = new Matrix4f();
	/** Angle of every mesh part about its pivot, degrees. */
	public final float[] parts = new float[3];

	private final float[] sample = new float[KnifeClip.CHANNELS];
	private final float[] sum = new float[KnifeClip.CHANNELS];
	private final Matrix4f gripToArm = new Matrix4f();
	private final Matrix3f restTurn = new Matrix3f();
	private final Matrix4f restTurn4 = new Matrix4f();
	private final Vector3f elbow = new Vector3f();
	private final Vector3f palm = new Vector3f();
	private KnifeType type = KnifeType.KARAMBIT;
	/** Animation time in seconds, advanced by the speed setting. */
	private float clock;
	private float drawAt = -100f;
	private float inspectAt = -100f;
	private float inspectCancelAt = Float.MAX_VALUE;
	private KnifeClip attack;
	private float attackAt = -100f;
	private KnifeClip previous;
	private float previousAt = -100f;
	private float previousEnd = -100f;
	private boolean slash;
	// Lag behind the camera: a critically damped spring per axis.
	private float swayX;
	private float swayY;
	private float swayVX;
	private float swayVY;

	public KnifeType type() {
		return type;
	}

	/** Switching to the knife (or to another one): plays its draw and stops anything else. */
	public void draw(KnifeType type) {
		this.type = type;
		drawAt = clock;
		inspectAt = -100f;
		attack = null;
		previous = null;
	}

	/** Holds this knife at rest without drawing it (the Knives tab's preview), stopping anything that plays. */
	public void hold(KnifeType type) {
		this.type = type;
		drawAt = clock - 1000f;
		inspectAt = clock - 1000f;
		inspectCancelAt = Float.MAX_VALUE;
		attack = null;
		previous = null;
	}

	/** Starts the inspect, unless the knife is being drawn or swung. */
	public void inspect() {
		if (drawing() || attacking()) return;
		inspectAt = clock;
		inspectCancelAt = Float.MAX_VALUE;
	}

	public boolean inspecting() {
		return clock - inspectAt < KnifeClips.inspect(type).duration && clock < inspectCancelAt + CANCEL;
	}

	private boolean drawing() {
		return clock - drawAt < KnifeClips.draw(type).duration;
	}

	private boolean attacking() {
		return attack != null && clock - attackAt < attack.duration;
	}

	/**
	 * A swing started: a heavy stab when the attack was fully charged, otherwise quick stabs and slashes in turn.
	 * Purely visual; the game's own attack timing is untouched.
	 */
	public void attack(boolean charged) {
		if (attacking()) {
			previous = attack;
			previousAt = attackAt;
			previousEnd = clock;
		}
		if (inspecting() && inspectCancelAt == Float.MAX_VALUE) inspectCancelAt = clock;
		if (charged) {
			attack = KnifeClips.HEAVY;
		} else {
			attack = slash ? KnifeClips.SLASH : KnifeClips.STAB;
			slash = !slash;
		}
		attackAt = clock;
	}

	/**
	 * Advances by dt real seconds and computes the matrices. yawSpeed and pitchSpeed are the camera's turn rate
	 * (degrees per second) for the lag, sway scales breathing and lag, offset is the user's position offset.
	 */
	public void update(float dt, float speed, float yawSpeed, float pitchSpeed, float sway, float offsetX, float offsetY, float offsetZ) {
		dt = Math.max(0f, Math.min(dt, 0.1f));
		clock += dt * speed;
		java.util.Arrays.fill(sum, 0f);
		float pivotX = 0f;
		float pivotY = 0f;
		KnifeClip draw = KnifeClips.draw(type);
		float sinceDraw = clock - drawAt;
		if (sinceDraw < draw.duration) {
			add(draw, sinceDraw, 1f);
			pivotX = draw.pivotX;
			pivotY = draw.pivotY;
		}
		KnifeClip inspect = KnifeClips.inspect(type);
		float sinceInspect = clock - inspectAt;
		if (sinceInspect < inspect.duration) {
			float weight = 1f - smooth(Math.max(0f, clock - inspectCancelAt) / CANCEL);
			if (weight > 0f) {
				add(inspect, sinceInspect, weight);
				pivotX = inspect.pivotX;
				pivotY = inspect.pivotY;
			}
		}
		if (previous != null) {
			float fade = 1f - smooth((clock - previousEnd) / BLEND);
			if (fade > 0f) add(previous, clock - previousAt, fade);
			else previous = null;
		}
		if (attack != null) {
			float since = clock - attackAt;
			if (since < attack.duration) add(attack, since, previous != null ? smooth((clock - previousEnd) / BLEND) : 1f);
			else attack = null;
		}
		// Breathing, and the knife trailing the camera as it turns (springs, so it settles without a jolt).
		float breath = (float) Math.sin(clock * 1.7f);
		sum[KnifeClip.HAND_X + 1] += 0.005f * breath * sway;
		sum[KnifeClip.HAND_RX + 2] += 0.7f * (float) Math.sin(clock * 1.7f + 0.6f) * sway;
		springTo(dt, -yawSpeed * 0.0014f * sway, -pitchSpeed * 0.0012f * sway);
		sum[KnifeClip.HAND_X] += swayX;
		sum[KnifeClip.HAND_X + 1] += swayY;
		sum[KnifeClip.HAND_RX + 1] += swayX * 160f;
		sum[KnifeClip.HAND_RX + 2] -= swayY * 140f;

		Rest rest = Rest.of(type.grip);
		rest.turn(restTurn);
		grip.translation(rest.x + offsetX + sum[0], rest.y + offsetY + sum[1], rest.z + offsetZ + sum[2])
			.rotateY(sum[4] * DEG).rotateX(sum[3] * DEG).rotateZ(sum[5] * DEG).mul(restTurn4.set(restTurn));
		// Forward grips hold the handle nearer its butt, so guard and bolster show in front of the fist.
		float hold = type.grip == KnifeType.Grip.FORWARD ? 1.3f : 0f;
		knife.set(grip).translate((pivotX + hold) * SCALE, pivotY * SCALE, 0f)
			.rotateX(sum[9] * DEG).rotateY(sum[10] * DEG).rotateZ(sum[11] * DEG)
			.translate((sum[6] - pivotX) * SCALE, (sum[7] - pivotY) * SCALE, sum[8] * SCALE);
		armFromGrip(rest);
		arm.set(grip).mul(gripToArm);
		parts[0] = 0f;
		parts[1] = sum[KnifeClip.PART_A];
		// A balisong's bite handle turns with the blade, both about the same pivot.
		parts[2] = type == KnifeType.BUTTERFLY ? sum[KnifeClip.PART_A] + sum[KnifeClip.PART_B] : sum[KnifeClip.PART_B];
	}

	private void add(KnifeClip clip, float t, float weight) {
		clip.sample(t, sample);
		for (int i = 0; i < KnifeClip.CHANNELS; i++) {
			float v = sample[i];
			// Angles count the short way round, so a clip faded out after a whole spin does not unwind it.
			if (i >= KnifeClip.HAND_RX && i < KnifeClip.KNIFE_X || i >= KnifeClip.KNIFE_RX) v -= 360f * (float) Math.floor((v + 180f) / 360f);
			sum[i] += v * weight;
		}
	}

	private void springTo(float dt, float tx, float ty) {
		float omega = 9f;
		float decay = (float) Math.exp(-omega * dt);
		float dx = swayX - tx;
		float temp = (swayVX + omega * dx) * dt;
		swayX = tx + (dx + temp) * decay;
		swayVX = (swayVX - omega * temp) * decay;
		float dy = swayY - ty;
		temp = (swayVY + omega * dy) * dt;
		swayY = ty + (dy + temp) * decay;
		swayVY = (swayVY - omega * temp) * decay;
	}

	private static float smooth(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3f - 2f * t);
	}

	/**
	 * The arm relative to the grip. At rest the forearm runs from the fist towards the elbow and the palm faces a given
	 * way (view space); taken into the knife's frame, that fixes how the fist holds the knife. The arm model's own frame
	 * (y from the shoulder to the hand, the fist at its far end) is turned to match and moved so the fist's centre is
	 * the grip point. Last, the shoulder pivot and the slight roll that PlayerEntityRenderer.renderArm applies itself
	 * are undone. Fist centre and shoulder pivot sit between those of wide and slim arms (a quarter pixel off either).
	 */
	private void armFromGrip(Rest rest) {
		Vector3f y = restTurn.transformTranspose(elbow.set(-rest.elbowX, -rest.elbowY, -rest.elbowZ)).normalize();
		Vector3f p = restTurn.transformTranspose(palm.set(rest.palmX, rest.palmY, rest.palmZ));
		float d = p.dot(y);
		p.sub(y.x * d, y.y * d, y.z * d).normalize();
		float px = p.x;
		float py = p.y;
		float pz = p.z;
		// z = x cross y completes the arm's frame.
		float zx = py * y.z - pz * y.y;
		float zy = pz * y.x - px * y.z;
		float zz = px * y.y - py * y.x;
		// Columns: the arm's axes in knife space; then from the fist centre to the arm's model space.
		gripToArm.set(px, py, pz, 0f, y.x, y.y, y.z, 0f, zx, zy, zz, 0f, 0f, 0f, 0f, 1f);
		gripToArm.translate(0.75f / 16f, -8.2f / 16f, 0f).rotateZ(-0.1f).translate(5f / 16f, -2.25f / 16f, 0f);
	}

	/**
	 * Resting pose of a grip, all in view space (x right, y up, z towards the viewer): the grip point (blocks), where the
	 * blade points (the knife's x axis, from butt to point), roughly where its spine faces (y axis), and the directions
	 * from the fist to the elbow and out of the palm.
	 */
	private record Rest(float x, float y, float z, float bladeX, float bladeY, float bladeZ, float spineX, float spineY, float spineZ,
						float elbowX, float elbowY, float elbowZ, float palmX, float palmY, float palmZ) {
		private static final Rest FORWARD = new Rest(0.46f, -0.48f, -0.92f, -0.62f, 0.5f, -0.6f, 0.25f, 0.85f, 0.45f,
			0.6f, -0.72f, 0.35f, -0.5f, 0.3f, -0.4f);
		private static final Rest REVERSE = new Rest(0.46f, -0.34f, -0.9f, -0.4f, -0.8f, -0.3f, 0.5f, -0.6f, 0.6f,
			0.8f, -0.25f, 0.5f, -0.5f, 0.4f, -0.5f);
		private static final Rest PUSH = new Rest(0.44f, -0.46f, -0.9f, -0.4f, 0.3f, -0.87f, 0.15f, 0.95f, 0.27f,
			0.4f, -0.35f, 0.85f, -0.8f, 0.1f, -0.2f);

		static Rest of(KnifeType.Grip grip) {
			return switch (grip) {
				case FORWARD -> FORWARD;
				case REVERSE -> REVERSE;
				case PUSH -> PUSH;
			};
		}

		/** The knife's resting orientation: blade direction, spine made square to it, flat normal completing the frame. */
		void turn(Matrix3f out) {
			float bl = (float) Math.sqrt(bladeX * bladeX + bladeY * bladeY + bladeZ * bladeZ);
			float bx = bladeX / bl;
			float by = bladeY / bl;
			float bz = bladeZ / bl;
			float d = spineX * bx + spineY * by + spineZ * bz;
			float sx = spineX - bx * d;
			float sy = spineY - by * d;
			float sz = spineZ - bz * d;
			float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
			sx /= sl;
			sy /= sl;
			sz /= sl;
			out.set(bx, by, bz, sx, sy, sz, by * sz - bz * sy, bz * sx - bx * sz, bx * sy - by * sx);
		}
	}
}
