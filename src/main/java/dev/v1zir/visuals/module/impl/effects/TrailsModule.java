package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

/** A glowing ribbon that follows you. */
public final class TrailsModule extends Module implements WorldEffect {
	public final NumberSetting length = add(new NumberSetting("Length", 24, 6, 60, 1));
	public final NumberSetting ribbonHeight = add(new NumberSetting("Height", 0.45, 0.1, 1.5, 0.01));

	private final Deque<Vec3d> points = new ArrayDeque<>();

	public TrailsModule() {
		super("Trails", "Glowing ribbon behind you", Category.EFFECTS);
	}

	@Override
	protected void onDisable() {
		points.clear();
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		Vec3d now = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
		Vec3d last = points.peekFirst();
		if (last == null || last.squaredDistanceTo(now) > 0.0004) points.addFirst(now);
		else if (!points.isEmpty()) points.removeLast();
		while (points.size() > length.i()) points.removeLast();
	}

	@Override
	public void render3D(float tickDelta) {
		if (mc.player == null || points.size() < 2) return;
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		double h = ribbonHeight.f();
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		int n = points.size();
		// In first person the newest part of the ribbon runs right past the eyes: segments near the
		// camera fade out so they never cover the screen (and cost nothing there).
		boolean firstPerson = mc.options.getPerspective().isFirstPerson();
		Vec3d head = mc.player.getLerpedPos(tickDelta);
		Vec3d p0 = head;
		float near0 = nearFade(p0, firstPerson);
		int i = 0;
		for (Vec3d p1 : points) {
			if (i++ == 0) continue;
			float f0 = 1f - (i - 2) / (float) (n - 1);
			float f1 = 1f - (i - 1) / (float) (n - 1);
			float near1 = nearFade(p1, firstPerson);
			if (near0 > 0.01f || near1 > 0.01f) {
				int c0 = ColorUtil.withAlpha(ColorUtil.lerp(a1, a2, 1f - f0), Math.round(200 * f0 * near0));
				int c1 = ColorUtil.withAlpha(ColorUtil.lerp(a1, a2, 1f - f1), Math.round(200 * f1 * near1));
				double base = 0.05;
				mesh.vertex(p0.x, p0.y + base, p0.z, -1f, 0f, c0);
				mesh.vertex(p0.x, p0.y + base + h, p0.z, 1f, 0f, c0);
				mesh.vertex(p1.x, p1.y + base + h, p1.z, 1f, 1f, c1);
				mesh.vertex(p1.x, p1.y + base, p1.z, -1f, 1f, c1);
			}
			p0 = p1;
			near0 = near1;
		}
	}

	/** 0 within 1.5 blocks of the camera, 1 from 3 blocks on (first person only). */
	private static float nearFade(Vec3d p, boolean firstPerson) {
		if (!firstPerson) return 1f;
		double dx = p.x - WorldEffectRenderer.camX;
		double dy = p.y - WorldEffectRenderer.camY;
		double dz = p.z - WorldEffectRenderer.camZ;
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float t = Math.max(0f, Math.min(1f, (d - 1.5f) / 1.5f));
		return t * t * (3f - 2f * t);
	}
}
