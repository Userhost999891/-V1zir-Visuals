package dev.v1zir.visuals.gui.loading;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * A dark backdrop with light falling in from the top corners and small stars drifting up through it, drawn in pixels
 * of the screen. It is under the loading screen and under the PS5 style main menu, which takes over from the loading
 * screen without a seam: the same light and the same stars on one clock. It draws only with plain fills and the
 * pipeline of the Mojang logo, so it works before anything of the game is loaded. Render thread only.
 */
public final class Backdrop {
	/** Stars of the PS5 style's backdrop; its colours are the theme's (accent, second accent, base). */
	private static final int PS5_STARS = 240;
	private static final int STARS = 90;
	private static final long EPOCH = System.nanoTime();
	private static final Identifier BEAM = V1zir.id("backdrop/beam");
	/** The cone of light, made at half the size it shows at for the screen it was made for. */
	private static int beamW;
	private static int beamH;
	private static int madeW;
	private static int madeH;
	private static boolean beamBroken;

	private Backdrop() {
	}

	/** Seconds on the backdrop's own clock, the same for every screen that draws it. */
	public static float time() {
		return (System.nanoTime() - EPOCH) / 1.0e9f;
	}

	/**
	 * The PS5 style backdrop over w x h pixels (the pose in pixels of the screen), in the theme's colours: one beam
	 * falls from the top left corner, near white where it comes in and the accent as it widens, in a faint haze of the
	 * second accent, swaying a little; stars all over, a few drifting, brighter where the beam passes over them.
	 * lightIn and starsIn (0 to 1) bring the light and the stars in; alpha fades it all.
	 */
	public static void ps5(DrawContext ctx, int w, int h, float scale, float lightIn, float starsIn, float alpha) {
		int base = Theme.preset().base();
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		int top = ColorUtil.darken(base, 0.45f) | 0xFF000000;
		int bottom = ColorUtil.lerp(ColorUtil.darken(base, 0.1f), accent2, 0.05f) | 0xFF000000;
		ctx.fillGradient(0, 0, w, h, ColorUtil.fade(top, alpha), ColorUtil.fade(bottom, alpha));
		float t = time();
		float light = lightIn * alpha;
		float ox = w * 0.02f, oy = -h * 0.05f;
		float angle = (float) Math.toRadians(61) + 0.025f * (float) Math.sin(t * 0.32f);
		float length = (float) Math.hypot(w, h) * 1.02f;
		float width = Math.max(w * 0.2f, h * 0.5f);
		if (light > 0.01f && ensureBeam(w, h)) {
			float breathe = 0.92f + 0.08f * (float) Math.sin(t * 0.6f);
			beam(ctx, ox, oy, angle, length * 1.05f, width * 2.1f, ColorUtil.fade(accent2, 0.2f * light));
			beam(ctx, ox, oy, angle, length, width, ColorUtil.fade(accent, 0.95f * light * breathe));
			beam(ctx, ox, oy, angle, length * 0.42f, width * 0.42f, ColorUtil.fade(ColorUtil.lerp(accent, 0xFFFFFFFF, 0.65f), 0.55f * light * breathe));
		}
		int starWhite = 0xFFE9E9EE;
		int starA = ColorUtil.lerp(0xFFFFFFFF, accent, 0.45f);
		int starB = ColorUtil.lerp(0xFFFFFFFF, accent2, 0.45f);
		float stars = starsIn * alpha;
		if (stars <= 0.01f) return;
		float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
		for (int i = 0; i < PS5_STARS; i++) {
			float h1 = hash(i * 1.37f + 0.5f), h2 = hash(i * 7.13f + 2.1f), h3 = hash(i * 3.71f + 9.4f), h4 = hash(i * 5.17f + 4.3f);
			float x = h1 * w;
			// Most stars hang still; one in four drifts slowly up.
			float speed = h4 < 0.25f ? (2f + 6f * h3) * scale : 0f;
			float span = h + 40f;
			float y = (h2 * span - t * speed) % span;
			if (y < 0f) y += span;
			y -= 20f;
			float twinkle = 0.5f + 0.5f * (float) Math.sin(t * (0.8f + 2.4f * h3) + h1 * 40f);
			float a = (0.12f + 0.55f * twinkle * twinkle) * (0.35f + 0.65f * h2);
			// Lit where the beam passes over: nearer its middle and nearer where it comes in, brighter.
			float px = x - ox, py = y - oy;
			float along = px * dx + py * dy;
			if (along > 0f && along < length) {
				float across = Math.abs(px * dy - py * dx);
				float half = width * (0.06f + 0.5f * along / length);
				float lit = (float) Math.exp(-(across / half) * (across / half) * 2f) * (1f - along / length);
				a += lit * 0.9f * light;
			}
			int color = h3 < 0.18f ? starA : h3 > 0.86f ? starB : starWhite;
			int size = Math.max(1, Math.round(scale * (0.4f + 0.75f * h4 * h4 * h4)));
			int sx = Math.round(x), sy = Math.round(y);
			ctx.fill(sx, sy, sx + size, sy + size, ColorUtil.fade(color, Math.min(1f, a) * stars));
		}
	}

