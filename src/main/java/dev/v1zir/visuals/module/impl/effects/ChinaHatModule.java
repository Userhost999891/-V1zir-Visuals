package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Translucent cone hat with a sheen sweeping around it and a glowing rim. */
public final class ChinaHatModule extends Module implements WorldEffect {
	private static final int SEGMENTS = 56;

	public final NumberSetting radius = add(new NumberSetting("Radius", 0.62, 0.3, 1.2, 0.01));
	public final NumberSetting height = add(new NumberSetting("Height", 0.3, 0.1, 0.8, 0.01));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.45, 0.1, 1.0, 0.01));
	public final BoolSetting others = add(new BoolSetting("On other players", false));

	private final Draw3D.Wave wave = new Draw3D.Wave();

	public ChinaHatModule() {
		super("China Hat", "A glowing cone hat", Category.VISUALS);
		setSubCategory("Render");
	}

	@Override
	public void render3D(float tickDelta) {
		if (mc.world == null || mc.player == null) return;
		boolean firstPerson = mc.options.getPerspective().isFirstPerson();
		for (PlayerEntity player : mc.world.getPlayers()) {
			boolean self = player == mc.player;
			if (!self && !others.on()) continue;
			if (self && firstPerson) continue;
			if (player.isInvisible()) continue;
			hat(player, tickDelta);
		}
	}

	private void hat(Entity entity, float tickDelta) {
		Vec3d pos = entity.getLerpedPos(tickDelta);
		double top = pos.y + entity.getHeight() + 0.06;
		double apexY = top + height.f();
		double r = radius.f();
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		float time = V1zir.time();
		float spin = time * 0.8f;
		// A highlight that sweeps around the cone, like light sliding over a glossy surface.
		float sheen = time * 1.3f;
		float fill = 255f * opacity.f();
		int apex = ColorUtil.withAlpha(ColorUtil.brighten(a1, 0.45f), Math.round(fill * 0.55f));
		WorldEffectRenderer.Mesh body = WorldEffectRenderer.translucent();
		int c0 = bodyColor(0, spin, sheen, a1, a2, fill);
		for (int i = 0; i < SEGMENTS; i++) {
			double t0 = Math.PI * 2 * i / SEGMENTS;
			double t1 = Math.PI * 2 * (i + 1) / SEGMENTS;
			int c1 = bodyColor(i + 1, spin, sheen, a1, a2, fill);
			body.vertex(pos.x, apexY, pos.z, 3f, 0f, apex);
			body.vertex(pos.x + Math.cos(t0) * r, top, pos.z + Math.sin(t0) * r, 3f, 0f, c0);
			body.vertex(pos.x + Math.cos(t1) * r, top, pos.z + Math.sin(t1) * r, 3f, 0f, c1);
			body.vertex(pos.x, apexY, pos.z, 3f, 0f, apex);
			c0 = c1;
		}
		WorldEffectRenderer.Mesh glow = WorldEffectRenderer.glow();
		Draw3D.flatRing(glow, pos.x, top, pos.z, r, 0.13, SEGMENTS, wave.set(a1, a2, spin, 0.3f, SEGMENTS));
		Draw3D.flatRing(glow, pos.x, top, pos.z, r, 0.045, SEGMENTS, wave.set(a1, a2, spin, 0.95f, SEGMENTS));
	}

	private static int bodyColor(int i, float spin, float sheen, int a1, int a2, float fill) {
		double angle = Math.PI * 2 * i / SEGMENTS;
		int color = ColorUtil.lerp(a1, a2, (float) (Math.sin(angle + spin) * 0.5 + 0.5));
		float light = (float) Math.max(0.0, Math.cos(angle - sheen));
		light *= light;
		light *= light;
		return ColorUtil.withAlpha(ColorUtil.brighten(color, 0.55f * light), Math.round(fill * (1f + 0.35f * light)));
	}
}
