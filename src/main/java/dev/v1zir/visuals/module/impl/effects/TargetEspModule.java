package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.Effects;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/** Animated marker around the entity you are fighting (Ring, Ghosts or Marker). */
public final class TargetEspModule extends Module implements WorldEffect {
	public final ModeSetting mode = add(new ModeSetting("Mode", "Ghosts", "Ghosts", "Ring", "Marker"));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.2, 3.0, 0.05).suffix("x"));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05).suffix("x"));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.2, 2.0, 0.05));

	public TargetEspModule() {
		super("Target ESP", "Highlights your current opponent", Category.EFFECTS);
	}

	@Override
	public String suffix() {
		return mode.get();
	}

	@Override
	public void render3D(float tickDelta) {
		LivingEntity target = Effects.combatTarget();
		if (target == null) return;
		Vec3d pos = target.getLerpedPos(tickDelta);
		float t = V1zir.time() * speed.f();
		float h = target.getHeight();
		float w = target.getWidth();
		switch (mode.get()) {
			case "Ring" -> ring(pos, w, h, t);
			case "Marker" -> marker(target, pos, h, t);
			default -> ghosts(pos, w, h, t);
		}
	}

	private int alpha(int color, float a) {
		return ColorUtil.withAlpha(color, Math.round(Math.min(255f, 255f * a * brightness.f())));
	}

	private void ghosts(Vec3d pos, float w, float h, float t) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		int[] colors = {Theme.accent(), Theme.accent2(), ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, 0.35f)};
		double radius = (w * 0.75 + 0.35) * size.f();
		for (int g = 0; g < 3; g++) {
			for (int k = 0; k < 18; k++) {
				float tt = t - k * 0.028f;
				double angle = tt * 2.4 + g * Math.PI * 2 / 3;
				double y = pos.y + h * 0.5 + Math.sin(tt * 1.6 + g * 2.1) * h * 0.38;
				double x = pos.x + Math.cos(angle) * radius;
				double z = pos.z + Math.sin(angle) * radius;
				float fade = 1f - k / 18f;
				float orb = (0.30f * fade + 0.04f) * size.f();
				mesh.orb(x, y, z, orb, alpha(colors[g], fade * fade * 0.85f));
				if (k == 0) mesh.orb(x, y, z, orb * 0.45f, alpha(0xFFFFFFFF, 0.9f));
			}
		}
	}

	private void ring(Vec3d pos, float w, float h, float t) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		double radius = (w * 0.7 + 0.15) * size.f();
		float wave = (float) (Math.sin(t * 2.0) * 0.5 + 0.5);
		float dir = (float) Math.signum(Math.cos(t * 2.0));
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		for (int k = 0; k < 10; k++) {
			double y = pos.y + 0.05 + wave * (h - 0.1) - dir * k * 0.045;
			float strength = (1f - k / 10f) * (k == 0 ? 1f : 0.35f);
			float rot = t * 0.4f;
			Draw3D.bandRing(mesh, pos.x, y, pos.z, radius, k == 0 ? 0.035 : 0.05, 48,
				i -> alpha(ColorUtil.lerp(a1, a2, (float) (Math.sin(i / 48.0 * Math.PI * 2 + rot) * 0.5 + 0.5)), strength));
		}
	}

	private void marker(LivingEntity target, Vec3d pos, float h, float t) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		double cx = pos.x;
		double cy = pos.y + h * 0.5;
		double cz = pos.z;
		float hurt = target.hurtTime / 10f;
		double s = (h * 0.42 + 0.15) * size.f() * (1.0 - hurt * 0.15);
		double rot = t * 1.4;
		var right = WorldEffectRenderer.RIGHT;
		var up = WorldEffectRenderer.UP;
		double[][] corners = new double[4][3];
		for (int i = 0; i < 4; i++) {
			double a = rot + i * Math.PI / 2 + Math.PI / 4;
			double ox = Math.cos(a) * s;
			double oy = Math.sin(a) * s;
			corners[i][0] = cx + right.x * ox + up.x * oy;
			corners[i][1] = cy + right.y * ox + up.y * oy;
			corners[i][2] = cz + right.z * ox + up.z * oy;
		}
		int c1 = ColorUtil.lerp(Theme.accent(), 0xFFFF3B4F, hurt);
		int c2 = ColorUtil.lerp(Theme.accent2(), 0xFFFF3B4F, hurt);
		for (int i = 0; i < 4; i++) {
			double[] p0 = corners[i];
			double[] p1 = corners[(i + 1) % 4];
			Draw3D.line(mesh, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 0.07f * size.f(), alpha(i % 2 == 0 ? c1 : c2, 0.9f), alpha(i % 2 == 0 ? c2 : c1, 0.9f));
			mesh.orb(p0[0], p0[1], p0[2], 0.12f * size.f(), alpha(0xFFFFFFFF, 0.8f));
		}
	}
}
