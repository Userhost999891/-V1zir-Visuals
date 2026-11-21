package dev.v1zir.visuals.render.knife;

import java.util.Arrays;

/**
 * A 2D path in the knife's profile plane (centimetres, x along the knife, y towards the spine), built from lines
 * and Bézier segments and flattened into a dense polyline. Knife models read it back by arc length (rings,
 * outlines) or as a function of x (the spine and edge of a straight blade).
 */
final class Curve {
	private static final int SEGMENT_STEPS = 24;

	private float[] xs = new float[64];
	private float[] ys = new float[64];
	private int n;
	/** Arc length at every point, built on first use. */
	private float[] lengths;

	static Curve start(float x, float y) {
		Curve c = new Curve();
		c.add(x, y);
		return c;
	}

	private void add(float x, float y) {
		if (n == xs.length) {
			xs = Arrays.copyOf(xs, n * 2);
			ys = Arrays.copyOf(ys, n * 2);
		}
		xs[n] = x;
		ys[n] = y;
		n++;
		lengths = null;
	}

	Curve line(float x, float y) {
		add(x, y);
		return this;
	}

	Curve quad(float cx, float cy, float x, float y) {
		float x0 = xs[n - 1];
		float y0 = ys[n - 1];
		for (int i = 1; i <= SEGMENT_STEPS; i++) {
			float t = i / (float) SEGMENT_STEPS;
			float u = 1f - t;
			add(u * u * x0 + 2f * u * t * cx + t * t * x, u * u * y0 + 2f * u * t * cy + t * t * y);
		}
		return this;
	}

	Curve cubic(float c1x, float c1y, float c2x, float c2y, float x, float y) {
		float x0 = xs[n - 1];
		float y0 = ys[n - 1];
		for (int i = 1; i <= SEGMENT_STEPS; i++) {
			float t = i / (float) SEGMENT_STEPS;
			float u = 1f - t;
			float a = u * u * u;
			float b = 3f * u * u * t;
			float c = 3f * u * t * t;
			float d = t * t * t;
			add(a * x0 + b * c1x + c * c2x + d * x, a * y0 + b * c1y + c * c2y + d * y);
		}
		return this;
	}

	/** Circular arc around (cx, cy) from the current point, turning by sweep radians (positive is counter-clockwise). */
	Curve arc(float cx, float cy, float sweep) {
		float x0 = xs[n - 1] - cx;
		float y0 = ys[n - 1] - cy;
		int steps = Math.max(4, Math.round(Math.abs(sweep) / (float) Math.PI * 24f));
		for (int i = 1; i <= steps; i++) {
			float a = sweep * i / steps;
			float c = (float) Math.cos(a);
			float s = (float) Math.sin(a);
			add(cx + x0 * c - y0 * s, cy + x0 * s + y0 * c);
		}
		return this;
	}

	int size() {
		return n;
	}

	float x(int i) {
		return xs[i];
	}

	float y(int i) {
		return ys[i];
	}

	float endX() {
		return xs[n - 1];
	}

	float endY() {
		return ys[n - 1];
	}

	float length() {
		measure();
		return lengths[n - 1];
	}

	private void measure() {
		if (lengths != null) return;
		lengths = new float[n];
		for (int i = 1; i < n; i++) {
			lengths[i] = lengths[i - 1] + (float) Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
		}
	}

	/** The point at a fraction s (0 to 1) of the arc length, written to out[0], out[1]. */
	void at(float s, float[] out) {
		measure();
		float target = Math.max(0f, Math.min(1f, s)) * lengths[n - 1];
		int lo = 0;
		int hi = n - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (lengths[mid] < target) lo = mid;
			else hi = mid;
		}
		float span = lengths[hi] - lengths[lo];
		float t = span > 1e-6f ? (target - lengths[lo]) / span : 0f;
		out[0] = xs[lo] + (xs[hi] - xs[lo]) * t;
		out[1] = ys[lo] + (ys[hi] - ys[lo]) * t;
	}

	/** y where the path crosses x, for a path whose x only grows (a straight blade's spine or edge). */
	float yAt(float x) {
		if (x <= xs[0]) return ys[0];
		if (x >= xs[n - 1]) return ys[n - 1];
		int lo = 0;
		int hi = n - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (xs[mid] < x) lo = mid;
			else hi = mid;
		}
		float span = xs[hi] - xs[lo];
		float t = span > 1e-6f ? (x - xs[lo]) / span : 0f;
		return ys[lo] + (ys[hi] - ys[lo]) * t;
	}

	/** n points evenly spaced by arc length along the path, as x0, y0, x1, y1... (a closed outline repeats no point). */
	float[] resample(int count, boolean closed) {
		float[] out = new float[count * 2];
		float[] p = new float[2];
		for (int i = 0; i < count; i++) {
			at(closed ? i / (float) count : i / (float) (count - 1), p);
			out[i * 2] = p[0];
			out[i * 2 + 1] = p[1];
		}
		return out;
	}
}
