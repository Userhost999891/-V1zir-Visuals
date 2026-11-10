package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.function.IntUnaryOperator;

/**
 * Animated marker around the entity you are fighting (or, if asked, the one you aim at): souls circling it with
 * comet tails (Ghosts), a ring gliding up and down it (Ring), lock-on brackets (Marker), a double helix of light
 * winding up around it (Helix), three tilted rings with a spark racing along each like an atom (Orbit), or glowing
 * cubes tumbling around it (Crystals). It grows in when the fight starts, fades out after it, and on every hit it
 * swells and flashes towards red. Everything is drawn twice: as light added to the scene, which glows at night,
 * and as a blended body of the colour under it, which is what shows against a bright sky or snow. Everything is a
 * closed formula of the time, so it costs a few hundred quads per frame.
 */
public final class TargetEspModule extends Module implements WorldEffect {
	private static final int HURT = 0xFFFF3B4F;
	private static final double TAU = Math.PI * 2;
	/** Segments of a ghost's tail, and how many seconds lie between two of them. */
	private static final int TAIL = 16;
	private static final float TAIL_STEP = 0.03f;
	private static final int SEGMENTS = 48;
	private static final int HELIX_SEGMENTS = 30;
	private static final int ORBIT_SEGMENTS = 44;
	private static final int CRYSTALS = 5;

