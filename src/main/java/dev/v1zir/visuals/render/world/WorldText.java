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
 * sorted back to front and drawn after the other world effects with one draw call per font
 * atlas page and depth mode. Positions are world coordinates and sizes are in blocks. A
 * {@link Style} is mutable and reusable: keep one per use and change it freely between calls.
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
	private static final IntComparator BACK_TO_FRONT = WorldText::backToFront;

	// Quads of the frame in queue order, and the copy re-ordered for upload.
	private static ByteBuffer staging = MemoryUtil.memAlloc(QUAD * 512);
	private static ByteBuffer upload = MemoryUtil.memAlloc(QUAD * 512);
	private static int quads;

	// One span per string and atlas page; spans are what gets sorted.
	private static int[] spanFirst = new int[64];
	private static int[] spanQuads = new int[64];
	private static int[] spanBucket = new int[64];
	private static float[] spanDepth = new float[64];
	private static int[] order = new int[64];
	private static int spans;

	// Draw calls of the sorted upload: bucket = page slot * 2 + see-through.
	private static int[] batchBucket = new int[64];
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
		if (text == null || text.isEmpty() || style.alpha <= 0.004f) return;
		ax = (float) (x - WorldEffectRenderer.camX);
		ay = (float) (y - WorldEffectRenderer.camY);
		az = (float) (z - WorldEffectRenderer.camZ);
		// Entirely behind the camera (letters are rarely wider than the size).
		if (ax * fwdX + ay * fwdY + az * fwdZ < -(text.length() + 2) * style.size) return;
		SdfFont font = font(style);
		layout(font, text, style);
		if (glyphs == 0) return;
		orient(style);
		float depth = ax * ax + ay * ay + az * az;
		int seeThrough = style.seeThrough ? 1 : 0;
		for (int i = 0; i < glyphs; i++) {
			GlyphAtlas page = glyphPage[i];
			if (queued(page, i)) continue;
			int first = quads;
			emit(font, page, style);
			addSpan(first, quads - first, slot(page) * 2 + seeThrough, depth);
		}
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

	private static void addSpan(int first, int count, int bucket, float depth) {
		if (spans == spanFirst.length) {
			int n = spans * 2;
			spanFirst = Arrays.copyOf(spanFirst, n);
			spanQuads = Arrays.copyOf(spanQuads, n);
			spanBucket = Arrays.copyOf(spanBucket, n);
			spanDepth = Arrays.copyOf(spanDepth, n);
			order = Arrays.copyOf(order, n);
			batchBucket = Arrays.copyOf(batchBucket, n);
			batchFirst = Arrays.copyOf(batchFirst, n);
			batchQuads = Arrays.copyOf(batchQuads, n);
		}
		spanFirst[spans] = first;
		spanQuads[spans] = count;
		spanBucket[spans] = bucket;
		spanDepth[spans] = depth;
		spans++;
	}

	/** Groups spans by page and depth mode; within a group far strings come first, ties keep queue order. */
	private static int backToFront(int a, int b) {
		if (spanBucket[a] != spanBucket[b]) return Integer.compare(spanBucket[a], spanBucket[b]);
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

	/** Draws everything queued this frame, sorted, with one draw per page and depth mode (WorldEffectRenderer). */
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
			if (batches == 0 || batchBucket[batches - 1] != spanBucket[s]) {
				batchBucket[batches] = spanBucket[s];
				batchFirst[batches] = written;
				batchQuads[batches] = 0;
				batches++;
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
				int bucket = batchBucket[b];
				pass.setPipeline((bucket & 1) != 0 ? V1zirPipelines.WORLD_TEXT_SEE_THROUGH : V1zirPipelines.WORLD_TEXT);
				pass.setUniform("WorldParams", params);
				pass.bindTexture("Sampler0", views[bucket >> 1], sampler);
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
