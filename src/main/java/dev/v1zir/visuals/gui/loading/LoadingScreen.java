package dev.v1zir.visuals.gui.loading;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.UiSounds;
import dev.v1zir.visuals.gui.menu.BootScreen;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The game's loading screen, at start and whenever the resource packs reload, in place of the Mojang one
 * (SplashOverlayMixin clips that away and leaves it to run its own course: the progress, the end of the load, the
 * fades). Plain and classic: the name, white, in the middle of a dark screen (the theme's base), and under it a white
 * capsule that fills with the progress, with a thin white rim a few pixels outside it, as the buttons of a console
 * mark their focus. When the load is done the capsule runs full and it all fades into the screen behind.
 * <p>
 * With the PS5 style theme it is a console's start instead: the client's mark alone on black, as long as the load
 * takes; at the first start the start itself takes over from it (BootScreen), drawing the same mark in the same place.
 * <p>
 * Until the first load is done neither the game's fonts nor this mod's shaders exist, so it draws only with what the
 * Mojang screen itself draws with: plain fills, and textures through the pipeline of its logo. The name, the capsule's
 * ends, the track and the rim are made here as textures of their own, at the size they show at in pixels of the screen,
 * and drawn pixel for pixel. Render thread only.
 */
public final class LoadingScreen {
	private static final String NAME = "V1zir Visuals";
	private static final String BOLD = "/assets/v1zir/fonts/inter_bold.ttf";
	private static final String MARK_FILE = "/assets/v1zir/textures/gui/logo.png";

	/** The overlay being drawn, and when it first showed: a new one starts its intro again. */
	private static Object overlay;
	private static long shownAt;
	private static long lastFrame;
	/** The game's first load has played its sound (the PS5 style's swell as the load is done). */
	private static boolean booted;
	/** The progress the bar shows: it follows the game's, never back, and runs full once the load is done. */
	private static float shown;
	/** What the textures were made for: the screen's size in pixels. */
	private static int madeW;
	private static int madeH;
	/** The textures could not be made: the screen draws without them. */
	private static boolean broken;
	private static Glyphs title;
	/** The PS5 style's mark, made at the size it shows at (BootScreen.MARK of the screen's height). */
	private static Sprite mark;
	/** A white disc as tall as the bar (its round ends), the bar's whole capsule (the track) and the rim around it. */
	private static Sprite cap;
	private static Sprite track;
	private static Sprite rim;
	private static final List<Identifier> MADE = new ArrayList<>();

	private LoadingScreen() {
	}

	/** Whether the loading screen replaces the Mojang one (Theme, Loading screen). */
	public static boolean enabled() {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		return theme == null || theme.loadingScreen.on();
	}

	/**
	 * Draws the loading screen, and before it the screen behind while it fades in or out, as the Mojang one does.
	 * reloading, progress, completeTime and startTime are the overlay's own (the times in Util.getMeasuringTimeMs, -1
	 * until then).
	 */
	public static void render(DrawContext ctx, Object owner, int mouseX, int mouseY, float delta, boolean reloading, float progress,
							  long completeTime, long startTime) {
		MinecraftClient mc = MinecraftClient.getInstance();
		long now = System.nanoTime();
		if (owner != overlay) {
			overlay = owner;
			shownAt = now;
			shown = 0f;
			lastFrame = now;
		}
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.1f);
		lastFrame = now;
		long ms = Util.getMeasuringTimeMs();
		float f = completeTime > -1L ? (ms - completeTime) / 1000f : -1f;
		float g = startTime > -1L ? (ms - startTime) / 500f : -1f;
		// How much of what is behind it the loading screen covers, faded in and out as the Mojang screen is.
		float cover;
		Screen screen = mc.currentScreen;
		boolean ps5 = Theme.ps5();
		if (f >= 1f) {
			// The PS5 style's start draws the same mark on black where this leaves it: it takes over at once.
			cover = ps5 && screen instanceof BootScreen ? 0f : 1f - Anim.clamp01(f - 1f);
			if (screen != null) screen.render(ctx, 0, 0, delta);
		} else if (reloading) {
			cover = Anim.clamp01(g);
			if (screen != null && g < 1f) screen.render(ctx, mouseX, mouseY, delta);
		} else {
			cover = 1f;
		}
		if (cover <= 0.002f) {
			if (f >= 2f) release();
			return;
		}
		if (f >= 0f && !reloading && !booted) {
			booted = true;
			UiSounds.play(UiSounds.BOOT);
		}
		shown = Math.max(shown, shown + (progress - shown) * Math.min(1f, dt * 6f));
		if (f >= 0f) shown += (1f - shown) * Math.min(1f, dt * 9f);
		shown = Anim.clamp01(shown);

		int w = mc.getWindow().getFramebufferWidth();
		int h = mc.getWindow().getFramebufferHeight();
		if (!broken && (title == null || w != madeW || h != madeH)) make(w, h);
		float t = (now - shownAt) / 1.0e9f * (reloading ? 1.6f : 1f);
		float scale = (float) mc.getWindow().getScaleFactor();
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		// Pixels of the screen from here on.
		m.scale(1f / scale, 1f / scale);
		if (ps5) {
			// As a console starts: its mark, alone, on black.
			ctx.fill(0, 0, w, h, ColorUtil.fade(0xFF000000, cover));
			if (mark != null) sprite(ctx, mark, (w - mark.width) / 2f, (h - mark.height) / 2f, mark.width, mark.height, ColorUtil.fade(0xFFFFFFFF, cover));
			m.popMatrix();
			if (f >= 2f) release();
			return;
		}
		int base = Theme.preset().base() | 0xFF000000;
		ctx.fillGradient(0, 0, w, h, ColorUtil.fade(ColorUtil.darken(base, 0.35f), cover), ColorUtil.fade(base, cover));
		float cx = w / 2f;
		float unit = Math.min(w, h);
		float titleH = titleSize(unit);
		float barH = barHeight(unit);
		float barW = barWidth(w, unit);
		float gap = rimGap(barH);
		float block = titleH + titleH * 0.95f + barH + gap * 2f;
		float top = h / 2f - block / 2f;
		// The name comes in rising a little, then the bar.
		float nameIn = in(t, 0.05f, 0.6f) * cover;
		if (title != null && nameIn > 0.01f) {
			float width = title.width(NAME, title.size * 0.01f);
			title.drawString(ctx, NAME, cx - width / 2f, top + title.ascent + (1f - nameIn) * titleH * 0.25f, title.size * 0.01f,
				ColorUtil.fade(0xFFFFFFFF, nameIn));
		}
		float barIn = in(t, 0.25f, 0.6f) * cover;
		float barY = top + titleH + titleH * 0.95f + gap;
		bar(ctx, cx - barW / 2f, barY, barW, barH, gap, barIn);
		m.popMatrix();
		if (f >= 2f) release();
	}

	private static float titleSize(float unit) {
		return Math.max(18f, Math.min(96f, unit * 0.06f));
	}

	private static float barHeight(float unit) {
		return Math.max(7f, Math.round(unit * 0.012f));
	}

	private static float barWidth(int w, float unit) {
		return Math.round(Math.max(200f, Math.min(w * 0.3f, unit * 0.62f)));
	}

	/** How far outside the capsule its rim sits. */
	private static float rimGap(float barH) {
		return Math.max(3f, Math.round(barH * 0.55f));
	}

	/** 0 before start, rising to 1 over length seconds with a soft end. */
	private static float in(float t, float start, float length) {
		return Anim.easeOutCubic((t - start) / length);
	}

	// ------------------------------------------------------------------ drawing

	/**
	 * The capsule: a faint track over its whole length, the white part filled as far as the progress (its round ends
	 * from the disc, its middle a plain fill), and the rim a few pixels outside it all.
	 */
	private static void bar(DrawContext ctx, float x, float y, float width, float height, float gap, float alpha) {
		if (alpha <= 0.01f) return;
		int x0 = Math.round(x), y0 = Math.round(y), bw = Math.round(width), bh = Math.round(height);
		if (track != null) sprite(ctx, track, x0, y0, track.width, track.height, ColorUtil.fade(0xFFFFFFFF, 0.14f * alpha));
		if (rim != null) {
			int g = Math.round(gap);
			sprite(ctx, rim, x0 - g, y0 - g, rim.width, rim.height, ColorUtil.fade(0xFFFFFFFF, 0.85f * alpha));
		}
		int fill = Math.max(bh, Math.round(bw * shown));
		int color = ColorUtil.fade(0xFFFFFFFF, alpha);
		if (cap == null) {
			ctx.fill(x0, y0, x0 + fill, y0 + bh, color);
			return;
		}
		int half = cap.width / 2;
		// The left end, the middle, the right end.
		ctx.drawTexture(RenderPipelines.MOJANG_LOGO, cap.id, x0, y0, 0f, 0f, half, bh, half, cap.height, cap.width, cap.height, color);
		if (fill > half * 2) ctx.fill(x0 + half, y0, x0 + fill - half, y0 + bh, color);
		ctx.drawTexture(RenderPipelines.MOJANG_LOGO, cap.id, x0 + fill - (cap.width - half), y0, half, 0f, cap.width - half, bh, cap.width - half,
			cap.height, cap.width, cap.height, color);
	}

	/** A texture drawn into the box (x, y, w, h), whole, in the colour given (its alpha the strength). */
	private static void sprite(DrawContext ctx, Sprite s, float x, float y, float w, float h, int color) {
		ctx.drawTexture(RenderPipelines.MOJANG_LOGO, s.id, Math.round(x), Math.round(y), 0f, 0f, Math.max(1, Math.round(w)), Math.max(1, Math.round(h)),
			s.width, s.height, s.width, s.height, color);
	}

	// ------------------------------------------------------------------ textures

	private static void make(int w, int h) {
		release();
		madeW = w;
		madeH = h;
		try {
			float unit = Math.min(w, h);
			title = Glyphs.make(V1zir.id("loading/title"), BOLD, distinct(NAME), titleSize(unit));
			int bh = Math.round(barHeight(unit));
			int bw = Math.round(barWidth(w, unit));
			int gap = Math.round(rimGap(bh));
			float rimWidth = Math.max(1.5f, bh * 0.2f);
			cap = register(V1zir.id("loading/cap"), capsule(bh, bh, 0f));
			track = register(V1zir.id("loading/track"), capsule(bw, bh, 0f));
			rim = register(V1zir.id("loading/rim"), capsule(bw + gap * 2, bh + gap * 2, rimWidth));
			int size = Math.max(16, Math.round(h * BootScreen.MARK));
			try (NativeImage logo = NativeImage.read(resource(MARK_FILE))) {
				mark = register(V1zir.id("loading/mark"), shrink(logo, size));
			}
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.warn("Could not make the loading screen; it shows without its text", e);
			broken = true;
		}
	}

	/** Frees the textures once the loading screen is gone (they are made again for the next one). */
	private static void release() {
		TextureManager textures = MinecraftClient.getInstance().getTextureManager();
		for (Identifier id : MADE) textures.destroyTexture(id);
		MADE.clear();
		title = null;
		mark = null;
		cap = null;
		track = null;
		rim = null;
	}

	private static Sprite register(Identifier id, NativeImage image) {
		TextureManager textures = MinecraftClient.getInstance().getTextureManager();
		NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "V1zir loading screen", image);
		textures.registerTexture(id, texture);
		MADE.add(id);
		return new Sprite(id, image.getWidth(), image.getHeight());
	}

	private static String distinct(String text) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (out.indexOf(String.valueOf(c)) < 0) out.append(c);
		}
		if (out.indexOf(" ") < 0) out.append(' ');
		return out.toString();
	}

	private static byte[] resource(String path) throws IOException {
		try (InputStream in = LoadingScreen.class.getResourceAsStream(path)) {
			if (in == null) throw new IOException("Missing " + path);
			return in.readAllBytes();
		}
	}

	/**
	 * A white capsule (a rounded rectangle with round ends) of w x h pixels, smooth at its edge: filled when rim is 0,
	 * else only a rim that wide along its edge.
	 */
	private static NativeImage capsule(int w, int h, float rim) {
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, Math.max(1, w), Math.max(1, h), false);
		float r = h / 2f;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				// Distance to the capsule's edge: negative inside.
				float px = x + 0.5f, py = y + 0.5f;
				float cx = Math.max(r, Math.min(w - r, px));
				float d = (float) Math.hypot(px - cx, py - r) - r;
				float a = Anim.clamp01(0.5f - d);
				if (rim > 0f) a *= Anim.clamp01(0.5f + d + rim);
				out.setColorArgb(x, y, Math.round(255f * a) << 24 | 0xFFFFFF);
			}
		}
		return out;
	}

	/**
	 * The image made size x size pixels (it is square), each new pixel the average of the old ones it covers, weighted
	 * by their cover, colours by their opacity (no dark fringe), so it shows sharp and smooth drawn pixel for pixel.
	 */
	private static NativeImage shrink(NativeImage source, int size) {
		int sw = source.getWidth(), sh = source.getHeight();
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		for (int y = 0; y < size; y++) {
			float y0 = (float) y * sh / size, y1 = (float) (y + 1) * sh / size;
			for (int x = 0; x < size; x++) {
				float x0 = (float) x * sw / size, x1 = (float) (x + 1) * sw / size;
				double a = 0, r = 0, g = 0, b = 0, weight = 0;
				for (int sy = (int) y0; sy < Math.min(sh, (int) Math.ceil(y1)); sy++) {
					float wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
					for (int sx = (int) x0; sx < Math.min(sw, (int) Math.ceil(x1)); sx++) {
						float wx = Math.min(x1, sx + 1) - Math.max(x0, sx);
						float cover = wx * wy;
						int argb = source.getColorArgb(sx, sy);
						double alpha = (argb >>> 24) / 255.0 * cover;
						a += alpha;
						r += ((argb >> 16) & 0xFF) * alpha;
						g += ((argb >> 8) & 0xFF) * alpha;
						b += (argb & 0xFF) * alpha;
						weight += cover;
					}
				}
				int pixel = 0;
				if (a > 0) {
					pixel = (int) Math.round(a / weight * 255.0) << 24 | (int) Math.round(r / a) << 16 | (int) Math.round(g / a) << 8 | (int) Math.round(b / a);
				}
				out.setColorArgb(x, y, pixel);
			}
		}
		return out;
	}

	private record Sprite(Identifier id, int width, int height) {
	}

	/**
	 * Some characters of one font at one pixel size, side by side in one texture, white, drawn one by one so that each
	 * can move on its own.
	 */
	private static final class Glyphs {
		final Identifier id;
		final String chars;
		final float size;
		final float ascent;
		final float[] advance;
		final float[] kerning;
		final int[] left;
		final int[] top;
		final int[] width;
		final int[] height;
		final int[] stripX;
		final int stripW;
		final int stripH;

		private Glyphs(Identifier id, String chars, float size, float ascent, float[] advance, float[] kerning, int[] left, int[] top, int[] width,
					   int[] height, int[] stripX, int stripW, int stripH) {
			this.id = id;
			this.chars = chars;
			this.size = size;
			this.ascent = ascent;
			this.advance = advance;
			this.kerning = kerning;
			this.left = left;
			this.top = top;
			this.width = width;
			this.height = height;
			this.stripX = stripX;
			this.stripW = stripW;
			this.stripH = stripH;
		}

		static Glyphs make(Identifier id, String font, String chars, float size) throws IOException {
			byte[] bytes = resource(font);
			ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
			STBTTFontinfo info = STBTTFontinfo.malloc();
			try {
				data.put(bytes).flip();
				if (!STBTruetype.stbtt_InitFont(info, data)) throw new IOException("Not a font: " + font);
				float scale = STBTruetype.stbtt_ScaleForPixelHeight(info, size);
				int[] a = new int[1], d = new int[1], g = new int[1];
				STBTruetype.stbtt_GetFontVMetrics(info, a, d, g);
				int n = chars.length();
				float[] advance = new float[n];
				float[] kerning = new float[n * n];
				int[] left = new int[n], top = new int[n], width = new int[n], height = new int[n], stripX = new int[n];
				int[] x0 = new int[1], y0 = new int[1], x1 = new int[1], y1 = new int[1], adv = new int[1], lsb = new int[1];
				int stripW = 1;
				int stripH = 1;
				for (int i = 0; i < n; i++) {
					int cp = chars.charAt(i);
					STBTruetype.stbtt_GetCodepointHMetrics(info, cp, adv, lsb);
					advance[i] = adv[0] * scale;
					if (cp != ' ' && STBTruetype.stbtt_FindGlyphIndex(info, cp) == 0) {
						// Not in the font: a gap, never the box of a missing glyph.
						advance[i] = size * 0.3f;
						stripX[i] = stripW;
						continue;
					}
					STBTruetype.stbtt_GetCodepointBitmapBox(info, cp, scale, scale, x0, y0, x1, y1);
					left[i] = x0[0];
					top[i] = y0[0];
					width[i] = Math.max(0, x1[0] - x0[0]);
					height[i] = Math.max(0, y1[0] - y0[0]);
					stripX[i] = stripW;
					stripW += width[i] + 2;
					stripH = Math.max(stripH, height[i] + 2);
					for (int j = 0; j < n; j++) kerning[i * n + j] = STBTruetype.stbtt_GetCodepointKernAdvance(info, cp, chars.charAt(j)) * scale;
				}
				NativeImage image = new NativeImage(NativeImage.Format.RGBA, stripW, stripH, false);
				for (int y = 0; y < stripH; y++) for (int x = 0; x < stripW; x++) image.setColorArgb(x, y, 0x00FFFFFF);
				for (int i = 0; i < n; i++) {
					if (width[i] == 0 || height[i] == 0) continue;
					ByteBuffer bitmap = MemoryUtil.memAlloc(width[i] * height[i]);
					try {
						STBTruetype.stbtt_MakeCodepointBitmap(info, bitmap, width[i], height[i], width[i], scale, scale, chars.charAt(i));
						for (int y = 0; y < height[i]; y++) {
							for (int x = 0; x < width[i]; x++) {
								int alpha = bitmap.get(y * width[i] + x) & 0xFF;
								image.setColorArgb(stripX[i] + x, 1 + y, alpha << 24 | 0xFFFFFF);
							}
						}
					} finally {
						MemoryUtil.memFree(bitmap);
					}
				}
				register(id, image);
				return new Glyphs(id, chars, size, a[0] * scale, advance, kerning, left, top, width, height, stripX, stripW, stripH);
			} finally {
				info.free();
				MemoryUtil.memFree(data);
			}
		}

		float advance(char c) {
			int i = chars.indexOf(c);
			return i < 0 ? size * 0.3f : advance[i];
		}

		float kern(char a, char b) {
			int i = chars.indexOf(a), j = chars.indexOf(b);
			return i < 0 || j < 0 ? 0f : kerning[i * chars.length() + j];
		}

		float width(String text, float spacing) {
			float x = 0f;
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				x += advance(c);
				if (i + 1 < text.length()) x += spacing + kern(c, text.charAt(i + 1));
			}
			return x;
		}

		/** One character with its pen at x on the baseline. */
		void drawChar(DrawContext ctx, char c, float x, float baseline, int color) {
			int i = chars.indexOf(c);
			if (i < 0 || width[i] == 0 || height[i] == 0) return;
			ctx.drawTexture(RenderPipelines.MOJANG_LOGO, id, Math.round(x + left[i]), Math.round(baseline + top[i]), stripX[i], 1f, width[i], height[i],
				width[i], height[i], stripW, stripH, color);
		}

		void drawString(DrawContext ctx, String text, float x, float baseline, float spacing, int color) {
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				drawChar(ctx, c, x, baseline, color);
				x += advance(c) + spacing;
				if (i + 1 < text.length()) x += kern(c, text.charAt(i + 1));
			}
		}
	}
}
