package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.ColorGrade;
import dev.v1zir.visuals.render.world.Draw3D;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * VIBE: one switch for a whole mood. Every theme brings its own particles drifting around you in the wind, a low haze
 * in its colours (the Mist's pass, render/PostFx, while Mist itself is off) and a colour grade of its own
 * (render/ColorGrade, while Color Grading is off):
 * <ul>
 *   <li>Sakura: cherry petals fluttering down, a soft pink haze, rosy light;</li>
 *   <li>Autumn: red, orange and golden leaves tumbling down, warm amber light;</li>
 *   <li>Winter: snow drifting down, a cold white haze, icy blue light;</li>
 *   <li>Fireflies: little lights blinking as they wander, a deep blue evening;</li>
 *   <li>Neon: magenta and cyan streaks rising, a violet haze, saturated light;</li>
 *   <li>Ocean: bubbles of light rising through a teal haze;</li>
 *   <li>Dreamy: soft pastel bokeh floating in a pale haze, gentle contrast;</li>
 *   <li>Stardust: tiny twinkling stars drifting, violet night light.</li>
 * </ul>
 * The particles live in the world (you walk through them) and those that leave the radius come back at its edge, so a
 * few hundred quads a frame are all it costs.
 */
public final class VibeModule extends Module implements WorldEffect {
	private static final int PETAL = 0;
	private static final int LEAF = 1;
	private static final int SNOW = 2;
	private static final int FIREFLY = 3;
	private static final int NEON = 4;
	private static final int BUBBLE = 5;
	private static final int BOKEH = 6;
	private static final int STAR = 7;
	/** Per particle: position relative to the anchor (3), wander velocity (3), phase, variety, size. */
	private static final int STRIDE = 9;

	/**
	 * A theme: its particle, how many at Particles 1x, two particle colours, the haze colour (night and day share it,
	 * darkened at night) and density, the grade's shadow, midtone and highlight colours with the look's strength, and
	 * white balance (warmth, magenta), saturation and contrast.
	 */
	private record Mood(int particle, int count, int colorA, int colorB, int haze, float hazeDensity, int shadow, int mid, int light,
						float look, float warmth, float magenta, float saturation, float contrast, float wind) {
	}

	private static final Mood[] MOODS = {
		// Sakura
		new Mood(PETAL, 170, 0xFFFFB7D5, 0xFFFF8FB8, 0xFFFFC9DD, 0.8f, 0x3A1030, 0xFF8FB8, 0xFFF0F5, 0.34f, 0.12f, 0.12f, 1.08f, 1.03f, 1f),
		// Autumn
		new Mood(LEAF, 110, 0xFFE8531F, 0xFFFFB13B, 0xFFE8B080, 0.7f, 0x2A1406, 0xE07A2E, 0xFFE8C0, 0.34f, 0.35f, 0f, 1.12f, 1.05f, 1.3f),
		// Winter
		new Mood(SNOW, 260, 0xFFFFFFFF, 0xFFE3F1FF, 0xFFDCE8F5, 1.0f, 0x0A1A30, 0x7FB8FF, 0xF0FAFF, 0.3f, -0.35f, 0f, 0.9f, 1.02f, 0.6f),
		// Fireflies
		new Mood(FIREFLY, 90, 0xFFD6FF6A, 0xFFFFE27A, 0xFF1E2C4A, 0.6f, 0x050A1A, 0x3050A0, 0xE0F0FF, 0.3f, -0.1f, 0f, 1.05f, 1.04f, 0.2f),
		// Neon
		new Mood(NEON, 120, 0xFFFF3DD8, 0xFF3DF2FF, 0xFF6A2A8A, 0.7f, 0x1A0530, 0xFF3DD8, 0x8FF8FF, 0.4f, -0.05f, 0.15f, 1.25f, 1.1f, 0f),
		// Ocean
		new Mood(BUBBLE, 120, 0xFF7AF2FF, 0xFF3DB8D8, 0xFF7ACFD8, 0.8f, 0x021A24, 0x2FB8C8, 0xE0FFFA, 0.35f, -0.2f, 0f, 1.08f, 1.03f, 0.3f),
		// Dreamy
		new Mood(BOKEH, 60, 0xFFFFC4E1, 0xFFD8B4FF, 0xFFF4DCEC, 0.9f, 0x2A1A3A, 0xD8A0E0, 0xFFF4F0, 0.35f, 0.1f, 0.08f, 0.95f, 0.94f, 0.2f),
		// Stardust
		new Mood(STAR, 220, 0xFFFFFFFF, 0xFFB9A6FF, 0xFF2A1F4A, 0.6f, 0x0A0620, 0x7A5CFF, 0xF0E8FF, 0.35f, -0.1f, 0.1f, 1.1f, 1.05f, 0.15f),
	};

	public final ModeSetting theme = add(new ModeSetting("Theme", "Sakura", "Sakura", "Autumn", "Winter", "Fireflies", "Neon", "Ocean", "Dreamy",
		"Stardust"));
	/** How strong the haze and the grade are. */
	public final NumberSetting intensity = add(new NumberSetting("Intensity", 1.0, 0.2, 2.0, 0.05).suffix("x"));
	public final NumberSetting particles = add(new NumberSetting("Particles", 1.0, 0.0, 3.0, 0.05).suffix("x"));
	public final NumberSetting particleSize = add(new NumberSetting("Particle size", 1.0, 0.4, 2.5, 0.05).suffix("x"));
	public final NumberSetting radius = add(new NumberSetting("Radius", 22, 8, 48, 1).suffix(" m"));
	public final NumberSetting wind = add(new NumberSetting("Wind", 1.0, 0.0, 3.0, 0.05).suffix("x"));
	public final BoolSetting haze = add(new BoolSetting("Haze", true));
	public final BoolSetting grade = add(new BoolSetting("Color grade", true));

	private float[] data = new float[0];
	private final float[] basis = new float[9];
	private long lastFrame = System.nanoTime();
	private int shownTheme = -1;
	// World position the particle offsets are stored against (floats stay precise near it).
	private double anchorX;
	private double anchorY;
	private double anchorZ;
	private double lastCamX;
	private double lastCamY;
	private double lastCamZ;

	public VibeModule() {
		super("VIBE", "A whole mood at once: Sakura, Autumn, Winter, Fireflies, Neon and more", Category.SHADERS);
		setSubCategory("World");
	}

	@Override
	public String suffix() {
		return theme.get();
	}

	@Override
	protected void onDisable() {
		data = new float[0];
	}

	private Mood mood() {
		return MOODS[Math.max(0, Math.min(MOODS.length - 1, theme.index()))];
	}

	// ------------------------------------------------------------------ haze and grade

	/** The module, when it is on and its haze should be drawn (PostFx, while Mist is off). */
	public static VibeModule hazing() {
		VibeModule vibe = ModuleManager.get(VibeModule.class);
		return vibe != null && vibe.isEnabled() && vibe.haze.on() ? vibe : null;
	}

	/** The haze's colour now: the theme's, darker at night; none of it in the Nether or the End. */
	public int hazeColor(ClientWorld world, float sunHeight) {
		int color = mood().haze();
		if (world.getRegistryKey() != World.OVERWORLD) return ColorUtil.darken(color, 0.6f);
		float day = Math.max(0f, Math.min(1f, (sunHeight + 0.12f) / 0.37f));
		return ColorUtil.lerp(ColorUtil.darken(color, 0.65f), color, day);
	}

	/** Density factor of the haze, as Mist's Density setting. */
	public float hazeDensity() {
		return mood().hazeDensity() * intensity.f();
	}

	/** The height over which the haze thins, blocks. */
	public float hazeHeight() {
		return 10f;
	}

	/** End of renderWorld, after Color Grading's turn (GameRendererMixin): the theme's grade, while Color Grading is off. */
	public static void gradeIfEnabled() {
		VibeModule vibe = ModuleManager.get(VibeModule.class);
		if (vibe == null || !vibe.isEnabled() || !vibe.grade.on() || mc.world == null) return;
		if (ModuleManager.isEnabled(ColorGradingModule.class)) return;
		Mood m = vibe.mood();
		float k = vibe.intensity.f();
		float warm = m.warmth() * k;
		float magenta = m.magenta() * k;
		float r = 1f + 0.12f * warm;
		float g = 1f - 0.1f * magenta;
		float b = 1f - 0.12f * warm;
		float scale = 1f / (0.2126f * r + 0.7152f * g + 0.0722f * b);
		float saturation = 1f + (m.saturation() - 1f) * k;
		float contrast = 1f + (m.contrast() - 1f) * k;
		ColorGrade.render(r * scale, g * scale, b * scale, contrast, saturation, 0.15f * k, 0.22f * Math.min(1f, k), m.shadow(), m.mid(), m.light(),
			Math.min(1f, m.look() * k));
	}

	// ------------------------------------------------------------------ particles

	@Override
	public void render3D(float tickDelta) {
		Mood m = mood();
		int n = Math.round(m.count() * particles.f());
		float r = radius.f();
		int kind = m.particle();
		double cx = WorldEffectRenderer.camX;
		double cy = WorldEffectRenderer.camY;
		double cz = WorldEffectRenderer.camZ;
		double jx = cx - lastCamX;
		double jy = cy - lastCamY;
		double jz = cz - lastCamZ;
		boolean teleported = jx * jx + jy * jy + jz * jz > r * r;
		lastCamX = cx;
		lastCamY = cy;
		lastCamZ = cz;
		if (n <= 0) return;
		if (data.length != n * STRIDE || theme.index() != shownTheme || teleported) {
			// A new count, theme or a jump far away: start over around the camera.
			data = new float[n * STRIDE];
			shownTheme = theme.index();
			anchorX = cx;
			anchorY = cy;
			anchorZ = cz;
			for (int i = 0; i < n; i++) respawn(i, kind, 0f, 0f, 0f, r, true);
		} else if (Math.abs(cx - anchorX) + Math.abs(cy - anchorY) + Math.abs(cz - anchorZ) > 256.0) {
			// Far from the anchor: move it to the camera so the float offsets stay precise (invisible).
			float sx = (float) (cx - anchorX);
			float sy = (float) (cy - anchorY);
			float sz = (float) (cz - anchorZ);
			for (int o = 0; o < data.length; o += STRIDE) {
				data[o] -= sx;
				data[o + 1] -= sy;
				data[o + 2] -= sz;
			}
			anchorX = cx;
			anchorY = cy;
			anchorZ = cz;
		}
		float ox = (float) (cx - anchorX);
		float oy = (float) (cy - anchorY);
		float oz = (float) (cz - anchorZ);
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.05f);
		lastFrame = now;
		float t = V1zir.time();
		// The wind turns slowly and comes in gusts.
		float windAngle = t * 0.04f + (float) Math.sin(t * 0.013f) * 1.7f;
		float gust = (0.55f + 0.45f * (float) Math.sin(t * 0.37f)) * wind.f() * m.wind();
		float wx = (float) Math.cos(windAngle) * gust;
		float wz = (float) Math.sin(windAngle) * gust;
		float scale = particleSize.f();
		WorldEffectRenderer.Mesh glow = WorldEffectRenderer.glow();
		WorldEffectRenderer.Mesh body = WorldEffectRenderer.translucent();
		for (int i = 0; i < n; i++) {
			int o = i * STRIDE;
			float phase = data[o + 6];
			float variety = data[o + 7];
			move(o, kind, phase, variety, t, dt, wx, wz);
			float dx = data[o] - ox;
			float dy = data[o + 1] - oy;
			float dz = data[o + 2] - oz;
			float dist2 = dx * dx + dy * dy + dz * dz;
			if (dist2 > r * r * 1.1f) {
				respawn(i, kind, ox, oy, oz, r, false);
				continue;
			}
			float dist = (float) Math.sqrt(dist2);
			// Faded in towards the edge of the radius, and out right in front of the eyes.
			float near = Math.max(0f, Math.min(1f, (dist - 0.6f) / 1.0f));
			float alpha = Math.min(1f, (1f - dist / r) * 2.5f) * near * near * (3f - 2f * near);
			if (alpha <= 0.01f) continue;
			double x = cx + dx;
			double y = cy + dy;
			double z = cz + dz;
			float size = data[o + 8] * scale;
			int color = ColorUtil.lerp(m.colorA(), m.colorB(), variety);
			switch (kind) {
				case PETAL -> leaf(body, glow, x, y, z, size, phase, variety, t, 2.2f, 0.55f, color, ColorUtil.lerp(color, 0xFFFFFFFF, 0.55f), alpha);
				case LEAF -> leaf(body, glow, x, y, z, size, phase, variety, t, 1.6f, 0.62f, autumn(variety), ColorUtil.lerp(autumn(variety), 0xFFFFE9B0, 0.3f),
					alpha);
				case SNOW -> {
					glow.orb(x, y, z, size * 1.9f, ColorUtil.withAlpha(0xFFF2F6FF, Math.round(80 * alpha)));
					glow.orb(x, y, z, size * 0.8f, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(220 * alpha)));
				}
				case FIREFLY -> {
					// Each one blinks on its own slow rhythm: mostly dim, now and then lit.
					float blink = (float) Math.pow(0.5 + 0.5 * Math.sin(t * (0.9 + variety * 1.3) + phase), 6.0);
					float a = alpha * (0.15f + 0.85f * blink);
					glow.orb(x, y, z, size * 4.5f, ColorUtil.withAlpha(color, Math.round(130 * a)));
					glow.orb(x, y, z, size * 1.1f, ColorUtil.withAlpha(0xFFFFFFE6, Math.round(230 * a)));
				}
				case NEON -> {
					int c = variety < 0.5f ? m.colorA() : m.colorB();
					float flicker = alpha * (0.7f + 0.3f * (float) Math.sin(t * 9f + phase * 3f));
					Draw3D.line(glow, x, y, z, x, y - (0.35f + variety * 0.4f) * scale, z, size * 0.55f,
						ColorUtil.withAlpha(c, Math.round(235 * flicker)), ColorUtil.withAlpha(c, 0));
					glow.orb(x, y, z, size * 2.2f, ColorUtil.withAlpha(c, Math.round(120 * flicker)));
				}
				case BUBBLE -> {
					glow.orb(x, y, z, size * 2.6f, ColorUtil.withAlpha(color, Math.round(70 * alpha)));
					body.orb(x, y, z, size * 1.3f, ColorUtil.withAlpha(ColorUtil.lerp(color, 0xFFFFFFFF, 0.4f), Math.round(60 * alpha)));
					glow.orb(x + size * 0.4f, y + size * 0.45f, z, size * 0.45f, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(200 * alpha)));
				}
				case BOKEH -> {
					float breathe = 0.75f + 0.25f * (float) Math.sin(t * 0.6f + phase);
					glow.orb(x, y, z, size * 5f, ColorUtil.withAlpha(color, Math.round(46 * alpha * breathe)));
					glow.orb(x, y, z, size * 2.6f, ColorUtil.withAlpha(ColorUtil.lerp(color, 0xFFFFFFFF, 0.5f), Math.round(40 * alpha * breathe)));
				}
				default -> {
					float twinkle = (float) Math.pow(0.5 + 0.5 * Math.sin(t * (2.0 + variety * 4.0) + phase), 3.0);
					glow.orb(x, y, z, size * 2.4f, ColorUtil.withAlpha(color, Math.round(110 * alpha * (0.3f + 0.7f * twinkle))));
					glow.orb(x, y, z, size * 0.7f, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(230 * alpha * (0.4f + 0.6f * twinkle))));
				}
			}
		}
	}

	/** Moves particle o by one frame of its kind: falling, rising or wandering, carried by the wind (wx, wz). */
	private void move(int o, int kind, float phase, float variety, float t, float dt, float wx, float wz) {
		float sway = (float) Math.sin(t * (0.9f + variety) + phase);
		float swayZ = (float) Math.cos(t * (0.7f + variety * 0.8f) + phase * 1.3f);
		switch (kind) {
			case PETAL -> {
				data[o] += (wx * 0.9f + sway * 0.45f) * dt;
				data[o + 1] -= (0.32f + variety * 0.35f + 0.15f * (float) Math.sin(t * 2.3f + phase)) * dt;
				data[o + 2] += (wz * 0.9f + swayZ * 0.45f) * dt;
			}
			case LEAF -> {
				data[o] += (wx * 1.1f + sway * 0.7f) * dt;
				data[o + 1] -= (0.55f + variety * 0.5f + 0.25f * Math.abs(sway)) * dt;
				data[o + 2] += (wz * 1.1f + swayZ * 0.7f) * dt;
			}
			case SNOW -> {
				data[o] += (wx * 0.6f + sway * 0.25f) * dt;
				data[o + 1] -= (0.45f + variety * 0.45f) * dt;
				data[o + 2] += (wz * 0.6f + swayZ * 0.25f) * dt;
			}
			case NEON -> data[o + 1] += (0.35f + variety * 0.6f) * dt;
			case BUBBLE -> {
				data[o] += sway * 0.2f * dt;
				data[o + 1] += (0.35f + variety * 0.45f) * dt;
				data[o + 2] += swayZ * 0.2f * dt;
			}
			default -> {
				// Fireflies, bokeh and stardust wander on their own and drift a little with the wind.
				data[o] += (data[o + 3] + sway * 0.25f + wx * 0.2f) * dt;
				data[o + 1] += (data[o + 4] + (float) Math.sin(t * 0.8f + phase * 2f) * 0.15f) * dt;
				data[o + 2] += (data[o + 5] + swayZ * 0.25f + wz * 0.2f) * dt;
			}
		}
	}

	/**
	 * A petal or a leaf: a pointed blade tumbling about an axis of its own as it falls, filled in its colour from a
	 * lighter tip, with a faint glow so it still reads in the dark.
	 */
	private void leaf(WorldEffectRenderer.Mesh body, WorldEffectRenderer.Mesh glow, double x, double y, double z, float size, float phase,
					  float variety, float t, float spin, float widthRatio, int color, int tip, float alpha) {
		float ax = (float) Math.sin(phase * 1.7f), ay = 0.6f + 0.4f * (float) Math.cos(phase), az = (float) Math.cos(phase * 2.3f);
		float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
		Draw3D.rotation(basis, ax / al, ay / al, az / al, t * spin * (0.7f + variety * 0.6f) + phase * 3f);
		float len = size;
		float wid = size * widthRatio;
		float[] b = basis;
		int a = Math.round(235 * alpha);
		int base = ColorUtil.withAlpha(color, a);
		int light = ColorUtil.withAlpha(tip, a);
		int dark = ColorUtil.withAlpha(ColorUtil.darken(color, 0.15f), a);
		// Tip, side, root, side: a blade a little pointed at its tip and rounder at its root.
		body.vertex(x + b[0] * len, y + b[1] * len, z + b[2] * len, 3f, 0f, light);
		body.vertex(x + b[3] * wid, y + b[4] * wid, z + b[5] * wid, 3f, 0f, base);
		body.vertex(x - b[0] * len * 0.62f, y - b[1] * len * 0.62f, z - b[2] * len * 0.62f, 3f, 0f, dark);
		body.vertex(x - b[3] * wid, y - b[4] * wid, z - b[5] * wid, 3f, 0f, base);
		glow.orb(x, y, z, size * 1.6f, ColorUtil.withAlpha(color, Math.round(26 * alpha)));
	}

	/** Autumn's leaves: red, orange, golden and brown. */
	private static int autumn(float variety) {
		if (variety < 0.3f) return ColorUtil.lerp(0xFFD8341E, 0xFFE8531F, variety / 0.3f);
		if (variety < 0.65f) return ColorUtil.lerp(0xFFE8731F, 0xFFFFB13B, (variety - 0.3f) / 0.35f);
		if (variety < 0.88f) return ColorUtil.lerp(0xFFFFC94A, 0xFFE0A030, (variety - 0.65f) / 0.23f);
		return 0xFF8A5A2E;
	}

	/** Places particle i around the camera (ox, oy, oz relative to the anchor): anywhere, or where it comes in from. */
	private void respawn(int i, int kind, float ox, float oy, float oz, float r, boolean anywhere) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int o = i * STRIDE;
		double theta = random.nextDouble() * Math.PI * 2;
		boolean falling = kind == PETAL || kind == LEAF || kind == SNOW;
		boolean rising = kind == NEON || kind == BUBBLE;
		// Falling ones come back anywhere over the top, rising ones under the bottom, the rest at the edge.
		double dist = (anywhere || falling || rising ? Math.sqrt(random.nextDouble()) * 0.95 : 0.75 + random.nextDouble() * 0.25) * r;
		data[o] = ox + (float) (Math.cos(theta) * dist);
		data[o + 2] = oz + (float) (Math.sin(theta) * dist);
		float y;
		if (anywhere) y = (random.nextFloat() * 2f - 1f) * r * 0.45f;
		else if (falling) y = r * (0.3f + random.nextFloat() * 0.15f);
		else if (rising) y = -r * (0.3f + random.nextFloat() * 0.15f);
		else y = (random.nextFloat() * 2f - 1f) * r * 0.35f;
		data[o + 1] = oy + y;
		data[o + 3] = (random.nextFloat() - 0.5f) * 0.5f;
		data[o + 4] = (random.nextFloat() - 0.5f) * 0.2f;
		data[o + 5] = (random.nextFloat() - 0.5f) * 0.5f;
		data[o + 6] = random.nextFloat() * 6.283f;
		data[o + 7] = random.nextFloat();
		data[o + 8] = switch (kind) {
			case PETAL -> 0.07f + random.nextFloat() * 0.05f;
			case LEAF -> 0.1f + random.nextFloat() * 0.08f;
			case SNOW -> 0.035f + random.nextFloat() * 0.04f;
			case FIREFLY -> 0.03f + random.nextFloat() * 0.03f;
			case NEON -> 0.05f + random.nextFloat() * 0.04f;
			case BUBBLE -> 0.05f + random.nextFloat() * 0.07f;
			case BOKEH -> 0.15f + random.nextFloat() * 0.2f;
			default -> 0.02f + random.nextFloat() * 0.03f;
		};
	}
}
