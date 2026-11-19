package dev.v1zir.visuals.gui.menu;

import net.minecraft.client.texture.NativeImage;

/**
 * Makes a small icon large for the PS5 style menu (a server's or world's icon is 64 pixels, shown several times that
 * size) with directional cubic convolution: each new pixel is interpolated along the edge it lies on, found from how
 * much the picture changes along the two diagonals (then across and down), instead of across it. Edges, rings and
 * letters come out smooth and sharp, without the stair steps of enlarged pixels, without the blur of a plain smooth
 * filter and without the light or dark halos of a sharpened one. It doubles the size per pass, in premultiplied alpha
 * so transparent borders do not darken, then a light sharpening that never leaves the range of the pixels around
 * (no ringing), and at the end a shrink to the size the menu shows it at when that is smaller. It runs off the render
 * thread (it reads and writes only the images it is given).
 */
final class IconScaler {
	/** How many times more one direction must change than the other for the edge to count as running along the other. */
	private static final float EDGE = 1.15f;
	/** How strongly the result is sharpened (kept within the range of its neighbours). */
	private static final float SHARPEN = 0.4f;

	private IconScaler() {
	}

	/**
	 * The image enlarged to about `target` pixels on its longer side (2, 4 or 8 times, then shrunk to the target if
	 * that is smaller); the image given is closed when a new one is made, and comes back as it is on failure.
	 */
	static NativeImage enlarge(NativeImage image, int target) {
		int w = image.getWidth(), h = image.getHeight();
		int longer = Math.max(w, h);
		if (w <= 0 || h <= 0 || target <= longer) return image;
		int passes = target <= longer * 2 ? 1 : target <= longer * 4 ? 2 : 3;
		int k = 1 << passes;
		// Premultiplied alpha (r, g, b, a), with one pixel of the edge repeated around it.
		int pw = w + 2, ph = h + 2;
		float[] grid = new float[pw * ph * 4];
		for (int y = 0; y < ph; y++) {
			for (int x = 0; x < pw; x++) {
				int c = image.getColorArgb(Math.max(0, Math.min(w - 1, x - 1)), Math.max(0, Math.min(h - 1, y - 1)));
				float a = (c >>> 24) / 255f;
				int i = (y * pw + x) * 4;
				grid[i] = (c >> 16 & 255) / 255f * a;
				grid[i + 1] = (c >> 8 & 255) / 255f * a;
				grid[i + 2] = (c & 255) / 255f * a;
				grid[i + 3] = a;
			}
		}
		int gw = pw, gh = ph;
		for (int p = 0; p < passes; p++) {
			grid = pass(grid, gw, gh);
			gw = gw * 2 - 1;
			gh = gh * 2 - 1;
		}
		// The passes put the image's pixels on a lattice from the padding's corner; the k * w pixels wanted sit at
		// (m + 0.5) / k - 0.5 of the image, the lattice point (k + 1) / 2 further on (rounded up: an eighth of a pixel).
		int offset = (k + 2) / 2;
		int outW = w * k, outH = h * k;
		float[] out = new float[outW * outH * 4];
		for (int y = 0; y < outH; y++) {
			System.arraycopy(grid, ((y + offset) * gw + offset) * 4, out, y * outW * 4, outW * 4);
		}
		out = sharpen(out, outW, outH, Math.max(1, k / 2));
		int finalW = outW, finalH = outH;
		if (Math.max(outW, outH) > target * 1.08f) {
			float s = (float) target / Math.max(outW, outH);
			finalW = Math.max(1, Math.round(outW * s));
			finalH = Math.max(1, Math.round(outH * s));
			out = shrink(out, outW, outH, finalW, finalH);
		}
		NativeImage result = new NativeImage(NativeImage.Format.RGBA, finalW, finalH, false);
		try {
			for (int y = 0; y < finalH; y++) {
				for (int x = 0; x < finalW; x++) {
					int o = (y * finalW + x) * 4;
					float a = clamp(out[o + 3], 0f, 1f);
					int argb = 0;
					if (a >= 1e-4f) {
						float r = clamp(out[o], 0f, a), g = clamp(out[o + 1], 0f, a), b = clamp(out[o + 2], 0f, a);
						argb = Math.round(a * 255f) << 24 | channel(r / a) << 16 | channel(g / a) << 8 | channel(b / a);
					}
					result.setColorArgb(x, y, argb);
				}
			}
		} catch (RuntimeException e) {
			result.close();
			return image;
		}
		image.close();
		return result;
	}

