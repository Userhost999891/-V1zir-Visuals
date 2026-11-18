package dev.v1zir.visuals.gui.loading;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.UiSounds;
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
 * fades). Light falls in from the top corners onto a dark {@link Backdrop} with stars drifting through it; the
 * client's mark lights up in the middle, its name rises in letter by letter, and a thin bar fills under it with a
 * glowing head, the step and the percentage beneath it. When the load is done the bar runs full and fades, the mark
 * flares, and it all grows a little as it fades into the screen behind. With the PS5 style theme the backdrop is deep
 * blue under a violet beam, the name thin and wide, and it fades into the PS5 style menu on the same backdrop.
 * <p>
 * Until the first load is done neither the game's fonts nor this mod's shaders exist, so it draws only with what the
 * Mojang screen itself draws with: plain fills, and textures through the pipeline of its logo. The text, the mark and
 * its glow are made here as textures of their own, at the size they show at in pixels of the screen (the glow at half
 * of that), and drawn pixel for pixel. Render thread only.
 */
public final class LoadingScreen {
	private static final String NAME = "V1ZIR VISUALS";
	private static final String STARTING = "Starting the game";
	private static final String RELOADING = "Loading resource packs";
	private static final String DIGITS = "0123456789%";
	private static final String BOLD = "/assets/v1zir/fonts/inter_display_bold.ttf";
	private static final String LIGHT = "/assets/v1zir/fonts/inter_display_light.ttf";
	private static final String MEDIUM = "/assets/v1zir/fonts/inter_medium.ttf";
	private static final String LOGO = "/assets/v1zir/textures/gui/logo.png";
	private static final int BAR_SEGMENTS = 24;
	/** The PS5 style theme's glow behind the mark and the end of the bar. */
	private static final int PS5_GLOW = 0xFF6F7BFF;
	private static final int PS5_BAR = 0xFFA9C4FF;

	/** The overlay being drawn, and when it first showed: a new one starts its intro again. */
	private static Object overlay;
	private static long shownAt;
	private static long lastFrame;
	/** The game's first load has played its sound (the PS5 style's swell as the load is done). */
	private static boolean booted;
	/** The progress the bar shows: it follows the game's, never back, and runs full once the load is done. */
	private static float shown;
	/** What the textures were made for: the screen's size in pixels and the style theme. */
	private static int madeW;
	private static int madeH;
	private static boolean madePs5;
	/** The textures could not be made: the screen draws without them. */
	private static boolean broken;
	private static Glyphs title;
	private static Glyphs caption;
	private static Sprite line;
	private static Sprite body;
	private static Sprite glow;
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
		if (f >= 1f) {
			cover = 1f - Anim.clamp01(f - 1f);
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
		boolean ps5 = Theme.ps5();
		if (!broken && (title == null || w != madeW || h != madeH || ps5 != madePs5)) make(w, h, ps5);
		float t = (now - shownAt) / 1.0e9f * (reloading ? 1.6f : 1f);
		float done = f < 0f ? 0f : Anim.clamp01(f / 0.6f);
		float leave = f < 1f ? 0f : Anim.clamp01(f - 1f);

		float scale = (float) mc.getWindow().getScaleFactor();
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		// Pixels of the screen from here on.
		m.scale(1f / scale, 1f / scale);
		int accent = ps5 ? Backdrop.PS5_BEAM : Theme.accent();
		int accent2 = ps5 ? Backdrop.PS5_BEAM_2 : Theme.accent2();
		float lightIn = in(t, 0f, 1.4f);
		float starsIn = in(t, 0.2f, 1.2f);
		if (ps5) {
			Backdrop.ps5(ctx, w, h, scale, lightIn, starsIn, cover);
		} else {
			int base = Theme.preset().base();
			Backdrop.draw(ctx, w, h, scale, ColorUtil.darken(base, 0.45f), ColorUtil.lerp(ColorUtil.darken(base, 0.1f), accent2, 0.05f), accent, accent2,
				0.7f, 0.5f, ColorUtil.lerp(0xFFFFFFFF, accent, 0.25f), lightIn, starsIn, cover);
		}

		float unit = Math.min(w, h);
		float mark = mark(unit);
		float cx = w / 2f;
		float bar = Math.max(2f, Math.round(mark * 0.022f));
		float titleH = mark * 0.26f;
		float block = mark + mark * 0.3f + titleH * 1.2f + mark * 0.42f + bar + mark * 0.16f + mark * 0.14f;
		float top = h / 2f - block / 2f - mark * 0.08f;
		float markCy = top + mark / 2f;
		// Leaving: it all grows a little around the mark as it fades.
		float grow = 1f + 0.07f * smooth(leave);
		m.pushMatrix();
		m.translate(cx, markCy);
		m.scale(grow, grow);
		m.translate(-cx, -markCy);
		markAndGlow(ctx, cx, markCy, mark, t, f, ps5 ? PS5_GLOW : accent, cover);
		float baseline = top + mark + mark * 0.3f + titleH;
		name(ctx, cx, baseline, mark, t, ps5, cover);
		float barY = baseline + titleH * 0.2f + mark * 0.42f;
		float barW = Math.min(w * 0.36f, mark * 3.4f);
		float content = cover * (1f - done);
		bar(ctx, cx, barY, barW, bar, t, ps5 ? 0xFFFFFFFF : accent, ps5 ? PS5_BAR : accent2, content);
		captionLine(ctx, cx, barY + bar + mark * 0.16f, barW, mark, t, reloading ? RELOADING : STARTING, content);
		m.popMatrix();
		m.popMatrix();
		if (f >= 2f) release();
	}

