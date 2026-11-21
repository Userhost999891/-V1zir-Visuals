package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Rings where you jump. Glow: an expanding shockwave, a crisp rotating rim inside a soft halo.
 * Water: the ground ripples as if you landed in still water. A crown of drops is flung up and out and
 * a jet rises from the centre and falls back, while a train of rings runs out whose surface really
 * rises and sinks, catching the light on the near sides of the crests and shaded on the far ones.
 */
public final class JumpCirclesModule extends Module implements WorldEffect {
	private static final int SEGMENTS = 56;
	/** Water rings spread wider and settle slower than the shockwave. */
	private static final float WATER_REACH = 1.8f;
	private static final float WATER_TIME = 1.6f;
	private static final int WATER_SHADE = 0x08121C;
	/** The crown: drops flung up from around the centre, gone once they fall back; the jet in the middle. */
	private static final int CROWN_DROPS = 22;
	private static final float CROWN_SECONDS = 0.8f;
	private static final float GRAVITY = 11f;
	private static final float JET_START = 0.05f;
	private static final float JET_SECONDS = 0.55f;

	public final ModeSetting style = add(new ModeSetting("Style", "Glow", "Glow", "Water"));
	public final NumberSetting radius = add(new NumberSetting("Radius", 1.4, 0.5, 3.0, 0.05));
	public final NumberSetting duration = add(new NumberSetting("Duration", 1.1, 0.4, 3.0, 0.05).suffix("s"));
	public final NumberSetting shine = add(new NumberSetting("Shine", 0.8, 0.0, 1.5, 0.05).visibleIf(() -> style.is("Water")));

	private final List<Circle> circles = new ArrayList<>();
	private final Draw3D.Wave wave = new Draw3D.Wave();

	public JumpCirclesModule() {
		super("Jump Circles", "Rings when you jump", Category.VISUALS);
		setSubCategory("World");
	}

	@Override
	protected void onDisable() {
		circles.clear();
	}

	/**
	 * Called by LivingEntityMixin when the local player really jumps off the ground. Only jumps make
	 * rings: no landings, falls, knockback or steps.
	 */
	public void onJump() {
		circles.add(new Circle(mc.player.getX(), mc.player.getY() + 0.02, mc.player.getZ(), System.nanoTime()));
		if (circles.size() > 12) circles.remove(0);
	}

	@Override
	public void render3D(float tickDelta) {
		long now = System.nanoTime();
		boolean water = style.is("Water");
		float life = duration.f() * (water ? WATER_TIME : 1f);
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		for (int i = circles.size() - 1; i >= 0; i--) {
			Circle c = circles.get(i);
			float age = (now - c.born) / 1.0e9f;
			if (age > life) {
				circles.remove(i);
				continue;
			}
			float p = age / life;
			if (water) {
				// The waves settle by themselves; the last moments only fade what is left.
				int alpha = Math.round(255f * (1f - Anim.easeInOutCubic(Math.max(0f, (p - 0.7f) / 0.3f))));
				int tint = ColorUtil.lerp(0xFFFFFFFF, a1, 0.25f);
				double reach = radius.f() * WATER_REACH;
				Draw3D.ripple(mesh, WorldEffectRenderer.translucent(), c.x, c.y, c.z, reach, p, shine.f(), ColorUtil.withAlpha(tint, alpha),
					ColorUtil.withAlpha(WATER_SHADE, alpha));
				crown(mesh, c, age, reach, tint);
				continue;
			}
			double r = Anim.easeOutCubic(p) * radius.f();
			// Holds its brightness while it spreads, then fades out as it slows down.
			float fade = 1f - Anim.easeInOutCubic(Math.max(0f, (p - 0.25f) / 0.75f));
			float spin = age * 2f;
			Draw3D.flatRing(mesh, c.x, c.y, c.z, r, 0.22 + r * 0.06, SEGMENTS, wave.set(a1, a2, spin, 0.32f * fade, SEGMENTS));
			Draw3D.flatRing(mesh, c.x, c.y, c.z, r, 0.05, SEGMENTS, wave.set(a1, a2, spin, 0.95f * fade, SEGMENTS));
		}
	}

	/**
	 * The splash of the Water style: drops flung up and out from around the centre, each a streak along its flight
	 * with a glint at its head, and a jet that rises from the centre and falls back with a drop on its top. Sized
	 * with the rings.
	 */
	private static void crown(WorldEffectRenderer.Mesh glow, Circle c, float age, double reach, int tint) {
		double scale = reach / (1.4 * WATER_REACH);
		if (age < CROWN_SECONDS) {
			float fade = 1f - age / CROWN_SECONDS;
			int head = ColorUtil.withAlpha(tint, Math.round(240f * fade));
			int tail = ColorUtil.withAlpha(tint, Math.round(36f * fade));
			int glint = ColorUtil.withAlpha(tint, Math.round(150f * fade));
			for (int i = 0; i < CROWN_DROPS; i++) {
				double angle = (i + hash(c.seed, i, 1) * 0.8) / CROWN_DROPS * Math.PI * 2.0;
				double out = (0.8 + 1.0 * hash(c.seed, i, 2)) * scale;
				double up = (1.9 + 1.7 * hash(c.seed, i, 3)) * Math.sqrt(scale);
				double height = up * age - 0.5 * GRAVITY * age * age;
				if (height < 0.0) continue;
				double r = 0.16 * reach + out * age;
				double cos = Math.cos(angle), sin = Math.sin(angle);
				double x = c.x + cos * r, y = c.y + height, z = c.z + sin * r;
				double vx = cos * out, vy = up - GRAVITY * age, vz = sin * out;
				Draw3D.line(glow, x, y, z, x - vx * 0.045, y - vy * 0.045, z - vz * 0.045, 0.02f * (float) Math.sqrt(scale), head, tail);
				glow.orb(x, y, z, 0.032f * (float) Math.sqrt(scale), glint);
			}
		}
		float jet = (age - JET_START) / JET_SECONDS;
		if (jet > 0f && jet < 1f) {
			double top = c.y + 0.62 * Math.sqrt(scale) * Math.sin(Math.PI * jet);
			float fade = 1f - jet * jet;
			int head = ColorUtil.withAlpha(tint, Math.round(230f * fade));
			Draw3D.line(glow, c.x, c.y, c.z, c.x, top, c.z, 0.04f * (float) Math.sqrt(scale), ColorUtil.withAlpha(tint, Math.round(60f * fade)), head);
			glow.orb(c.x, top, c.z, 0.07f * (float) Math.sqrt(scale), head);
		}
	}

	/** Stable pseudo-random 0..1 per circle, drop and property. */
	private static float hash(int seed, int i, int k) {
		int h = seed * 0x9E3779B1 + i * 0x85EBCA77 + k * 0xC2B2AE3D;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h >>> 8) / 16777216f;
	}

	private record Circle(double x, double y, double z, long born, int seed) {
		Circle(double x, double y, double z, long born) {
			this(x, y, z, born, (int) (born ^ born >>> 32));
		}
	}
}
