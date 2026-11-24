package dev.v1zir.visuals.render.winter;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.WinterModule;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * The Winter module's touches on screen: snow falling in the menus, snow lying on the top of panels, a string of
 * Christmas lights along the top of the screen, frost creeping in from its edges and a cold cast over the world.
 * Each draws only while the module (and its setting) is on. Render thread only.
 */
public final class WinterGui {
	private static final int[] BULBS = {0xFFFF3B3B, 0xFF2EE66B, 0xFFFFC23D, 0xFF3D8BFF, 0xFFFF6BD5};
	@Nullable
	private static TextureSetup frost;
	private static boolean frostBroken;

	private WinterGui() {
	}

	@Nullable
	private static WinterModule module() {
		WinterModule m = ModuleManager.get(WinterModule.class);
		return m != null && m.isEnabled() ? m : null;
	}

	/** Whether menus get their snow (falling, and lying on panels). */
	public static boolean menus() {
		WinterModule m = module();
		return m != null && m.menuSnow.on();
	}

	// ------------------------------------------------------------------ menus

	/**
	 * Snow falling over a menu's background, in three layers: far flakes small, slow and faint, near ones larger,
	 * faster and soft, so it has depth. w and h are the screen's size; alpha fades it with the screen (on top of
	 * Render2D's own fade).
	 */
	public static void snow(DrawContext ctx, float w, float h, float alpha) {
		if (!menus() || alpha <= 0.01f) return;
		float t = V1zir.time();
		float wind = (float) Math.sin(t * 0.13f) * 6f + 4f;
		int[] counts = {80, 50, 22};
		float[] sizes = {0.55f, 0.95f, 1.6f};
		float[] speeds = {9f, 17f, 30f};
		float[] opacity = {0.35f, 0.6f, 0.82f};
		for (int layer = 0; layer < 3; layer++) {
			for (int i = 0; i < counts[layer]; i++) {
				float h1 = hash(i * 1.37f + layer * 31.1f), h2 = hash(i * 7.13f + layer * 13.7f), h3 = hash(i * 3.71f + layer * 5.3f);
				float span = h + 24f;
				float y = (h2 * span + t * speeds[layer] * (0.8f + 0.4f * h3)) % span - 12f;
				float x = (h1 * (w + 40f) + t * wind * (0.5f + layer * 0.35f) + (float) Math.sin(t * (0.6f + h3) + h1 * 40f) * (4f + layer * 3f))
					% (w + 40f);
				if (x < 0f) x += w + 40f;
				x -= 20f;
				float r = sizes[layer] * (0.7f + 0.6f * h3);
				int color = ColorUtil.fade(0xFFF4F8FF, opacity[layer] * alpha * (0.7f + 0.3f * h1));
				if (layer == 2) Render2D.shadow(ctx, x - r, y - r, r * 2f, r * 2f, r, r * 1.6f, ColorUtil.fade(0xFFF4F8FF, 0.3f * alpha));
				Render2D.circle(ctx, x, y, r, color);
			}
		}
	}

	/**
	 * Snow lying along the top edge of a panel (x, y, w; corner radius r): a soft ridge of drifts a few pixels high,
	 * rounded at its ends short of the corners, with a bluish shade under it and a few small icicles hanging below.
	 */
	public static void cap(DrawContext ctx, float x, float y, float w, float r) {
		if (!menus() || w < 30f) return;
		// Render2D fades the shapes with the window itself.
		float alpha = 1f;
		Random random = new Random(Float.floatToIntBits(w) * 31L + 7L);
		float left = x + r * 0.55f;
		float right = x + w - r * 0.55f;
		// The shade it casts on the panel, then the snow itself.
		Render2D.round(ctx, left + 1f, y + 0.5f, right - left - 2f, 2.2f, 1.1f, ColorUtil.fade(0xFF8FA6C8, 0.35f * alpha));
		Render2D.round(ctx, left, y - 2.2f, right - left, 3.4f, 1.7f, ColorUtil.fade(0xFFF7FAFF, alpha));
		float at = left + 2f;
		while (at < right - 6f) {
			float bump = 7f + random.nextFloat() * 16f;
			float tall = 1.2f + random.nextFloat() * 2.4f;
			float bw = Math.min(bump, right - at - 1f);
			Render2D.round(ctx, at, y - 2.2f - tall, bw, tall * 2f + 1f, Math.min(bw, tall * 2f + 1f) / 2f, ColorUtil.fade(0xFFF7FAFF, alpha));
			// Light from above on the drift's top.
			Render2D.round(ctx, at + bw * 0.2f, y - 2.2f - tall + 0.3f, bw * 0.55f, 0.9f, 0.45f, ColorUtil.fade(0xFFFFFFFF, 0.8f * alpha));
			at += bump * (0.55f + random.nextFloat() * 0.35f);
		}
		// Icicles.
		int icicles = (int) (w / 70f);
		for (int i = 0; i < icicles; i++) {
			float ix = left + 8f + random.nextFloat() * (right - left - 16f);
			float len = 2.5f + random.nextFloat() * 4.5f;
			Render2D.round(ctx, ix - 0.7f, y, 1.4f, len, 0.7f, ColorUtil.fade(0xFFD8ECFF, 0.85f * alpha));
		}
	}