	/** The size of the mark in pixels on a screen whose shorter side is unit pixels. */
	private static float mark(float unit) {
		return Math.max(48f, Math.min(360f, unit * 0.17f));
	}

	private static float smooth(float t) {
		t = Anim.clamp01(t);
		return t * t * (3f - 2f * t);
	}

	/** 0 before start, rising to 1 over length seconds with a soft end. */
	private static float in(float t, float start, float length) {
		return Anim.easeOutCubic((t - start) / length);
	}

	// ------------------------------------------------------------------ drawing

	/** The mark lighting up over its glow: it grows into place, and flares when the load is done. */
	private static void markAndGlow(DrawContext ctx, float cx, float cy, float mark, float t, float f, int glowColor, float cover) {
		float u = Anim.clamp01((t - 0.1f) / 0.75f);
		float appear = Anim.easeOutCubic(u) * cover;
		if (appear <= 0.01f) return;
		float flare = f < 0f ? 0f : (float) Math.exp(-Math.pow((f - 0.25f) / 0.22f, 2));
		if (glow != null) {
			float pulse = 0.3f + 0.08f * (float) Math.sin(t * 2.1f);
			float size = mark * (3.1f + 0.5f * flare);
			sprite(ctx, glow, cx - size / 2f, cy - size / 2f, size, size, ColorUtil.fade(glowColor, (pulse + 0.45f * flare) * appear));
		}
		float s = mark * (0.86f + 0.14f * Anim.easeOutBack(u));
		float x = cx - s / 2f, y = cy - s / 2f;
		if (body != null) sprite(ctx, body, x, y, s, s, ColorUtil.fade(glowColor, (0.3f + 0.25f * flare) * appear));
		if (line != null) sprite(ctx, line, x, y, s, s, ColorUtil.fade(0xFFFFFFFF, appear));
	}

