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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
		List<Vec3d> list = new ArrayList<>(points);
		list.set(0, mc.player.getLerpedPos(tickDelta));
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		double h = ribbonHeight.f();
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		int n = list.size();
		for (int i = 0; i < n - 1; i++) {
			Vec3d p0 = list.get(i);
			Vec3d p1 = list.get(i + 1);
			float f0 = 1f - i / (float) (n - 1);
			float f1 = 1f - (i + 1) / (float) (n - 1);
			int c0 = ColorUtil.withAlpha(ColorUtil.lerp(a1, a2, 1f - f0), Math.round(200 * f0));
			int c1 = ColorUtil.withAlpha(ColorUtil.lerp(a1, a2, 1f - f1), Math.round(200 * f1));
			double base = 0.05;
			mesh.vertex(p0.x, p0.y + base, p0.z, -1f, 0f, c0);
			mesh.vertex(p0.x, p0.y + base + h, p0.z, 1f, 0f, c0);
			mesh.vertex(p1.x, p1.y + base + h, p1.z, 1f, 1f, c1);
			mesh.vertex(p1.x, p1.y + base, p1.z, -1f, 1f, c1);
		}
	}
}
