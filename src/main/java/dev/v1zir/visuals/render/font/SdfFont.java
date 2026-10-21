package dev.v1zir.visuals.render.font;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * TrueType font rendered as signed distance fields.
 *
 * Glyphs are rasterised lazily with stb_truetype at {@link #BASE_SIZE} pixels and packed
 * into 1024x1024 single channel atlas pages. Because the atlas stores distances instead of
 * coverage, text stays razor sharp at every GUI scale and supports glow, soft shadows and
 * weight changes in the shader.
 */
public final class SdfFont {
	/** Pixel height (ascent - descent) glyphs are rasterised at. */
	public static final int BASE_SIZE = 56;
	/** Distance range around every glyph, in atlas pixels. */
	public static final int PADDING = 8;
	private static final float PIXEL_DIST_SCALE = 128f / PADDING;

	private final String name;
	private final ByteBuffer data;
	private final STBTTFontinfo info;
	private final float scale;
	private final float ascent;
	private final float descent;
	private final float lineGap;
	private final int baseSize;
	private final Int2ObjectOpenHashMap<Glyph> glyphs = new Int2ObjectOpenHashMap<>();
	private final List<GlyphAtlas> pages = new ArrayList<>();
	private SdfFont fallback;
	private float capHeight = -1;

	/**
	 * @param advance  horizontal advance in base pixels
	 * @param x0,y0    top-left of the SDF bitmap relative to the pen on the baseline (base px)
	 * @param x1,y1    bottom-right of the bitmap
	 * @param u0..v1   atlas coordinates
	 */
	public record Glyph(int codepoint, int index, SdfFont font, float advance, float x0, float y0, float x1, float y1,
						float u0, float v0, float u1, float v1, GlyphAtlas page) {
		public boolean visible() {
			return page != null;
		}
	}

	private SdfFont(String name, ByteBuffer data, int baseSize) {
		this.name = name;
		this.data = data;
		this.baseSize = baseSize;
		this.info = STBTTFontinfo.malloc();
		if (!STBTruetype.stbtt_InitFont(info, data)) throw new IllegalStateException("Failed to parse font " + name);
		this.scale = STBTruetype.stbtt_ScaleForPixelHeight(info, baseSize);
		int[] a = new int[1];
		int[] d = new int[1];
		int[] g = new int[1];
		STBTruetype.stbtt_GetFontVMetrics(info, a, d, g);
		this.ascent = a[0] * scale;
		this.descent = d[0] * scale;
		this.lineGap = g[0] * scale;
	}

	public static SdfFont load(String name, String resourcePath) {
		return load(name, resourcePath, BASE_SIZE);
	}

	public static SdfFont load(String name, String resourcePath, int baseSize) {
		try (InputStream in = SdfFont.class.getResourceAsStream(resourcePath)) {
			if (in == null) throw new IOException("Missing font resource " + resourcePath);
			byte[] bytes = in.readAllBytes();
			ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
			buffer.put(bytes).flip();
			return new SdfFont(name, buffer, baseSize);
		} catch (IOException e) {
			throw new IllegalStateException("Could not load font " + name, e);
		}
	}

	public String name() {
		return name;
	}

	public SdfFont withFallback(SdfFont fallback) {
		this.fallback = fallback == this ? null : fallback;
		return this;
	}

	public List<GlyphAtlas> pages() {
		return pages;
	}

	public boolean has(int codepoint) {
		return STBTruetype.stbtt_FindGlyphIndex(info, codepoint) != 0;
	}

	public Glyph glyph(int codepoint) {
		Glyph glyph = glyphs.get(codepoint);
		if (glyph == null) {
			glyph = rasterize(codepoint);
			glyphs.put(codepoint, glyph);
		}
		return glyph;
	}

	private Glyph rasterize(int codepoint) {
		int index = STBTruetype.stbtt_FindGlyphIndex(info, codepoint);
		if (index == 0 && codepoint != ' ') {
			if (fallback != null && fallback.has(codepoint)) return fallback.glyph(codepoint);
			if (codepoint != '?') return glyph('?');
		}
		int[] advance = new int[1];
		int[] bearing = new int[1];
		STBTruetype.stbtt_GetGlyphHMetrics(info, index, advance, bearing);
		float adv = advance[0] * scale;

		int[] w = new int[1];
		int[] h = new int[1];
		int[] xoff = new int[1];
		int[] yoff = new int[1];
		ByteBuffer sdf = STBTruetype.stbtt_GetGlyphSDF(info, scale, index, PADDING, (byte) 128, PIXEL_DIST_SCALE, w, h, xoff, yoff);
		if (sdf == null || w[0] <= 0 || h[0] <= 0) {
			if (sdf != null) STBTruetype.stbtt_FreeSDF(sdf);
			return new Glyph(codepoint, index, this, adv, 0, 0, 0, 0, 0, 0, 0, 0, null);
		}
		try {
			GlyphAtlas page = pages.isEmpty() ? null : pages.get(pages.size() - 1);
			int[] pos = page == null ? null : page.allocate(w[0], h[0]);
			if (pos == null) {
				page = new GlyphAtlas();
				pages.add(page);
				pos = page.allocate(w[0], h[0]);
				if (pos == null) throw new IllegalStateException("Glyph too large for atlas: " + codepoint);
			}
			page.blit(sdf, pos[0], pos[1], w[0], h[0]);
			float inv = 1f / GlyphAtlas.SIZE;
			return new Glyph(codepoint, index, this, adv,
				xoff[0], yoff[0], xoff[0] + w[0], yoff[0] + h[0],
				pos[0] * inv, pos[1] * inv, (pos[0] + w[0]) * inv, (pos[1] + h[0]) * inv, page);
		} finally {
			STBTruetype.stbtt_FreeSDF(sdf);
		}
	}

	/** Kerning between two glyphs of this font, in base pixels. */
	public float kerning(Glyph left, Glyph right) {
		if (left == null || right == null || left.font() != this || right.font() != this) return 0;
		return STBTruetype.stbtt_GetGlyphKernAdvance(info, left.index(), right.index()) * scale;
	}

	/** Factor converting base pixels into the requested text size. */
	public float k(float size) {
		return size / baseSize;
	}

	public float ascent(float size) {
		return ascent * k(size);
	}

	public float descent(float size) {
		return descent * k(size);
	}

	public float lineHeight(float size) {
		return (ascent - descent + lineGap) * k(size);
	}

	/** Height of capital letters, used to centre text optically. */
	public float capHeight(float size) {
		if (capHeight < 0) {
			Glyph h = glyph('H');
			capHeight = h.visible() ? -(h.y0() + PADDING) : ascent * 0.7f;
			if (capHeight <= 0) capHeight = ascent * 0.7f;
		}
		return capHeight * k(size);
	}

	public float width(String text, float size) {
		return width(text, size, 0);
	}

	public float width(String text, float size, float letterSpacing) {
		if (text == null || text.isEmpty()) return 0;
		float x = 0;
		Glyph prev = null;
		int count = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			Glyph g = glyph(cp);
			x += kerning(prev, g) + g.advance();
			prev = g;
			count++;
		}
		return x * k(size) + Math.max(0, count - 1) * letterSpacing;
	}

	/** Cuts the text so it fits into maxWidth, appending an ellipsis. */
	public String trim(String text, float size, float maxWidth) {
		if (width(text, size) <= maxWidth) return text;
		String ellipsis = "…";
		float ew = width(ellipsis, size);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			sb.appendCodePoint(cp);
			if (width(sb.toString(), size) + ew > maxWidth) {
				sb.setLength(sb.length() - Character.charCount(cp));
				break;
			}
		}
		return sb + ellipsis;
	}

	@Override
	public String toString() {
		return "SdfFont[" + name + "]";
	}
}
