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
 * Water: the ground ripples as if a drop fell into still water, a train of rings that catch the sky
 * on their crests and darken in their troughs, spreading out and settling.
 */
public final class JumpCirclesModule extends Module implements WorldEffect {
	private static final int SEGMENTS = 56;
	/** Water rings spread wider and settle slower than the shockwave. */
	private static final float WATER_REACH = 1.8f;
	private static final float WATER_TIME = 1.6f;
	private static final int WATER_SHADE = 0x08121C;

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
				int light = ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFFFF, a1, 0.25f), alpha);
				Draw3D.ripple(mesh, WorldEffectRenderer.translucent(), c.x, c.y, c.z, radius.f() * WATER_REACH, p, shine.f(), light,
					ColorUtil.withAlpha(WATER_SHADE, alpha));
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

	private record Circle(double x, double y, double z, long born) {
	}
}
