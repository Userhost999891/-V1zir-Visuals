package dev.v1zir.visuals.gui.menu;

import net.minecraft.client.texture.NativeImage;

/**
 * Makes a small icon large and smooth for the PS5 style menu: a server's or world's icon is 64 pixels, shown many times
 * that size. Lanczos resampling (three lobes, the sharpest of the smooth filters) in premultiplied alpha, then a light
 * unsharp mask: edges and letters stay crisp without the stair steps of enlarged pixels, and transparent borders do not
 * darken. It runs off the render thread (it reads and writes only the images it is given).
 */
final class IconScaler {
	private static final int LOBES = 3;
	private static final int TAPS = LOBES * 2;
	/** How strongly the unsharp mask brings the detail back. */
	private static final float SHARPEN = 0.6f;

	private IconScaler() {
	}

	/** The image enlarged k times (it is closed when a new one is made; on failure the image given comes back). */
	static NativeImage enlarge(NativeImage image, int k) {
		int w = image.getWidth(), h = image.getHeight();
		if (k <= 1 || w <= 0 || h <= 0) return image;
		int outW = w * k, outH = h * k;
		// Premultiplied alpha, so a colour next to transparency does not bleed its darkness into the edge.
		float[] src = new float[w * h * 4];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = image.getColorArgb(x, y);
				float a = (c >>> 24) / 255f;
				int i = (y * w + x) * 4;
				src[i] = a;
				src[i + 1] = (c >> 16 & 255) / 255f * a;
				src[i + 2] = (c >> 8 & 255) / 255f * a;
				src[i + 3] = (c & 255) / 255f * a;
			}
		}
		int[] firstX = new int[outW];
		float[] weightX = new float[outW * TAPS];
		kernel(w, k, firstX, weightX);
		int[] firstY = new int[outH];
		float[] weightY = new float[outH * TAPS];
		kernel(h, k, firstY, weightY);
		// Across first (w x h to outW x h), then down (to outW x outH).
		float[] across = new float[outW * h * 4];
		for (int y = 0; y < h; y++) {
			for (int ox = 0; ox < outW; ox++) {
				float a = 0f, r = 0f, g = 0f, b = 0f;
				for (int t = 0; t < TAPS; t++) {
					float wgt = weightX[ox * TAPS + t];
					int sx = Math.max(0, Math.min(w - 1, firstX[ox] + t));
					int i = (y * w + sx) * 4;
					a += src[i] * wgt;
					r += src[i + 1] * wgt;
					g += src[i + 2] * wgt;
					b += src[i + 3] * wgt;
				}
				int o = (y * outW + ox) * 4;
				across[o] = a;
				across[o + 1] = r;
				across[o + 2] = g;
				across[o + 3] = b;
			}
		}
		float[] dst = new float[outW * outH * 4];
		for (int oy = 0; oy < outH; oy++) {
			for (int ox = 0; ox < outW; ox++) {
				int o = (oy * outW + ox) * 4;
				for (int t = 0; t < TAPS; t++) {
					float wgt = weightY[oy * TAPS + t];
					int sy = Math.max(0, Math.min(h - 1, firstY[oy] + t));
					int i = (sy * outW + ox) * 4;
					dst[o] += across[i] * wgt;
					dst[o + 1] += across[i + 1] * wgt;
					dst[o + 2] += across[i + 2] * wgt;
					dst[o + 3] += across[i + 3] * wgt;
				}
			}
		}
		sharpen(dst, outW, outH, Math.max(1, Math.round(k * 0.6f)));
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, outW, outH, false);
		try {
			for (int oy = 0; oy < outH; oy++) {
				for (int ox = 0; ox < outW; ox++) {
					int o = (oy * outW + ox) * 4;
					float a = Math.max(0f, Math.min(1f, dst[o]));
					float r = Math.max(0f, Math.min(a, dst[o + 1]));
					float g = Math.max(0f, Math.min(a, dst[o + 2]));
					float b = Math.max(0f, Math.min(a, dst[o + 3]));
					int argb;
					if (a < 1e-4f) {
						argb = 0;
					} else {
						argb = Math.round(a * 255f) << 24 | channel(r / a) << 16 | channel(g / a) << 8 | channel(b / a);
					}
					out.setColorArgb(ox, oy, argb);
				}
			}
		} catch (RuntimeException e) {
			out.close();
			return image;
		}
		image.close();
		return out;
	}

	/** An unsharp mask: each value pushed away from a blur of its surroundings (two box blurs of the radius). */
	private static void sharpen(float[] values, int w, int h, int radius) {
		float[] blur = values.clone();
		float[] temp = new float[values.length];
		for (int pass = 0; pass < 2; pass++) {
			boxBlur(blur, temp, w, h, radius, true);
			boxBlur(temp, blur, w, h, radius, false);
		}
		for (int i = 0; i < values.length; i++) values[i] += (values[i] - blur[i]) * SHARPEN;
	}

	/** A box blur of the radius along rows (across) or columns, from one array of RGBA samples into the other. */
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

	private static int channel(float v) {
		return Math.round(Math.max(0f, Math.min(1f, v)) * 255f);
	}

	/** For each of n * k samples along an axis of n: the first source sample it reads and its TAPS weights (summing to 1). */
	private static void kernel(int n, int k, int[] first, float[] weights) {
		for (int o = 0; o < n * k; o++) {
			float center = (o + 0.5f) / k - 0.5f;
			int start = (int) Math.floor(center) - LOBES + 1;
			float sum = 0f;
			for (int t = 0; t < TAPS; t++) {
				float wgt = lanczos(center - (start + t));
				weights[o * TAPS + t] = wgt;
				sum += wgt;
			}
			if (sum != 0f) for (int t = 0; t < TAPS; t++) weights[o * TAPS + t] /= sum;
			first[o] = start;
		}
	}

	private static float lanczos(float x) {
		if (Math.abs(x) < 1e-6f) return 1f;
		if (Math.abs(x) >= LOBES) return 0f;
		double px = Math.PI * x;
		return (float) (LOBES * Math.sin(px) * Math.sin(px / LOBES) / (px * px));
	}
}
