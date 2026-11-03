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
	/** Fist to elbow, blocks: the elbow sits below the bottom right corner of the view, the pivot of arm swings. */
	private static final float FOREARM = 0.62f;
	/** The shoulder the arm swings from (view space, blocks): right of, below and a little behind the eye. */
	private static final float SHOULDER_X = 0.32f;
	private static final float SHOULDER_Y = -0.5f;
	private static final float SHOULDER_Z = 0.22f;
	/** Inspect fades out over this when an attack or a switch interrupts it, seconds. */
	private static final float CANCEL = 0.16f;
	/** A slash this soon after the last attack is a follow-up and comes back the other way, seconds. */
	private static final float FOLLOW_UP = 0.7f;
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
	private final Matrix4f outer = new Matrix4f();
	private final Vector3f fist = new Vector3f();
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
	// The player's movement (move()) and the motion it drives.
	private float moveForward;
	private float moveStrafe;
	private float moveVertical;
	private boolean grounded = true;
	private boolean running;
	private float walk;
	private float sprint;
	private float lean;
	private float bobPhase;
	private float lift;
	private float liftVel;
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
	 * A swing started: a heavy stab for a critical hit (heavy = true), otherwise slashes, across and back in turn.
	 * Purely visual; the game's own attack timing is untouched.
	 */
	public void attack(boolean heavy) {
		if (attacking()) {
			previous = attack;
			previousAt = attackAt;
			previousEnd = clock;
		}
		if (inspecting() && inspectCancelAt == Float.MAX_VALUE) inspectCancelAt = clock;
		if (heavy) {
			attack = KnifeClips.heavy(type);
		} else {
			// Slashes alternate while they follow each other; after a pause the next one is the forehand again.
			if (clock - attackAt > FOLLOW_UP) slash = false;
			attack = KnifeClips.slash(type, slash);
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
			if (since < attack.duration) {
				add(attack, since, previous != null ? smooth((clock - previousEnd) / BLEND) : 1f);
				// A karambit's attacks turn it about the finger in its ring, like its draw and inspect.
				pivotX = attack.pivotX;
				pivotY = attack.pivotY;
			} else {
				attack = null;
			}
		}
		// Breathing, and the knife trailing the camera as it turns (springs, so it settles without a jolt).
		float breath = (float) Math.sin(clock * 1.7f);
		sum[KnifeClip.HAND_X + 1] += 0.005f * breath * sway;
		sum[KnifeClip.HAND_RX + 2] += 0.7f * (float) Math.sin(clock * 1.7f + 0.6f) * sway;
		springTo(dt, -yawSpeed * 0.0014f * sway, -pitchSpeed * 0.0012f * sway);
		sum[KnifeClip.HAND_X] += swayX;
		sum[KnifeClip.HAND_X + 1] += swayY;
		sum[KnifeClip.HAND_RX + 1] += swayX * 160f;
		sum[KnifeClip.HAND_RX] -= swayY * 140f;
		living(dt, sway);

		Rest rest = Rest.of(type.grip);
		rest.turn(restTurn);
		// The arm moves like an arm: the fist is carried by the shoulder (hand offset), swung about the elbow below the
		// view (pitch lifts it, yaw sweeps it left across the view like a wiper) and the forearm twists about its own
		// axis (roll), so the wrist turns
		// the knife over without the forearm ever pointing at the camera. Bends of the wrist and tricks in the fingers
		// are the knife's own spin, below.
		float fx = rest.elbowX;
		float fy = rest.elbowY;
		float fz = rest.elbowZ;
		float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
		fx /= fl;
		fy /= fl;
		fz /= fl;
		float gx = rest.x + offsetX + sum[0];
		float gy = rest.y + offsetY + sum[1];
		float gz = rest.z + offsetZ + sum[2];
		float ex = gx + fx * FOREARM;
		float ey = gy + fy * FOREARM;
		float ez = gz + fz * FOREARM;
		// Pitch axis: up x forearm, level and square to the forearm, so a positive pitch lifts the fist.
		float ax = fz;
		float az = -fx;
		float al = (float) Math.sqrt(ax * ax + az * az);
		// The sweep about the elbow would carry the fist down along its arc; the shoulder lifts it back, so sweeps
		// travel level across the view while the blade turns with them.
		float sweep = sum[4] * DEG;
		float rx = gx - ex;
		float ry = gy - ey;
		float drop = rx * (float) Math.sin(sweep) + ry * (float) Math.cos(sweep) - ry;
		grip.translation(ex, ey - drop, ez)
			.rotateZ(sweep)
			.rotate(sum[3] * DEG, ax / al, 0f, az / al)
			.translate(gx - ex, gy - ey, gz - ez)
			.rotate(sum[5] * DEG, fx, fy, fz)
			.mul(restTurn4.set(restTurn));
		// Outside that: arm and knife aimed together about the fist (a stab points the blade at the crosshair), and the
		// whole arm swung from the shoulder behind the view (slashes sweep across it, draws rise into it).
		grip.getTranslation(fist);
		float sx = SHOULDER_X + offsetX;
		float sy = SHOULDER_Y + offsetY;
		float sz = SHOULDER_Z + offsetZ;
		outer.translation(sx, sy, sz)
			.rotateZ(sum[KnifeClip.SWING + 2] * DEG)
			.rotateY(sum[KnifeClip.SWING] * DEG)
			.rotateX(sum[KnifeClip.SWING + 1] * DEG)
			.translate(fist.x - sx, fist.y - sy, fist.z - sz)
			.rotateY(sum[KnifeClip.AIM] * DEG)
			.rotateX(sum[KnifeClip.AIM + 1] * DEG)
			.translate(-fist.x, -fist.y, -fist.z);
		grip.mulLocal(outer);
		// Forward grips hold the handle nearer its butt, so guard and bolster show in front of the fist.
		float hold = type.grip == KnifeType.Grip.FORWARD ? 1.3f : 0f;
		// The shift is in the hand's frame (a toss rises, whichever way the knife is turning), the spin about the pivot.
		knife.set(grip).translate((sum[6] + pivotX + hold) * SCALE, (sum[7] + pivotY) * SCALE, sum[8] * SCALE)
			.rotateX(sum[9] * DEG).rotateY(sum[10] * DEG).rotateZ(sum[11] * DEG)
			.translate(-pivotX * SCALE, -pivotY * SCALE, 0f);
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

	/**
	 * The player's movement this frame, relative to the view (blocks per second: forward, to the right, up), whether
	 * they stand on the ground and sprint. Drives the walking bob, the sprint pose, leaning into strafes and the
	 * spring of jumps and landings. Call before {@link #update}; previews leave it at rest.
	 */
	public void move(float forward, float strafe, float vertical, boolean onGround, boolean sprinting) {
		moveForward = forward;
		moveStrafe = strafe;
		moveVertical = vertical;
		grounded = onGround;
		running = sprinting;
	}

	/**
	 * Motion that keeps the knife alive between and under the clips: breathing on several axes, a walking bob in a
	 * figure of eight, the knife lowered and turned in while sprinting, the arm leaning against strafes, a springy dip
	 * as a jump pushes off and a bounce on landing, and the wrist settling the knife in the fingers.
	 */
	private void living(float dt, float sway) {
		float speed = (float) Math.sqrt(moveForward * moveForward + moveStrafe * moveStrafe);
		walk += ((grounded ? Math.min(1f, speed / 4.3f) : 0f) - walk) * (1f - (float) Math.exp(-dt * 8f));
		sprint += ((running && grounded ? 1f : 0f) - sprint) * (1f - (float) Math.exp(-dt * 6f));
		lean += (Math.max(-6f, Math.min(6f, moveStrafe)) - lean) * (1f - (float) Math.exp(-dt * 6f));
		if (walk > 0.01f) bobPhase += dt * (5.4f + speed * 1.05f);
		if (bobPhase > 1000f) bobPhase -= (float) (Math.PI * 2.0 * 150.0);
		float bob = walk * sway;
		float s = (float) Math.sin(bobPhase);
		float c = (float) Math.cos(bobPhase);
		sum[KnifeClip.HAND_X] += s * 0.024f * bob;
		sum[KnifeClip.HAND_X + 1] += (0.014f - Math.abs(c) * 0.028f) * bob;
		sum[KnifeClip.HAND_RX] += (float) Math.cos(bobPhase * 2f) * 1.6f * bob;
		sum[KnifeClip.HAND_RX + 1] += s * 2.4f * bob;
		sum[KnifeClip.HAND_RX + 2] += s * 2.0f * bob;
		// Sprinting: lowered, tipped down and turned in, like running with a knife.
		sum[KnifeClip.HAND_X] += 0.035f * sprint;
		sum[KnifeClip.HAND_X + 1] -= 0.06f * sprint;
		sum[KnifeClip.HAND_X + 2] += 0.02f * sprint;
		sum[KnifeClip.HAND_RX] -= 14f * sprint;
		sum[KnifeClip.HAND_RX + 1] -= 6f * sprint;
		sum[KnifeClip.HAND_RX + 2] += 18f * sprint;
		// Strafing: the arm trails the move, swinging a little the other way.
		sum[KnifeClip.HAND_RX + 1] += lean * 1.2f * sway;
		sum[KnifeClip.HAND_X] -= lean * 0.004f * sway;
		// Jumps and landings on an underdamped spring: the knife dips as the jump pushes off, floats while falling and
		// bounces when the fall stops.
		float target = Math.max(-0.04f, Math.min(0.05f, -moveVertical * 0.006f));
		float omega = 10f;
		liftVel += (omega * omega * (target - lift) - 2f * 0.4f * omega * liftVel) * dt;
		lift += liftVel * dt;
		sum[KnifeClip.HAND_X + 1] += lift * sway;
		sum[KnifeClip.HAND_RX] += lift * 60f * sway;
		// Breathing on more axes, slowly out of step, and the knife settling in the fingers.
		sum[KnifeClip.HAND_X] += 0.006f * (float) Math.sin(clock * 0.9f) * sway;
		sum[KnifeClip.HAND_RX] += 1.1f * (float) Math.sin(clock * 1.3f + 1f) * sway;
		sum[KnifeClip.HAND_RX + 1] += 0.8f * (float) Math.sin(clock * 0.7f + 2f) * sway;
		sum[KnifeClip.KNIFE_RX + 2] += 1.3f * (float) Math.sin(clock * 1.1f + 0.5f) * sway;
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
		// The fist sits low in the right of the view with the forearm rising to it from below the corner; the blade
		// leaves the fist bent up at the wrist and points left across the view, its flat towards the eye.
		private static final Rest FORWARD = new Rest(0.47f, -0.4f, -0.88f, -0.88f, 0.3f, -0.37f, 0.15f, 0.97f, 0.1f,
			0.55f, -0.75f, 0.38f, -0.6f, 0.15f, -0.5f);
		// Reverse grip: the ring or butt on top of the fist, the blade below it curving forward and left.
		private static final Rest REVERSE = new Rest(0.48f, -0.27f, -0.85f, -0.55f, -0.6f, -0.5f, 0.75f, -0.1f, 0.65f,
			0.55f, -0.75f, 0.38f, -0.6f, 0.1f, -0.5f);
		// Push dagger: the T-handle across the palm, the blade out between the fingers pointing left and up, its flat
		// turned to the eye.
		private static final Rest PUSH = new Rest(0.45f, -0.4f, -0.86f, -0.75f, 0.45f, -0.48f, 0.4f, 0.85f, -0.2f,
			0.55f, -0.75f, 0.38f, -0.5f, 0.2f, -0.6f);

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