	/**
	 * One doubling: the pixels of a (w x h) grid at the even places of a (2w - 1) x (2h - 1) one; the places between
	 * four of them first, along whichever diagonal the picture changes least, then the places between two, along
	 * across or down the same way.
	 */
	private static float[] pass(float[] src, int w, int h) {
		// Three pixels of the edge repeated around it, so every place has its neighbours; cut away at the end.
		int pad = 3;
		int pw = w + pad * 2, ph = h + pad * 2;
		float[] p = new float[pw * ph * 4];
		for (int y = 0; y < ph; y++) {
			int sy = Math.max(0, Math.min(h - 1, y - pad));
			for (int x = 0; x < pw; x++) {
				int sx = Math.max(0, Math.min(w - 1, x - pad));
				System.arraycopy(src, (sy * w + sx) * 4, p, (y * pw + x) * 4, 4);
			}
		}
		int lw = pw * 2 - 1, lh = ph * 2 - 1;
		float[] q = new float[lw * lh * 4];
		float[] luma = new float[lw * lh];
		for (int y = 0; y < ph; y++) {
			for (int x = 0; x < pw; x++) {
				int to = (y * 2 * lw + x * 2);
				System.arraycopy(p, (y * pw + x) * 4, q, to * 4, 4);
				luma[to] = luma(p, (y * pw + x) * 4);
			}
		}
		// Between four: the centre of the block whose top left is (i, j).
		float[] pa = new float[4];
		float[] pb = new float[4];
		for (int i = 1; i < ph - 2; i++) {
			for (int j = 1; j < pw - 2; j++) {
				float d45 = 0f, d135 = 0f;
				for (int r = 0; r < 3; r++) {
					for (int c = -1; c < 2; c++) {
						d45 += Math.abs(luma[at(lw, i + r, j + c)] - luma[at(lw, i + r - 1, j + c + 1)]);
						d135 += Math.abs(luma[at(lw, i + r - 1, j + c)] - luma[at(lw, i + r, j + c + 1)]);
					}
				}
				cubic(q, lw, pa, i + 2, j - 1, i + 1, j, i, j + 1, i - 1, j + 2);
				cubic(q, lw, pb, i - 1, j - 1, i, j, i + 1, j + 1, i + 2, j + 2);
				int to = (2 * i + 1) * lw + 2 * j + 1;
				blend(q, to * 4, pa, pb, d45, d135);
				luma[to] = luma(q, to * 4);
			}
		}
		// Between two: across (even row, odd column) and down (odd row, even column); their four neighbours are known.
		for (int y = 4; y < lh - 4; y++) {
			for (int x = 4 + ((y + 1) & 1); x < lw - 4; x += 2) {
				float dh = 0f, dv = 0f;
				for (int dy = -1; dy <= 1; dy += 2) {
					for (int dx = -2; dx <= 0; dx += 2) {
						dh += Math.abs(luma[y * lw + x + dx + dy * lw] - luma[(y + dy) * lw + x + dx + 2]);
						dv += Math.abs(luma[(y + dx) * lw + x + dy] - luma[(y + dx + 2) * lw + x + dy]);
					}
				}
				for (int dx = -3; dx <= 1; dx += 2) {
					dh += Math.abs(luma[y * lw + x + dx] - luma[y * lw + x + dx + 2]);
					dv += Math.abs(luma[(y + dx) * lw + x] - luma[(y + dx + 2) * lw + x]);
				}
				for (int dy = -2; dy <= 2; dy += 4) {
					dh += Math.abs(luma[(y + dy) * lw + x - 1] - luma[(y + dy) * lw + x + 1]);
					dv += Math.abs(luma[(y - 1) * lw + x + dy] - luma[(y + 1) * lw + x + dy]);
				}
				lattice(q, lw, pa, y, x - 3, y, x - 1, y, x + 1, y, x + 3);
				lattice(q, lw, pb, y - 3, x, y - 1, x, y + 1, x, y + 3, x);
				int to = y * lw + x;
				blend(q, to * 4, pa, pb, dh, dv);
				luma[to] = luma(q, to * 4);
			}
		}
		// Back to the grid's own area: its pixel (0, 0) is at the padding's (pad, pad), the lattice's (2 pad, 2 pad).
		int ow = w * 2 - 1, oh = h * 2 - 1;
		float[] out = new float[ow * oh * 4];
		for (int y = 0; y < oh; y++) {
			System.arraycopy(q, ((y + pad * 2) * lw + pad * 2) * 4, out, y * ow * 4, ow * 4);
		}
		return out;
	}

	/** The lattice index of the grid pixel (row, column), known at the even places. */
	private static int at(int lw, int row, int column) {
		return row * 2 * lw + column * 2;
	}

	/** Cubic convolution (-1, 9, 9, -1) / 16 of four grid pixels on a line, into out. */
	private static void cubic(float[] q, int lw, float[] out, int r0, int c0, int r1, int c1, int r2, int c2, int r3, int c3) {
		int a = at(lw, r0, c0) * 4, b = at(lw, r1, c1) * 4, c = at(lw, r2, c2) * 4, d = at(lw, r3, c3) * 4;
		for (int ch = 0; ch < 4; ch++) out[ch] = (-q[a + ch] + 9f * q[b + ch] + 9f * q[c + ch] - q[d + ch]) / 16f;
	}

	/** The same along four lattice places (row, column) on a line. */
	private static void lattice(float[] q, int lw, float[] out, int r0, int c0, int r1, int c1, int r2, int c2, int r3, int c3) {
		int a = (r0 * lw + c0) * 4, b = (r1 * lw + c1) * 4, c = (r2 * lw + c2) * 4, d = (r3 * lw + c3) * 4;
		for (int ch = 0; ch < 4; ch++) out[ch] = (-q[a + ch] + 9f * q[b + ch] + 9f * q[c + ch] - q[d + ch]) / 16f;
	}

