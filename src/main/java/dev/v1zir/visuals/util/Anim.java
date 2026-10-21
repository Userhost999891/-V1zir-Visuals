package dev.v1zir.visuals.util;

/**
 * Frame-rate independent critically damped spring plus a few easing curves.
 * <p>
 * The spring starts and stops smoothly on its own, so values read from it must not be passed
 * through another easing curve. It never overshoots its target, which keeps it safe for alpha
 * and size values. Every read advances it by the real time since the previous read.
 */
public final class Anim {
	private static final float SETTLE = 1e-4f;

	private float value;
	private float velocity;
	private float target;
	private final float omega;
	private long last = System.nanoTime();

	/** speed is roughly 3 / (seconds to reach 95% of the target). */
	public Anim(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.omega = speed * 1.6f;
	}

	public Anim target(float target) {
		this.target = target;
		return this;
	}

	public float get() {
		long now = System.nanoTime();
		float dt = Math.min((now - last) / 1.0e9f, 0.1f);
		last = now;
		if (dt > 0f && (value != target || velocity != 0f)) step(dt);
		return value;
	}

	/** Same as {@link #get()}; kept so layout code and render code can read the same value. */
	public float value() {
		return get();
	}

	public float target() {
		return target;
	}

	public boolean settled() {
		return value == target && velocity == 0f;
	}

	public void snap(float v) {
		value = v;
		target = v;
		velocity = 0f;
		last = System.nanoTime();
	}

	private void step(float dt) {
		// Exact solution of a critically damped spring, stable for any time step.
		float delta = value - target;
		float decay = (float) Math.exp(-omega * dt);
		float temp = (velocity + omega * delta) * dt;
		float next = target + (delta + temp) * decay;
		velocity = (velocity - omega * temp) * decay;
		if ((next - target) * delta < 0f || Math.abs(next - target) < SETTLE && Math.abs(velocity) < SETTLE * 10f) {
			value = target;
			velocity = 0f;
		} else {
			value = next;
		}
	}

	public static float clamp01(float t) {
		return Math.max(0f, Math.min(1f, t));
	}

	public static float easeOutCubic(float t) {
		t = clamp01(t);
		float f = 1f - t;
		return 1f - f * f * f;
	}

	public static float easeOutQuart(float t) {
		t = clamp01(t);
		float f = 1f - t;
		return 1f - f * f * f * f;
	}

	public static float easeInOutCubic(float t) {
		t = clamp01(t);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	public static float easeInOutSine(float t) {
		t = clamp01(t);
		return (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
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