	/** The client's name, its letters rising in one by one. */
	private static void name(DrawContext ctx, float cx, float baseline, float mark, float t, boolean ps5, float cover) {
		Glyphs glyphs = title;
		if (glyphs == null) return;
		float spacing = glyphs.size * (ps5 ? 0.42f : 0.2f);
		float x = cx - glyphs.width(NAME, spacing) / 2f;
		for (int i = 0; i < NAME.length(); i++) {
			char c = NAME.charAt(i);
			float e = in(t, 0.32f + i * 0.035f, 0.5f);
			if (e > 0.01f) glyphs.drawChar(ctx, c, x, baseline + (1f - e) * mark * 0.09f, ColorUtil.fade(0xFFFFFFFF, e * cover));
			x += glyphs.advance(c) + spacing;
			if (i + 1 < NAME.length()) x += glyphs.kern(c, NAME.charAt(i + 1));
		}
	}

	/** The bar: its track grows out from the middle, it fills with the progress, a glowing head and a shimmer running along it. */
	private static void bar(DrawContext ctx, float cx, float y, float width, float thickness, float t, int from, int to, float alpha) {
		float grow = in(t, 0.62f, 0.55f);
		if (grow <= 0.01f || alpha <= 0.01f) return;
		float half = width / 2f * grow;
		int top = Math.round(y), bottom = Math.round(y + thickness);
		ctx.fill(Math.round(cx - half), top, Math.round(cx + half), bottom, ColorUtil.withAlpha(0xFFFFFF, Math.round(30 * alpha)));
		float left = cx - width / 2f;
		float filled = width * shown;
		float start = Math.max(left, cx - half);
		float end = Math.min(left + filled, cx + half);
		if (end > start + 0.5f) {
			for (int i = 0; i < BAR_SEGMENTS; i++) {
				float a = left + width * i / BAR_SEGMENTS, b = left + width * (i + 1) / BAR_SEGMENTS;
				a = Math.max(a, start);
				b = Math.min(b, end);
				if (b <= a) continue;
				int c = ColorUtil.lerp(from, to, (i + 0.5f) / BAR_SEGMENTS);
				ctx.fill(Math.round(a), top, Math.round(b), bottom, ColorUtil.fade(c, alpha));
			}
			// A brighter band running along the filled part now and then.
			float run = (t * 0.55f) % 1.4f;
			float band = width * 0.14f;
			float bandX = left - band + (filled + band * 2f) * run;
			float ba = Math.max(start, bandX - band / 2f), bb = Math.min(end, bandX + band / 2f);
			if (run < 1f && bb > ba) ctx.fill(Math.round(ba), top, Math.round(bb), bottom, ColorUtil.withAlpha(0xFFFFFF, Math.round(110 * alpha)));
			if (glow != null) {
				float size = thickness * 14f;
				sprite(ctx, glow, end - size / 2f, y + thickness / 2f - size / 2f, size, size, ColorUtil.fade(to, 0.85f * alpha));
				float core = thickness * 5f;
				sprite(ctx, glow, end - core / 2f, y + thickness / 2f - core / 2f, core, core, ColorUtil.fade(0xFFFFFFFF, 0.9f * alpha));
			}
		}
	}

	/** What is loading, under the bar's left end, and how far it is, under its right end. */
	private static void captionLine(DrawContext ctx, float cx, float y, float width, float mark, float t, String step, float alpha) {
		Glyphs glyphs = caption;
		if (glyphs == null) return;
		float e = in(t, 0.85f, 0.5f) * alpha;
		if (e <= 0.01f) return;
		float baseline = y + glyphs.ascent;
		float spacing = glyphs.size * 0.02f;
		int color = ColorUtil.fade(0xFFFFFFFF, 0.62f * e);
		glyphs.drawString(ctx, step, cx - width / 2f, baseline, spacing, color);
		String percent = Math.round(shown * 100f) + "%";
		glyphs.drawString(ctx, percent, cx + width / 2f - glyphs.width(percent, spacing), baseline, spacing, ColorUtil.fade(0xFFFFFFFF, 0.85f * e));
	}

	/** A texture drawn into the box (x, y, w, h), whole, in the colour given (its alpha the strength). */
	private static void sprite(DrawContext ctx, Sprite s, float x, float y, float w, float h, int color) {
		ctx.drawTexture(RenderPipelines.MOJANG_LOGO, s.id, Math.round(x), Math.round(y), 0f, 0f, Math.max(1, Math.round(w)), Math.max(1, Math.round(h)),
			s.width, s.height, s.width, s.height, color);
	}