	/**
	 * The new pixel from the interpolations along two directions (a and b) and how much the picture changes along
	 * each: along the one it changes least when one clearly wins (an edge), else both, weighted the same way.
	 */
	private static void blend(float[] q, int to, float[] a, float[] b, float alongA, float alongB) {
		if ((1f + alongA) / (1f + alongB) > EDGE) {
			System.arraycopy(b, 0, q, to, 4);
		} else if ((1f + alongB) / (1f + alongA) > EDGE) {
			System.arraycopy(a, 0, q, to, 4);
		} else {
			float wa = 1f / (1f + pow5(alongA)), wb = 1f / (1f + pow5(alongB));
			float sum = wa + wb;
			for (int ch = 0; ch < 4; ch++) q[to + ch] = (a[ch] * wa + b[ch] * wb) / sum;
		}
	}

	private static float pow5(float v) {
		float v2 = v * v;
		return v2 * v2 * v;
	}

	/** Brightness on the 0 to 255 scale the directions are weighed on; transparency counts as a change too. */
	private static float luma(float[] v, int i) {
		return (v[i] * 0.299f + v[i + 1] * 0.587f + v[i + 2] * 0.114f) * 255f + v[i + 3] * 127.5f;
	}

	/** A light unsharp mask, each value kept between the lowest and highest of the 3 x 3 around it (no halos). */
	private static float[] sharpen(float[] v, int w, int h, int radius) {
		float[] blur = v.clone();
		float[] temp = new float[v.length];
		for (int pass = 0; pass < 2; pass++) {
			boxBlur(blur, temp, w, h, radius, true);
			boxBlur(temp, blur, w, h, radius, false);
		}
		float[] out = new float[v.length];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				for (int c = 0; c < 4; c++) {
					float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
					for (int dy = -1; dy <= 1; dy++) {
						int yy = Math.max(0, Math.min(h - 1, y + dy));
						for (int dx = -1; dx <= 1; dx++) {
							float n = v[(yy * w + Math.max(0, Math.min(w - 1, x + dx))) * 4 + c];
							lo = Math.min(lo, n);
							hi = Math.max(hi, n);
						}
					}
					int i = (y * w + x) * 4 + c;
					out[i] = clamp(v[i] + (v[i] - blur[i]) * SHARPEN, lo, hi);
				}
			}
		}
		return out;
	}

	/** A box blur of the radius along rows (across) or columns, from one array of samples into the other. */
	private static void boxBlur(float[] from, float[] to, int w, int h, int radius, boolean across) {
		int lines = across ? h : w;
		int length = across ? w : h;
		float norm = 1f / (radius * 2 + 1);
		for (int line = 0; line < lines; line++) {
			for (int c = 0; c < 4; c++) {
				float sum = 0f;
				for (int j = -radius; j <= radius; j++) sum += from[index(line, Math.max(0, Math.min(length - 1, j)), w, across) + c];
				for (int p = 0; p < length; p++) {
					to[index(line, p, w, across) + c] = sum * norm;
					int add = Math.min(length - 1, p + radius + 1);
					int drop = Math.max(0, p - radius);
					sum += from[index(line, add, w, across) + c] - from[index(line, drop, w, across) + c];
				}
			}
		}
	}

	private static int index(int line, int position, int w, boolean across) {
		return (across ? line * w + position : position * w + line) * 4;
	}

	/** Shrinks by averaging the area each new pixel covers (no aliasing), across then down. */
	private static float[] shrink(float[] v, int w, int h, int nw, int nh) {
		float[] across = new float[nw * h * 4];
		for (int y = 0; y < h; y++) area(v, y * w * 4, 4, w, across, y * nw * 4, 4, nw);
		float[] out = new float[nw * nh * 4];
		for (int x = 0; x < nw; x++) area(across, x * 4, nw * 4, h, out, x * 4, nw * 4, nh);
		return out;
	}

	/** One line of n samples (stride apart from start) averaged by area into m samples. */
	private static void area(float[] from, int start, int stride, int n, float[] to, int toStart, int toStride, int m) {
		float scale = (float) n / m;
		for (int o = 0; o < m; o++) {
			float a = o * scale, b = a + scale;
			float r = 0f, g = 0f, bl = 0f, al = 0f;
			for (int s = (int) a; s < Math.min(n, (int) Math.ceil(b)); s++) {
				float cover = Math.min(b, s + 1f) - Math.max(a, s);
				if (cover <= 0f) continue;
				int i = start + s * stride;
				r += from[i] * cover;
				g += from[i + 1] * cover;
				bl += from[i + 2] * cover;
				al += from[i + 3] * cover;
			}
			int t = toStart + o * toStride;
			to[t] = r / scale;
			to[t + 1] = g / scale;
			to[t + 2] = bl / scale;
			to[t + 3] = al / scale;
		}
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	private static int channel(float v) {
		return Math.round(clamp(v, 0f, 1f) * 255f);
	}
}
