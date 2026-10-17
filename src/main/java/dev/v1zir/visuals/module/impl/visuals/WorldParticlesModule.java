package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;

import java.util.concurrent.ThreadLocalRandom;

/** Ambient glowing particles floating around you. */
public final class WorldParticlesModule extends Module implements WorldEffect {
	public final ModeSetting mode = add(new ModeSetting("Mode", "Fireflies", "Fireflies", "Snow", "Stars", "Embers"));
	public final NumberSetting count = add(new NumberSetting("Count", 140, 20, 600, 10));
	public final NumberSetting radius = add(new NumberSetting("Radius", 18, 6, 40, 1));
	public final NumberSetting particleSize = add(new NumberSetting("Size", 1.0, 0.3, 3.0, 0.05).suffix("x"));

	private float[] data = new float[0];
	private long lastFrame = System.nanoTime();
	private static final int STRIDE = 8;

	public WorldParticlesModule() {
		super("World Particles", "Fireflies, snow, stars or embers around you", Category.WORLD);
	}

	@Override
	public String suffix() {
		return mode.get();
	}

	@Override
	public void render3D(float tickDelta) {
		int n = count.i();
		float r = radius.f();
		double cx = WorldEffectRenderer.camX;
		double cy = WorldEffectRenderer.camY;
		double cz = WorldEffectRenderer.camZ;
		if (data.length != n * STRIDE) {
			data = new float[n * STRIDE];
			for (int i = 0; i < n; i++) respawn(i, cx, cy, cz, r, true);
		}
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.05f);
		lastFrame = now;
		float t = V1zir.time();
		String m = mode.get();
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		for (int i = 0; i < n; i++) {
			int o = i * STRIDE;
			float phase = data[o + 6];
			switch (m) {
				case "Snow" -> {
					data[o + 1] -= dt * (0.6f + data[o + 7] * 0.6f);
					data[o] += (float) Math.sin(t * 0.7f + phase) * dt * 0.3f;
				}
				case "Embers" -> {
					data[o + 1] += dt * (0.4f + data[o + 7] * 0.8f);
					data[o] += (float) Math.sin(t * 1.3f + phase) * dt * 0.4f;
				}
				default -> {
					data[o] += data[o + 3] * dt;
					data[o + 1] += data[o + 4] * dt + (float) Math.sin(t * 0.9f + phase) * dt * 0.15f;
					data[o + 2] += data[o + 5] * dt;
				}
			}
			double x = cx + data[o];
			double y = cy + data[o + 1];
			double z = cz + data[o + 2];
			float dist2 = data[o] * data[o] + data[o + 1] * data[o + 1] + data[o + 2] * data[o + 2];
			if (dist2 > r * r * 1.1f) {
				respawn(i, cx, cy, cz, r, false);
				continue;
			}
			float edge = 1f - (float) Math.sqrt(dist2) / r;
			float twinkle = 0.5f + 0.5f * (float) Math.sin(t * (1.5f + data[o + 7] * 3f) + phase);
			float size = (0.05f + data[o + 7] * 0.06f) * particleSize.f();
			int color = switch (m) {
				case "Snow" -> 0xFFF2F6FF;
				case "Embers" -> ColorUtil.lerp(0xFFFF8A3D, 0xFFFFD27A, data[o + 7]);
				case "Stars" -> ColorUtil.lerp(a1, 0xFFFFFFFF, data[o + 7]);
				default -> ColorUtil.lerp(0xFFD6FF7A, a2, data[o + 7] * 0.4f);
			};
			float alpha = Math.min(1f, edge * 2.5f) * (m.equals("Snow") ? 0.85f : twinkle);
			mesh.orb(x, y, z, size * 3f, ColorUtil.withAlpha(color, Math.round(150 * alpha)));
			mesh.orb(x, y, z, size, ColorUtil.withAlpha(0xFFFFFF, Math.round(200 * alpha)));
		}
	}

	private void respawn(int i, double cx, double cy, double cz, float r, boolean anywhere) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int o = i * STRIDE;
		double theta = random.nextDouble() * Math.PI * 2;
		double dist = (anywhere ? Math.cbrt(random.nextDouble()) : 0.75 + random.nextDouble() * 0.25) * r;
		data[o] = (float) (Math.cos(theta) * dist);
		data[o + 2] = (float) (Math.sin(theta) * dist);
		String m = mode.get();
		if (m.equals("Snow")) data[o + 1] = anywhere ? (random.nextFloat() * 2f - 1f) * r * 0.5f : r * 0.45f;
		else if (m.equals("Embers")) data[o + 1] = anywhere ? (random.nextFloat() * 2f - 1f) * r * 0.5f : -r * 0.45f;
		else data[o + 1] = (random.nextFloat() * 2f - 1f) * r * 0.4f;
		data[o + 3] = (random.nextFloat() - 0.5f) * 0.6f;
		data[o + 4] = (random.nextFloat() - 0.5f) * 0.3f;
		data[o + 5] = (random.nextFloat() - 0.5f) * 0.6f;
		data[o + 6] = random.nextFloat() * 6.283f;
		data[o + 7] = random.nextFloat();
	}
}
