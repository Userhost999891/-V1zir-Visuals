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

/** Translucent cone hat with a glowing rim. */
public final class ChinaHatModule extends Module implements WorldEffect {
	public final NumberSetting radius = add(new NumberSetting("Radius", 0.62, 0.3, 1.2, 0.01));
	public final NumberSetting height = add(new NumberSetting("Height", 0.3, 0.1, 0.8, 0.01));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.45, 0.1, 1.0, 0.01));
	public final BoolSetting others = add(new BoolSetting("On other players", false));

	public ChinaHatModule() {
		super("China Hat", "A glowing cone hat", Category.EFFECTS);
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
		int segments = 56;
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		float spin = V1zir.time() * 0.8f;
		int fill = Math.round(255 * opacity.f());
		WorldEffectRenderer.Mesh body = WorldEffectRenderer.translucent();
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1r = Math.PI * 2 * (i + 1) / segments;
			int c0 = ColorUtil.withAlpha(ColorUtil.lerp(a1, a2, (float) (Math.sin(a0 + spin) * 0.5 + 0.5)), fill);
			int c1 = ColorUtil.withAlpha(ColorUtil.lerp(a1, a2, (float) (Math.sin(a1r + spin) * 0.5 + 0.5)), fill);
			int apex = ColorUtil.withAlpha(ColorUtil.brighten(a1, 0.4f), Math.round(fill * 0.6f));
			body.vertex(pos.x, apexY, pos.z, 3f, 0f, apex);
			body.vertex(pos.x + Math.cos(a0) * r, top, pos.z + Math.sin(a0) * r, 3f, 0f, c0);
			body.vertex(pos.x + Math.cos(a1r) * r, top, pos.z + Math.sin(a1r) * r, 3f, 0f, c1);
			body.vertex(pos.x, apexY, pos.z, 3f, 0f, apex);
		}
		Draw3D.flatRing(WorldEffectRenderer.glow(), pos.x, top, pos.z, r, 0.05, segments,
			i -> ColorUtil.withAlpha(ColorUtil.lerp(a1, a2, (float) (Math.sin(Math.PI * 2 * i / segments + spin) * 0.5 + 0.5)), 230));
	}
}
