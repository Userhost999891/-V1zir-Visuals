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
import java.util.concurrent.CompletableFuture;

/**
 * The Winter module's touches on screen: snow falling in the menus, snow lying on the top of panels, a string of
 * Christmas lights along the top of the screen, frost creeping in from its edges and a cold cast over the world.
 * Each draws only while the module (and its setting) is on. Render thread only.
 */
public final class WinterGui {
	private static final int[] BULBS = {0xFFFF3B3B, 0xFF2EE66B, 0xFFFFC23D, 0xFF3D8BFF, 0xFFFF6BD5};
	@Nullable
	private static TextureSetup frost;
	@Nullable
	private static CompletableFuture<NativeImage> frostJob;
	private static boolean frostBroken;
	/** How far the frost has thawed (0 to 1), following the warmth round the player. */
	private static float thaw;
	private static long thawAt;

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
	 * Snow lying along the top edge of a panel (x, y, w; corner radius r): a soft band of snow over the edge, fluffy
	 * drifts piled on it in a wavy line (white on top, blue-grey in their shade, each catching the light on its crown),
	 * its shadow on the panel, and a few clear icicles tapering down from it.
	 */
	public static void cap(DrawContext ctx, float x, float y, float w, float r) {
		if (!menus() || w < 30f) return;
		Random random = new Random(Float.floatToIntBits(w) * 31L + 7L);
		float left = x + r * 0.3f;
		float right = x + w - r * 0.3f;
		// The snow comes down a little over the panel's edge.
		float foot = y + 1.4f;
		float band = y - 3f;
		Render2D.shadow(ctx, left + 3f, foot - 1.2f, right - left - 6f, 2.4f, 1.2f, 3.5f, 0x55607A9E);
		Render2D.gradientV(ctx, left, band, right - left, foot - band, 2.2f, 0xFFF7FAFF, 0xFFC6D5EC);
		float at = left + 1f;
		while (at < right - 3f) {
			float half = 3.5f + random.nextFloat() * 8f;
			float tall = 1.3f + random.nextFloat() * 3.6f;
			float cx = Math.min(at + half, right - half);
			float top = band - tall;
			float h = foot - top;
			Render2D.gradientV(ctx, cx - half, top, half * 2f, h, Math.min(half, h / 2f), 0xFFFFFFFF, 0xFFCAD8EE);
			// Light on the crown of the drift.
			Render2D.round(ctx, cx - half * 0.55f, top + 0.45f, half * 1.1f, 1.2f, 0.6f, 0xD9FFFFFF);
			at += half * (0.85f + random.nextFloat() * 0.6f);
		}
		// Icicles: clear and tapering, a bright line down one side.
		int icicles = Math.max(2, (int) (w / 55f));
		for (int i = 0; i < icicles; i++) {
			float ix = left + 10f + random.nextFloat() * (right - left - 20f);
			float len = 3f + random.nextFloat() * 6.5f;
			Render2D.round(ctx, ix - 1.2f, foot - 0.6f, 2.4f, len * 0.4f + 0.6f, 1.2f, 0xE0E3EEFF);
			Render2D.round(ctx, ix - 0.8f, foot - 0.6f, 1.6f, len * 0.75f, 0.8f, 0xCCD3E4FA);
			Render2D.round(ctx, ix - 0.42f, foot - 0.6f, 0.84f, len, 0.42f, 0xB3C9DDF5);
			Render2D.round(ctx, ix - 0.6f, foot, 0.36f, len * 0.6f, 0.18f, 0xE6FFFFFF);
		}
	}

	// ------------------------------------------------------------------ in game

