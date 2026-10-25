package dev.v1zir.visuals.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.font.AtlasTexture;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.GlyphAtlas;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.util.ColorUtil;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import net.minecraft.client.gl.GpuSampler;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * SDF text in world space, for damage numbers, nametags and 3D lyrics.
 *
 * <p>Call {@link #draw} from {@link WorldEffect#render3D}. Every string of the frame is queued,
 * sorted back to front and drawn after the other world effects; neighbours in that order that
 * share a font atlas page and depth mode share one draw call. Positions are world coordinates and
 * sizes are in blocks. A {@link Style} is mutable and reusable: keep one per use and change it
 * freely between calls.
 *
 * <p>Labels (nametags) are built from several parts around one anchor: text moved within the text
 * plane by an offset, and rounded {@link #plate plates} and soft {@link #halo halos} for
 * backgrounds and bars. Parts with the same anchor sort as one and draw in the order they were
 * queued, so a near label always covers a far one.
 * <pre>
 *   private final WorldText.Style style = new WorldText.Style().size(0.3f).shadow().glow(0, 0.6f);
 *
 *   public void render3D(float tickDelta) {
 *       WorldText.draw("-4.5", x, y, z, style.color(Theme.accent()).alpha(fade));
 *   }
 * </pre>
 */
public final class WorldText {
	private static final int STRIDE = 28;
	private static final int QUAD = STRIDE * 4;
	/** Change of the stored distance per atlas pixel (SdfFont maps PADDING pixels onto 128 of 255). */
	private static final float SD_PER_PIXEL = 128f / SdfFont.PADDING / 255f;
	/** Edge shifts beyond this would reach the end of the glyph padding and show the quad's border. */
	private static final float MAX_SHIFT = 0.42f;
	private static final int ADDITIVE = 16384;
	/** UV2.y flag of plate quads: UV0 is then the position in half heights from the plate's centre. */
	private static final int PLATE = 8192;
	/** Plates have no atlas page and fit into any batch of their depth mode. */
	private static final int ANY_PAGE = -1;
	private static final IntComparator BACK_TO_FRONT = WorldText::backToFront;

	// Quads of the frame in queue order, and the copy re-ordered for upload.
	private static ByteBuffer staging = MemoryUtil.memAlloc(QUAD * 512);
	private static ByteBuffer upload = MemoryUtil.memAlloc(QUAD * 512);
	private static int quads;

	// One span per string and atlas page (or per plate); spans are what gets sorted.
	private static int[] spanFirst = new int[64];
	private static int[] spanQuads = new int[64];
	private static int[] spanPage = new int[64];
	private static boolean[] spanSeeThrough = new boolean[64];
	private static float[] spanDepth = new float[64];
	private static int[] order = new int[64];
	private static int spans;

	// Draw calls of the sorted upload: page slot (ANY_PAGE until a string joins) and depth mode.
	private static int[] batchPage = new int[64];
	private static boolean[] batchSeeThrough = new boolean[64];
	private static int[] batchFirst = new int[64];
	private static int[] batchQuads = new int[64];

	private static GlyphAtlas[] pages = new GlyphAtlas[4];
	private static GpuTextureView[] views = new GpuTextureView[4];
	private static int pageCount;

	// Layout of the string being queued: plane rectangle (x0, y0 top, x1, y1 bottom) and atlas rectangle per glyph.
	private static float[] layout = new float[8 * 32];
	private static GlyphAtlas[] glyphPage = new GlyphAtlas[32];
	private static int glyphs;
	private static float lineLeft;
	private static float lineWidth;

	// Camera-relative anchor and orthonormal basis (right, up, normal towards the viewer) of the string being queued.
	private static float ax, ay, az;
	private static float rx, ry, rz, ux, uy, uz, nx, ny, nz;
	private static float fwdX, fwdY, fwdZ;
	private static SdfFont defaultFont;

	private WorldText() {
	}

	/** Horizontal placement of the text relative to its anchor. */
	public enum Align {
		LEFT(0f), CENTER(0.5f), RIGHT(1f);

		final float factor;

		Align(float factor) {
			this.factor = factor;
		}
	}

	/** Orientation of the text plane. */
	public enum Facing {
		/** Parallel to the screen, like vanilla nametags. */
		BILLBOARD,
		/** Stays upright and only turns around the vertical axis to face the camera. */
		UPRIGHT,
		/** Fixed in space along {@link Style#basis}; mirrored when seen from behind so it always reads left to right. */
		FIXED
	}

	/** Look of a string. Setters return the style so it can be configured in one chain. */
	public static final class Style {
		private SdfFont font;
		private float size = 0.25f;
		private int color = 0xFFFFFFFF;
		private int colorEnd;
		private float alpha = 1f;
		private float weight = 0.03f;
		private float spacing;
		private Align align = Align.CENTER;
		private Facing facing = Facing.BILLBOARD;
		private final Vector3f right = new Vector3f(1, 0, 0);
		private final Vector3f up = new Vector3f(0, 1, 0);
		private boolean seeThrough;
		private int outlineColor;
		private float outline;
		private int glowColor;
		private float glow;
		private int shadowColor;
		private int extrudeColor;
		private float extrude;
		private int layers;

		/** Font; null (the default) follows the client font family at semibold weight. */
		public Style font(SdfFont font) {
			this.font = font;
			return this;
		}

		/** Text size (line height) in blocks; vanilla nametags are about 0.25. */
		public Style size(float blocks) {
			this.size = blocks;
			return this;
		}

		/** ARGB text colour. */
		public Style color(int argb) {
			this.color = argb;
			return this;
		}

		/** Left to right gradient from the text colour to this ARGB colour; 0 turns it off. */
		public Style gradient(int argb) {
			this.colorEnd = argb;
			return this;
		}

		/** Opacity multiplier for every layer, for fades. */
		public Style alpha(float alpha) {
			this.alpha = alpha;
			return this;
		}

		/** Weight change: positive is bolder, negative thinner (about -0.1 to 0.15). */
		public Style weight(float weight) {
			this.weight = weight;
			return this;
		}

		/** Extra space between letters, in blocks. */
		public Style spacing(float blocks) {
			this.spacing = blocks;
			return this;
		}

		/** Horizontal alignment to the anchor. Vertically the anchor is always the middle of the capital letters. */
		public Style align(Align align) {
			this.align = align;
			return this;
		}

		/** Faces the camera like vanilla nametags (the default). */
		public Style billboard() {
			this.facing = Facing.BILLBOARD;
			return this;
		}

		/** Faces the camera but stays upright. */
		public Style upright() {
			this.facing = Facing.UPRIGHT;
			return this;
		}

		/**
		 * Fixed orientation: right and up are the world-space directions of the text's x and y axes
		 * (perpendicular; normalised here). The front faces right x up.
		 */
		public Style basis(Vector3fc right, Vector3fc up) {
			this.right.set(right).normalize();
			this.up.set(up).normalize();
			this.facing = Facing.FIXED;
			return this;
		}

		/** Draws through walls instead of being hidden by the world. */
		public Style seeThrough(boolean seeThrough) {
			this.seeThrough = seeThrough;
			return this;
		}

		/** Soft outline; thickness is a fraction of the size (about 0.02 to 0.08), 0 turns it off. */
		public Style outline(int argb, float thickness) {
			this.outlineColor = argb;
			this.outline = thickness;
			return this;
		}

		/** Additive glow around the letters; colour 0 uses the text colour, strength (0 to 1) 0 turns it off. */
		public Style glow(int argb, float strength) {
			this.glowColor = argb;
			this.glow = strength;
			return this;
		}

		/** Soft drop shadow in the default colour. */
		public Style shadow() {
			return shadow(0x90000000);
		}

		/** Soft drop shadow in the given ARGB colour; 0 turns it off. */
		public Style shadow(int argb) {
			this.shadowColor = argb;
			return this;
		}

		/**
		 * 3D lettering: copies of the text stacked behind the front face, darker towards the back.
		 * Depth is a fraction of the size (about 0.1 to 0.3); layers (8 to 16 look solid) 0 turns it off.
		 */
		public Style extrude(float depth, int layers) {
			this.extrude = depth;
			this.layers = Math.max(0, Math.min(32, layers));
			return this;
		}

		/** Colour of the extruded sides next to the front face (darkened further back); 0 uses the text colour. */
		public Style extrudeColor(int argb) {
			this.extrudeColor = argb;
			return this;
		}
	}

	/** Width of the text in blocks. */
	public static float width(String text, Style style) {
		return font(style).width(text, style.size, style.spacing);
	}

	/** Distance between lines in blocks, for stacking several lines along the up direction. */
	public static float lineHeight(Style style) {
		return font(style).lineHeight(style.size);
	}

	/**
	 * Queues text for this frame. The anchor (x, y, z) is placed horizontally per the style's
	 * alignment and vertically at the middle of the capital letters.
	 */
	public static void draw(String text, double x, double y, double z, Style style) {
		draw(text, x, y, z, 0f, 0f, style);
	}

	/**
	 * Queues text moved by (dx, dy) blocks along the right and up directions of the text plane, for
	 * the lines of a label that share one anchor.
	 */
	public static void draw(String text, double x, double y, double z, float dx, float dy, Style style) {
		if (text == null || text.isEmpty() || style.alpha <= 0.004f) return;
		// Letters are rarely wider than the size.
		if (!anchor(x, y, z, (text.length() + 2) * style.size + Math.abs(dx) + Math.abs(dy))) return;
		SdfFont font = font(style);
		layout(font, text, style);
		if (glyphs == 0) return;
		for (int i = 0; i < glyphs; i++) {
			int o = i * 8;
			layout[o] += dx;
			layout[o + 1] += dy;
			layout[o + 2] += dx;
			layout[o + 3] += dy;
		}
		lineLeft += dx;
		orient(style);
		float depth = ax * ax + ay * ay + az * az;
		for (int i = 0; i < glyphs; i++) {
			GlyphAtlas page = glyphPage[i];
			if (queued(page, i)) continue;
			int first = quads;
			emit(font, page, style);
			addSpan(first, quads - first, slot(page), style.seeThrough, depth);
		}
	}

	/**
	 * Queues a rounded rectangle of width x height blocks centred at (dx, dy) in the text plane of
	 * the anchor, coloured from left to right: label backgrounds and bars. The style gives the
	 * facing, the fade and the depth mode.
	 */
	public static void plate(double x, double y, double z, float dx, float dy, float width, float height, float radius,
							 int left, int right, Style style) {
		shape(x, y, z, dx, dy, width, height, radius, 0f, left, right, false, style);
	}

	/**
	 * Queues the soft shadow (additive false) or glow (additive true) of a rounded rectangle; it
	 * fades out over softness blocks around the rectangle.
	 */
	public static void halo(double x, double y, double z, float dx, float dy, float width, float height, float radius,
							float softness, int argb, boolean additive, Style style) {
		shape(x, y, z, dx, dy, width, height, radius, Math.max(softness, 1e-3f), argb, argb, additive, style);
	}

	private static void shape(double x, double y, double z, float dx, float dy, float width, float height, float radius,
							  float softness, int left, int right, boolean additive, Style style) {
		if (style.alpha <= 0.004f || width <= 0f || height <= 0f) return;
		float hh = height * 0.5f;
		// Everything in half heights: the shader gets the aspect ratio, radius and softness of the quad.
		float aspect = Math.min(width * 0.5f / hh, 127f);
		float r = Math.min(radius / hh, Math.min(1f, aspect));
		float soft = Math.min(softness / hh, 7.8f);
		// Crisp plates keep a little room around the edge for its antialiasing.
		float margin = soft > 0f ? soft : 0.25f;
		float ex = (aspect + margin) * hh;
		float ey = (1f + margin) * hh;
		if (!anchor(x, y, z, ex + ey + Math.abs(dx) + Math.abs(dy))) return;
		orient(style);
		// A batch of plates alone still binds an atlas page.
		if (pageCount == 0) slot(font(style).glyph('0').page());
		short aspectBits = (short) Math.round(aspect * 256f);
		short shapeBits = (short) (PLATE + (additive ? ADDITIVE : 0) + (Math.round(soft * 8f) << 6) + Math.round(r * 63f));
		int a = ColorUtil.fade(left, style.alpha);
		int b = ColorUtil.fade(right, style.alpha);
		float u = aspect + margin;
		float v = 1f + margin;
		ensureStaging(quads + 1);
		long p = MemoryUtil.memAddress(staging) + (long) quads * QUAD;
		vertex(p, ax, ay, az, dx - ex, dy + ey, -u, v, a, aspectBits, shapeBits);
		vertex(p + STRIDE, ax, ay, az, dx - ex, dy - ey, -u, -v, a, aspectBits, shapeBits);
		vertex(p + STRIDE * 2, ax, ay, az, dx + ex, dy - ey, u, -v, b, aspectBits, shapeBits);
		vertex(p + STRIDE * 3, ax, ay, az, dx + ex, dy + ey, u, v, b, aspectBits, shapeBits);
		quads++;
		addSpan(quads - 1, 1, ANY_PAGE, style.seeThrough, ax * ax + ay * ay + az * az);
	}

	/** Moves the anchor of the next part into camera space; false when it lies entirely behind the camera. */
	private static boolean anchor(double x, double y, double z, float reach) {
		ax = (float) (x - WorldEffectRenderer.camX);
		ay = (float) (y - WorldEffectRenderer.camY);
		az = (float) (z - WorldEffectRenderer.camZ);
		return ax * fwdX + ay * fwdY + az * fwdZ >= -reach;
	}

	private static SdfFont font(Style style) {
		if (style.font != null) return style.font;
		if (defaultFont == null) defaultFont = Fonts.semibold();
		return defaultFont;
	}

	/** Lays the glyphs out in the text plane (x right, y up, anchor at the origin). */
	private static void layout(SdfFont font, String text, Style style) {
		float size = style.size;
		float k = font.k(size);
		float baseline = -font.capHeight(size) * 0.5f;
		float pen = 0;
		glyphs = 0;
		SdfFont.Glyph prev = null;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			SdfFont.Glyph g = font.glyph(cp);
			if (prev != null) pen += prev.font().kerning(prev, g) * k + style.spacing;
			float gk = g.font() == font ? k : g.font().k(size);
			if (g.visible()) {
				if (glyphs == glyphPage.length) {
					glyphPage = Arrays.copyOf(glyphPage, glyphs * 2);
					layout = Arrays.copyOf(layout, glyphs * 16);
				}
				int o = glyphs * 8;
				layout[o] = pen + g.x0() * gk;
				layout[o + 1] = baseline - g.y0() * gk;
				layout[o + 2] = pen + g.x1() * gk;
				layout[o + 3] = baseline - g.y1() * gk;
				layout[o + 4] = g.u0();
				layout[o + 5] = g.v0();
				layout[o + 6] = g.u1();
				layout[o + 7] = g.v1();
				glyphPage[glyphs++] = g.page();
			}
			pen += g.advance() * gk;
			prev = g;
		}
		lineLeft = -pen * style.align.factor;
		lineWidth = Math.max(pen, 1e-4f);
		for (int i = 0; i < glyphs; i++) {
			layout[i * 8] += lineLeft;
			layout[i * 8 + 2] += lineLeft;
		}
	}

	private static void orient(Style style) {
		Vector3f right = WorldEffectRenderer.RIGHT;
		Vector3f up = WorldEffectRenderer.UP;
		switch (style.facing) {
			case BILLBOARD -> {
				rx = right.x;
				ry = right.y;
				rz = right.z;
				ux = up.x;
				uy = up.y;
				uz = up.z;
			}
			case UPRIGHT -> {
				float len = (float) Math.sqrt(right.x * right.x + right.z * right.z);
				rx = len < 1e-4f ? 1f : right.x / len;
				ry = 0f;
				rz = len < 1e-4f ? 0f : right.z / len;
				ux = 0f;
				uy = 1f;
				uz = 0f;
			}
			case FIXED -> {
				rx = style.right.x;
				ry = style.right.y;
				rz = style.right.z;
				ux = style.up.x;
				uy = style.up.y;
				uz = style.up.z;
			}
		}
		nx = ry * uz - rz * uy;
		ny = rz * ux - rx * uz;
		nz = rx * uy - ry * ux;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len > 1e-6f) {
			nx /= len;
			ny /= len;
			nz /= len;
		}
		// Seen from behind: mirror so the text still reads left to right and extrudes away from the viewer.
		if (style.facing == Facing.FIXED && nx * ax + ny * ay + nz * az > 0) {
			rx = -rx;
			ry = -ry;
			rz = -rz;
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
	}

	/** Emits every layer of the string's glyphs on one page, back to front. */
	private static void emit(SdfFont font, GlyphAtlas page, Style style) {
		float size = style.size;
		float fade = style.alpha;
		float weight = style.weight;
		int face = ColorUtil.fade(style.color, fade);
		int faceEnd = style.colorEnd != 0 ? ColorUtil.fade(style.colorEnd, fade) : face;
		boolean extruded = style.layers > 0 && style.extrude > 0f;
		float back = extruded ? -style.extrude * size : 0f;

		if (style.glow > 0f) {
			int glow = ColorUtil.fade(style.glowColor != 0 ? style.glowColor : style.color, Math.min(1f, style.glow) * fade);
			layer(page, 0f, 0f, back, weight + 0.2f, 0.25f, true, glow, glow);
		}
		if (style.shadowColor != 0) {
			int shadow = ColorUtil.fade(style.shadowColor, fade);
			layer(page, size * 0.035f, -size * 0.055f, back, weight + 0.05f, 0.12f, false, shadow, shadow);
		}
		if (extruded) {
			int side = style.extrudeColor != 0 ? ColorUtil.fade(style.extrudeColor, fade) : face;
			int sideEnd = style.extrudeColor != 0 ? side : faceEnd;
			for (int i = 0; i < style.layers; i++) {
				// 0 at the back, 1 next to the front face.
				float t = style.layers == 1 ? 1f : i / (float) (style.layers - 1);
				float dark = 0.7f - 0.42f * t;
				float offset = back * (style.layers - i) / style.layers;
				layer(page, 0f, 0f, offset, weight, 0f, false, ColorUtil.darken(side, dark), ColorUtil.darken(sideEnd, dark));
			}
		}
		if (style.outline > 0f && style.outlineColor != 0) {
			// Thickness is a fraction of the size: convert it to the font's atlas pixels, then to distance units.
			float shift = weight + style.outline / font.k(1f) * SD_PER_PIXEL;
			int outline = ColorUtil.fade(style.outlineColor, fade);
			layer(page, 0f, 0f, 0f, shift, 0.035f, false, outline, outline);
		}
		layer(page, 0f, 0f, 0f, weight, 0f, false, face, faceEnd);
	}

	/** One copy of the glyphs on a page, moved by (dx, dy) in the text plane and dz along the normal. */
	private static void layer(GlyphAtlas page, float dx, float dy, float dz, float shift, float soft, boolean additive, int left, int right) {
		float ox = ax + rx * dx + ux * dy + nx * dz;
		float oy = ay + ry * dx + uy * dy + ny * dz;
		float oz = az + rz * dx + uz * dy + nz * dz;
		short shiftBits = (short) Math.round(Math.max(-MAX_SHIFT, Math.min(MAX_SHIFT, shift)) * 256f);
		short softBits = (short) (Math.round(Math.min(soft, 0.5f) * 256f) + (additive ? ADDITIVE : 0));
		boolean gradient = left != right;
		ensureStaging(quads + glyphs);
		long base = MemoryUtil.memAddress(staging);
		for (int i = 0; i < glyphs; i++) {
			if (glyphPage[i] != page) continue;
			int g = i * 8;
			float x0 = layout[g];
			float y0 = layout[g + 1];
			float x1 = layout[g + 2];
			float y1 = layout[g + 3];
			int c0 = left;
			int c1 = right;
			if (gradient) {
				c0 = ColorUtil.lerp(left, right, (x0 - lineLeft) / lineWidth);
				c1 = ColorUtil.lerp(left, right, (x1 - lineLeft) / lineWidth);
			}
			long p = base + (long) quads * QUAD;
			vertex(p, ox, oy, oz, x0, y0, layout[g + 4], layout[g + 5], c0, shiftBits, softBits);
			vertex(p + STRIDE, ox, oy, oz, x0, y1, layout[g + 4], layout[g + 7], c0, shiftBits, softBits);
			vertex(p + STRIDE * 2, ox, oy, oz, x1, y1, layout[g + 6], layout[g + 7], c1, shiftBits, softBits);
			vertex(p + STRIDE * 3, ox, oy, oz, x1, y0, layout[g + 6], layout[g + 5], c1, shiftBits, softBits);
			quads++;
		}
	}

	/** One POSITION_TEXTURE_COLOR_LIGHT vertex at plane position (px, py) relative to the origin (ox, oy, oz). */
	private static void vertex(long p, float ox, float oy, float oz, float px, float py, float u, float v, int argb, short shift, short soft) {
		MemoryUtil.memPutFloat(p, ox + rx * px + ux * py);
		MemoryUtil.memPutFloat(p + 4, oy + ry * px + uy * py);
		MemoryUtil.memPutFloat(p + 8, oz + rz * px + uz * py);
		MemoryUtil.memPutFloat(p + 12, u);
		MemoryUtil.memPutFloat(p + 16, v);
		MemoryUtil.memPutByte(p + 20, (byte) (argb >> 16 & 0xFF));
		MemoryUtil.memPutByte(p + 21, (byte) (argb >> 8 & 0xFF));
		MemoryUtil.memPutByte(p + 22, (byte) (argb & 0xFF));
		MemoryUtil.memPutByte(p + 23, (byte) (argb >>> 24));
		MemoryUtil.memPutShort(p + 24, shift);
		MemoryUtil.memPutShort(p + 26, soft);
	}

	private static void ensureStaging(int quadCount) {
		if ((long) quadCount * QUAD > staging.capacity()) {
			staging = MemoryUtil.memRealloc(staging, Math.max(staging.capacity() * 2, quadCount * QUAD));
		}
	}

	/** Whether an earlier glyph of the string is on this page (its layers were emitted with that glyph). */
	private static boolean queued(GlyphAtlas page, int index) {
		for (int i = 0; i < index; i++) if (glyphPage[i] == page) return true;
		return false;
	}

	private static int slot(GlyphAtlas page) {
		for (int i = 0; i < pageCount; i++) if (pages[i] == page) return i;
		if (pageCount == pages.length) {
			pages = Arrays.copyOf(pages, pageCount * 2);
			views = Arrays.copyOf(views, pageCount * 2);
		}
		pages[pageCount] = page;
		return pageCount++;
	}

	private static void addSpan(int first, int count, int page, boolean seeThrough, float depth) {
		if (spans == spanFirst.length) {
			int n = spans * 2;
			spanFirst = Arrays.copyOf(spanFirst, n);
			spanQuads = Arrays.copyOf(spanQuads, n);
			spanPage = Arrays.copyOf(spanPage, n);
			spanSeeThrough = Arrays.copyOf(spanSeeThrough, n);
			spanDepth = Arrays.copyOf(spanDepth, n);
			order = Arrays.copyOf(order, n);
			batchPage = Arrays.copyOf(batchPage, n);
			batchSeeThrough = Arrays.copyOf(batchSeeThrough, n);
			batchFirst = Arrays.copyOf(batchFirst, n);
			batchQuads = Arrays.copyOf(batchQuads, n);
		}
		spanFirst[spans] = first;
		spanQuads[spans] = count;
		spanPage[spans] = page;
		spanSeeThrough[spans] = seeThrough;
		spanDepth[spans] = depth;
		spans++;
	}

	/** Far spans first; ties (the parts of one label share their anchor's depth) keep queue order. */
	private static int backToFront(int a, int b) {
		if (spanDepth[a] != spanDepth[b]) return Float.compare(spanDepth[b], spanDepth[a]);
		return Integer.compare(a, b);
	}

	/** Starts a frame (WorldEffectRenderer, after the camera vectors are set): drops anything left and caches per-frame values. */
	static void begin() {
		quads = 0;
		spans = 0;
		pageCount = 0;
		defaultFont = null;
		// RIGHT x UP points at the viewer, so UP x RIGHT is the view direction.
		Vector3f right = WorldEffectRenderer.RIGHT;
		Vector3f up = WorldEffectRenderer.UP;
		fwdX = up.y * right.z - up.z * right.y;
		fwdY = up.z * right.x - up.x * right.z;
		fwdZ = up.x * right.y - up.y * right.x;
	}

	static boolean isEmpty() {
		return spans == 0;
	}

	/** Draws everything queued this frame back to front, merging neighbours that can share a draw (WorldEffectRenderer). */
	static void flush(GpuBufferSlice params) {
		if (spans == 0) return;
		for (int i = 0; i < spans; i++) order[i] = i;
		IntArrays.quickSort(order, 0, spans, BACK_TO_FRONT);
		if (upload.capacity() < quads * QUAD) upload = MemoryUtil.memRealloc(upload, Math.max(upload.capacity() * 2, quads * QUAD));

		long src = MemoryUtil.memAddress(staging);
		long dst = MemoryUtil.memAddress(upload);
		int written = 0;
		int batches = 0;
		int largest = 0;
		for (int i = 0; i < spans; i++) {
			int s = order[i];
			int page = spanPage[s];
			int last = batches - 1;
			if (batches == 0 || batchSeeThrough[last] != spanSeeThrough[s]
				|| page != ANY_PAGE && batchPage[last] != ANY_PAGE && batchPage[last] != page) {
				batchPage[batches] = page;
				batchSeeThrough[batches] = spanSeeThrough[s];
				batchFirst[batches] = written;
				batchQuads[batches] = 0;
				batches++;
			} else if (batchPage[last] == ANY_PAGE) {
				batchPage[last] = page;
			}
			MemoryUtil.memCopy(src + (long) spanFirst[s] * QUAD, dst + (long) written * QUAD, (long) spanQuads[s] * QUAD);
			written += spanQuads[s];
			batchQuads[batches - 1] += spanQuads[s];
			largest = Math.max(largest, batchQuads[batches - 1]);
		}

		// Uploads have to happen before the pass is opened.
		for (int i = 0; i < pageCount; i++) views[i] = AtlasTexture.view(pages[i]);
		upload.position(0).limit(written * QUAD);
		GpuBuffer vertices = V1zirPipelines.WORLD_TEXT.getVertexFormat().uploadImmediateVertexBuffer(upload);
		upload.clear();
		var indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
		GpuBuffer indexBuffer = indices.getIndexBuffer(largest * 6);
		GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
		try (RenderPass pass = WorldEffectRenderer.openPass(() -> "V1zir world text")) {
			for (int b = 0; b < batches; b++) {
				pass.setPipeline(batchSeeThrough[b] ? V1zirPipelines.WORLD_TEXT_SEE_THROUGH : V1zirPipelines.WORLD_TEXT);
				pass.setUniform("WorldParams", params);
				pass.bindTexture("Sampler0", views[Math.max(batchPage[b], 0)], sampler);
				pass.setVertexBuffer(0, vertices);
				pass.setIndexBuffer(indexBuffer, indices.getIndexType());
				// Quad indices restart at 0 for every batch; baseVertex moves them onto its vertices.
				pass.drawIndexed(batchFirst[b] * 4, 0, batchQuads[b] * 6, 1);
			}
		}
		quads = 0;
		spans = 0;
		pageCount = 0;
	}
}
