package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Bursts of sparks when you hit something: hot streaks that fly out, fall and bounce off the ground. */
public final class HitParticlesModule extends Module implements WorldEffect {
	private static final int MAX_SPARKS = 600;
	/** Seconds of motion a streak spans; fast sparks draw long, slow ones shrink to a dot. */
	private static final double STREAK = 0.035;

	public final NumberSetting count = add(new NumberSetting("Count", 14, 2, 60, 1));
	public final NumberSetting particleSize = add(new NumberSetting("Size", 1.0, 0.3, 3.0, 0.05).suffix("x"));
	public final NumberSetting lifetime = add(new NumberSetting("Lifetime", 1.4, 0.3, 4.0, 0.05).suffix("s"));

	private final List<Spark> sparks = new ArrayList<>();
	private long lastFrame = System.nanoTime();

	public HitParticlesModule() {
		super("Hit Particles", "Sparks on every hit", Category.COMBAT);
		setSubCategory("Hits");
	}

	@Override
	protected void onDisable() {
		sparks.clear();
	}

	public void spawn(Entity entity) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double cx = entity.getX();
		double cy = entity.getY() + entity.getHeight() * 0.6;
		double cz = entity.getZ();
		double floor = entity.getY() + 0.05;
		int n = count.i();
		for (int i = 0; i < n; i++) {
			double theta = random.nextDouble() * Math.PI * 2;
			double phi = Math.acos(random.nextDouble() * 2 - 1);
			double speed = 1.5 + random.nextDouble() * 2.5;
			sparks.add(new Spark(cx, cy, cz,
				Math.sin(phi) * Math.cos(theta) * speed, Math.abs(Math.cos(phi)) * speed * 0.9 + 1.0, Math.sin(phi) * Math.sin(theta) * speed,
				floor, random.nextFloat(), System.nanoTime(), lifetime.f() * (0.7f + random.nextFloat() * 0.6f)));
		}
		if (sparks.size() > MAX_SPARKS) sparks.subList(0, sparks.size() - MAX_SPARKS).clear();
	}

	@Override
	public void render3D(float tickDelta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.05f);
		lastFrame = now;
		if (sparks.isEmpty()) return;
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		float scale = particleSize.f();
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		for (int i = sparks.size() - 1; i >= 0; i--) {
			Spark s = sparks.get(i);
			float age = (now - s.born) / 1.0e9f;
			if (age > s.life) {
				// Order does not matter for additive sparks: swap the last one in.
				int last = sparks.size() - 1;
				sparks.set(i, sparks.get(last));
				sparks.remove(last);
				continue;
			}
			s.vy -= 9.0 * dt;
			s.vx *= 1.0 - 1.6 * dt;
			s.vz *= 1.0 - 1.6 * dt;
			s.x += s.vx * dt;
			s.y += s.vy * dt;
			s.z += s.vz * dt;
			if (s.y < s.floor) {
				s.y = s.floor;
				s.vy = -s.vy * 0.45;
			}
			float near = Draw3D.cameraFade(s.x, s.y, s.z, 0.5f, 1.4f);
			if (near <= 0.01f) continue;
			float fade = 1f - age / s.life;
			float a = fade * near;
			int color = ColorUtil.lerp(a1, a2, s.tint);
			int hot = ColorUtil.lerp(color, 0xFFFFFFFF, 0.55f * fade);
			float width = (0.03f + 0.04f * fade) * scale;
			Draw3D.line(mesh, s.x, s.y, s.z, s.x - s.vx * STREAK, s.y - s.vy * STREAK, s.z - s.vz * STREAK, width,
				ColorUtil.withAlpha(hot, Math.round(255 * a)), ColorUtil.withAlpha(color, 0));
			mesh.orb(s.x, s.y, s.z, width * 2.6f, ColorUtil.withAlpha(color, Math.round(150 * a)));
		}
	}

	private static final class Spark {
		double x, y, z, vx, vy, vz;
		final double floor;
		final float tint;
		final long born;
		final float life;

		Spark(double x, double y, double z, double vx, double vy, double vz, double floor, float tint, long born, float life) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.floor = floor;
			this.tint = tint;
			this.born = born;
			this.life = life;
		}
	}
}
