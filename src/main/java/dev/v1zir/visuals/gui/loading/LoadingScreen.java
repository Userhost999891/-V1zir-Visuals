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
 * fades). It starts dark: stars come out, soft motes of light drift up through the dark, and light falls in from the
 * top; the client's mark lights up in the middle with a gleam passing over it now and then, its name rolls in letter by
 * letter (each coming in stretched wide and settling), the version under it, and a thin line fills under that, its head
 * a small comet throwing sparks, the step and the percentage beneath. When the load is done the line runs full and
 * fades, the mark flares and sends a ring of light out, and it all grows a little as it fades into the screen behind.
 * With the PS5 style theme it is the PS5 backdrop, the stars come out on black first and the one violet beam after
 * them, as a console boots; the name thin and wide; and it fades into the PS5 style menu on the same backdrop.
 * <p>
 * Until the first load is done neither the game's fonts nor this mod's shaders exist, so it draws only with what the
 * Mojang screen itself draws with: plain fills, and textures through the pipeline of its logo (which adds its light to
 * what is under it). The text, the mark, its glow and the ring are made here as textures of their own, at the size they
 * show at in pixels of the screen (the glow and the ring at half of that), and drawn pixel for pixel. Render thread only.
 */
public final class LoadingScreen {
	private static final String NAME = "V1ZIR VISUALS";
	private static final String STARTING = "Starting the game";
	private static final String RELOADING = "Loading resource packs";
	private static final String DIGITS = "0123456789%";
	/** Full fonts: the display cuts only hold what the clocks need (digits). */
	private static final String BOLD = "/assets/v1zir/fonts/inter_bold.ttf";
	private static final String REGULAR = "/assets/v1zir/fonts/inter_regular.ttf";
	private static final String MEDIUM = "/assets/v1zir/fonts/inter_medium.ttf";
	private static final String LOGO = "/assets/v1zir/textures/gui/logo.png";
	private static final int BAR_SEGMENTS = 24;
	private static final int MOTES = 34;
	private static final int SPARKS = 7;
	/** Cells the gleam over the mark is drawn in, across and down. */
	private static final int GLEAM_CELLS = 12;
	/** The PS5 style theme's glow behind the mark and the end of the bar. */
	private static final int PS5_GLOW = 0xFF8C6BFF;
	private static final int PS5_BAR = 0xFFC9B8FF;

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
	private static Glyphs small;
	private static Sprite line;
	private static Sprite body;
	private static Sprite glow;
	private static Sprite ring;
	private static final List<Identifier> MADE = new ArrayList<>();

	private LoadingScreen() {
	}

	/** Whether the loading screen replaces the Mojang one (Theme, Loading screen). */
	public static boolean enabled() {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		return theme == null || theme.loadingScreen.on();
	}