	/**
	 * The HUD's winter layer, under the hotbar and the client's HUD: the cold cast, the frost, the lights. Warmth thaws
	 * the frost: by a fire it melts back off the screen in a few seconds and a warm glow rises from below; in the cold it
	 * creeps back, slowly.
	 */
	public static void hud(DrawContext ctx, float w, float h) {
		WinterModule m = module();
		if (m == null || V1zir.mc().world == null) return;
		long now = System.nanoTime();
		float dt = thawAt == 0L ? 0f : Math.min(0.1f, (now - thawAt) / 1.0e9f);
		thawAt = now;
		float warmth = m.warmth();
		float rate = warmth > thaw ? 1.3f : 0.16f;
		thaw += (warmth - thaw) * (1f - (float) Math.exp(-rate * dt));
		if (m.coldTint.on()) Render2D.rect(ctx, 0, 0, w, h, ColorUtil.fade(0x10A8C8FF, 1f - thaw));
		if (thaw > 0.01f) {
			// The warmth of the fire, rising from below.
			Render2D.gradientV(ctx, 0, h * 0.45f, w, h * 0.55f, 0f, 0x00FF9A4A, ColorUtil.withAlpha(0xFF9A4A, Math.round(30 * thaw)));
		}
		if (m.frost.on() && thaw < 0.98f) {
			// Frost catches the light: bright by day, dim at night.
			float day = SnowRenderer.daylight(V1zir.mc().world, V1zir.mc().getRenderTickCounter().getTickProgress(false));
			frost(ctx, w, h, 0.8f * (0.32f + 0.68f * day) * (1f - 0.92f * thaw), thaw);
		}
		if (m.lights.on()) lights(ctx, w, 0f);
	}

	/**
	 * Frost creeping in from the screen's edges: feathery crystals over a soft white rim. As it thaws (0 to 1) it fades
	 * and draws back towards the edges and off the screen.
	 */
	public static void frost(DrawContext ctx, float w, float h, float opacity, float thawed) {
		TextureSetup texture = frostTexture();
		if (texture == null) return;
		float breathe = 0.92f + 0.08f * (float) Math.sin(V1zir.time() * 0.35f);
		float grow = 1f + 0.3f * thawed;
		Render2D.image(ctx, texture, -w * (grow - 1f) / 2f, -h * (grow - 1f) / 2f, w * grow, h * grow, 0, ColorUtil.fade(0xFFFFFFFF, opacity * breathe));
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
		// Made on another thread the first time (a few tens of milliseconds), shown once it is ready.
		if (frostJob == null) frostJob = CompletableFuture.supplyAsync(WinterGui::makeFrost);
		if (!frostJob.isDone()) return null;
		try {
			NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "V1zir frost", frostJob.join());
			frost = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		} catch (RuntimeException e) {
			V1zir.LOGGER.warn("Could not make the frost", e);
			frostBroken = true;
		}
		return frost;
	}

	/** The frost's picture: a soft uneven rim round the edges and fine feathery crystals growing in from them. */
	private static NativeImage makeFrost() {
		int w = 768, h = 432;
		float[] alpha = new float[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float u = x / (float) w, v = y / (float) h;
				float edge = Math.min(Math.min(u, 1f - u) * (w / (float) h), Math.min(v, 1f - v));
				float n = fbm(u * 7f, v * 7f);
				float d = edge - 0.05f * n;
				alpha[y * w + x] = smooth(0.15f, 0.0f, d) * (0.3f + 0.42f * n);
			}
		}
		Random random = new Random(0xF0057L);
		for (int i = 0; i < 320; i++) {
			int side = random.nextInt(4);
			float sx, sy, dir;
			float along = random.nextFloat();
			float inset = random.nextFloat() * 0.045f;
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
			branch(alpha, w, h, sx, sy, dir + (random.nextFloat() - 0.5f) * 70f, 14f + random.nextFloat() * 50f, 0.95f, random, 0);
		}
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float a = Math.min(1f, alpha[y * w + x]);
				image.setColorArgb(x, y, Math.round(a * 215f) << 24 | 0xE6F3FF);
			}
		}
		return image;
	}

	/**
	 * A crystal branch: a straight arm, thinning and fading towards its tip, with smaller arms off it at sixty degrees,
	 * each again with smaller ones. Drawn in half-pixel steps, so the lines are whole.
	 */
	private static void branch(float[] alpha, int w, int h, float x, float y, float dir, float length, float width, Random random, int depth) {
		double a = Math.toRadians(dir);
		float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
		int steps = Math.max(4, Math.round(length * 2f));
		for (int s = 0; s <= steps; s++) {
			float along = s * 0.5f;
			float px = x + dx * along, py = y + dy * along;
			float fall = 1f - s / (float) steps * 0.7f;
			stamp(alpha, w, h, px, py, width * fall, 0.8f * fall);
			if (depth < 2 && s > 5 && s % 7 == 0 && random.nextFloat() < 0.75f) {
				float side = random.nextBoolean() ? 60f : -60f;
				branch(alpha, w, h, px, py, dir + side, length * (0.26f + random.nextFloat() * 0.2f) * fall, width * 0.72f, random, depth + 1);
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
