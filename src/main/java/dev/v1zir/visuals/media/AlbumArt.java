package dev.v1zir.visuals.media;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Album art of the playing song as a dynamic texture, shared by the Spotify widgets.
 *
 * <p>The image (a file path or an http(s)/file URL) is read and decoded with stb_image on a
 * background thread, centre-cropped, scaled down to at most {@link #SIZE} pixels and its dominant
 * colour measured there; the render thread then only uploads it into a NativeImageBackedTexture
 * registered under v1zir:spotify/art_N. A new cover cross-fades over the old one, which is
 * destroyed a moment after it was last drawn. Everything but the decoding is render thread only.
 */
public final class AlbumArt {
	private static final int SIZE = 160;
	private static final int MAX_BYTES = 8 << 20;
	/** A retired texture may still be referenced by the frame being drawn; it is destroyed after this. */
	private static final long RETIRE_NANOS = 400_000_000L;
	/** A cover that disappears (players clear it while switching songs) is kept this long for the next one. */
	private static final long CLEAR_DELAY_NANOS = 1_500_000_000L;

	private static final class Art {
		final Identifier id;
		final TextureSetup texture;
		final int color;
		long retiredAt;

		Art(Identifier id, TextureSetup texture, int color) {
			this.id = id;
			this.texture = texture;
			this.color = color;
		}
	}

	private record Decoded(NativeImage image, int color, int generation) {
	}

	private static final AtomicReference<Decoded> PENDING = new AtomicReference<>();
	private static final List<Art> RETIRED = new ArrayList<>();
	/** 0 to 1 while the current cover (or the placeholder) fades in over the previous one. */
	private static final Anim FADE = new Anim(1f, 7f);
	private static String source = "";
	private static int generation;
	private static int counter;
	private static long clearAt;
	private static Art current;
	private static Art previous;

	private AlbumArt() {
	}

	/** Follows the snapshot's art reference; call once per frame before drawing. */
	public static void sync(String art) {
		long now = System.nanoTime();
		if (!art.equals(source)) {
			source = art;
			generation++;
			if (art.isEmpty()) clearAt = now + CLEAR_DELAY_NANOS;
			else load(art, generation);
		}
		if (source.isEmpty() && current != null && now >= clearAt) show(null);
		Decoded decoded = PENDING.getAndSet(null);
		if (decoded != null) {
			if (decoded.generation() == generation) upload(decoded);
			else decoded.image().close();
		}
		if (previous != null && FADE.get() >= 1f) {
			retire(previous);
			previous = null;
		}
		for (int i = RETIRED.size() - 1; i >= 0; i--) {
			Art old = RETIRED.get(i);
			if (now - old.retiredAt < RETIRE_NANOS) continue;
			MinecraftClient.getInstance().getTextureManager().destroyTexture(old.id);
			RETIRED.remove(i);
		}
	}

	/** Dominant colour of the cover (cross-faded like the image), or the fallback without one. */
	public static int color(int fallback) {
		int to = current != null && current.color != 0 ? current.color : fallback;
		float f = FADE.get();
		if (f >= 1f) return to;
		int from = previous != null && previous.color != 0 ? previous.color : fallback;
		return ColorUtil.lerp(from, to, f);
	}

	/** Draws the cover, or a placeholder (music icon on the accent gradient). tint multiplies the image. */
	public static void draw(DrawContext ctx, float x, float y, float size, float radius, int tint, int accent, int accent2) {
		float f = FADE.get();
		if (f < 1f) layer(ctx, previous, x, y, size, radius, tint, accent, accent2);
		layer(ctx, current, x, y, size, radius, f < 1f ? ColorUtil.fade(tint, f) : tint, accent, accent2);
	}

	private static void layer(DrawContext ctx, Art art, float x, float y, float size, float radius, int tint, int accent, int accent2) {
		if (art != null) {
			Render2D.image(ctx, art.texture, x, y, size, size, radius, tint);
			return;
		}
		int alpha = ColorUtil.alpha(tint);
		Render2D.round(ctx, x, y, size, size, radius, ColorUtil.withAlpha(ColorUtil.darken(accent, 0.35f), alpha),
			ColorUtil.withAlpha(ColorUtil.darken(accent2, 0.55f), alpha), ColorUtil.withAlpha(ColorUtil.darken(accent, 0.7f), alpha),
			ColorUtil.withAlpha(ColorUtil.darken(accent2, 0.45f), alpha));
		Render2D.icon(ctx, Icons.MUSIC, x + size / 2f, y + size / 2f, size * 0.42f, ColorUtil.withAlpha(0xFFFFFF, alpha * 200 / 255));
	}

	/** Frees every texture (the widgets were turned off). */
	public static void clear() {
		generation++;
		source = "";
		Decoded decoded = PENDING.getAndSet(null);
		if (decoded != null) decoded.image().close();
		var textures = MinecraftClient.getInstance().getTextureManager();
		if (current != null) textures.destroyTexture(current.id);
		if (previous != null) textures.destroyTexture(previous.id);
		for (Art old : RETIRED) textures.destroyTexture(old.id);
		RETIRED.clear();
		current = null;
		previous = null;
		FADE.snap(1f);
	}

	private static void show(Art art) {
		if (previous != null) retire(previous);
		previous = current;
		current = art;
		FADE.snap(0f);
		FADE.target(1f);
	}

	private static void retire(Art art) {
		art.retiredAt = System.nanoTime();
		RETIRED.add(art);
	}

	private static void upload(Decoded decoded) {
		Identifier id = V1zir.id("spotify/art_" + counter++);
		NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "V1zir album art", decoded.image());
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
		show(new Art(id, TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR)), decoded.color()));
	}

	// ------------------------------------------------------------------ background loading

	private static void load(String art, int generation) {
		if (art.startsWith("http://") || art.startsWith("https://")) {
			HttpRequest request = HttpRequest.newBuilder(URI.create(art))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", Media.userAgent())
				.GET()
				.build();
			Media.http().sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.thenAcceptAsync(response -> {
					if (response.statusCode() == 200) offer(response.body(), generation);
				}, Media.io());
			return;
		}
		Media.io().execute(() -> {
			try {
				Path path = art.startsWith("file:") ? Path.of(URI.create(art)) : Path.of(art);
				if (Files.size(path) <= MAX_BYTES) offer(Files.readAllBytes(path), generation);
			} catch (IOException | RuntimeException e) {
				V1zir.LOGGER.debug("Could not read album art {}", art, e);
			}
		});
	}

	/** Decodes on the calling (background) thread and hands the image to the render thread. */
	private static void offer(byte[] bytes, int generation) {
		if (bytes.length == 0 || bytes.length > MAX_BYTES) return;
		Decoded decoded = decode(bytes, generation);
		if (decoded == null) return;
		Decoded stale = PENDING.getAndSet(decoded);
		if (stale != null) stale.image().close();
	}

	private static Decoded decode(byte[] bytes, int generation) {
		ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
		try {
			encoded.put(bytes).flip();
			int[] w = new int[1];
			int[] h = new int[1];
			int[] channels = new int[1];
			ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, w, h, channels, 4);
			if (pixels == null) return null;
			try {
				return scale(pixels, w[0], h[0], generation);
			} finally {
				STBImage.stbi_image_free(pixels);
			}
		} finally {
			MemoryUtil.memFree(encoded);
		}
	}

	/** Centre-crops to a square, box-filters it down to at most SIZE pixels and measures the dominant colour. */
	private static Decoded scale(ByteBuffer rgba, int width, int height, int generation) {
		int side = Math.min(width, height);
		int ox = (width - side) / 2;
		int oy = (height - side) / 2;
		int n = Math.min(side, SIZE);
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, n, n, false);
		// Hue histogram weighted by saturation and brightness: grey and dark pixels hardly count.
		float[] weight = new float[24];
		float[] sum = new float[24 * 3];
		for (int ty = 0; ty < n; ty++) {
			int y0 = oy + ty * side / n;
			int y1 = Math.max(y0 + 1, oy + (ty + 1) * side / n);
			for (int tx = 0; tx < n; tx++) {
				int x0 = ox + tx * side / n;
				int x1 = Math.max(x0 + 1, ox + (tx + 1) * side / n);
				int r = 0, g = 0, b = 0, a = 0;
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						int p = (y * width + x) * 4;
						r += rgba.get(p) & 0xFF;
						g += rgba.get(p + 1) & 0xFF;
						b += rgba.get(p + 2) & 0xFF;
						a += rgba.get(p + 3) & 0xFF;
					}
				}
				int count = (y1 - y0) * (x1 - x0);
				int argb = ColorUtil.argb(a / count, r / count, g / count, b / count);
				image.setColorArgb(tx, ty, argb);
				float[] hsv = ColorUtil.toHsv(argb);
				float w = hsv[1] * hsv[1] * hsv[2];
				int bucket = Math.min(23, (int) (hsv[0] * 24f));
				weight[bucket] += w;
				sum[bucket * 3] += ColorUtil.red(argb) * w;
				sum[bucket * 3 + 1] += ColorUtil.green(argb) * w;
				sum[bucket * 3 + 2] += ColorUtil.blue(argb) * w;
			}
		}
		int best = 0;
		float total = 0f;
		for (int i = 0; i < 24; i++) {
			total += weight[i];
			if (weight[i] > weight[best]) best = i;
		}
		int color = 0;
		if (total > n * n * 0.02f) {
			float w = weight[best];
			int dominant = ColorUtil.argb(255, Math.round(sum[best * 3] / w), Math.round(sum[best * 3 + 1] / w), Math.round(sum[best * 3 + 2] / w));
			// Keep it a tasteful accent: never muddy, never neon.
			float[] hsv = ColorUtil.toHsv(dominant);
			color = ColorUtil.fromHsv(hsv[0], Math.max(0.35f, Math.min(0.75f, hsv[1])), Math.max(0.78f, Math.min(0.96f, hsv[2])), 255);
		}
		return new Decoded(image, color, generation);
	}
}