	public final ModeSetting mode = add(new ModeSetting("Mode", "Ghosts", "Ghosts", "Ring", "Marker", "Helix", "Orbit", "Crystals"));
	public final ModeSetting targets = add(new ModeSetting("Target", "Attacked", "Attacked", "Attacked or aimed at"));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.2, 3.0, 0.05).suffix("x"));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05).suffix("x"));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.2, 2.0, 0.05));
	public final BoolSetting throughWalls = add(new BoolSetting("Through walls", false));
	public final BoolSetting hitFlash = add(new BoolSetting("Hit flash", true));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF8A5CFF, false).visibleIf(() -> !themeColor.on()));
	public final ColorSetting color2 = add(new ColorSetting("Second color", 0xFF5CE1FF, false).visibleIf(() -> !themeColor.on()));

	private final Anim appear = new Anim(0f, 8f);
	/** Follows the hits: jumps up with each and eases back, smoother than the 20 Hz hurt timer. */
	private final Anim punch = new Anim(0f, 7f);
	/** Marker corners and ghost path points (x, y, z each), reused every frame. */
	private final double[] corners = new double[12];
	private final double[] point = new double[3];
	private final float[] basis = new float[9];
	private final Vector3f axisA = new Vector3f();
	private final Vector3f axisB = new Vector3f();
	private int ringColor;
	private final IntUnaryOperator ringColorAt = i -> ringColor;
	/** This frame's batches: light added to the scene, and the blended body under it. */
	private WorldEffectRenderer.Mesh glow;
	private WorldEffectRenderer.Mesh solid;
	/** The entity being drawn; kept while the marker fades out after the fight. */
	private LivingEntity shown;
	private int lastHurtTime;
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
		punch.snap(0f);
	}

	/** The entity to mark: the one attacked last, or with "aimed at" also the one under the crosshair. */
	private LivingEntity target() {
		LivingEntity attacked = Effects.combatTarget();
		if (attacked != null || targets.index() == 0) return attacked;
		LivingEntity aimed = Effects.target();
		return aimed == null || aimed == mc.player || aimed instanceof ArmorStandEntity || aimed.isInvisible() ? null : aimed;
	}

	@Override
	public void render3D(float tickDelta) {
		LivingEntity target = target();
		if (target != null && target != shown) {
			shown = target;
			lastHurtTime = target.hurtTime;
			punch.snap(0f);
		}
		fade = appear.target(target != null ? 1f : 0f).get();
		if (shown == null) return;
		if (target == null && (fade <= 0.005f || shown.isRemoved())) {
			shown = null;
			return;
		}
		// A new hit starts the hurt timer again: the marker swells with it.
		if (shown.hurtTime > lastHurtTime) punch.snap(1f);
		lastHurtTime = shown.hurtTime;
		float hit = punch.target(0f).get();

		Vec3d pos = shown.getLerpedPos(tickDelta);
		float t = V1zir.time() * speed.f();
		float h = shown.getHeight();
		float w = shown.getWidth();
		float flash = hitFlash.on() ? hit * 0.7f : 0f;
		int c1 = ColorUtil.lerp(themeColor.on() ? Theme.accent() : color.argb(), HURT, flash);
		int c2 = ColorUtil.lerp(themeColor.on() ? Theme.accent2() : color2.argb(), HURT, flash);
		// Grows into place while fading in, and swells a little with every hit.
		float s = size.f() * (0.8f + 0.2f * Anim.easeOutCubic(fade)) * (1f + 0.1f * hit);
		boolean through = throughWalls.on();
		glow = through ? WorldEffectRenderer.glowThrough() : WorldEffectRenderer.glow();
		solid = through ? WorldEffectRenderer.translucentThrough() : WorldEffectRenderer.translucent();
		WorldEffectRenderer.Mesh mesh = glow;
		switch (mode.get()) {
			case "Ring" -> ring(mesh, pos, w, h, t, s, c1, c2);
			case "Marker" -> marker(mesh, pos, h, t, s, hit, c1, c2);
			case "Helix" -> helix(mesh, pos, w, h, t, s, c1, c2);
			case "Orbit" -> orbit(mesh, pos, w, h, t, s, c1, c2);
			case "Crystals" -> crystals(mesh, pos, w, h, t, s, c1, c2);
			default -> ghosts(mesh, pos, w, h, t, s, c1, c2);
		}
	}

	private int alpha(int color, float a) {
		return ColorUtil.withAlpha(color, Math.round(Math.max(0f, Math.min(255f, 255f * a * fade * brightness.f()))));
	}

	/** A glowing orb with a smaller blended body of its colour in it. */
	private void orb(double x, double y, double z, float size, int color, float a) {
		glow.orb(x, y, z, size, alpha(color, a));
		solid.orb(x, y, z, size * 0.6f, alpha(color, Math.min(1f, a * 1.1f)));
	}

	/** A glowing line with a thinner blended body of its colours in it. */
	private void line(double x0, double y0, double z0, double x1, double y1, double z1, float width, int c0, float a0, int c1, float a1) {
		Draw3D.line(glow, x0, y0, z0, x1, y1, z1, width, alpha(c0, a0), alpha(c1, a1));
		Draw3D.line(solid, x0, y0, z0, x1, y1, z1, width * 0.62f, alpha(c0, Math.min(1f, a0 * 1.1f)), alpha(c1, Math.min(1f, a1 * 1.1f)));
	}

	// ------------------------------------------------------------------ ghosts

	/** Where ghost g is at time tt: circling the target, weaving up and down, its orbit breathing a little. */
	private void ghostAt(Vec3d pos, float w, float h, float tt, float s, int g, double[] out) {
		double angle = tt * 2.4 + g * TAU / 3;
		double radius = (w * 0.75 + 0.35) * s * (1.0 + 0.08 * Math.sin(tt * 1.3 + g * 1.9));
		out[0] = pos.x + Math.cos(angle) * radius;
		out[1] = pos.y + h * 0.5 + Math.sin(tt * 1.6 + g * 2.1) * h * 0.38;
		out[2] = pos.z + Math.sin(angle) * radius;
	}

	/**
	 * Three glowing souls circling the target: a bright head in a soft halo, and behind it a tapering comet tail that
	 * shifts from one accent to the other.
	 */
	private void ghosts(WorldEffectRenderer.Mesh mesh, Vec3d pos, float w, float h, float t, float s, int c1, int c2) {
		int light = ColorUtil.lerp(c1, 0xFFFFFFFF, 0.35f);
		double[] p = point;
		for (int g = 0; g < 3; g++) {
			int head = g == 0 ? c1 : g == 1 ? c2 : light;
			int tail = g == 1 ? c1 : c2;
			ghostAt(pos, w, h, t, s, g, p);
			double hx = p[0], hy = p[1], hz = p[2];
			float near = Draw3D.cameraFade(hx, hy, hz, 0.6f, 1.6f);
			if (near <= 0.01f) continue;
			double px = hx, py = hy, pz = hz;
			for (int k = 1; k <= TAIL; k++) {
				ghostAt(pos, w, h, t - k * TAIL_STEP, s, g, p);
				float f0 = 1f - (k - 1) / (float) TAIL, f1 = 1f - k / (float) TAIL;
				line(px, py, pz, p[0], p[1], p[2], (0.2f * f0 + 0.025f) * s,
					ColorUtil.lerp(tail, head, f0), 0.85f * f0 * f0 * near, ColorUtil.lerp(tail, head, f1), 0.85f * f1 * f1 * near);
				// A few soft puffs along the first half give the tail some body.
				if (k % 3 == 0 && k <= TAIL / 2) mesh.orb(p[0], p[1], p[2], 0.22f * f1 * s, alpha(ColorUtil.lerp(tail, head, f1), 0.4f * f1 * near));
				px = p[0];
				py = p[1];
				pz = p[2];
			}
			orb(hx, hy, hz, 0.34f * s, head, 0.8f * near);
			orb(hx, hy, hz, 0.13f * s, 0xFFFFFFFF, 0.95f * near);
		}
	}

	// ------------------------------------------------------------------ ring

	/**
	 * A bright ring gliding up and down the target, trailing a soft curtain that is longest while it moves fastest and
	 * gone at the turning points; a fainter second ring runs against it, and sparks ride the first.
	 */
	private void ring(WorldEffectRenderer.Mesh mesh, Vec3d pos, float w, float h, float t, float s, int c1, int c2) {
		double r = (w * 0.7 + 0.15) * s;
		float wave = (float) (Math.sin(t * 2.0) * 0.5 + 0.5);
		float velocity = (float) Math.cos(t * 2.0);
		double y = pos.y + 0.05 + wave * (h - 0.1);
		double tail = y - velocity * h * 0.32;
		double rim = 0.05 * s;
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
			// Rim: a thin band with the glow falloff across it, and its blended body.
			for (int pass = 0; pass < 2; pass++) {
				WorldEffectRenderer.Mesh band = pass == 0 ? mesh : solid;
				double half = pass == 0 ? rim : rim * 0.7;
				band.vertex(x0, y - half, z0, -1f, 0f, alpha(col0, 1f));
				band.vertex(x0, y + half, z0, 1f, 0f, alpha(col0, 1f));
				band.vertex(x1, y + half, z1, 1f, 1f, alpha(col1, 1f));
				band.vertex(x1, y - half, z1, -1f, 1f, alpha(col1, 1f));
			}
			// The curtain's body, fainter than its light.
			solid.vertex(x0, y, z0, 3f, 0f, alpha(col0, 0.22f));
			solid.vertex(x0, tail, z0, 3f, 0f, alpha(col0, 0f));
			solid.vertex(x1, tail, z1, 3f, 0f, alpha(col1, 0f));
			solid.vertex(x1, y, z1, 3f, 0f, alpha(col1, 0.22f));
		}
		// The second ring, where the first one is not.
		double y2 = pos.y + 0.05 + (1f - wave) * (h - 0.1);
		ringColor = alpha(ColorUtil.lerp(c1, c2, 0.5f), 0.5f);
		Draw3D.bandRing(mesh, pos.x, y2, pos.z, r * 0.86, rim * 0.7, SEGMENTS, ringColorAt);
		Draw3D.bandRing(solid, pos.x, y2, pos.z, r * 0.86, rim * 0.45, SEGMENTS, ringColorAt);
		for (int i = 0; i < 4; i++) {
			double a = t * 1.9 + i * TAU / 4;
			double x = pos.x + Math.cos(a) * r, z = pos.z + Math.sin(a) * r;
			float near = Draw3D.cameraFade(x, y, z, 0.5f, 1.4f);
			orb(x, y, z, 0.15f * s, i % 2 == 0 ? c1 : c2, 0.75f * near);
			orb(x, y, z, 0.055f * s, 0xFFFFFFFF, 0.95f * near);
		}
	}

	// ------------------------------------------------------------------ marker

	/** Four lock-on brackets facing the camera, turning slowly, breathing, and tightening on every hit. */
	private void marker(WorldEffectRenderer.Mesh mesh, Vec3d pos, float h, float t, float s, float hit, int c1, int c2) {
		double cx = pos.x;
		double cy = pos.y + h * 0.5;
		double cz = pos.z;
		double half = (h * 0.42 + 0.15) * s * (1.0 - hit * 0.22) * (1.0 + 0.03 * Math.sin(t * 3.0));
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
		float width = 0.075f * s;
		for (int i = 0; i < 4; i++) {
			int color = i % 2 == 0 ? c1 : c2;
			bracketArm(mesh, i, (i + 1) % 4, width, color);
			bracketArm(mesh, i, (i + 3) % 4, width, color);
			orb(corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2], 0.1f * s, 0xFFFFFFFF, 0.85f);
		}
		// A soft dot in the middle, brighter with every hit.
		orb(cx, cy, cz, (0.1f + 0.12f * hit) * s, ColorUtil.lerp(c1, 0xFFFFFFFF, 0.4f), 0.35f + 0.5f * hit);
	}

	/** One arm of a bracket: a third of the way from a corner towards its neighbour, fading out. */
	private void bracketArm(WorldEffectRenderer.Mesh mesh, int from, int to, float width, int color) {
		double x0 = corners[from * 3];
		double y0 = corners[from * 3 + 1];
		double z0 = corners[from * 3 + 2];
		double x1 = x0 + (corners[to * 3] - x0) * 0.34;
		double y1 = y0 + (corners[to * 3 + 1] - y0) * 0.34;
		double z1 = z0 + (corners[to * 3 + 2] - z0) * 0.34;
		line(x0, y0, z0, x1, y1, z1, width, color, 0.95f, color, 0f);
	}

	// ------------------------------------------------------------------ helix

	/**
	 * Two strands of light wind up around the target from its feet to over its head, turning slowly, with pulses of
	 * brightness running up them and faint rungs between them, like a helix of DNA.
	 */
	private void helix(WorldEffectRenderer.Mesh mesh, Vec3d pos, float w, float h, float t, float s, int c1, int c2) {
		double r = (w * 0.72 + 0.2) * s;
		double height = h + 0.35;
		double turns = 1.35;
		for (int strand = 0; strand < 2; strand++) {
			double px = 0, py = 0, pz = 0;
			int pc = 0;
			float pa = 0f;
			for (int i = 0; i <= HELIX_SEGMENTS; i++) {
				float u = i / (float) HELIX_SEGMENTS;
				double angle = u * TAU * turns + t * 1.8 + strand * Math.PI;
				double x = pos.x + Math.cos(angle) * r;
				double y = pos.y - 0.05 + u * height;
				double z = pos.z + Math.sin(angle) * r;
				// Pulses run up the strand; both ends fade out.
				float pulse = (float) Math.pow(0.5 + 0.5 * Math.sin(u * TAU * 1.5 - t * 3.2 + strand * Math.PI), 2.0);
				float ends = (float) Math.sin(Math.PI * u);
				float near = Draw3D.cameraFade(x, y, z, 0.5f, 1.4f);
				int c = ColorUtil.lerp(strand == 0 ? c1 : c2, 0xFFFFFFFF, 0.45f * pulse);
				float ca = (0.4f + 0.6f * pulse) * ends * near;
				if (i > 0) line(px, py, pz, x, y, z, (0.075f + 0.04f * pulse) * s, pc, pa, c, ca);
				if (pulse > 0.93f && i % 2 == 0) mesh.orb(x, y, z, 0.15f * s, alpha(0xFFFFFFFF, 0.6f * ends * near));
				// Rungs to the other strand, on the first pass only.
				if (strand == 0 && i % 5 == 2) {
					double ox = pos.x - Math.cos(angle) * r, oz = pos.z - Math.sin(angle) * r;
					int rung = ColorUtil.lerp(c1, c2, 0.5f);
					line(x, y, z, ox, y, oz, 0.035f * s, rung, 0.3f * ends * near, rung, 0.3f * ends * near);
				}
				px = x;
				py = y;
				pz = z;
				pc = c;
				pa = ca;
			}
		}
	}

	// ------------------------------------------------------------------ orbit

	/**
	 * Three thin rings tilted against each other turn around the target's middle like the orbits of an atom, a
	 * bright spark with a short tail racing along each.
	 */
	private void orbit(WorldEffectRenderer.Mesh mesh, Vec3d pos, float w, float h, float t, float s, int c1, int c2) {
		double cx = pos.x, cy = pos.y + h * 0.5, cz = pos.z;
		double radius = (Math.max(w, h * 0.55) * 0.95 + 0.25) * s;
		for (int j = 0; j < 3; j++) {
			// The ring's plane: tilted from the horizontal, and the tilt turning around the target.
			double tilt = Math.toRadians(62.0) * (j == 0 ? 0.25 : 1.0);
			double turn = t * 0.5 + j * TAU / 3;
			float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
			float cu = (float) Math.cos(turn), su = (float) Math.sin(turn);
			axisA.set(cu, 0f, su);
			axisB.set(-su * ct, st, cu * ct);
			int base = j == 0 ? c1 : j == 1 ? c2 : ColorUtil.lerp(c1, c2, 0.5f);
			ringColor = alpha(base, 0.45f);
			Draw3D.ring(mesh, cx, cy, cz, axisA.x, axisA.y, axisA.z, axisB.x, axisB.y, axisB.z, radius, 0.03 * s, ORBIT_SEGMENTS, ringColorAt);
			Draw3D.ring(solid, cx, cy, cz, axisA.x, axisA.y, axisA.z, axisB.x, axisB.y, axisB.z, radius, 0.018 * s, ORBIT_SEGMENTS, ringColorAt);
			double e = t * (2.6 + 0.5 * j) + j * 2.1;
			for (int k = 0; k < 7; k++) {
				double a = e - k * 0.11;
				double ca = Math.cos(a) * radius, sa = Math.sin(a) * radius;
				double x = cx + axisA.x * ca + axisB.x * sa, y = cy + axisA.y * ca + axisB.y * sa, z = cz + axisA.z * ca + axisB.z * sa;
				float f = 1f - k / 7f;
				float near = Draw3D.cameraFade(x, y, z, 0.5f, 1.4f);
				orb(x, y, z, (0.06f + 0.17f * f) * s, base, 0.8f * f * f * near);
				if (k == 0) orb(x, y, z, 0.08f * s, 0xFFFFFFFF, 0.95f * near);
			}
		}
		mesh.orb(cx, cy, cz, (float) (radius * 0.9), alpha(ColorUtil.lerp(c1, c2, 0.5f), 0.1f));
	}

	// ------------------------------------------------------------------ crystals

	/** Small glowing cubes circle the target at different heights, tumbling and bobbing, each in a soft halo. */
	private void crystals(WorldEffectRenderer.Mesh mesh, Vec3d pos, float w, float h, float t, float s, int c1, int c2) {
		double radius = (w * 0.8 + 0.42) * s;
		for (int i = 0; i < CRYSTALS; i++) {
			float k = i / (float) (CRYSTALS - 1);
			double angle = t * 1.25 + i * TAU / CRYSTALS;
			double r = radius * (1.0 + 0.07 * Math.sin(t * 1.7 + i * 2.3));
			double x = pos.x + Math.cos(angle) * r;
			double y = pos.y + h * (0.18 + 0.64 * ((i * 2) % CRYSTALS) / (float) (CRYSTALS - 1)) + Math.sin(t * 2.1 + i * 1.7) * 0.12;
			double z = pos.z + Math.sin(angle) * r;
			float near = Draw3D.cameraFade(x, y, z, 0.5f, 1.4f);
			if (near <= 0.01f) continue;
			int c = ColorUtil.lerp(c1, c2, k);
			// Tumbling about an axis of its own.
			float ax = (float) Math.sin(i * 1.3), ay = 1f, az = (float) Math.cos(i * 2.1);
			float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
			Draw3D.rotation(basis, ax / al, ay / al, az / al, t * (1.6f + 0.3f * i) + i);
			float half = (0.095f + 0.02f * (float) Math.sin(t * 2.6 + i)) * s;
			Draw3D.cube(mesh, x, y, z, half, basis, alpha(ColorUtil.lerp(c, 0xFFFFFFFF, 0.45f), 0.95f * near));
			Draw3D.cube(solid, x, y, z, half, basis, alpha(c, 0.9f * near));
			orb(x, y, z, half * 3.4f, c, 0.45f * near);
			// A short trail of fading sparks along its orbit.
			for (int j = 1; j <= 3; j++) {
				double a = angle - j * 0.16;
				mesh.orb(pos.x + Math.cos(a) * r, y, pos.z + Math.sin(a) * r, half * (1.5f - 0.3f * j), alpha(c, 0.3f * (1f - j / 4f) * near));
			}
		}
	}
}