	/**
	 * The PS5 style's boot stage, as a console's welcome: a dark room, a spotlight falling from above the top left, a
	 * haze of light lying on the floor and specks of dust floating in the light, over w x h pixels (the pose in pixels
	 * of the screen). It needs the client's shaders (the haze), so only after the first load. alpha fades it all.
	 */
	public static void stage(DrawContext ctx, int w, int h, float scale, float alpha) {
		if (alpha <= 0.004f) return;
		ctx.fillGradient(0, 0, w, h, ColorUtil.fade(0xFF101216, alpha), ColorUtil.fade(0xFF15171C, alpha));
		float t = time();
		float ox = w * 0.13f, oy = -h * 0.08f;
		float angle = (float) Math.toRadians(55) + 0.012f * (float) Math.sin(t * 0.27f);
		float length = (float) Math.hypot(w, h) * 0.95f;
		float width = Math.max(w * 0.24f, h * 0.62f);
		if (ensureBeam(w, h)) {
			float breathe = 0.94f + 0.06f * (float) Math.sin(t * 0.5f);
			beam(ctx, ox, oy, angle, length * 1.1f, width * 2.1f, ColorUtil.fade(0xFFD8D2C8, 0.1f * alpha));
			beam(ctx, ox, oy, angle, length * 0.92f, width, ColorUtil.fade(0xFFEAE4DA, 0.34f * alpha * breathe));
			beam(ctx, ox, oy, angle, length * 0.36f, width * 0.36f, ColorUtil.fade(0xFFFFFFFF, 0.32f * alpha * breathe));
		}
		// The haze on the floor: light lying low across the room, brightest at the left, under the spotlight.
		Render2D.gradientV(ctx, 0f, h * 0.64f, w, h * 0.24f, 0f, 0x0030363E, ColorUtil.fade(0xFF30363E, 0.5f * alpha));
		Render2D.gradientV(ctx, 0f, h * 0.88f, w, h * 0.12f + 1f, 0f, ColorUtil.fade(0xFF30363E, 0.5f * alpha), ColorUtil.fade(0xFF1A1D22, 0.7f * alpha));
		Render2D.shadow(ctx, -w * 0.08f, h * 0.83f, w * 0.62f, h * 0.08f, h * 0.04f, h * 0.12f, ColorUtil.fade(0xFF8E959D, 0.2f * alpha));
		dust(ctx, w, h, scale, t, ox, oy, angle, length, width, alpha);
	}

