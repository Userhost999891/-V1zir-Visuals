package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.Theme;

import java.util.ArrayList;
import java.util.List;

/** Expanding shockwave rings where you jump and land: a crisp rotating rim inside a soft halo. */
public final class JumpCirclesModule extends Module implements WorldEffect {
	private static final int SEGMENTS = 56;

	public final NumberSetting radius = add(new NumberSetting("Radius", 1.4, 0.5, 3.0, 0.05));
	public final NumberSetting duration = add(new NumberSetting("Duration", 1.1, 0.4, 3.0, 0.05).suffix("s"));
	public final BoolSetting onLanding = add(new BoolSetting("On landing", true));

	private final List<Circle> circles = new ArrayList<>();
	private final Draw3D.Wave wave = new Draw3D.Wave();
	/** In the air after a real jump, so the landing ring only follows jumps, not walking off an edge. */
	private boolean jumping;

	public JumpCirclesModule() {
		super("Jump Circles", "Rings when you jump and land", Category.EFFECTS);
		setSubCategory("Cosmetic");
	}

	@Override
	protected void onDisable() {
		circles.clear();
		jumping = false;
	}

	/** Called by LivingEntityMixin when the local player really jumps. */
	public void onJump() {
		spawn();
		jumping = true;
	}

	@Override
	public void onTick() {
		if (mc.player == null) {
			jumping = false;
			return;
		}
		if (!jumping || !mc.player.isOnGround()) return;
		// Ignore the first tick: the player is still on the ground in the tick of the jump itself.
		if (mc.player.getVelocity().y > 0) return;
		jumping = false;
		if (onLanding.on() && !mc.player.isTouchingWater()) spawn();
	}

	private void spawn() {
		circles.add(new Circle(mc.player.getX(), mc.player.getY() + 0.02, mc.player.getZ(), System.nanoTime()));
		if (circles.size() > 12) circles.remove(0);
	}

	@Override
	public void render3D(float tickDelta) {
		long now = System.nanoTime();
		float life = duration.f();
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