	// ------------------------------------------------------------------ in game

	/** The HUD's winter layer, under the hotbar and the client's HUD: the cold cast, the frost, the lights. */
	public static void hud(DrawContext ctx, float w, float h) {
		WinterModule m = module();
		if (m == null || V1zir.mc().world == null) return;
		if (m.coldTint.on()) Render2D.rect(ctx, 0, 0, w, h, 0x10A8C8FF);
		if (m.frost.on()) frost(ctx, w, h, 0.85f);
		if (m.lights.on()) lights(ctx, w, 0f);
	}

	/** Frost creeping in from the screen's edges: feathery crystals over a soft white rim. */
	public static void frost(DrawContext ctx, float w, float h, float opacity) {
		TextureSetup texture = frostTexture();
		if (texture == null) return;
		float breathe = 0.92f + 0.08f * (float) Math.sin(V1zir.time() * 0.35f);
		Render2D.image(ctx, texture, 0, 0, w, h, 0, ColorUtil.fade(0xFFFFFFFF, opacity * breathe));
	}

	/**
	 * A string of Christmas lights along the top of the screen: the wire hangs in shallow loops between hooks, bulbs
	 * hang from it in turn red, green, gold, blue and pink, each glowing and twinkling on its own, now and then a wave
	 * of light running along the string.
	 */
	public static void lights(DrawContext ctx, float w, float top) {
		float t = V1zir.time();
		float spacing = 24f;
		float loop = spacing * 3f;
		float sag = 7f;
		// The wire: short straight pieces along the loops.
		float prevX = 0f, prevY = wireY(0f, loop, sag) + top;
		for (float x = 2f; x <= w + 2f; x += 2f) {
			float y = wireY(x, loop, sag) + top;
			line(ctx, prevX, prevY, x, y, 0.9f, 0xFF1F2A22);
			prevX = x;
			prevY = y;
		}
		int n = (int) (w / spacing) + 1;
		float wave = (t * 0.6f) % 4f;
		for (int i = 0; i < n; i++) {
			float bx = spacing * 0.5f + i * spacing;
			float by = wireY(bx, loop, sag) + top;
			int color = BULBS[i % BULBS.length];
			float h1 = hash(i * 3.17f + 0.5f);
			float twinkle = 0.62f + 0.38f * (float) Math.sin(t * (1.4f + h1 * 2.2f) + h1 * 20f);
			// The wave: a band of extra light running along the string every few seconds.
			float run = Math.max(0f, 1f - Math.abs(bx / Math.max(1f, w) * 4f - wave * 1.4f) * 2.5f);
			float glow = Math.min(1f, twinkle + run * 0.6f);
			float cx = bx;
			float cy = by + 5.2f;
			Render2D.shadow(ctx, cx - 4f, cy - 4f, 8f, 8f, 4f, 7f, ColorUtil.fade(color, 0.55f * glow));
			// The socket, then the bulb: an oval, brighter at its heart.
			Render2D.round(ctx, cx - 1.3f, by + 0.6f, 2.6f, 2.4f, 0.6f, 0xFF2B2E2A);
			Render2D.round(ctx, cx - 2.1f, by + 2.6f, 4.2f, 5.8f, 2.1f, ColorUtil.lerp(ColorUtil.darken(color, 0.35f), color, glow));
			Render2D.round(ctx, cx - 1.1f, by + 3.6f, 2.2f, 3.2f, 1.1f, ColorUtil.fade(ColorUtil.lerp(color, 0xFFFFFFFF, 0.6f), glow));
			Render2D.circle(ctx, cx - 0.8f, by + 3.9f, 0.55f, ColorUtil.fade(0xFFFFFFFF, 0.8f * glow));
		}
	}

	private static float wireY(float x, float loop, float sag) {
		float f = (x % loop) / loop;
		// A shallow hanging curve between hooks.
		return 1.5f + sag * 4f * f * (1f - f);
	}

