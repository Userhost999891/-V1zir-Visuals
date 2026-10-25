package dev.v1zir.visuals.module.impl.world;

import dev.v1zir.visuals.V1zir;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Paper sky lanterns rising from the land around you into the night: glowing six-sided bodies lit
 * by a flickering flame, swaying and drifting with a slow wind, fading in when released and out
 * high in the sky or far away. By default only at night and under the open sky.
 */
public final class SkyLanternsModule extends Module implements WorldEffect {
	private static final int MAX = 64;
	private static final int SIDES = 6;
	private static final float TAU = (float) (Math.PI * 2);
	/** Height above the camera where lanterns have faded out and are released again. */
	private static final float CEILING = 90f;

	public final NumberSetting count = add(new NumberSetting("Count", 24, 4, MAX, 1));
	public final NumberSetting radius = add(new NumberSetting("Radius", 48, 16, 96, 1));
	public final NumberSetting lanternSize = add(new NumberSetting("Size", 1.0, 0.5, 2.5, 0.05).suffix("x"));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.3, 3.0, 0.05).suffix("x"));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", false));
	public final BoolSetting onlyNight = add(new BoolSetting("Only at night", true));
	public final BoolSetting onlyOutdoors = add(new BoolSetting("Only outdoors", true));

	// One lantern per index: position, rise speed (blocks/s), phase, size, age (s), colour variety.
	private final double[] x = new double[MAX];
	private final double[] y = new double[MAX];
	private final double[] z = new double[MAX];
	private final float[] rise = new float[MAX];
	private final float[] phase = new float[MAX];
	private final float[] size = new float[MAX];
	private final float[] age = new float[MAX];
	private final float[] variety = new float[MAX];
	/** Far to near, so the alpha blended bodies overlap correctly. */
	private final int[] order = new int[MAX];
	private final float[] depth = new float[MAX];
	private int alive;
	private final Anim visible = new Anim(0f, 0.9f);
	private final BlockPos.Mutable cell = new BlockPos.Mutable();
	private boolean allowed;
	private long lastFrame = System.nanoTime();
	private double lastCamX;
	private double lastCamZ;

	public SkyLanternsModule() {
		super("Sky Lanterns", "Glowing lanterns rising into the night sky", Category.WORLD);
		setSubCategory("Ambience");
	}

	@Override
	protected void onDisable() {
		alive = 0;
		visible.snap(0f);
	}

	@Override
	public void onTick() {
		if (mc.world == null || mc.player == null) {
			allowed = false;
			return;
		}
		boolean night = !onlyNight.on() || mc.world.isNight();
		// Sky light rather than "sky visible": trees and doorways do not count as indoors.
		boolean outdoors = !onlyOutdoors.on() || mc.world.getLightLevel(LightType.SKY, cell.set(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ())) >= 10;
		allowed = night && outdoors;
	}

	@Override
	public void render3D(float tickDelta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.05f);
		lastFrame = now;
		float shown = visible.target(allowed ? 1f : 0f).get();
		if (shown <= 0.004f) {
			// Released afresh when they come back.
			alive = 0;
			return;
		}
		double cx = WorldEffectRenderer.camX;
		double cy = WorldEffectRenderer.camY;
		double cz = WorldEffectRenderer.camZ;
		float r = radius.f();
		double jx = cx - lastCamX;
		double jz = cz - lastCamZ;
		lastCamX = cx;
		lastCamZ = cz;
		if (jx * jx + jz * jz > r * r) alive = 0;
		int n = count.i();
		// New lanterns (first frame, a larger count, a teleport) start anywhere along their way up.
		for (; alive < n; alive++) release(alive, cx, cy, cz, r, true);
		alive = n;

		float t = V1zir.time();
		float windAngle = t * 0.004f;
		float windX = (float) Math.cos(windAngle) * 0.18f * speed.f();
		float windZ = (float) Math.sin(windAngle) * 0.18f * speed.f();
		for (int i = 0; i < n; i++) {
			y[i] += rise[i] * speed.f() * dt;
			x[i] += windX * dt;
			z[i] += windZ * dt;
			age[i] += dt;
			double dx = x[i] - cx;
			double dz = z[i] - cz;
			if (y[i] - cy > CEILING || dx * dx + dz * dz > r * r * 1.96) release(i, cx, cy, cz, r, false);
			double dy = y[i] - cy;
			dx = x[i] - cx;
			dz = z[i] - cz;
			depth[i] = (float) (dx * dx + dy * dy + dz * dz);
			order[i] = i;
		}
		// Insertion sort: the order barely changes between frames, so this is close to linear.
		for (int i = 1; i < n; i++) {
			int k = order[i];
			int j = i - 1;
			while (j >= 0 && depth[order[j]] < depth[k]) {
				order[j + 1] = order[j];
				j--;
			}
			order[j + 1] = k;
		}

		int warm = themeColor.on() ? ColorUtil.brighten(Theme.accent(), 0.45f) : 0xFFFFC46B;
		int deep = themeColor.on() ? Theme.accent2() : 0xFFE4562C;
		float s0 = lanternSize.f();
		for (int k = 0; k < n; k++) {
			int i = order[k];
			double dx = x[i] - cx;
			double dz = z[i] - cz;
			float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
			float alpha = shown
				* smooth(age[i] / 2.5f)
				* (1f - smooth(((float) (y[i] - cy) - CEILING * 0.6f) / (CEILING * 0.4f)))
				* (1f - smooth((horizontal - r * 0.85f) / (r * 0.5f)));
			if (alpha <= 0.004f) continue;
			draw(i, t, s0 * size[i], alpha, ColorUtil.lerp(warm, 0xFFFFE2A0, variety[i] * 0.4f), deep);
		}
	}

	private static float smooth(float v) {
		v = Math.max(0f, Math.min(1f, v));
		return v * v * (3f - 2f * v);
	}

	/** Lantern i: a tapered six-sided paper body with a cap, lit from inside, a flame and a soft halo. */
	private void draw(int i, float t, float s, float alpha, int warm, int deep) {
		float p = phase[i];
		// Gentle sway around the drift path, the body leaning with it, and a slow turn.
		float swayA = t * 0.37f + p;
		float swayB = t * 0.29f + p * 1.7f;
		double px = x[i] + Math.sin(swayA) * 0.5;
		double py = y[i];
		double pz = z[i] + Math.cos(swayB) * 0.5;
		float leanX = (float) Math.cos(swayA) * 0.09f;
		float leanZ = (float) -Math.sin(swayB) * 0.09f;
		float turn = t * 0.15f + p;
		float flicker = 0.85f + 0.1f * (float) Math.sin(t * 8.3f + p * 3f) + 0.05f * (float) Math.sin(t * 21.7f + p * 5f);
		alpha *= Draw3D.cameraFade(px, py, pz, 1f, 3f);
		if (alpha <= 0.004f) return;

		float h = 0.62f * s;
		float bottomR = 0.2f * s;
		float topR = 0.27f * s;
		double tx = px + leanX * h;
		double ty = py + h;
		double tz = pz + leanZ * h;
		int bottom = ColorUtil.withAlpha(ColorUtil.lerp(deep, warm, flicker), Math.round(225 * alpha));
		int top = ColorUtil.withAlpha(deep, Math.round(205 * alpha));
		WorldEffectRenderer.Mesh body = WorldEffectRenderer.translucent();
		for (int k = 0; k < SIDES; k++) {
			float a0 = turn + k * TAU / SIDES;
			float a1 = a0 + TAU / SIDES;
			float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
			body.vertex(px + c0 * bottomR, py, pz + s0 * bottomR, 3f, 0f, bottom);
			body.vertex(px + c1 * bottomR, py, pz + s1 * bottomR, 3f, 0f, bottom);
			body.vertex(tx + c1 * topR, ty, tz + s1 * topR, 3f, 0f, top);
			body.vertex(tx + c0 * topR, ty, tz + s0 * topR, 3f, 0f, top);
		}
		// Cap: the hexagon as two quads.
		int cap = ColorUtil.withAlpha(ColorUtil.darken(deep, 0.25f), Math.round(215 * alpha));
		for (int half = 0; half < 2; half++) {
			for (int k = 0; k < 4; k++) {
				int corner = k == 0 ? 0 : half * 2 + k;
				float a = turn + corner * TAU / SIDES;
				body.vertex(tx + MathHelper.cos(a) * topR, ty, tz + MathHelper.sin(a) * topR, 3f, 0f, cap);
			}
		}

		WorldEffectRenderer.Mesh glow = WorldEffectRenderer.glow();
		double mx = (px + tx) * 0.5;
		double mz = (pz + tz) * 0.5;
		glow.orb(mx, py + h * 0.45, mz, 0.55f * s, ColorUtil.withAlpha(warm, Math.round(150 * alpha * flicker)));
		glow.orb(px + leanX * h * 0.1, py + h * 0.1, pz + leanZ * h * 0.1, 0.15f * s, ColorUtil.withAlpha(0xFFFFF2C8, Math.round(240 * alpha * flicker)));
		glow.orb(mx, py + h * 0.4, mz, 1.9f * s, ColorUtil.withAlpha(warm, Math.round(60 * alpha * flicker)));
	}

	/** Releases lantern i from the ground around the camera, or anywhere along its way up. */
	private void release(int i, double cx, double cy, double cz, float r, boolean anywhere) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double angle = random.nextDouble() * Math.PI * 2;
		double dist = r * (anywhere ? 0.2 + 0.8 * Math.sqrt(random.nextDouble()) : 0.6 + 0.4 * random.nextDouble());
		x[i] = cx + Math.cos(angle) * dist;
		z[i] = cz + Math.sin(angle) * dist;
		int ground = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x[i]), MathHelper.floor(z[i]));
		// Unloaded columns report the bottom of the world: start a little below the camera there.
		double base = ground <= mc.world.getBottomY() ? cy - 6 : Math.max(ground, cy - 16);
		y[i] = base + 1 + random.nextDouble() * 4 + (anywhere ? random.nextDouble() * CEILING * 0.7 : 0);
		rise[i] = 0.45f + random.nextFloat() * 0.5f;
		phase[i] = random.nextFloat() * TAU;
		size[i] = 0.8f + random.nextFloat() * 0.45f;
		age[i] = anywhere ? 1.5f : 0f;
		variety[i] = random.nextFloat();
	}
}
