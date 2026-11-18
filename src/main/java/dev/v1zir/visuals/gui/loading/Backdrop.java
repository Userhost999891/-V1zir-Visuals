package dev.v1zir.visuals.gui.loading;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
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
	/** The PS5 style theme's backdrop, light and stars. */
	public static final int PS5_TOP = 0xFF02030A;
	public static final int PS5_BOTTOM = 0xFF0A1036;
	public static final int PS5_BEAM = 0xFF8C63FF;
	public static final int PS5_BEAM_2 = 0xFF3B6BFF;
	public static final int PS5_STAR = 0xFFC9D6FF;
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

	/** The PS5 style backdrop over w x h pixels. */
	public static void ps5(DrawContext ctx, int w, int h, float scale, float lightIn, float starsIn, float alpha) {
		draw(ctx, w, h, scale, PS5_TOP, PS5_BOTTOM, PS5_BEAM, PS5_BEAM_2, 0.95f, 0.32f, PS5_STAR, lightIn, starsIn, alpha);
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
