package dev.v1zir.visuals.render.world;

import dev.v1zir.visuals.util.ColorUtil;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Blood: droplets thrown from a point fall with gravity, streak along their path and turn into flat
 * stains where they land; stains darken a little as they dry and fade out after a while. Droplets and
 * stains live in fixed pools (the oldest stain makes room for a new one), so nothing is allocated
 * while it runs. Everything is alpha blended: blood does not glow.
 */
public final class Splatter {
	private static final int MAX_DROPS = 320;
	private static final int MAX_STAINS = 160;
	/** Per droplet: x, y, z, vx, vy, vz, ground height, size. */
	private static final int DROP = 8;
	/** Per stain: x, y, z, half axis u (x, z), half axis v (x, z), age in seconds. */
	private static final int STAIN = 8;
	private static final float GRAVITY = 15f;
	/** Seconds of motion a droplet's streak spans. */
	private static final float STREAK = 0.03f;

	private final double[] drops = new double[MAX_DROPS * DROP];
	private final double[] stains = new double[MAX_STAINS * STAIN];
	private int dropCount;
	private int stainCount;
	/** Next stain slot to overwrite once the pool is full. */
	private int oldest;

	public boolean isEmpty() {
		return dropCount == 0 && stainCount == 0;
	}

	public void clear() {
		dropCount = 0;
		stainCount = 0;
		oldest = 0;
	}

	/**
	 * Throws count droplets from (x, y, z), outwards and up at about speed blocks per second and pushed
	 * along the horizontal unit direction (pushX, pushZ) (0, 0 for none); ground is the surface below
	 * them. size scales the droplets and their stains.
	 */
	public void spawn(double x, double y, double z, double ground, int count, float speed, float size, float pushX, float pushZ) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < count && dropCount < MAX_DROPS; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double out = speed * (0.35 + random.nextDouble() * 0.75);
			int o = dropCount++ * DROP;
			drops[o] = x + (random.nextDouble() - 0.5) * 0.2;
			drops[o + 1] = y + (random.nextDouble() - 0.5) * 0.2;
			drops[o + 2] = z + (random.nextDouble() - 0.5) * 0.2;
			double push = speed * (0.4 + random.nextDouble() * 0.9);
			drops[o + 3] = Math.cos(angle) * out + pushX * push;
			drops[o + 4] = speed * (0.2 + random.nextDouble() * 0.9);
			drops[o + 5] = Math.sin(angle) * out + pushZ * push;
			drops[o + 6] = ground;
			drops[o + 7] = size * (0.6 + random.nextDouble() * 0.8);
		}
	}

	/** A round stain right away (a pool under a body); it grows to its size over the first half second. */
	public void pool(double x, double ground, double z, float radius) {
		addStain(x, ground, z, radius, 0f, radius, 0.0);
	}

	/**
	 * Steps the droplets by dt seconds and draws droplets and stains in color; stains last {@code stainLife}
	 * seconds (0 leaves none).
	 */
	public void render(float dt, int color, float stainLife) {
		if (isEmpty()) return;
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.translucent();
		int dark = ColorUtil.darken(color, 0.45f);
		for (int i = dropCount - 1; i >= 0; i--) {
			int o = i * DROP;
			drops[o + 4] -= GRAVITY * dt;
			drops[o + 3] *= 1.0 - 0.8 * dt;
			drops[o + 5] *= 1.0 - 0.8 * dt;
			drops[o] += drops[o + 3] * dt;
			drops[o + 1] += drops[o + 4] * dt;
			drops[o + 2] += drops[o + 5] * dt;
			if (drops[o + 1] <= drops[o + 6] && !land(o, stainLife)) {
				// Landed (or gone): the last droplet takes this slot.
				dropCount--;
				System.arraycopy(drops, dropCount * DROP, drops, o, DROP);
				continue;
			}
			double x = drops[o], y = drops[o + 1], z = drops[o + 2];
			float near = Draw3D.cameraFade(x, y, z, 0.4f, 1.0f);
			if (near <= 0.01f) continue;
			float s = (float) drops[o + 7];
			int head = ColorUtil.withAlpha(color, Math.round(235 * near));
			Draw3D.line(mesh, x, y, z, x - drops[o + 3] * STREAK, y - drops[o + 4] * STREAK, z - drops[o + 5] * STREAK,
				0.028f * s, head, ColorUtil.withAlpha(dark, 0));
			mesh.orb(x, y, z, 0.05f * s, head);
		}
		for (int i = stainCount - 1; i >= 0; i--) {
			int o = i * STAIN;
			double age = stains[o + 7] += dt;
			if (age >= stainLife) {
				removeStain(i);
				continue;
			}
			// Spreads out quickly, then dries darker and fades over the last second and a half.
			float grow = 0.55f + 0.45f * (1f - (float) Math.exp(-age * 9.0));
			float fade = (float) Math.min(1.0, (stainLife - age) / 1.5);
			int c = ColorUtil.lerp(color, dark, (float) Math.min(1.0, age / 4.0));
			c = ColorUtil.withAlpha(c, Math.round(225 * fade));
			Draw3D.flatOrb(mesh, stains[o], stains[o + 1], stains[o + 2], (float) stains[o + 3] * grow, (float) stains[o + 4] * grow,
				(float) stains[o + 5] * grow, (float) stains[o + 6] * grow, c);
		}
	}

	/** The droplet at offset o reached its ground: true if it keeps falling to a lower surface, else it leaves a stain. */
	private boolean land(int o, float stainLife) {
		double x = drops[o], z = drops[o + 2];
		double ground = drops[o + 6];
		double below = Effects.groundBelow(x, ground + 0.5, z);
		if (below < ground - 0.05) {
			// Flew over an edge: fall on to the surface below.
			drops[o + 6] = below;
			return true;
		}
		if (stainLife <= 0f) return false;
		// Stretched along the direction the droplet was flying, the faster the longer.
		double vx = drops[o + 3], vz = drops[o + 5];
		double speed = Math.sqrt(vx * vx + vz * vz);
		float s = (float) drops[o + 7];
		float width = 0.07f * s;
		float length = width * (1f + (float) Math.min(1.6, speed * 0.35));
		float dx = speed > 1e-4 ? (float) (vx / speed) : 1f;
		float dz = speed > 1e-4 ? (float) (vz / speed) : 0f;
		addStain(x, below, z, dx * length, dz * length, width, 0.25);
		return false;
	}

	/** A stain with the half axis (ux, uz) and a perpendicular one of length width (0: as long as the first). */
	private void addStain(double x, double ground, double z, float ux, float uz, float width, double age) {
		int slot;
		if (stainCount < MAX_STAINS) {
			slot = stainCount++;
		} else {
			slot = oldest;
			oldest = (oldest + 1) % MAX_STAINS;
		}
		int o = slot * STAIN;
		float length = (float) Math.sqrt(ux * ux + uz * uz);
		float w = width > 0f ? width : length;
		float k = length > 1e-5f ? w / length : 0f;
		stains[o] = x;
		// Just above the surface, so it does not flicker against it.
		stains[o + 1] = ground + 0.015;
		stains[o + 2] = z;
		stains[o + 3] = ux;
		stains[o + 4] = uz;
		stains[o + 5] = -uz * k;
		stains[o + 6] = ux * k;
		stains[o + 7] = age;
	}

	private void removeStain(int i) {
		stainCount--;
		System.arraycopy(stains, stainCount * STAIN, stains, i * STAIN, STAIN);
		if (oldest > stainCount) oldest = 0;
	}
}
