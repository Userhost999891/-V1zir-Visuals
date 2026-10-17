package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Expanding glowing rings where you jump and land. */
public final class JumpCirclesModule extends Module implements WorldEffect {
	public final NumberSetting radius = add(new NumberSetting("Radius", 1.4, 0.5, 3.0, 0.05));
	public final NumberSetting duration = add(new NumberSetting("Duration", 1.1, 0.4, 3.0, 0.05).suffix("s"));
	public final BoolSetting onLanding = add(new BoolSetting("On landing", true));

	private final List<Circle> circles = new ArrayList<>();
	private boolean wasOnGround = true;

	public JumpCirclesModule() {
		super("Jump Circles", "Rings when you jump and land", Category.EFFECTS);
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		boolean onGround = mc.player.isOnGround();
		if (wasOnGround && !onGround && mc.player.getVelocity().y > 0.2) spawn();
		else if (!wasOnGround && onGround && onLanding.on()) spawn();
		wasOnGround = onGround;
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
		Iterator<Circle> it = circles.iterator();
		while (it.hasNext()) {
			Circle c = it.next();
			float age = (now - c.born) / 1.0e9f;
			if (age > life) {
				it.remove();
				continue;
			}
			float p = age / life;
			float r = Anim.easeOutCubic(p) * radius.f();
			float fade = 1f - Anim.easeInOutCubic(p);
			float spin = age * 2f;
			Draw3D.flatRing(WorldEffectRenderer.glow(), c.x, c.y, c.z, r, 0.09, 64,
				i -> ColorUtil.withAlpha(ColorUtil.lerp(a1, a2, (float) (Math.sin(i / 64.0 * Math.PI * 2 + spin) * 0.5 + 0.5)), Math.round(230 * fade)));
			Draw3D.flatRing(WorldEffectRenderer.glow(), c.x, c.y, c.z, r * 0.62, 0.05, 48,
				i -> ColorUtil.withAlpha(0xFFFFFF, Math.round(90 * fade)));
		}
	}

	private record Circle(double x, double y, double z, long born) {
	}
}
