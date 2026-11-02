package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.world.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Every now and then a meteor streaks across the night sky: a glowing head over a white core trailing
 * a tapering tail. Meteors fly along great circles of a sphere around the camera, inside the far plane,
 * so they stay with the sky while you walk and set behind terrain. Each is a handful of quads, and
 * nothing is drawn by day (unless asked), in the rain or outside the Overworld.
 */
public final class ShootingStarsModule extends Module implements WorldEffect {
	private static final int MAX = 8;
	/** Radius of the sky sphere in blocks, inside the far plane even at the lowest render distance. */
	private static final float DISTANCE = 110f;
	private static final int TRAIL = 14;

	public final NumberSetting rate = add(new NumberSetting("Rate", 12, 1, 60, 1).suffix("/min"));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.4, 2.5, 0.05).suffix("x"));
	public final NumberSetting trail = add(new NumberSetting("Trail", 1.0, 0.3, 2.5, 0.05).suffix("x"));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", false));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFF5CD6, false).visibleIf(() -> !themeColor.on()));
	public final BoolSetting daytime = add(new BoolSetting("Daytime", false));

	private final Meteor[] meteors = new Meteor[MAX];
	private long lastFrame = System.nanoTime();

	public ShootingStarsModule() {
		super("Shooting Stars", "Meteors with glowing trails in the night sky", Category.SHADERS);
		setSubCategory("Sky");
		for (int i = 0; i < MAX; i++) meteors[i] = new Meteor();
	}

	@Override
	protected void onDisable() {
		for (Meteor meteor : meteors) meteor.alive = false;
	}

	/** How dark the sky is for meteors: 1 at night, 0 by day and in the rain. */
	private float night(float tickDelta) {
		if (mc.world == null || mc.world.getRegistryKey() != World.OVERWORLD) return 0f;
		float time = Math.floorMod(mc.world.getTimeOfDay(), 24000L);
		float n = smooth((time - 12600f) / 1000f) * (1f - smooth((time - 22400f) / 1000f));
		if (daytime.on()) n = Math.max(n, 0.7f);
		return n * (1f - mc.world.getRainGradient(tickDelta));
	}

	private static float smooth(float t) {
		t = Anim.clamp01(t);
		return t * t * (3f - 2f * t);
	}

	@Override
	public void render3D(float tickDelta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.1f);
		lastFrame = now;
		float sky = night(tickDelta);
		// Spawning is a Poisson process: on average Rate meteors a minute.
		if (sky > 0.05f && ThreadLocalRandom.current().nextFloat() < rate.f() / 60f * dt) spawn();
		int c = themeColor.on() ? Theme.accent() : color.argb();
		int core = ColorUtil.lerp(c, 0xFFFFFFFF, 0.65f);
		float s = size.f();
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		double cx = WorldEffectRenderer.camX, cy = WorldEffectRenderer.camY, cz = WorldEffectRenderer.camZ;
		for (Meteor m : meteors) {
			if (!m.alive) continue;
			m.age += dt;
			if (m.age >= m.life) {
				m.alive = false;
				continue;
			}
			float p = m.age / m.life;
			// Flares up quickly, burns, and fades out as it runs dry.
			float bright = sky * Anim.clamp01(p / 0.12f) * (1f - smooth((p - 0.55f) / 0.45f));
			if (bright <= 0.01f) continue;
			float headAngle = m.speed * m.age;
			// The tail is the arc the head covered, up to the trail's length.
			float tail = Math.min(headAngle, m.speed * 0.32f * trail.f());
			float px = 0f, py = 0f, pz = 0f;
			for (int k = 0; k <= TRAIL; k++) {
				float angle = headAngle - tail * k / TRAIL;
				float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
				float x = (m.dx * cos + m.tx * sin) * DISTANCE, y = (m.dy * cos + m.ty * sin) * DISTANCE, z = (m.dz * cos + m.tz * sin) * DISTANCE;
				if (k > 0) {
					float f0 = 1f - (k - 1) / (float) TRAIL, f1 = 1f - k / (float) TRAIL;
					Draw3D.line(mesh, cx + px, cy + py, cz + pz, cx + x, cy + y, cz + z, 0.38f * s * m.size * f0,
						ColorUtil.fade(c, 0.85f * bright * f0 * f0), ColorUtil.fade(c, 0.85f * bright * f1 * f1));
					if (k <= TRAIL / 3) {
						Draw3D.line(mesh, cx + px, cy + py, cz + pz, cx + x, cy + y, cz + z, 0.1f * s * m.size * f0,
							ColorUtil.fade(core, bright * f0), ColorUtil.fade(core, bright * f1 * f1));
					}
				} else {
					mesh.orb(cx + x, cy + y, cz + z, 1.5f * s * m.size, ColorUtil.fade(c, 0.75f * bright));
					mesh.orb(cx + x, cy + y, cz + z, 0.45f * s * m.size, ColorUtil.fade(0xFFFFFFFF, bright));
				}
				px = x;
				py = y;
				pz = z;
			}
		}
	}

	/** A meteor high in the sky, heading sideways and somewhat down along a great circle. */
	private void spawn() {
		Meteor m = null;
		for (Meteor meteor : meteors) {
			if (!meteor.alive) {
				m = meteor;
				break;
			}
		}
		if (m == null) return;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double azimuth = random.nextDouble() * Math.PI * 2;
		double elevation = Math.toRadians(28 + random.nextDouble() * 42);
		m.dx = (float) (Math.cos(elevation) * Math.cos(azimuth));
		m.dy = (float) Math.sin(elevation);
		m.dz = (float) (Math.cos(elevation) * Math.sin(azimuth));
		// Travel direction: sideways along the sky with a downward lean, made perpendicular to the start direction.
		double heading = azimuth + (random.nextBoolean() ? 1 : -1) * Math.PI / 2 + (random.nextDouble() - 0.5) * 0.8;
		float tx = (float) Math.cos(heading), ty = -0.25f - random.nextFloat() * 0.5f, tz = (float) Math.sin(heading);
		float along = tx * m.dx + ty * m.dy + tz * m.dz;
		tx -= along * m.dx;
		ty -= along * m.dy;
		tz -= along * m.dz;
		float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
		m.tx = tx / length;
		m.ty = ty / length;
		m.tz = tz / length;
		m.speed = (float) Math.toRadians(14 + random.nextDouble() * 16);
		m.life = 0.7f + random.nextFloat() * 0.8f;
		m.size = 0.7f + random.nextFloat() * 0.6f;
		m.age = 0f;
		m.alive = true;
	}

	/** One meteor slot; reused, so meteors allocate nothing. */
	private static final class Meteor {
		boolean alive;
		/** Start direction from the camera and travel direction (both unit, perpendicular). */
		float dx, dy, dz, tx, ty, tz;
		/** Radians per second along the sky. */
		float speed;
		float age, life, size;
	}
}
