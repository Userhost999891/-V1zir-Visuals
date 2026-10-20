package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

/**
 * A finishing effect where an entity you were fighting dies: a lightning bolt, rising souls or a
 * burst ring. Everything is client side and purely visual; particle paths are closed formulas of
 * their age, so nothing is simulated per frame.
 */
public final class KillEffectsModule extends Module implements WorldEffect {
	private static final int LIGHTNING = 0;
	private static final int SOUL = 1;
	private static final int BURST = 2;
	private static final int MAX_EFFECTS = 8;
	// Bolt layout: segments of (x0, y0, z0, x1, y1, z1, width) relative to the feet.
	private static final int BOLT_SEGMENTS = 16;
	private static final int BRANCHES = 3;
	private static final int BRANCH_SEGMENTS = 4;
	private static final int SOULS = 9;
	private static final int SOUL_TAIL = 7;
	private static final int SPARKS = 26;

	public final ModeSetting mode = add(new ModeSetting("Mode", "Lightning", "Lightning", "Soul", "Burst"));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05).suffix("x"));
	public final NumberSetting duration = add(new NumberSetting("Duration", 1.0, 0.4, 3.0, 0.05).suffix("x"));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF7FD4FF, false).visibleIf(() -> !themeColor.on()));
	public final BoolSetting sound = add(new BoolSetting("Sound", false).visibleIf(() -> mode.is("Lightning")));

	private final List<Kill> kills = new ArrayList<>();
	private int ringColor;
	private final IntUnaryOperator ringColorAt = i -> ringColor;

	public KillEffectsModule() {
		super("Kill Effects", "Lightning, souls or a burst on kills", Category.EFFECTS);
	}

	@Override
	public String suffix() {
		return mode.get();
	}

	@Override
	protected void onDisable() {
		kills.clear();
	}

	/** The entity died shortly after you hit it (Effects). */
	public void spawn(LivingEntity entity) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int type = mode.index();
		float s = size.f();
		float h = entity.getHeight();
		float[] data = switch (type) {
			case LIGHTNING -> bolt(random, 14f + 4f * s, s);
			case SOUL -> souls(random, entity.getWidth(), h);
			default -> sparks(random, s);
		};
		float life = duration.f() * (type == SOUL ? 1.8f : type == BURST ? 1.0f : 0.8f);
		if (kills.size() == MAX_EFFECTS) kills.remove(0);
		kills.add(new Kill(type, entity.getX(), entity.getY(), entity.getZ(), h, s, life, random.nextFloat() * 100f, System.nanoTime(), data));
		if (type == LIGHTNING && sound.on() && mc.world != null) {
			mc.world.playSoundClient(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER,
				1f, 0.9f + random.nextFloat() * 0.2f, false);
		}
	}

	@Override
	public void render3D(float tickDelta) {
		if (kills.isEmpty()) return;
		long now = System.nanoTime();
		int a1 = themeColor.on() ? Theme.accent() : color.argb();
		int a2 = themeColor.on() ? Theme.accent2() : ColorUtil.brighten(a1, 0.45f);
		for (int i = kills.size() - 1; i >= 0; i--) {
			Kill kill = kills.get(i);
			float age = (now - kill.born) / 1.0e9f;
			if (age >= kill.life) {
				kills.remove(i);
				continue;
			}
			switch (kill.type) {
				case LIGHTNING -> lightning(kill, age, a1);
				case SOUL -> soul(kill, age, a1, a2);
				default -> burst(kill, age, a1, a2);
			}
		}
	}

	// ------------------------------------------------------------------ lightning

	/** A jagged bolt from the sky that ends exactly on the target, with a few side branches. */
	private static float[] bolt(ThreadLocalRandom random, float height, float size) {
		float[] x = new float[BOLT_SEGMENTS + 1];
		float[] z = new float[BOLT_SEGMENTS + 1];
		float ox = 0f;
		float oz = 0f;
		for (int i = 0; i <= BOLT_SEGMENTS; i++) {
			x[i] = ox;
			z[i] = oz;
			ox += (random.nextFloat() - 0.5f) * 1.3f * size;
			oz += (random.nextFloat() - 0.5f) * 1.3f * size;
		}
		// Bend the random walk so its last point lands on the target.
		float ex = x[BOLT_SEGMENTS];
		float ez = z[BOLT_SEGMENTS];
		for (int i = 0; i <= BOLT_SEGMENTS; i++) {
			x[i] -= ex * i / BOLT_SEGMENTS;
			z[i] -= ez * i / BOLT_SEGMENTS;
		}
		float step = height / BOLT_SEGMENTS;
		float[] segments = new float[(BOLT_SEGMENTS + BRANCHES * BRANCH_SEGMENTS) * 7];
		int o = 0;
		for (int i = 0; i < BOLT_SEGMENTS; i++) {
			o = segment(segments, o, x[i], height - step * i, z[i], x[i + 1], height - step * (i + 1), z[i + 1], 1f);
		}
		for (int b = 0; b < BRANCHES; b++) {
			int from = 2 + random.nextInt(BOLT_SEGMENTS - 6);
			double angle = random.nextDouble() * Math.PI * 2;
			float bx = x[from];
			float by = height - step * from;
			float bz = z[from];
			for (int k = 0; k < BRANCH_SEGMENTS; k++) {
				float nx = bx + (float) Math.cos(angle) * 0.8f * size + (random.nextFloat() - 0.5f) * 0.6f * size;
				float nz = bz + (float) Math.sin(angle) * 0.8f * size + (random.nextFloat() - 0.5f) * 0.6f * size;
				float ny = by - step * (0.6f + random.nextFloat() * 0.5f);
				o = segment(segments, o, bx, by, bz, nx, ny, nz, 0.55f - k * 0.1f);
				bx = nx;
				by = ny;
				bz = nz;
			}
		}
		return segments;
	}

	private static int segment(float[] s, int o, float x0, float y0, float z0, float x1, float y1, float z1, float width) {
		s[o] = x0;
		s[o + 1] = y0;
		s[o + 2] = z0;
		s[o + 3] = x1;
		s[o + 4] = y1;
		s[o + 5] = z1;
		s[o + 6] = width;
		return o + 7;
	}

	private void lightning(Kill kill, float age, int accent) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		float p = age / kill.life;
		float fade = (float) Math.pow(1f - p, 1.5);
		// Strobes like vanilla lightning for the first third, then fades out.
		if (p < 0.35f) fade *= 0.55f + 0.45f * hash(kill.seed + (float) Math.floor(age * 28f));
		float s = kill.size;
		int glow = ColorUtil.fade(accent, 0.75f * fade);
		int core = ColorUtil.withAlpha(0xFFFFFF, Math.round(255 * fade));
		float[] seg = kill.data;
		for (int o = 0; o < seg.length; o += 7) {
			double x0 = kill.x + seg[o], y0 = kill.y + seg[o + 1], z0 = kill.z + seg[o + 2];
			double x1 = kill.x + seg[o + 3], y1 = kill.y + seg[o + 4], z1 = kill.z + seg[o + 5];
			float w = seg[o + 6];
			Draw3D.line(mesh, x0, y0, z0, x1, y1, z1, 0.42f * s * w, glow, glow);
			Draw3D.line(mesh, x0, y0, z0, x1, y1, z1, 0.07f * s * w, core, core);
		}
		mesh.orb(kill.x, kill.y + 0.15, kill.z, 1.4f * s * (0.5f + 0.5f * fade), ColorUtil.fade(accent, 0.8f * fade));
		mesh.orb(kill.x, kill.y + 0.15, kill.z, 0.5f * s, core);
		float spread = Anim.easeOutCubic(age / 0.45f);
		ringColor = ColorUtil.fade(accent, 0.9f * (1f - p));
		Draw3D.flatRing(mesh, kill.x, kill.y + 0.03, kill.z, (0.4f + 2.2f * spread) * s, 0.08, 48, ringColorAt);
	}

	// ------------------------------------------------------------------ soul

	/** Per soul: start offset (x, y, z), start delay, sway phase and rise speed. */
	private static float[] souls(ThreadLocalRandom random, float width, float height) {
		float[] d = new float[SOULS * 6];
		for (int i = 0; i < SOULS; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			float radius = width * 0.5f * random.nextFloat();
			d[i * 6] = (float) Math.cos(angle) * radius;
			d[i * 6 + 1] = height * (0.2f + 0.7f * random.nextFloat());
			d[i * 6 + 2] = (float) Math.sin(angle) * radius;
			d[i * 6 + 3] = random.nextFloat() * 0.35f;
			d[i * 6 + 4] = random.nextFloat() * 6.283f;
			d[i * 6 + 5] = 0.9f + random.nextFloat() * 0.8f;
		}
		return d;
	}

	private void soul(Kill kill, float age, int a1, int a2) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		float p = age / kill.life;
		float fade = (float) Math.pow(1f - p, 1.3);
		float s = kill.size;
		float[] d = kill.data;
		// A soft wisp leaving the body.
		mesh.orb(kill.x, kill.y + kill.height * 0.6 + age * 0.6, kill.z, 0.9f * s, ColorUtil.fade(a1, 0.35f * fade));
		for (int i = 0; i < SOULS; i++) {
			int o = i * 6;
			float t = age - d[o + 3];
			if (t <= 0f) continue;
			float alpha = fade * Math.min(1f, t / 0.15f);
			for (int k = SOUL_TAIL - 1; k >= 0; k--) {
				float tk = t - k * 0.05f;
				if (tk < 0f) continue;
				float sway = 0.28f * s * Math.min(1f, tk);
				double x = kill.x + d[o] + Math.sin(tk * 2.2f + d[o + 4]) * sway;
				double y = kill.y + d[o + 1] + d[o + 5] * tk + 0.25f * tk * tk;
				double z = kill.z + d[o + 2] + Math.cos(tk * 1.7f + d[o + 4]) * sway;
				float tail = 1f - k / (float) SOUL_TAIL;
				mesh.orb(x, y, z, (0.08f + 0.18f * tail) * s, ColorUtil.fade(ColorUtil.lerp(a2, a1, tail), 0.85f * alpha * tail));
				if (k == 0) mesh.orb(x, y, z, 0.08f * s, ColorUtil.withAlpha(0xFFFFFF, Math.round(230 * alpha)));
			}
		}
	}

	// ------------------------------------------------------------------ burst

	/** Per spark: horizontal direction (x, z), speed and upward speed. */
	private static float[] sparks(ThreadLocalRandom random, float size) {
		float[] d = new float[SPARKS * 4];
		for (int i = 0; i < SPARKS; i++) {
			double angle = Math.PI * 2 * (i + random.nextFloat() * 0.6f) / SPARKS;
			d[i * 4] = (float) Math.cos(angle);
			d[i * 4 + 1] = (float) Math.sin(angle);
			d[i * 4 + 2] = (4f + random.nextFloat() * 2f) * size;
			d[i * 4 + 3] = -0.5f + random.nextFloat() * 2f;
		}
		return d;
	}

	private void burst(Kill kill, float age, int a1, int a2) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		float p = age / kill.life;
		float fade = 1f - p;
		float s = kill.size;
		double cy = kill.y + kill.height * 0.5;
		// Drag k: distance v / k * (1 - e^(-k t)) covers most of the way early, then glides.
		float drag = 3.2f;
		float travel = (1f - (float) Math.exp(-drag * age)) / drag;
		float[] d = kill.data;
		for (int i = 0; i < SPARKS; i++) {
			int o = i * 4;
			float r = d[o + 2] * travel;
			double x = kill.x + d[o] * r;
			double y = cy + d[o + 3] * age - 2f * age * age;
			double z = kill.z + d[o + 1] * r;
			int c = ColorUtil.lerp(a1, a2, i / (float) SPARKS);
			mesh.orb(x, y, z, (0.1f + 0.12f * fade) * s * 1.6f, ColorUtil.fade(c, 0.7f * fade * fade));
			mesh.orb(x, y, z, 0.07f * s, ColorUtil.withAlpha(0xFFFFFF, Math.round(220 * fade * fade)));
		}
		float flash = Math.max(0f, 1f - p * 5f);
		if (flash > 0f) mesh.orb(kill.x, cy, kill.z, 1.5f * s, ColorUtil.fade(a1, flash));
		float spread = Anim.easeOutCubic(p);
		ringColor = ColorUtil.fade(a1, 0.9f * fade);
		Draw3D.flatRing(mesh, kill.x, kill.y + 0.03, kill.z, (0.3f + 3f * spread) * s, 0.14f * fade, 56, ringColorAt);
		ringColor = ColorUtil.fade(a2, 0.8f * fade);
		Draw3D.bandRing(mesh, kill.x, cy, kill.z, (0.3f + 1.6f * spread) * s, 0.06, 48, ringColorAt);
	}

	/** Pseudo random 0..1 from a float, for the lightning strobe. */
	private static float hash(float v) {
		float n = (float) (Math.sin(v * 12.9898) * 43758.5453);
		return n - (float) Math.floor(n);
	}

	private record Kill(int type, double x, double y, double z, float height, float size, float life, float seed, long born, float[] data) {
	}
}
