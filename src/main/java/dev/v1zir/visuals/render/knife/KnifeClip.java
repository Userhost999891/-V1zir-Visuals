package dev.v1zir.visuals.render.knife;

import java.util.Arrays;

/**
 * One first-person animation: keyframes of offsets from the resting pose, played additively. Channels are the
 * hand's position and turn about the grip (view space: blocks, and degrees of pitch, yaw and roll in the screen plane),
 * the knife's shift and spin relative to the hand (centimetres and degrees about the knife's own axes: x along the
 * blade, y towards the spine, z through the flat; the spin about the clip's pivot) and the angles of the moving parts.
 * Keys are joined by cubic Hermite curves whose tangents
 * follow the neighbouring keys (Catmull-Rom over uneven spacing), so motion flows through every key; the clip eases
 * in from and out to its first and last key.
 * <p>
 * Built with a small fluent API: at(time) starts a key as a copy of the previous one and the setters change it.
 * Unless a clip says otherwise, it starts and ends at rest (all zero).
 */
final class KnifeClip {
	static final int HAND_X = 0;
	static final int HAND_RX = 3;
	static final int KNIFE_X = 6;
	static final int KNIFE_RX = 9;
	static final int PART_A = 12;
	static final int PART_B = 13;
	static final int CHANNELS = 14;

	final float duration;
	/** The knife spins about this point (centimetres, knife space): the grip, a finger ring, the balance point. */
	final float pivotX;
	final float pivotY;
	private float[] times = new float[8];
	private float[][] keys = new float[8][];
	private int count;
	private boolean closed;

	KnifeClip(float duration, float pivotX, float pivotY) {
		this.duration = duration;
		this.pivotX = pivotX;
		this.pivotY = pivotY;
		times[0] = 0f;
		keys[0] = new float[CHANNELS];
		count = 1;
	}

	KnifeClip(float duration) {
		this(duration, 0f, 0f);
	}

	/** Starts a key at time t (seconds) as a copy of the previous one; at(0) edits the first key. */
	KnifeClip at(float t) {
		if (t <= 0f && count == 1) return this;
		if (count == times.length) {
			times = Arrays.copyOf(times, count * 2);
			keys = Arrays.copyOf(keys, count * 2);
		}
		times[count] = t;
		keys[count] = keys[count - 1].clone();
		count++;
		return this;
	}

	private KnifeClip set(int channel, float a, float b, float c) {
		float[] k = keys[count - 1];
		k[channel] = a;
		k[channel + 1] = b;
		k[channel + 2] = c;
		return this;
	}

	/** Hand position offset, blocks (view space: x right, y up, z towards the viewer). */
	KnifeClip hand(float x, float y, float z) {
		return set(HAND_X, x, y, z);
	}

	/** Hand turn about the grip, degrees in view space: x tips the point up, y turns it left, z rolls it anticlockwise on screen. */
	KnifeClip turn(float rx, float ry, float rz) {
		return set(HAND_RX, rx, ry, rz);
	}

	/** Knife position relative to the hand, centimetres in knife space (a toss lifts it out of the hand). */
	KnifeClip shift(float x, float y, float z) {
		return set(KNIFE_X, x, y, z);
	}

	/** Knife rotation relative to the hand about the pivot, degrees about the knife's axes. */
	KnifeClip spin(float rx, float ry, float rz) {
		return set(KNIFE_RX, rx, ry, rz);
	}

	/** Angles of the moving parts (a folding blade; a balisong's blade and bite handle), degrees. */
	KnifeClip parts(float a, float b) {
		float[] k = keys[count - 1];
		k[PART_A] = a;
		k[PART_B] = b;
		return this;
	}

	/** Ends the clip on its last key instead of returning to rest (a draw that ends where the next pose begins). */
	KnifeClip hold() {
		closed = true;
		return this;
	}

	private void finish() {
		if (closed || times[count - 1] >= duration) return;
		at(duration);
		Arrays.fill(keys[count - 1], 0f);
		closed = true;
	}

	/** Writes the channels at time t (seconds, clamped to the clip) into out. */
	void sample(float t, float[] out) {
		finish();
		if (t <= times[0]) {
			System.arraycopy(keys[0], 0, out, 0, CHANNELS);
			return;
		}
		int last = count - 1;
		if (t >= times[last]) {
			System.arraycopy(keys[last], 0, out, 0, CHANNELS);
			return;
		}
		int i = 0;
		while (i < last - 1 && t >= times[i + 1]) i++;
		float t0 = times[i];
		float t1 = times[i + 1];
		float dt = t1 - t0;
		float s = (t - t0) / dt;
		float s2 = s * s;
		float s3 = s2 * s;
		float h00 = 2f * s3 - 3f * s2 + 1f;
		float h10 = s3 - 2f * s2 + s;
		float h01 = -2f * s3 + 3f * s2;
		float h11 = s3 - s2;
		float[] a = keys[i];
		float[] b = keys[i + 1];
		for (int c = 0; c < CHANNELS; c++) {
			out[c] = h00 * a[c] + h01 * b[c] + (h10 * tangent(i, c) + h11 * tangent(i + 1, c)) * dt;
		}
	}

	/** Slope at key i: from its neighbours, flat at the first and last key so the clip eases in and out. */
	private float tangent(int i, int c) {
		if (i == 0 || i == count - 1) return 0f;
		return (keys[i + 1][c] - keys[i - 1][c]) / (times[i + 1] - times[i - 1]);
	}
}
