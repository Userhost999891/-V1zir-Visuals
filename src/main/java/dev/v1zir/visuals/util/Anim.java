package dev.v1zir.visuals.util;

/** Frame-rate independent exponential smoothing plus a few easing curves. */
public final class Anim {
	private float value;
	private float target;
	private final float speed;
	private long last = System.nanoTime();

	public Anim(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public Anim target(float target) {
		this.target = target;
		return this;
	}

	public float get() {
		long now = System.nanoTime();
		float dt = Math.min((now - last) / 1.0e9f, 0.1f);
		last = now;
		value += (target - value) * (1f - (float) Math.exp(-speed * dt));
		if (Math.abs(target - value) < 1e-4f) value = target;
		return value;
	}

	public float value() {
		return value;
	}

	public void snap(float v) {
		value = v;
		target = v;
	}

	public static float clamp01(float t) {
		return Math.max(0f, Math.min(1f, t));
	}

	public static float easeOutCubic(float t) {
		t = clamp01(t);
		float f = 1f - t;
		return 1f - f * f * f;
	}

	public static float easeInOutCubic(float t) {
		t = clamp01(t);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	public static float easeOutBack(float t) {
		t = clamp01(t);
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
	}

	/** Progress of an animation that starts after delay seconds and lasts duration seconds. */
	public static float progress(float elapsed, float delay, float duration) {
		return clamp01((elapsed - delay) / duration);
	}
}