	/** A short straight piece of line, as a thin rounded rectangle turned to it. */
	private static void line(DrawContext ctx, float x0, float y0, float x1, float y1, float width, int color) {
		float dx = x1 - x0, dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.01f) return;
		var m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(x0, y0);
		m.rotate((float) Math.atan2(dy, dx));
		Render2D.round(ctx, 0f, -width / 2f, len + 0.4f, width, width / 2f, color);
		m.popMatrix();
	}

	// ------------------------------------------------------------------ frost texture

	@Nullable
	private static TextureSetup frostTexture() {
		if (frost != null || frostBroken) return frost;
		try {
			int w = 480, h = 270;
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
			float[] alpha = new float[w * h];
			// A soft rim, uneven: thicker in some places along the edges than others.
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					float u = x / (float) w, v = y / (float) h;
					float edge = Math.min(Math.min(u, 1f - u) * (w / (float) h), Math.min(v, 1f - v));
					float n = fbm(u * 7f, v * 7f);
					float d = edge - 0.05f * n;
					alpha[y * w + x] = smooth(0.16f, 0.0f, d) * (0.35f + 0.45f * n);
				}
			}
			// Crystals: feathery branches growing in from the edges.
			Random random = new Random(0xF0057L);
			for (int i = 0; i < 230; i++) {
				int side = random.nextInt(4);
				float sx, sy, dir;
				float along = random.nextFloat();
				float inset = random.nextFloat() * 0.05f;
				switch (side) {
					case 0 -> {
						sx = along * w;
						sy = inset * h;
						dir = 90f;
					}
					case 1 -> {
						sx = along * w;
						sy = h - inset * h;
						dir = -90f;
					}
					case 2 -> {
						sx = inset * h;
						sy = along * h;
						dir = 0f;
					}
					default -> {
						sx = w - inset * h;
						sy = along * h;
						dir = 180f;
					}
				}
				// Fewer towards the middle of the long edges, more round the corners.
				float corner = Math.min(along, 1f - along);
				if (random.nextFloat() < corner * 1.2f) continue;
				branch(alpha, w, h, sx, sy, dir + (random.nextFloat() - 0.5f) * 70f, 10f + random.nextFloat() * 34f, 1.1f, random, 0);
			}
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					float a = Math.min(1f, alpha[y * w + x]);
					image.setColorArgb(x, y, Math.round(a * 220f) << 24 | 0xE6F3FF);
				}
			}
			NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "V1zir frost", image);
			frost = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		} catch (RuntimeException e) {
			V1zir.LOGGER.warn("Could not make the frost", e);
			frostBroken = true;
		}
		return frost;
	}

	/** A crystal branch: a straight arm with smaller arms off it at sixty degrees, each again with smaller ones. */
	private static void branch(float[] alpha, int w, int h, float x, float y, float dir, float length, float width, Random random, int depth) {
		double a = Math.toRadians(dir);
		float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
		int steps = Math.max(2, Math.round(length));
		for (int s = 0; s <= steps; s++) {
			float px = x + dx * s, py = y + dy * s;
			float fall = 1f - s / (float) steps * 0.7f;
			stamp(alpha, w, h, px, py, width * fall, 0.85f * fall);
			if (depth < 2 && s > 2 && s % 4 == 0 && random.nextFloat() < 0.75f) {
				float side = random.nextBoolean() ? 60f : -60f;
				branch(alpha, w, h, px, py, dir + side, length * (0.28f + random.nextFloat() * 0.2f) * fall, width * 0.7f, random, depth + 1);
			}
		}
	}

	private static void stamp(float[] alpha, int w, int h, float x, float y, float radius, float strength) {
		int x0 = (int) Math.floor(x - radius - 1), x1 = (int) Math.ceil(x + radius + 1);
		int y0 = (int) Math.floor(y - radius - 1), y1 = (int) Math.ceil(y + radius + 1);
		for (int py = Math.max(0, y0); py <= Math.min(h - 1, y1); py++) {
			for (int px = Math.max(0, x0); px <= Math.min(w - 1, x1); px++) {
				float d = (float) Math.hypot(px + 0.5f - x, py + 0.5f - y);
				float a = Math.max(0f, Math.min(1f, radius + 0.5f - d)) * strength;
				int i = py * w + px;
				alpha[i] = Math.max(alpha[i], a);
			}
		}
	}

	private static float fbm(float x, float y) {
		float s = 0f, amp = 0.5f;
		for (int i = 0; i < 4; i++) {
			s += amp * noise(x, y);
			x = x * 2.03f + 1.7f;
			y = y * 2.03f + 9.2f;
			amp *= 0.5f;
		}
		return s;
	}

	private static float noise(float x, float y) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
		float fx = x - ix, fy = y - iy;
		fx = fx * fx * (3f - 2f * fx);
		fy = fy * fy * (3f - 2f * fy);
		float a = hash2(ix, iy), b = hash2(ix + 1, iy), c = hash2(ix, iy + 1), d = hash2(ix + 1, iy + 1);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
	}

	private static float hash2(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	private static float smooth(float e0, float e1, float x) {
		float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
		return t * t * (3f - 2f * t);
	}

	private static float hash(float v) {
		float n = (float) (Math.sin(v * 12.9898) * 43758.5453);
		return n - (float) Math.floor(n);
	}
}