	/** Specks of dust floating slowly up through the stage, a few large and soft as if out of focus, lit in the beam. */
	private static void dust(DrawContext ctx, int w, int h, float scale, float t, float ox, float oy, float angle, float length, float width,
							 float alpha) {
		float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
		for (int i = 0; i < 170; i++) {
			float h1 = hash(i * 2.17f + 0.3f), h2 = hash(i * 5.31f + 1.7f), h3 = hash(i * 3.03f + 6.2f), h4 = hash(i * 7.77f + 3.9f);
			float span = h + 60f;
			float y = (h2 * span - t * (3f + 7f * h3) * scale) % span;
			if (y < 0f) y += span;
			y -= 30f;
			float x = (h1 * w + (float) Math.sin(t * (0.2f + 0.3f * h4) + h1 * 30f) * 14f * scale) % w;
			float twinkle = 0.55f + 0.45f * (float) Math.sin(t * (0.7f + 1.9f * h3) + h2 * 50f);
			float a = (0.1f + 0.4f * twinkle) * (0.3f + 0.7f * h4);
			float px = x - ox, py = y - oy;
			float along = px * dx + py * dy;
			if (along > 0f && along < length) {
				float across = Math.abs(px * dy - py * dx);
				float half = width * (0.08f + 0.5f * along / length);
				a += (float) Math.exp(-(across / half) * (across / half) * 2f) * (1f - along / length) * 0.9f;
			}
			int color = h3 < 0.3f ? 0xFFF3D7A4 : 0xFFF4F1EA;
			if (h4 > 0.94f) {
				// Out of focus: a soft disc of light.
				float r = (2.5f + 4f * h1) * scale;
				Render2D.shadow(ctx, x - r, y - r, r * 2f, r * 2f, r, r * 1.2f, ColorUtil.fade(color, Math.min(1f, a) * 0.35f * alpha));
				continue;
			}
			int size = Math.max(1, Math.round(scale * (0.45f + 0.8f * h4 * h4)));
			int sx = Math.round(x), sy = Math.round(y);
			ctx.fill(sx, sy, sx + size, sy + size, ColorUtil.fade(color, Math.min(1f, a) * alpha));
		}
	}

	/** Where the PS5 beam comes in and the way it points, for what is lit by it (x, y in pixels, then the direction). */
	public static float[] ps5Beam(int w, int h) {
		float angle = (float) Math.toRadians(61) + 0.025f * (float) Math.sin(time() * 0.32f);
		return new float[]{w * 0.02f, -h * 0.05f, (float) Math.cos(angle), (float) Math.sin(angle), (float) Math.hypot(w, h) * 1.02f,
			Math.max(w * 0.2f, h * 0.5f)};
	}

	/**
	 * Draws the backdrop over w x h pixels (the pose must be in pixels of the screen): a gradient from top to bottom,
	 * the beam from the top left in beam at strength, the one from the top right in beam2 at strength2, and stars.
	 * lightIn and starsIn (0 to 1) bring the light and the stars in; alpha fades it all.
	 */
	public static void draw(DrawContext ctx, int w, int h, float scale, int top, int bottom, int beam, int beam2, float strength, float strength2,
							int star, float lightIn, float starsIn, float alpha) {
		ctx.fillGradient(0, 0, w, h, ColorUtil.fade(top | 0xFF000000, alpha), ColorUtil.fade(bottom | 0xFF000000, alpha));
		float t = time();
		float light = lightIn * alpha;
		if (light > 0.01f && ensureBeam(w, h)) {
			float length = (float) Math.hypot(w, h) * 0.95f;
			float width = Math.max(w, h) * 0.34f;
			float sway = 0.035f * (float) Math.sin(t * 0.45f);
			beam(ctx, w * 0.06f, -h * 0.06f, (float) Math.toRadians(52) + sway, length, width, ColorUtil.fade(beam, strength * light));
			float breathe = 0.85f + 0.15f * (float) Math.sin(t * 0.7f + 1.3f);
			beam(ctx, w * 0.94f, -h * 0.06f, (float) Math.toRadians(128) - sway * 0.8f, length * 0.85f, width * 0.8f,
				ColorUtil.fade(beam2, strength2 * light * breathe));
		}
		float stars = starsIn * alpha;
		if (stars <= 0.01f) return;
		for (int i = 0; i < STARS; i++) {
			float h1 = hash(i * 1.37f + 0.5f), h2 = hash(i * 7.13f + 2.1f), h3 = hash(i * 3.71f + 9.4f);
			float x = h1 * w;
			float speed = (4f + 10f * h3) * scale;
			float span = h + 40f;
			float y = (h2 * span - t * speed) % span;
			if (y < 0f) y += span;
			y -= 20f;
			float twinkle = 0.5f + 0.5f * (float) Math.sin(t * (1.1f + 2.2f * h3) + h1 * 40f);
			float a = (0.15f + 0.6f * twinkle * twinkle) * stars * (0.4f + 0.6f * h2);
			int size = Math.max(1, Math.round(scale * (0.45f + 0.7f * h3 * h3)));
			int px = Math.round(x), py = Math.round(y);
			ctx.fill(px, py, px + size, py + size, ColorUtil.fade(star, a));
		}
	}

