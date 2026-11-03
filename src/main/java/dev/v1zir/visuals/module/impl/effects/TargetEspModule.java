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
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Animated marker around the entity you are fighting (Ghosts, Ring or Marker). It fades in when
 * the fight starts, fades out after it, and flashes towards red on every hit.
 */
public final class TargetEspModule extends Module implements WorldEffect {
	private static final int HURT = 0xFFFF3B4F;
	private static final double TAU = Math.PI * 2;
	private static final int TAIL = 20;
	private static final int SEGMENTS = 48;

	public final ModeSetting mode = add(new ModeSetting("Mode", "Ghosts", "Ghosts", "Ring", "Marker"));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.2, 3.0, 0.05).suffix("x"));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05).suffix("x"));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.2, 2.0, 0.05));

	private final Anim appear = new Anim(0f, 8f);
	/** Marker corners (x, y, z per corner), reused every frame. */
	private final double[] corners = new double[12];
	/** The entity being drawn; kept while the marker fades out after the fight. */
	private LivingEntity shown;
	private float fade;

	public TargetEspModule() {
		super("Target ESP", "Highlights your current opponent", Category.COMBAT);
		setSubCategory("Target");
	}

	@Override
	public String suffix() {
		return mode.get();
	}

	@Override
	protected void onDisable() {
		shown = null;
		appear.snap(0f);
	}

	@Override
	public void render3D(float tickDelta) {
		LivingEntity target = Effects.combatTarget();
		if (target != null) shown = target;
		fade = appear.target(target != null ? 1f : 0f).get();
		if (shown == null) return;
		if (target == null && fade <= 0.005f) {
			shown = null;
			return;
		}
		Vec3d pos = shown.getLerpedPos(tickDelta);
		float t = V1zir.time() * speed.f();
		float h = shown.getHeight();
		float w = shown.getWidth();
		float hurt = Math.min(shown.hurtTime, 10) / 10f;
		int c1 = ColorUtil.lerp(Theme.accent(), HURT, hurt * 0.65f);
		int c2 = ColorUtil.lerp(Theme.accent2(), HURT, hurt * 0.65f);
		// Grows slightly into place while fading in.
		float s = size.f() * (0.85f + 0.15f * fade);
		switch (mode.get()) {
			case "Ring" -> ring(pos, w, h, t, s, c1, c2);
			case "Marker" -> marker(pos, h, t, s, hurt, c1, c2);
			default -> ghosts(pos, w, h, t, s, c1, c2);
		}
	}

	private int alpha(int color, float a) {
		return ColorUtil.withAlpha(color, Math.round(Math.min(255f, 255f * a * fade * brightness.f())));
	}

	/** Three glowing souls circling the target, each trailing a tail that shifts between the accents. */
	private void ghosts(Vec3d pos, float w, float h, float t, float s, int c1, int c2) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		double radius = (w * 0.75 + 0.35) * s;
		int light = ColorUtil.lerp(c1, 0xFFFFFFFF, 0.35f);
		for (int g = 0; g < 3; g++) {
			int head = g == 0 ? c1 : g == 1 ? c2 : light;
			int tail = g == 1 ? c1 : c2;
			for (int k = 0; k < TAIL; k++) {
				float tt = t - k * 0.022f;
				double angle = tt * 2.4 + g * TAU / 3;
				double y = pos.y + h * 0.5 + Math.sin(tt * 1.6 + g * 2.1) * h * 0.38;
				double x = pos.x + Math.cos(angle) * radius;
				double z = pos.z + Math.sin(angle) * radius;
				float near = Draw3D.cameraFade(x, y, z, 0.6f, 1.6f);
				if (near <= 0.01f) continue;
				float f = 1f - k / (float) TAIL;
				mesh.orb(x, y, z, (0.22f * f + 0.03f) * s, alpha(ColorUtil.lerp(head, tail, 1f - f), f * f * 0.85f * near));
				if (k == 0) mesh.orb(x, y, z, 0.1f * s, alpha(0xFFFFFFFF, 0.95f * near));
			}
		}
	}

	/**
	 * A bright ring gliding up and down the target, trailing a soft curtain that is longest while it
	 * moves fastest and gone at the turning points.
	 */
	private void ring(Vec3d pos, float w, float h, float t, float s, int c1, int c2) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		double r = (w * 0.7 + 0.15) * s;
		float wave = (float) (Math.sin(t * 2.0) * 0.5 + 0.5);
		float velocity = (float) Math.cos(t * 2.0);
		double y = pos.y + 0.05 + wave * (h - 0.1);
		double tail = y - velocity * h * 0.32;
		double rim = 0.035 * s;
		float spin = t * 0.6f;
		for (int i = 0; i < SEGMENTS; i++) {
			double a0 = TAU * i / SEGMENTS;
			double a1 = TAU * (i + 1) / SEGMENTS;
			double x0 = pos.x + Math.cos(a0) * r;
			double z0 = pos.z + Math.sin(a0) * r;
			double x1 = pos.x + Math.cos(a1) * r;
			double z1 = pos.z + Math.sin(a1) * r;
			int col0 = ColorUtil.lerp(c1, c2, (float) (Math.sin(a0 + spin) * 0.5 + 0.5));
			int col1 = ColorUtil.lerp(c1, c2, (float) (Math.sin(a1 + spin) * 0.5 + 0.5));
			// Curtain: a solid quad (u > 2) whose vertex alpha fades away from the ring.
			mesh.vertex(x0, y, z0, 3f, 0f, alpha(col0, 0.36f));
			mesh.vertex(x0, tail, z0, 3f, 0f, alpha(col0, 0f));
			mesh.vertex(x1, tail, z1, 3f, 0f, alpha(col1, 0f));
			mesh.vertex(x1, y, z1, 3f, 0f, alpha(col1, 0.36f));
			// Rim: a thin band with the glow falloff across it.
			mesh.vertex(x0, y - rim, z0, -1f, 0f, alpha(col0, 1f));
			mesh.vertex(x0, y + rim, z0, 1f, 0f, alpha(col0, 1f));
			mesh.vertex(x1, y + rim, z1, 1f, 1f, alpha(col1, 1f));
			mesh.vertex(x1, y - rim, z1, -1f, 1f, alpha(col1, 1f));
		}
	}

	/** Four lock-on brackets facing the camera, turning slowly and tightening on every hit. */
	private void marker(Vec3d pos, float h, float t, float s, float hurt, int c1, int c2) {
		WorldEffectRenderer.Mesh mesh = WorldEffectRenderer.glow();
		double cx = pos.x;
		double cy = pos.y + h * 0.5;
		double cz = pos.z;
		double half = (h * 0.42 + 0.15) * s * (1.0 - hurt * 0.15);
		double rot = t * 1.4;
		Vector3f right = WorldEffectRenderer.RIGHT;
		Vector3f up = WorldEffectRenderer.UP;
		for (int i = 0; i < 4; i++) {
			double a = rot + i * Math.PI / 2 + Math.PI / 4;
			double ox = Math.cos(a) * half;
			double oy = Math.sin(a) * half;
			corners[i * 3] = cx + right.x * ox + up.x * oy;
			corners[i * 3 + 1] = cy + right.y * ox + up.y * oy;
			corners[i * 3 + 2] = cz + right.z * ox + up.z * oy;
		}
		float width = 0.06f * s;
		for (int i = 0; i < 4; i++) {
			int color = i % 2 == 0 ? c1 : c2;
			bracketArm(mesh, i, (i + 1) % 4, width, color);
			bracketArm(mesh, i, (i + 3) % 4, width, color);
			mesh.orb(corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2], 0.09f * s, alpha(0xFFFFFFFF, 0.85f));
		}
	}

	/** One arm of a bracket: a third of the way from a corner towards its neighbour, fading out. */
	private void bracketArm(WorldEffectRenderer.Mesh mesh, int from, int to, float width, int color) {
		double x0 = corners[from * 3];
		double y0 = corners[from * 3 + 1];
		double z0 = corners[from * 3 + 2];
		double x1 = x0 + (corners[to * 3] - x0) * 0.34;
		double y1 = y0 + (corners[to * 3 + 1] - y0) * 0.34;
		double z1 = z0 + (corners[to * 3 + 2] - z0) * 0.34;
		Draw3D.line(mesh, x0, y0, z0, x1, y1, z1, width, alpha(color, 0.95f), alpha(color, 0f));
	}
}
