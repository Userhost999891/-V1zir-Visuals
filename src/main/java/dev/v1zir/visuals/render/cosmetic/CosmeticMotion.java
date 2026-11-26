package dev.v1zir.visuals.render.cosmetic;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * How one wearer's cosmetics move this frame: the time for animated materials, how far the wings stand open and
 * their beat. Driven by the wearer's movement (velocity from their position between frames, sneaking, gliding,
 * swimming), smoothed with springs so nothing jumps. Capes are cloth of their own (CapeCloth).
 */
public final class CosmeticMotion {
	private static final Map<Integer, CosmeticMotion> BY_ENTITY = new HashMap<>();
	/** The Cosmetics tab's previews: idle and ever so slightly breezy. */
	private static final CosmeticMotion PREVIEW = new CosmeticMotion();
	private static long lastSweep;

	/** Seconds, for animated materials and loops. */
	float time;
	/** Degrees the wings stand open from the back, and their current beat on top. */
	float wingOpen = 35f;
	float wingBeat;
	/** Degrees a propeller has turned: steadily, faster on the move and in the air. */
	float spin;

	private float wingOpenVel;
	private float phase;
	private double lastX;
	private double lastY;
	private double lastZ;
	private long lastNanos;
	private float vx;
	private float vy;
	private float vz;
	private boolean started;

	private CosmeticMotion() {
	}

	/** This wearer's motion, advanced to now (once per frame, however often it is asked). */
	public static CosmeticMotion of(int entityId, double x, double y, double z, float bodyYaw, boolean sneaking, boolean gliding,
									boolean swimming) {
		long now = System.nanoTime();
		CosmeticMotion m = BY_ENTITY.computeIfAbsent(entityId, id -> new CosmeticMotion());
		m.advance(now, x, y, z, bodyYaw, sneaking, gliding, swimming);
		if (now - lastSweep > 10_000_000_000L) {
			// Forget wearers not seen for a while.
			lastSweep = now;
			Iterator<CosmeticMotion> it = BY_ENTITY.values().iterator();
			while (it.hasNext()) if (now - it.next().lastNanos > 10_000_000_000L) it.remove();
		}
		return m;
	}

	/** The motion the tab's previews use, advanced to now. */
	public static CosmeticMotion preview() {
		PREVIEW.advance(System.nanoTime(), 0.0, 0.0, 0.0, 0f, false, false, false);
		return PREVIEW;
	}

	private void advance(long now, double x, double y, double z, float bodyYaw, boolean sneaking, boolean gliding, boolean swimming) {
		if (started && now == lastNanos) return;
		float dt = started ? Math.min(0.1f, (now - lastNanos) / 1.0e9f) : 0f;
		if (started && dt > 0f) {
			// Velocity from the position between frames, smoothed against frame time jitter; teleports ignored.
			float nvx = (float) ((x - lastX) / dt);
			float nvy = (float) ((y - lastY) / dt);
			float nvz = (float) ((z - lastZ) / dt);
			if (Math.abs(nvx) + Math.abs(nvy) + Math.abs(nvz) < 80f) {
				float k = 1f - (float) Math.exp(-dt * 12f);
				vx += (nvx - vx) * k;
				vy += (nvy - vy) * k;
				vz += (nvz - vz) * k;
			}
		}
		started = true;
		lastNanos = now;
		lastX = x;
		lastY = y;
		lastZ = z;
		time += dt;

		float speed = (float) Math.sqrt(vx * vx + vz * vz);
		boolean airborne = gliding || vy < -2.5f;

		// Wings: open wider in the air, folded when sneaking; beat faster when flying.
		float openTarget = sneaking ? 14f : airborne ? 78f : swimming ? 22f : 34f + Math.min(speed, 6f) * 1.6f;
		float omega = 5f;
		float accel = omega * omega * (openTarget - wingOpen) - 2f * omega * wingOpenVel;
		wingOpenVel += accel * dt;
		wingOpen += wingOpenVel * dt;
		float rate = airborne ? 2.4f : 0.75f + Math.min(speed, 6f) * 0.08f;
		float amplitude = airborne ? 16f : 4f + Math.min(speed, 6f) * 0.7f;
		phase += dt * rate * (float) (Math.PI * 2.0);
		if (phase > Math.PI * 2000.0) phase -= (float) (Math.PI * 2000.0);
		wingBeat = (float) Math.sin(phase) * amplitude;
		spin = (spin + dt * (420f + Math.min(speed, 8f) * 90f + (airborne ? 500f : 0f))) % 360f;
	}
}
