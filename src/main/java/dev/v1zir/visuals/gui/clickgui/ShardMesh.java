package dev.v1zir.visuals.gui.clickgui;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Splits the window into glass shards for the 3D Frost close: a spider-web crack pattern around the impact
 * point (jittered spokes crossed by jittered rings), each cell clipped to the window and fanned into
 * triangles from its centre. Built once per close into one GPU buffer; gui_shards.vsh animates it.
 * Vertex layout (POSITION_TEXTURE_COLOR, 24 bytes): position xy and rim weight, the shard centre, four random bytes.
 */
final class ShardMesh {
	private static final int STRIDE = 24;
	private static final int MAX_SPOKES = 13;
	private static final int RINGS = 5;
	/** Ring radii as fractions of the distance to the farthest window corner; the last ring lies safely outside. */
	private static final float[] RING = {0.09f, 0.22f, 0.4f, 0.64f, 1.5f};
	/** Enough for every cell clipped to at most eight corners. */
	private static final int MAX_VERTICES = MAX_SPOKES * RINGS * 8 * 3;
	/** GpuBuffer.USAGE_VERTEX */
	private static final int USAGE_VERTEX = 32;

	private static ByteBuffer data;
	// Scratch polygons for clipping, reused between builds.
	private static final float[] polyA = new float[32];
	private static final float[] polyB = new float[32];
	private static final float[] pointX = new float[MAX_SPOKES * (RINGS + 1)];
	private static final float[] pointY = new float[MAX_SPOKES * (RINGS + 1)];

	private GpuBuffer buffer;
	private int vertices;

	/** Number of vertices to draw (TRIANGLES). */
	int vertices() {
		return vertices;
	}

	GpuBuffer buffer() {
		return buffer;
	}

	/** Builds the shards of the rectangle (x, y, w, h) around the impact point (ox, oy); all in framebuffer pixels. */
	void build(float x, float y, float w, float h, float ox, float oy, long seed) {
		close();
		if (data == null) data = MemoryUtil.memAlloc(MAX_VERTICES * STRIDE);
		data.clear();
		vertices = 0;
		Random random = new Random(seed);
		int spokes = 10 + random.nextInt(MAX_SPOKES - 9);
		float reach = 0f;
		for (int c = 0; c < 4; c++) {
			float cx = (c & 1) == 0 ? x : x + w;
			float cy = (c & 2) == 0 ? y : y + h;
			reach = Math.max(reach, (float) Math.hypot(cx - ox, cy - oy));
		}
		// Crack points: spoke i, ring j (ring 0 is the impact itself). Jitter makes the cracks zig-zag.
		float base = random.nextFloat() * 6.2831855f;
		float step = 6.2831855f / spokes;
		for (int i = 0; i < spokes; i++) {
			float spoke = base + i * step + (random.nextFloat() - 0.5f) * step * 0.5f;
			pointX[i] = ox;
			pointY[i] = oy;
			for (int j = 1; j <= RINGS; j++) {
				float angle = spoke + (random.nextFloat() - 0.5f) * step * 0.3f;
				float radius = reach * RING[j - 1] * (0.85f + random.nextFloat() * 0.3f);
				pointX[j * spokes + i] = ox + (float) Math.cos(angle) * radius;
				pointY[j * spokes + i] = oy + (float) Math.sin(angle) * radius;
			}
		}
		for (int j = 0; j < RINGS; j++) {
			for (int i = 0; i < spokes; i++) {
				int next = (i + 1) % spokes;
				int n = 0;
				n = put(polyA, n, j * spokes + i);
				if (j > 0) n = put(polyA, n, j * spokes + next);
				n = put(polyA, n, (j + 1) * spokes + next);
				n = put(polyA, n, (j + 1) * spokes + i);
				n = clip(polyA, n, polyB, 0, x);
				n = clip(polyB, n, polyA, 1, x + w);
				n = clip(polyA, n, polyB, 2, y);
				n = clip(polyB, n, polyA, 3, y + h);
				if (n >= 3) fan(polyA, n, random.nextInt());
			}
		}
		data.flip();
		if (vertices > 0) buffer = RenderSystem.getDevice().createBuffer(() -> "V1zir GUI shards", USAGE_VERTEX, data);
	}

	private static int put(float[] poly, int n, int point) {
		poly[n * 2] = pointX[point];
		poly[n * 2 + 1] = pointY[point];
		return n + 1;
	}

	/** Sutherland-Hodgman against one window edge: axis 0 keeps x >= v, 1 x <= v, 2 y >= v, 3 y <= v. */
	private static int clip(float[] in, int n, float[] out, int axis, float v) {
		int m = 0;
		int coord = axis < 2 ? 0 : 1;
		float sign = axis % 2 == 0 ? 1f : -1f;
		for (int k = 0; k < n && m < 15; k++) {
			int l = (k + 1) % n;
			float a = (in[k * 2 + coord] - v) * sign;
			float b = (in[l * 2 + coord] - v) * sign;
			if (a >= 0f) {
				out[m * 2] = in[k * 2];
				out[m * 2 + 1] = in[k * 2 + 1];
				m++;
			}
			if (a >= 0f != b >= 0f) {
				float t = a / (a - b);
				out[m * 2] = in[k * 2] + (in[l * 2] - in[k * 2]) * t;
				out[m * 2 + 1] = in[k * 2 + 1] + (in[l * 2 + 1] - in[k * 2 + 1]) * t;
				m++;
			}
		}
		return m;
	}

	/** Triangles from the polygon's centre to each edge; the centre carries rim weight 1, the corners 0. */
	private void fan(float[] poly, int n, int randomBits) {
		float cx = 0f;
		float cy = 0f;
		for (int k = 0; k < n; k++) {
			cx += poly[k * 2];
			cy += poly[k * 2 + 1];
		}
		cx /= n;
		cy /= n;
		for (int k = 0; k < n; k++) {
			int l = (k + 1) % n;
			vertex(cx, cy, 1f, cx, cy, randomBits);
			vertex(poly[k * 2], poly[k * 2 + 1], 0f, cx, cy, randomBits);
			vertex(poly[l * 2], poly[l * 2 + 1], 0f, cx, cy, randomBits);
		}
	}

	private void vertex(float x, float y, float rim, float cx, float cy, int randomBits) {
		if (vertices >= MAX_VERTICES) return;
		data.putFloat(x).putFloat(y).putFloat(rim).putFloat(cx).putFloat(cy).putInt(randomBits);
		vertices++;
	}

	void close() {
		if (buffer != null) buffer.close();
		buffer = null;
		vertices = 0;
	}
}
