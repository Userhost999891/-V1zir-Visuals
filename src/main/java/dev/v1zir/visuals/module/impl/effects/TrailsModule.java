package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.util.math.Vec3d;

/** A glowing ribbon that follows you, smoothed into a curve and fading out along its length. */
public final class TrailsModule extends Module implements WorldEffect {
	/** Room for the longest trail plus the interpolated head. */
	private static final int CAPACITY = 64;
	private static final double BASE = 0.05;

	public final NumberSetting length = add(new NumberSetting("Length", 24, 6, 60, 1));
	public final NumberSetting ribbonHeight = add(new NumberSetting("Height", 0.45, 0.1, 1.5, 0.01));

	// Recorded positions, newest first, in a ring buffer (no objects per tick).
	private final double[] px = new double[CAPACITY];
	private final double[] py = new double[CAPACITY];
	private final double[] pz = new double[CAPACITY];
	private int newest;
	private int count;
	// Polyline of the frame: the interpolated head followed by the recorded points.
	private final double[] lx = new double[CAPACITY];
	private final double[] ly = new double[CAPACITY];
	private final double[] lz = new double[CAPACITY];

	public TrailsModule() {
		super("Trails", "Glowing ribbon behind you", Category.VISUALS);
		setSubCategory("Render");
	}

	@Override
	protected void onDisable() {
		count = 0;
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		double x = mc.player.getX();
		double y = mc.player.getY();
		double z = mc.player.getZ();
		if (count == 0 || squaredDistance(newest, x, y, z) > 0.0004) {
			newest = (newest + CAPACITY - 1) % CAPACITY;
			px[newest] = x;
			py[newest] = y;
			pz[newest] = z;
			count = Math.min(count + 1, CAPACITY - 1);
		} else {
			// Standing still: the tail catches up with you.
			count--;
		}
		count = Math.min(count, length.i());
	}

	private double squaredDistance(int i, double x, double y, double z) {
		double dx = px[i] - x;
		double dy = py[i] - y;
		double dz = pz[i] - z;
		return dx * dx + dy * dy + dz * dz;
	}

	@Override
	public void render3D(float tickDelta) {
		if (mc.player == null || count < 2) return;
		Vec3d head = mc.player.getLerpedPos(tickDelta);
		lx[0] = head.x;
		ly[0] = head.y;
		lz[0] = head.z;
		// The newest recorded point is replaced by the interpolated head.
		int n = 1;
		for (int k = 1; k < count; k++, n++) {
			int i = (newest + k) % CAPACITY;
			lx[n] = px[i];
			ly[n] = py[i];
			lz[n] = pz[i];
		}

		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		double h = ribbonHeight.f();
		int a1 = Theme.accent();
		int a2 = Theme.accent2();
		// In first person the newest part of the ribbon runs right past the eyes: segments near the
		// camera fade out so they never cover the screen (and cost nothing there).
		boolean firstPerson = mc.options.getPerspective().isFirstPerson();
		float last = n - 1;
		double x0 = lx[0];
		double y0 = ly[0];
		double z0 = lz[0];
		int c0 = color(x0, y0, z0, 0f, firstPerson, a1, a2);
		for (int i = 0; i < n - 1; i++) {
			// Each segment is split at its Catmull-Rom midpoint so turns read as curves, not corners.
			int i0 = Math.max(i - 1, 0);
			int i3 = Math.min(i + 2, n - 1);
			double mx = (9 * (lx[i] + lx[i + 1]) - lx[i0] - lx[i3]) / 16;
			double my = (9 * (ly[i] + ly[i + 1]) - ly[i0] - ly[i3]) / 16;
			double mz = (9 * (lz[i] + lz[i + 1]) - lz[i0] - lz[i3]) / 16;
			int cm = color(mx, my, mz, (i + 0.5f) / last, firstPerson, a1, a2);
			int c1 = color(lx[i + 1], ly[i + 1], lz[i + 1], (i + 1) / last, firstPerson, a1, a2);
			quad(mesh, x0, y0, z0, c0, mx, my, mz, cm, h);
			quad(mesh, mx, my, mz, cm, lx[i + 1], ly[i + 1], lz[i + 1], c1, h);
			x0 = lx[i + 1];
			y0 = ly[i + 1];
			z0 = lz[i + 1];
			c0 = c1;
		}
	}

	/** Colour at a point f (0 at you, 1 at the end) along the ribbon: accent to accent 2, fading out. */
	private static int color(double x, double y, double z, float f, boolean firstPerson, int a1, int a2) {
		float near = firstPerson ? Draw3D.cameraFade(x, y, z, 1.5f, 3f) : 1f;
		float k = 1f - f;
		return ColorUtil.withAlpha(ColorUtil.lerp(a1, a2, f), Math.round(210 * k * k * (3f - 2f * k) * near));
	}

	private static void quad(WorldEffectRenderer.Mesh mesh, double x0, double y0, double z0, int c0, double x1, double y1, double z1, int c1, double h) {
		if ((c0 >>> 24) == 0 && (c1 >>> 24) == 0) return;
		mesh.vertex(x0, y0 + BASE, z0, -1f, 0f, c0);
		mesh.vertex(x0, y0 + BASE + h, z0, 1f, 0f, c0);
		mesh.vertex(x1, y1 + BASE + h, z1, 1f, 1f, c1);
		mesh.vertex(x1, y1 + BASE, z1, -1f, 1f, c1);
	}
}
