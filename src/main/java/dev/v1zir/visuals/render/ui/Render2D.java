package dev.v1zir.visuals.render.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.font.AtlasTexture;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.GlyphAtlas;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Immediate-style 2D drawing API on top of vanilla's GUI render state.
 * Everything is resolution independent: shapes are signed distance fields and text uses SDF fonts.
 */
public final class Render2D {
	public static final int CORNER_TL = 1;
	public static final int CORNER_TR = 2;
	public static final int CORNER_BR = 4;
	public static final int CORNER_BL = 8;
	public static final int CORNERS_TOP = CORNER_TL | CORNER_TR;
	public static final int CORNERS_BOTTOM = CORNER_BL | CORNER_BR;
	public static final int CORNERS_LEFT = CORNER_TL | CORNER_BL;
	public static final int CORNERS_RIGHT = CORNER_TR | CORNER_BR;

	/** Global alpha multiplier, used for fade in/out animations of whole screens. */
	private static float alpha = 1f;

	private Render2D() {
	}

	public static void setAlpha(float value) {
		alpha = Math.max(0f, Math.min(1f, value));
	}

	public static float alpha() {
		return alpha;
	}

	private static int a(int color) {
		return alpha >= 1f ? color : ColorUtil.fade(color, alpha);
	}

	// ------------------------------------------------------------------ helpers

	private static Matrix3x2f pose(DrawContext ctx) {
		return new Matrix3x2f(ctx.getMatrices());
	}

	@Nullable
	private static ScreenRect scissor(DrawContext ctx) {
		return ctx.scissorStack.peekLast();
	}

	@Nullable
	static ScreenRect bounds(float x, float y, float w, float h, Matrix3x2f pose, @Nullable ScreenRect scissor) {
		int left = (int) Math.floor(x);
		int top = (int) Math.floor(y);
		int right = (int) Math.ceil(x + w);
		int bottom = (int) Math.ceil(y + h);
		ScreenRect rect = new ScreenRect(left, top, Math.max(1, right - left), Math.max(1, bottom - top)).transformEachVertex(pose);
		return scissor != null ? scissor.intersection(rect) : rect;
	}

	// ------------------------------------------------------------------ shapes

	public static void shape(DrawContext ctx, RenderPipeline pipeline, TextureSetup texture, float x, float y, float w, float h,
							 float radius, float softness, float thickness, int mode, int cornerMask,
							 int tl, int tr, int br, int bl) {
		if (w <= 0 || h <= 0) return;
		tl = a(tl);
		tr = a(tr);
		br = a(br);
		bl = a(bl);
		if (pipeline != V1zirPipelines.GLASS && (tl >>> 24) == 0 && (tr >>> 24) == 0 && (br >>> 24) == 0 && (bl >>> 24) == 0) return;
		Matrix3x2f pose = pose(ctx);
		ScreenRect scissor = scissor(ctx);
		float pad = ShapeElement.padding(mode, softness);
		ScreenRect bounds = bounds(x - pad, y - pad, w + pad * 2, h + pad * 2, pose, scissor);
		if (bounds == null) return;
		ctx.state.addSimpleElement(new ShapeElement(pipeline, texture, pose, scissor, bounds,
			x, y, w, h, radius, softness, thickness, mode, cornerMask, tl, tr, br, bl));
	}