	/** The line under the name. */
	private static String versionLine() {
		return "VISUALS MOD  V" + V1zir.version().toUpperCase(java.util.Locale.ROOT);
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
		// On a console the stars come out on black first, then the light.
		float lightIn = ps5 ? in(t, 0.7f, 1.8f) : in(t, 0f, 1.4f);
		float starsIn = in(t, 0.1f, 1.2f);
		if (ps5) {
			Backdrop.ps5(ctx, w, h, scale, lightIn, starsIn, cover);
		} else {
			int base = Theme.preset().base();
			Backdrop.draw(ctx, w, h, scale, ColorUtil.darken(base, 0.45f), ColorUtil.lerp(ColorUtil.darken(base, 0.1f), accent2, 0.05f), accent, accent2,
				0.7f, 0.5f, ColorUtil.lerp(0xFFFFFFFF, accent, 0.25f), lightIn, starsIn, cover);
		}
		float unit = Math.min(w, h);
		motes(ctx, w, h, unit, t, ps5, ps5 ? PS5_BAR : ColorUtil.lerp(accent, 0xFFFFFFFF, 0.35f), lightIn, in(t, 0.3f, 1.6f) * cover);

		float mark = mark(unit);
		float cx = w / 2f;
		float bar = Math.max(2f, Math.round(mark * 0.018f));
		float titleH = mark * 0.26f;
		float smallH = small != null ? small.size : mark * 0.08f;
		float block = mark + mark * 0.3f + titleH * 1.2f + smallH * 1.6f + mark * 0.36f + bar + mark * 0.16f + mark * 0.14f;
		float top = h / 2f - block / 2f - mark * 0.08f;
		float markCy = top + mark / 2f;
		// Leaving: it all grows a little around the mark as it fades.
		float grow = 1f + 0.08f * smooth(leave);
		m.pushMatrix();
		m.translate(cx, markCy);
		m.scale(grow, grow);
		m.translate(-cx, -markCy);
		int glowColor = ps5 ? PS5_GLOW : accent;
		markAndGlow(ctx, cx, markCy, mark, t, f, glowColor, cover);
		float baseline = top + mark + mark * 0.3f + titleH;
		name(ctx, cx, baseline, mark, t, ps5, cover);
		float smallBaseline = baseline + titleH * 0.2f + smallH * 1.5f;
		versionLine(ctx, cx, smallBaseline, t, ps5, cover);
		float barY = smallBaseline + mark * 0.36f;
		float barW = Math.min(w * 0.36f, mark * 3.4f);
		float content = cover * (1f - done);
		bar(ctx, cx, barY, barW, bar, mark, t, ps5 ? 0xFFFFFFFF : accent, ps5 ? PS5_BAR : accent2, content);
		captionLine(ctx, cx, barY + bar + mark * 0.16f, barW, mark, t, reloading ? RELOADING : STARTING, content);
		flash(ctx, cx, markCy, mark, f, ps5 ? 0xFFE9DEFF : ColorUtil.lerp(accent, 0xFFFFFFFF, 0.5f), cover);
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

	private static float easeOutExpo(float t) {
		t = Anim.clamp01(t);
		return t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
	}

	// ------------------------------------------------------------------ drawing

	/**
	 * Soft motes of light drifting up through the dark at different depths, the nearer larger, fainter and faster, swaying
	 * a little; with the PS5 style those in the beam light up with it.
	 */
	private static void motes(DrawContext ctx, int w, int h, float unit, float t, boolean ps5, int color, float lightIn, float alpha) {
		if (glow == null || alpha <= 0.01f) return;
		float[] beam = ps5 ? Backdrop.ps5Beam(w, h) : null;
		for (int i = 0; i < MOTES; i++) {
			float h1 = hash(i * 2.31f + 0.7f), h2 = hash(i * 5.79f + 1.3f), h3 = hash(i * 9.13f + 3.9f), h4 = hash(i * 4.41f + 7.7f);
			float depth = h3 * h3;
			float size = unit * (0.012f + 0.05f * depth);
			float span = h + size * 2f;
			float y = (h2 * span - t * unit * (0.008f + 0.03f * depth)) % span;
			if (y < 0f) y += span;
			y -= size;
			float x = h1 * w + (float) Math.sin(t * (0.2f + 0.3f * h4) + h1 * 12f) * unit * 0.025f;
			float a = (0.05f + 0.16f * h4) * (1f - 0.45f * depth);
			if (beam != null) {
				float px = x - beam[0], py = y - beam[1];
				float along = px * beam[2] + py * beam[3];
				if (along > 0f && along < beam[4]) {
					float across = Math.abs(px * beam[3] - py * beam[2]);
					float half = beam[5] * (0.06f + 0.5f * along / beam[4]);
					a += (float) Math.exp(-(across / half) * (across / half) * 2f) * (1f - along / beam[4]) * 0.35f * lightIn;
				}
			}
			sprite(ctx, glow, x - size / 2f, y - size / 2f, size, size, ColorUtil.fade(color, Math.min(1f, a) * alpha));
		}
	}

	/**
	 * The mark lighting up over its glow: it grows into place, a gleam passes over it every few seconds, and it flares
	 * when the load is done.
	 */
	private static void markAndGlow(DrawContext ctx, float cx, float cy, float mark, float t, float f, int glowColor, float cover) {
		float u = Anim.clamp01((t - 0.15f) / 0.75f);
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
		// The gleam: a band of light sweeping across the mark from its top left, drawn in cells of the mark's own shape.
		float cycle = 3.4f;
		float sweep = ((t - 1.1f) % cycle) / 1.1f;
		if (t > 1.1f && sweep < 1f && body != null && line != null) {
			float position = -0.35f + 1.7f * Anim.easeInOutCubic(sweep);
			gleam(ctx, body, x, y, s, position, ColorUtil.fade(0xFFFFFFFF, 0.55f * appear));
			gleam(ctx, line, x, y, s, position, ColorUtil.fade(glowColor, 0.5f * appear));
		}
	}

	/** One texture of the mark, lit along a diagonal band through position (0 at the top left, 1 at the bottom right). */
	private static void gleam(DrawContext ctx, Sprite sprite, float x, float y, float size, float position, int color) {
		int n = GLEAM_CELLS;
		float alpha = (color >>> 24) / 255f;
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				float d = ((i + 0.5f) / n + (j + 0.5f) / n) / 2f - position;
				float band = (float) Math.exp(-d * d / 0.006f);
				if (band < 0.03f) continue;
				int x0 = Math.round(x + size * i / n), x1 = Math.round(x + size * (i + 1) / n);
				int y0 = Math.round(y + size * j / n), y1 = Math.round(y + size * (j + 1) / n);
				int u0 = sprite.width * i / n, u1 = sprite.width * (i + 1) / n;
				int v0 = sprite.height * j / n, v1 = sprite.height * (j + 1) / n;
				if (x1 <= x0 || y1 <= y0 || u1 <= u0 || v1 <= v0) continue;
				ctx.drawTexture(RenderPipelines.MOJANG_LOGO, sprite.id, x0, y0, u0, v0, x1 - x0, y1 - y0, u1 - u0, v1 - v0, sprite.width, sprite.height,
					ColorUtil.withAlpha(color, Math.round(255f * alpha * band)));
			}
		}
	}

	/** The ring of light the mark sends out as the load is done, and a soft flash behind it. */
	private static void flash(DrawContext ctx, float cx, float cy, float mark, float f, int color, float cover) {
		if (f < 0.05f || f > 1.2f) return;
		float u = Anim.clamp01((f - 0.05f) / 0.9f);
		float e = easeOutExpo(u);
		float fade = (1f - u) * (1f - u) * cover;
		if (ring != null) {
			float size = mark * (1.1f + 4.2f * e);
			sprite(ctx, ring, cx - size / 2f, cy - size / 2f, size, size, ColorUtil.fade(color, 0.7f * fade));
		}
		if (glow != null) {
			float size = mark * (1.5f + 2.5f * e);
			sprite(ctx, glow, cx - size / 2f, cy - size / 2f, size, size, ColorUtil.fade(color, 0.35f * fade));
		}
	}

	/**
	 * The client's name, its letters rolling in one after another: each comes in from the right stretched wide, with a
	 * fading trail, and settles into place.
	 */
	private static void name(DrawContext ctx, float cx, float baseline, float mark, float t, boolean ps5, float cover) {
		Glyphs glyphs = title;
		if (glyphs == null) return;
		Matrix3x2fStack m = ctx.getMatrices();
		float spacing = glyphs.size * (ps5 ? 0.42f : 0.2f);
		float x = cx - glyphs.width(NAME, spacing) / 2f;
		for (int i = 0; i < NAME.length(); i++) {
			char c = NAME.charAt(i);
			float local = t - (0.45f + i * 0.045f);
			float e = easeOutExpo(local / 0.8f);
			float a = Anim.clamp01(local / 0.22f) * cover;
			if (a > 0.01f) {
				float left = 1f - e;
				float stretch = 1f + 2.4f * left * left;
				float center = x + glyphs.advance(c) / 2f;
				for (int k = 2; k >= 0; k--) {
					float ghost = k == 0 ? 1f : 0.32f / k * left;
					if (ghost <= 0.01f) continue;
					float dx = left * glyphs.size * (1.3f + 0.55f * k);
					m.pushMatrix();
					m.translate(center + dx, baseline);
					m.scale(stretch * (1f + 0.35f * k * left), 1f);
					m.translate(-center, -baseline);
					glyphs.drawChar(ctx, c, x, baseline, ColorUtil.fade(0xFFFFFFFF, a * ghost));
					m.popMatrix();
				}
			}
			x += glyphs.advance(c) + spacing;
			if (i + 1 < NAME.length()) x += glyphs.kern(c, NAME.charAt(i + 1));
		}
	}

	/** The line under the name: what it is and its version, small and wide apart. */
	private static void versionLine(DrawContext ctx, float cx, float baseline, float t, boolean ps5, float cover) {
		Glyphs glyphs = small;
		if (glyphs == null) return;
		float e = in(t, 1.05f, 0.6f) * cover;
		if (e <= 0.01f) return;
		String text = versionLine();
		float spacing = glyphs.size * 0.36f;
		float width = glyphs.width(text, spacing);
		glyphs.drawString(ctx, text, cx - width / 2f, baseline + (1f - e) * glyphs.size * 0.6f, spacing,
			ColorUtil.fade(ps5 ? 0xFFD7CCFF : 0xFFFFFFFF, 0.5f * e));
	}

	/**
	 * The bar: its track grows out from the middle, it fills with the progress in a gradient, a brighter band runs along
	 * it, and its head is a small comet with a trail, throwing sparks that drift up and fade.
	 */
	private static void bar(DrawContext ctx, float cx, float y, float width, float thickness, float mark, float t, int from, int to, float alpha) {
		float grow = in(t, 0.75f, 0.55f);
		if (grow <= 0.01f || alpha <= 0.01f) return;
		float half = width / 2f * grow;
		int top = Math.round(y), bottom = Math.round(y + thickness);
		ctx.fill(Math.round(cx - half), top, Math.round(cx + half), bottom, ColorUtil.withAlpha(0xFFFFFF, Math.round(28 * alpha)));
		float left = cx - width / 2f;
		float filled = width * shown;
		float start = Math.max(left, cx - half);
		float end = Math.min(left + filled, cx + half);
		if (end <= start + 0.5f) return;
		for (int i = 0; i < BAR_SEGMENTS; i++) {
			float a = left + width * i / BAR_SEGMENTS, b = left + width * (i + 1) / BAR_SEGMENTS;
			a = Math.max(a, start);
			b = Math.min(b, end);
			if (b <= a) continue;
			int c = ColorUtil.lerp(from, to, (i + 0.5f) / BAR_SEGMENTS);
			// The filled part fades in from its far end, so the head is the brightest.
			float lift = 0.55f + 0.45f * Anim.clamp01((b - left) / Math.max(1f, filled));
			ctx.fill(Math.round(a), top, Math.round(b), bottom, ColorUtil.fade(c, alpha * lift));
		}
		// A brighter band running along the filled part now and then.
		float run = (t * 0.55f) % 1.4f;
		float band = width * 0.14f;
		float bandX = left - band + (filled + band * 2f) * run;
		float ba = Math.max(start, bandX - band / 2f), bb = Math.min(end, bandX + band / 2f);
		if (run < 1f && bb > ba) ctx.fill(Math.round(ba), top, Math.round(bb), bottom, ColorUtil.withAlpha(0xFFFFFF, Math.round(110 * alpha)));
		if (glow == null) return;
		float cy = y + thickness / 2f;
		// The comet's trail, then its head.
		for (int k = 4; k >= 1; k--) {
			float size = thickness * (9f - k * 1.4f);
			float tx = end - k * thickness * 3.2f;
			if (tx < start) continue;
			sprite(ctx, glow, tx - size / 2f, cy - size / 2f, size, size, ColorUtil.fade(to, 0.18f * (5 - k) / 4f * alpha));
		}
		float size = thickness * 14f;
		sprite(ctx, glow, end - size / 2f, cy - size / 2f, size, size, ColorUtil.fade(to, 0.85f * alpha));
		float core = thickness * 5f;
		sprite(ctx, glow, end - core / 2f, cy - core / 2f, core, core, ColorUtil.fade(0xFFFFFFFF, 0.9f * alpha));
		// Sparks thrown off the head: each lives a second, drifting back and up.
		for (int i = 0; i < SPARKS; i++) {
			float period = 1.05f + 0.3f * hash(i * 3.3f + 1f);
			float age = ((t + i * period / SPARKS) % period) / period;
			float sx = end - age * thickness * (10f + 8f * hash(i * 1.9f + 4f));
			float sy = cy - age * mark * (0.06f + 0.08f * hash(i * 6.1f + 2f)) + (float) Math.sin(age * 9f + i) * thickness;
			float spark = thickness * (3.2f - 1.6f * age);
			float a = (1f - age) * (1f - age) * 0.75f * alpha;
			if (sx >= start - thickness * 2f) sprite(ctx, glow, sx - spark / 2f, sy - spark / 2f, spark, spark, ColorUtil.fade(to, a));
		}
	}

	/** What is loading, under the bar's left end, and how far it is, under its right end. */
	private static void captionLine(DrawContext ctx, float cx, float y, float width, float mark, float t, String step, float alpha) {
		Glyphs glyphs = caption;
		if (glyphs == null) return;
		float e = in(t, 1.0f, 0.5f) * alpha;
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
			title = Glyphs.make(V1zir.id("loading/title"), ps5 ? REGULAR : BOLD, distinct(NAME), Math.max(10f, mark * 0.26f));
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
		small = null;
		line = null;
		body = null;
		glow = null;
		ring = null;
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

	/** A thin soft ring of light, white. */
	private static NativeImage ring(int size) {
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		float half = size / 2f;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float dx = (x + 0.5f - half) / half, dy = (y + 0.5f - half) / half;
				float r = (float) Math.sqrt(dx * dx + dy * dy);
				float d = (r - 0.82f) / 0.05f;
				float a = (float) Math.exp(-d * d) * 0.9f + (float) Math.exp(-Math.pow((r - 0.8f) / 0.16f, 2)) * 0.25f;
				if (r > 0.99f) a = 0f;
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
