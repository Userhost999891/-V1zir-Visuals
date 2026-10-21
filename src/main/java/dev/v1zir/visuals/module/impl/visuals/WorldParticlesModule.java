package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambient glowing particles around you. They live in the world, so you walk through them with
 * parallax; the ones that drift out of range respawn at the edge of the radius.
 */
public final class WorldParticlesModule extends Module implements WorldEffect {
	private static final int FIREFLIES = 0;
	private static final int SNOW = 1;
	private static final int STARS = 2;
	private static final int EMBERS = 3;
	/** Per particle: position relative to the anchor (3), velocity (3), phase, variety. */
	private static final int STRIDE = 8;

	public final ModeSetting mode = add(new ModeSetting("Mode", "Fireflies", "Fireflies", "Snow", "Stars", "Embers"));
	public final NumberSetting count = add(new NumberSetting("Count", 140, 20, 600, 10));
	public final NumberSetting radius = add(new NumberSetting("Radius", 18, 6, 40, 1));
	public final NumberSetting particleSize = add(new NumberSetting("Size", 1.0, 0.3, 3.0, 0.05).suffix("x"));

	private float[] data = new float[0];
	private long lastFrame = System.nanoTime();
	private int shownMode = -1;
	// World position the particle offsets are stored against (floats stay precise near it).
	private double anchorX;
	private double anchorY;
	private double anchorZ;
	private double lastCamX;
	private double lastCamY;
	private double lastCamZ;

	public WorldParticlesModule() {
		super("World Particles", "Fireflies, snow, stars or embers around you", Category.WORLD);
		setSubCategory("Ambience");
	}

	@Override
	public String suffix() {
		return mode.get();
	}

	@Override
	protected void onDisable() {
		data = new float[0];
	}

