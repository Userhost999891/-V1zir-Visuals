package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Bursts of glowing sparks when you hit something. */
public final class HitParticlesModule extends Module implements WorldEffect {
	public final NumberSetting count = add(new NumberSetting("Count", 14, 2, 60, 1));
	public final NumberSetting particleSize = add(new NumberSetting("Size", 1.0, 0.3, 3.0, 0.05).suffix("x"));
	public final NumberSetting lifetime = add(new NumberSetting("Lifetime", 1.4, 0.3, 4.0, 0.05).suffix("s"));

	private final List<Spark> sparks = new ArrayList<>();
	private long lastFrame = System.nanoTime();

	public HitParticlesModule() {
		super("Hit Particles", "Sparks on every hit", Category.EFFECTS);
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
		while (sparks.size() > 600) sparks.remove(0);
	}

	@Override
	public void render3D(float tickDelta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.05f);
		lastFrame = now;
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		Iterator<Spark> it = sparks.iterator();
		while (it.hasNext()) {
			Spark s = it.next();
			float age = (now - s.born) / 1.0e9f;
			if (age > s.life) {
				it.remove();
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
			float fade = 1f - age / s.life;
			float size = (0.06f + 0.08f * fade) * particleSize.f();
			int color = ColorUtil.withAlpha(ColorUtil.lerp(a1, a2, s.tint), Math.round(255 * fade));
			mesh.orb(s.x, s.y, s.z, size * 2.2f, ColorUtil.fade(color, 0.6f));
			mesh.orb(s.x, s.y, s.z, size * 0.7f, ColorUtil.withAlpha(0xFFFFFF, Math.round(220 * fade)));
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