	/** Pseudo random 0..1 from a float. */
	private static float hash(float v) {
		float n = (float) (Math.sin(v * 12.9898) * 43758.5453);
		return n - (float) Math.floor(n);
	}

	// ------------------------------------------------------------------ textures

	private static void make(int w, int h, boolean ps5) {
		release();
		madeW = w;
		madeH = h;
		madePs5 = ps5;
		try {
			float mark = mark(Math.min(w, h));
			title = Glyphs.make(V1zir.id("loading/title"), ps5 ? LIGHT : BOLD, distinct(NAME), Math.max(10f, mark * 0.26f));
			caption = Glyphs.make(V1zir.id("loading/caption"), MEDIUM, distinct(STARTING + RELOADING + DIGITS), Math.max(8f, mark * 0.115f));
			int size = Math.round(mark);
			NativeImage source = readLogo();
			try {
				line = register(V1zir.id("loading/mark_line"), logo(source, size, true));
				body = register(V1zir.id("loading/mark_body"), logo(source, size, false));
			} finally {
				source.close();
			}
			glow = register(V1zir.id("loading/glow"), glow(Math.max(32, Math.round(mark * 1.6f))));
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.warn("Could not make the loading screen; it shows without its text and light", e);
			broken = true;
		}
	}

	/** Frees the textures once the loading screen is gone (they are made again for the next one). */
	private static void release() {
		TextureManager textures = MinecraftClient.getInstance().getTextureManager();
		for (Identifier id : MADE) textures.destroyTexture(id);
		MADE.clear();
		title = null;
		caption = null;
		line = null;
		body = null;
		glow = null;
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

	/** The logo, read straight from the mod (the resource packs may not be loaded yet). */
	private static NativeImage readLogo() throws IOException {
		try (InputStream in = LoadingScreen.class.getResourceAsStream(LOGO)) {
			if (in == null) throw new IOException("Missing " + LOGO);
			return NativeImage.read(in);
		}
	}

	/**
	 * The mark at size x size pixels, white: its light lines (outline) or its dark body, each as its own coverage, so
	 * the lines can be drawn white and the body in the colour of the light.
	 */
	private static NativeImage logo(NativeImage source, int size, boolean lines) {
		int sw = source.getWidth(), sh = source.getHeight();
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		int samples = 4;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float sum = 0f;
				for (int j = 0; j < samples; j++) {
					for (int i = 0; i < samples; i++) {
						int px = Math.min(sw - 1, (int) ((x + (i + 0.5f) / samples) / size * sw));
						int py = Math.min(sh - 1, (int) ((y + (j + 0.5f) / samples) / size * sh));
						int argb = source.getColorArgb(px, py);
						float a = (argb >>> 24) / 255f;
						float l = ((argb >> 16 & 255) * 0.299f + (argb >> 8 & 255) * 0.587f + (argb & 255) * 0.114f) / 255f;
						sum += lines ? a * l : a * (1f - l);
					}
				}
				int alpha = Math.round(255f * sum / (samples * samples));
				out.setColorArgb(x, y, alpha << 24 | 0xFFFFFF);
			}
		}
		return out;
	}

	/** A soft round glow, white. */
	private static NativeImage glow(int size) {
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		float half = size / 2f;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float dx = (x + 0.5f - half) / half, dy = (y + 0.5f - half) / half;
				float r2 = dx * dx + dy * dy;
				float a = ((float) Math.exp(-r2 * 4.5f) * 0.7f + (float) Math.exp(-r2 * 18f) * 0.3f) * (1f - smooth((r2 - 0.72f) / 0.28f));
				out.setColorArgb(x, y, Math.round(255f * Anim.clamp01(a)) << 24 | 0xFFFFFF);
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