	public static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
		round(ctx, x, y, w, h, 0, color);
	}

	public static void round(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
		shape(ctx, V1zirPipelines.SHAPE, TextureSetup.empty(), x, y, w, h, radius, 0, 0, ShapeElement.FILL, 0, color, color, color, color);
	}

	public static void round(DrawContext ctx, float x, float y, float w, float h, float radius, int corners, int color) {
		shape(ctx, V1zirPipelines.SHAPE, TextureSetup.empty(), x, y, w, h, radius, 0, 0, ShapeElement.FILL, corners, color, color, color, color);
	}

	/** Four corner gradient: top-left, top-right, bottom-right, bottom-left. */
	public static void round(DrawContext ctx, float x, float y, float w, float h, float radius, int tl, int tr, int br, int bl) {
		shape(ctx, V1zirPipelines.SHAPE, TextureSetup.empty(), x, y, w, h, radius, 0, 0, ShapeElement.FILL, 0, tl, tr, br, bl);
	}

	public static void gradientH(DrawContext ctx, float x, float y, float w, float h, float radius, int left, int right) {
		round(ctx, x, y, w, h, radius, left, right, right, left);
	}

	public static void gradientV(DrawContext ctx, float x, float y, float w, float h, float radius, int top, int bottom) {
		round(ctx, x, y, w, h, radius, top, top, bottom, bottom);
	}

	public static void outline(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
		shape(ctx, V1zirPipelines.SHAPE, TextureSetup.empty(), x, y, w, h, radius, 0, thickness, ShapeElement.OUTLINE, 0, color, color, color, color);
	}

	public static void outline(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int tl, int tr, int br, int bl) {
		shape(ctx, V1zirPipelines.SHAPE, TextureSetup.empty(), x, y, w, h, radius, 0, thickness, ShapeElement.OUTLINE, 0, tl, tr, br, bl);
	}

	/** Soft drop shadow or glow around a rounded rectangle. */
	public static void shadow(DrawContext ctx, float x, float y, float w, float h, float radius, float softness, int color) {
		shape(ctx, V1zirPipelines.SHAPE, TextureSetup.empty(), x, y, w, h, radius, softness, 0, ShapeElement.SHADOW, 0, color, color, color, color);
	}

	public static void glow(DrawContext ctx, float x, float y, float w, float h, float radius, float softness, int c1, int c2) {
		shape(ctx, V1zirPipelines.SHAPE, TextureSetup.empty(), x, y, w, h, radius, softness, 0, ShapeElement.SHADOW, 0, c1, c2, c2, c1);
	}

	public static void circle(DrawContext ctx, float cx, float cy, float radius, int color) {
		round(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
	}

	public static void ring(DrawContext ctx, float cx, float cy, float radius, float thickness, int color) {
		outline(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, thickness, color);
	}

	/**
	 * Frosted glass panel: the blurred world behind the GUI, tinted. Falls back to a plain
	 * translucent panel when blur is off or nothing is rendered behind the GUI.
	 */
	public static void glass(DrawContext ctx, float x, float y, float w, float h, float radius, int tint) {
		glass(ctx, x, y, w, h, radius, 0, tint);
	}

	public static void glass(DrawContext ctx, float x, float y, float w, float h, float radius, int corners, int tint) {
		if (BackdropBlur.available()) {
			BackdropBlur.request();
			int t = ColorUtil.withAlpha(tint, Math.round(ColorUtil.alpha(tint) * alpha));
			// The glass pipeline uses vertex alpha as tint strength; coverage alpha comes from the global fade.
			Matrix3x2f pose = pose(ctx);
			ScreenRect scissor = scissor(ctx);
			ScreenRect bounds = bounds(x - 2, y - 2, w + 4, h + 4, pose, scissor);
			if (bounds == null || alpha <= 0.01f) return;
			if (alpha < 0.99f) {
				// While fading, a translucent fill looks better than a half transparent blur.
				round(ctx, x, y, w, h, radius, corners, ColorUtil.withAlpha(tint, Math.max(ColorUtil.alpha(tint), 200)));
				return;
			}
			ctx.state.addSimpleElement(new ShapeElement(V1zirPipelines.GLASS, BackdropBlur.textureSetup(), pose, scissor, bounds,
				x, y, w, h, radius, 0, 0, ShapeElement.FILL, corners, t, t, t, t));
		} else {
			round(ctx, x, y, w, h, radius, corners, ColorUtil.withAlpha(tint, Math.max(ColorUtil.alpha(tint), 225)));
		}
	}

	/** Animated shader background (sky presets in "menu" mode) covering the given area. */
	/** A UI panel (fill and border, no shadow) drawn in the Theme style: Normal (solid grey), Blur or Glassmorphism. */
	public static void panel(DrawContext ctx, float x, float y, float w, float h, float radius) {
		panel(ctx, x, y, w, h, radius, 0);
	}

	public static void panel(DrawContext ctx, float x, float y, float w, float h, float radius, int corners) {
		switch (Theme.style()) {
			case GLASS -> {
				if (BackdropBlur.available()) frosted(ctx, x, y, w, h, radius, corners);
				else round(ctx, x, y, w, h, radius, corners, Theme.panel());
			}
			case BLUR -> {
				glass(ctx, x, y, w, h, radius, corners, Theme.panel());
				outline(ctx, x, y, w, h, radius, 1f, Theme.outline());
			}
			default -> {
				round(ctx, x, y, w, h, radius, corners, Theme.solidPanel());
				outline(ctx, x, y, w, h, radius, 1f, Theme.outline());
			}
		}
	}

	/**
	 * Glassmorphism pane: frosted, refracting glass over the blurred scene and a thin border that
	 * catches the light from the top-left.
	 */
	public static void frosted(DrawContext ctx, float x, float y, float w, float h, float radius, int corners) {
		if (w <= 0 || h <= 0 || alpha <= 0.01f) return;
		BackdropBlur.request();
		Matrix3x2f pose = pose(ctx);
		ScreenRect scissor = scissor(ctx);
		ScreenRect bounds = bounds(x - 2, y - 2, w + 4, h + 4, pose, scissor);
		if (bounds == null) return;
		// The tint is the pane's milky haze over any dimming drawn behind it (the blurred scene is
		// captured before the GUI); the thickness channel carries the pane's opacity (fades).
		int haze = Theme.glassTint();
		float screenH = Math.max(1f, V1zir.mc().getWindow().getScaledHeight());
		float top = (pose.m01() * x + pose.m11() * y + pose.m21()) / screenH;
		float bottom = (pose.m01() * x + pose.m11() * (y + h) + pose.m21()) / screenH;
		int tintTop = BackdropBlur.paneTint(haze, top);
		int tintBottom = BackdropBlur.paneTint(haze, bottom);
		ctx.state.addSimpleElement(new ShapeElement(V1zirPipelines.FROSTED, BackdropBlur.textureSetup(), pose, scissor, bounds,
			x, y, w, h, radius, 0, alpha * 16f, ShapeElement.FILL, corners, tintTop, tintTop, tintBottom, tintBottom));
		outline(ctx, x, y, w, h, radius, 1f, 0x73FFFFFF, 0x38FFFFFF, 0x14FFFFFF, 0x30FFFFFF);
	}

	/** Full-screen dim; glass panes drawn after it show the scene behind it dimmed the same way. */
	public static void screenDim(DrawContext ctx, float width, float height, int color) {
		rect(ctx, 0, 0, width, height, color);
		int c = a(color);
		BackdropBlur.dim(c, c);
	}

	public static void menuBackground(DrawContext ctx, float width, float height, int color) {
		Matrix3x2f pose = pose(ctx);
		ScreenRect bounds = bounds(0, 0, width, height, pose, scissor(ctx));
		if (bounds == null) return;
		ctx.state.addSimpleElement(new MenuBackgroundElement(pose, scissor(ctx), bounds, width, height, a(color)));
	}

	// ------------------------------------------------------------------ text

	public static final class TextStyle {
		public int color = 0xFFFFFFFF;
		public int colorEnd = 0;
		public float weight = 0.02f;
		public float softness = 0f;
		public float letterSpacing = 0f;
		public boolean shadow;
		public int shadowColor = 0x90000000;

		public static TextStyle of(int color) {
			TextStyle style = new TextStyle();
			style.color = color;
			return style;
		}

		public TextStyle gradient(int end) {
			this.colorEnd = end;
			return this;
		}

		public TextStyle weight(float weight) {
			this.weight = weight;
			return this;
		}

		public TextStyle soft(float softness) {
			this.softness = softness;
			return this;
		}

		public TextStyle spacing(float spacing) {
			this.letterSpacing = spacing;
			return this;
		}

		public TextStyle shadow() {
			this.shadow = true;
			return this;
		}

		public TextStyle shadow(int color) {
			this.shadow = true;
			this.shadowColor = color;
			return this;
		}
	}

	/** Draws text with its top-left at (x, y). Returns the text width. */
	public static float text(DrawContext ctx, SdfFont font, String text, float x, float y, float size, int color) {
		return text(ctx, font, text, x, y, size, TextStyle.of(color));
	}

	public static float text(DrawContext ctx, SdfFont font, String text, float x, float y, float size, TextStyle style) {
		if (text == null || text.isEmpty()) return 0;
		if (style.shadow) {
			TextStyle shadow = new TextStyle();
			shadow.color = style.shadowColor;
			shadow.weight = style.weight + 0.04f;
			shadow.softness = 0.12f + style.softness;
			shadow.letterSpacing = style.letterSpacing;
			float off = Math.max(0.5f, size * 0.06f);
			emitText(ctx, font, text, x + off * 0.4f, y + off, size, shadow);
		}
		return emitText(ctx, font, text, x, y, size, style);
	}

	public static float textCentered(DrawContext ctx, SdfFont font, String text, float centerX, float y, float size, int color) {
		float w = font.width(text, size);
		text(ctx, font, text, centerX - w / 2f, y, size, color);
		return w;
	}

	public static float textCentered(DrawContext ctx, SdfFont font, String text, float centerX, float y, float size, TextStyle style) {
		float w = font.width(text, size, style.letterSpacing);
		text(ctx, font, text, centerX - w / 2f, y, size, style);
		return w;
	}

	/** Top coordinate that optically centres capital letters around centerY. */
	public static float middle(SdfFont font, float centerY, float size) {
		return centerY + font.capHeight(size) / 2f - font.ascent(size);
	}

	/** Text vertically centred (by cap height) on centerY. */
	public static float textMiddle(DrawContext ctx, SdfFont font, String text, float x, float centerY, float size, int color) {
		return text(ctx, font, text, x, middle(font, centerY, size), size, color);
	}

	public static float textMiddle(DrawContext ctx, SdfFont font, String text, float x, float centerY, float size, TextStyle style) {
		return text(ctx, font, text, x, middle(font, centerY, size), size, style);
	}

	/** Draws an icon glyph centred at (cx, cy). */
	public static void icon(DrawContext ctx, int codepoint, float cx, float cy, float size, int color) {
		icon(ctx, codepoint, cx, cy, size, TextStyle.of(color).weight(0.0f));
	}

	public static void icon(DrawContext ctx, int codepoint, float cx, float cy, float size, TextStyle style) {
		SdfFont font = Fonts.icons();
		SdfFont.Glyph glyph = font.glyph(codepoint);
		float k = font.k(size);
		// Centre the visible bitmap (minus the SDF padding) instead of the advance box.
		float gx = (glyph.x0() + glyph.x1()) * 0.5f * k;
		float gy = (glyph.y0() + glyph.y1()) * 0.5f * k;
		String s = new String(Character.toChars(codepoint));
		emitText(ctx, font, s, cx - gx, cy - gy - font.ascent(size), size, style);
	}

	private static float emitText(DrawContext ctx, SdfFont font, String text, float x, float y, float size, TextStyle style) {
		float k = font.k(size);
		float baseline = y + font.ascent(size);
		float totalWidth = style.colorEnd != 0 ? Math.max(font.width(text, size, style.letterSpacing), 1f) : 1f;
		Matrix3x2f pose = pose(ctx);
		ScreenRect scissor = scissor(ctx);

		List<Run> runs = new ArrayList<>(1);
		Run current = null;
		float penX = x;
		SdfFont.Glyph prev = null;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			SdfFont.Glyph g = font.glyph(cp);
			if (prev != null) penX += prev.font().kerning(prev, g) * k + style.letterSpacing;
			if (g.visible()) {
				float gk = g.font() == font ? k : g.font().k(size);
				float x0 = penX + g.x0() * gk;
				float x1 = penX + g.x1() * gk;
				float y0 = baseline + g.y0() * gk;
				float y1 = baseline + g.y1() * gk;
				int left = style.color;
				int right = style.color;
				if (style.colorEnd != 0) {
					left = ColorUtil.lerp(style.color, style.colorEnd, (x0 - x) / totalWidth);
					right = ColorUtil.lerp(style.color, style.colorEnd, (x1 - x) / totalWidth);
				}
				if (current == null || current.page != g.page()) {
					current = null;
					for (Run run : runs) if (run.page == g.page()) current = run;
					if (current == null) {
						current = new Run(g.page());
						runs.add(current);
					}
				}
				current.add(x0, y0, x1, y1, g.u0(), g.v0(), g.u1(), g.v1(), a(left), a(right));
			}
			penX += g.advance() * (g.font() == font ? k : g.font().k(size));
			prev = g;
		}
		int weight = Math.round(style.weight * 256f);
		int softness = Math.round(style.softness * 256f);
		for (Run run : runs) {
			if (run.count == 0) continue;
			ScreenRect bounds = bounds(run.minX, run.minY, run.maxX - run.minX, run.maxY - run.minY, pose, scissor);
			if (bounds == null) continue;
			TextureSetup setup = AtlasTexture.prepare(run.page);
			ctx.state.addSimpleElement(new TextElement(V1zirPipelines.TEXT, setup, pose, scissor, bounds,
				run.quads, run.colors, run.count, weight, softness));
		}
		return penX - x;
	}

	private static final class Run {
		final GlyphAtlas page;
		float[] quads = new float[64];
		int[] colors = new int[16];
		int count;
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;

		Run(GlyphAtlas page) {
			this.page = page;
		}

		void add(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int left, int right) {
			if ((count + 1) * 8 > quads.length) {
				quads = java.util.Arrays.copyOf(quads, quads.length * 2);
				colors = java.util.Arrays.copyOf(colors, colors.length * 2);
			}
			int q = count * 8;
			quads[q] = x0;
			quads[q + 1] = y0;
			quads[q + 2] = x1;
			quads[q + 3] = y1;
			quads[q + 4] = u0;
			quads[q + 5] = v0;
			quads[q + 6] = u1;
			quads[q + 7] = v1;
			colors[count * 2] = left;
			colors[count * 2 + 1] = right;
			count++;
			minX = Math.min(minX, x0);
			minY = Math.min(minY, y0);
			maxX = Math.max(maxX, x1);
			maxY = Math.max(maxY, y1);
		}
	}
}