	@Override
	public void render3D(float tickDelta) {
		int n = count.i();
		float r = radius.f();
		int m = mode.index();
		double cx = WorldEffectRenderer.camX;
		double cy = WorldEffectRenderer.camY;
		double cz = WorldEffectRenderer.camZ;
		double jx = cx - lastCamX;
		double jy = cy - lastCamY;
		double jz = cz - lastCamZ;
		boolean teleported = jx * jx + jy * jy + jz * jz > r * r;
		lastCamX = cx;
		lastCamY = cy;
		lastCamZ = cz;
		if (data.length != n * STRIDE || m != shownMode || teleported) {
			// New layout, new mode or a jump far away: start over around the camera.
			data = new float[n * STRIDE];
			shownMode = m;
			anchorX = cx;
			anchorY = cy;
			anchorZ = cz;
			for (int i = 0; i < n; i++) respawn(i, m, 0f, 0f, 0f, r, true);
		} else if (Math.abs(cx - anchorX) + Math.abs(cy - anchorY) + Math.abs(cz - anchorZ) > 256.0) {
			// Far from the anchor: move it to the camera so the float offsets stay precise (invisible).
			float sx = (float) (cx - anchorX);
			float sy = (float) (cy - anchorY);
			float sz = (float) (cz - anchorZ);
			for (int o = 0; o < data.length; o += STRIDE) {
				data[o] -= sx;
				data[o + 1] -= sy;
				data[o + 2] -= sz;
			}
			anchorX = cx;
			anchorY = cy;
			anchorZ = cz;
		}
		// Camera position relative to the anchor.
		float ox = (float) (cx - anchorX);
		float oy = (float) (cy - anchorY);
		float oz = (float) (cz - anchorZ);
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.05f);
		lastFrame = now;
		float t = V1zir.time();
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		float scale = particleSize.f();
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		for (int i = 0; i < n; i++) {
			int o = i * STRIDE;
			float phase = data[o + 6];
			float variety = data[o + 7];
			switch (m) {
				case SNOW -> {
					data[o + 1] -= dt * (0.6f + variety * 0.6f);
					data[o] += (float) Math.sin(t * 0.7f + phase) * dt * 0.3f;
				}
				case EMBERS -> {
					data[o + 1] += dt * (0.4f + variety * 0.8f);
					data[o] += (float) Math.sin(t * 1.3f + phase) * dt * 0.4f;
				}
				default -> {
					data[o] += data[o + 3] * dt;
					data[o + 1] += data[o + 4] * dt + (float) Math.sin(t * 0.9f + phase) * dt * 0.15f;
					data[o + 2] += data[o + 5] * dt;
				}
			}
			float dx = data[o] - ox;
			float dy = data[o + 1] - oy;
			float dz = data[o + 2] - oz;
			float dist2 = dx * dx + dy * dy + dz * dz;
			if (dist2 > r * r * 1.1f) {
				respawn(i, m, ox, oy, oz, r, false);
				continue;
			}
			float dist = (float) Math.sqrt(dist2);
			// Faded in towards the edge of the radius, and out right in front of the eyes.
			float near = Math.max(0f, Math.min(1f, (dist - 0.8f) / 1.2f));
			float alpha = Math.min(1f, (1f - dist / r) * 2.5f) * near * near * (3f - 2f * near);
			if (alpha <= 0.01f) continue;
			float twinkle = 0.5f + 0.5f * (float) Math.sin(t * (1.5f + variety * 3f) + phase);
			double x = cx + dx;
			double y = cy + dy;
			double z = cz + dz;
			float size = (0.05f + variety * 0.06f) * scale;
			switch (m) {
				case SNOW -> {
					// Soft white flakes: a small halo, mostly core.
					mesh.orb(x, y, z, size * 1.8f, ColorUtil.withAlpha(0xFFF2F6FF, Math.round(90 * alpha)));
					mesh.orb(x, y, z, size * 0.8f, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(210 * alpha)));
				}
				case EMBERS -> {
					// Rising sparks stretched along their path, flickering.
					int color = ColorUtil.lerp(0xFFFF7A2E, 0xFFFFD27A, variety);
					float flicker = alpha * (0.55f + 0.45f * twinkle);
					Draw3D.line(mesh, x, y, z, x, y - 0.12f * scale, z, size * 0.7f,
						ColorUtil.withAlpha(color, Math.round(230 * flicker)), ColorUtil.withAlpha(color, 0));
					mesh.orb(x, y, z, size * 2.4f, ColorUtil.withAlpha(color, Math.round(110 * flicker)));
				}
				default -> {
					int color = m == STARS ? ColorUtil.lerp(a1, 0xFFFFFFFF, variety) : ColorUtil.lerp(0xFFD6FF7A, a2, variety * 0.4f);
					float glow = alpha * twinkle;
					mesh.orb(x, y, z, size * 3f, ColorUtil.withAlpha(color, Math.round(150 * glow)));
					mesh.orb(x, y, z, size, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(200 * glow)));
				}
			}
		}
	}

	/** Places particle i around the camera (at ox, oy, oz relative to the anchor): anywhere, or at the edge of the radius. */
	private void respawn(int i, int m, float ox, float oy, float oz, float r, boolean anywhere) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int o = i * STRIDE;
		double theta = random.nextDouble() * Math.PI * 2;
		double dist = (anywhere ? Math.cbrt(random.nextDouble()) : 0.75 + random.nextDouble() * 0.25) * r;
		data[o] = ox + (float) (Math.cos(theta) * dist);
		data[o + 2] = oz + (float) (Math.sin(theta) * dist);
		float y;
		if (m == SNOW) y = anywhere ? (random.nextFloat() * 2f - 1f) * r * 0.5f : r * 0.45f;
		else if (m == EMBERS) y = anywhere ? (random.nextFloat() * 2f - 1f) * r * 0.5f : -r * 0.45f;
		else y = (random.nextFloat() * 2f - 1f) * r * 0.4f;
		data[o + 1] = oy + y;
		data[o + 3] = (random.nextFloat() - 0.5f) * 0.6f;
		data[o + 4] = (random.nextFloat() - 0.5f) * 0.3f;
		data[o + 5] = (random.nextFloat() - 0.5f) * 0.6f;
		data[o + 6] = random.nextFloat() * 6.283f;
		data[o + 7] = random.nextFloat();
	}
}