	private static void beam(DrawContext ctx, float x, float y, float angle, float length, float width, int color) {
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(x, y);
		m.rotate(angle);
		ctx.drawTexture(RenderPipelines.MOJANG_LOGO, BEAM, 0, Math.round(-width / 2f), 0f, 0f, Math.round(length), Math.round(width),
			beamW, beamH, beamW, beamH, color);
		m.popMatrix();
	}

	/** Makes the cone of light for a w x h screen, once per size; false when it cannot be made. */
	private static boolean ensureBeam(int w, int h) {
		if (beamBroken) return false;
		if (w == madeW && h == madeH) return true;
		int length = Math.max(64, Math.round((float) Math.hypot(w, h) * 0.95f / 2f));
		int width = Math.max(32, Math.round(Math.max(w, h) * 0.34f / 2f));
		try {
			NativeImage image = cone(length, width);
			TextureManager textures = MinecraftClient.getInstance().getTextureManager();
			if (madeW != 0) textures.destroyTexture(BEAM);
			madeW = 0;
			textures.registerTexture(BEAM, new NativeImageBackedTexture(() -> "V1zir backdrop light", image));
			beamW = length;
			beamH = width;
			madeW = w;
			madeH = h;
			return true;
		} catch (RuntimeException e) {
			V1zir.LOGGER.warn("Could not make the backdrop's light", e);
			beamBroken = true;
			return false;
		}
	}

	/** A cone of light, white: narrow and bright where it comes in (left), wide and gone at its far end (right). */
	private static NativeImage cone(int length, int width) {
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, length, width, false);
		float half = width / 2f;
		for (int x = 0; x < length; x++) {
			float u = (x + 0.5f) / length;
			float spread = 0.08f + 0.92f * u;
			float along = (float) Math.pow(1f - u, 1.35f) * smooth(u / 0.04f);
			float hot = (float) Math.pow(1f - u, 3f);
			for (int y = 0; y < width; y++) {
				float v = (y + 0.5f - half) / half / spread;
				float a = along * (float) Math.exp(-v * v * 2.6f) * 0.8f + hot * (float) Math.exp(-v * v * 22f) * 0.5f;
				out.setColorArgb(x, y, Math.round(255f * Anim.clamp01(a)) << 24 | 0xFFFFFF);
			}
		}
		return out;
	}

	private static float smooth(float t) {
		t = Anim.clamp01(t);
		return t * t * (3f - 2f * t);
	}

	/** Pseudo random 0..1 from a float. */
	private static float hash(float v) {
		float n = (float) (Math.sin(v * 12.9898) * 43758.5453);
		return n - (float) Math.floor(n);
	}
}
