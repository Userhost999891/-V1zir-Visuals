package dev.v1zir.visuals.render.wallpaper;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.v1zir.visuals.V1zir;
import net.minecraft.client.texture.NativeImage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pictures of the wallpapers folder as GPU textures. Files are read and decoded on a background thread (stb_image for
 * PNG and JPEG, javax.imageio for the frames of an animated GIF), box-filtered down to a sensible size and handed to the
 * render thread, which uploads a few frames per frame. Each picture exists in two sizes: full for a screen, small for
 * the cards of the picker. A picture nothing drew for a while is freed.
 */
public final class WallpaperImages {
	private static final int FULL_SIZE = 2560;
	private static final int SMALL_SIZE = 480;
	/** Animated pictures: longest side, frame count and all frames together (about 64 MB of video memory). */
	private static final int GIF_SIZE = 720;
	private static final int GIF_FRAMES = 150;
	private static final long GIF_PIXELS = 16_000_000L;
	private static final long MAX_BYTES = 64L << 20;
	private static final int MAX_SIDE = 12_000;
	private static final int UPLOADS_PER_FRAME = 6;
	private static final long KEEP_NANOS = 15_000_000_000L;

	private static final Map<String, Picture> FULL = new HashMap<>();
	private static final Map<String, Picture> SMALL = new HashMap<>();
	private static final ConcurrentLinkedQueue<Decoded> DECODED = new ConcurrentLinkedQueue<>();
	private static final List<Decoded> UPLOADING = new ArrayList<>();
	private static ExecutorService worker;
	private static long nextEviction;

	private WallpaperImages() {
	}

	/** A loaded (or loading) picture in one size; frames play at their own delays. */
	public static final class Picture {
		private final Path file;
		private final boolean small;
		private GpuTexture[] textures = new GpuTexture[0];
		private GpuTextureView[] views = new GpuTextureView[0];
		/** End of each frame in milliseconds from the start of the loop. */
		private int[] ends = new int[0];
		private int count;
		private boolean ready;
		/** Could not be read, or freed while it was decoding; set from the decoding thread too. */
		private volatile boolean failed;
		private float aspect = 16f / 9f;
		private long lastUsed = System.nanoTime();

		private Picture(Path file, boolean small) {
			this.file = file;
			this.small = small;
		}

		public boolean ready() {
			return ready;
		}

		public float aspect() {
			return aspect;
		}

		/** The frame to show now. */
		GpuTextureView view() {
			if (count == 1) return views[0];
			int t = (int) (System.currentTimeMillis() % ends[count - 1]);
			for (int i = 0; i < count; i++) if (t < ends[i]) return views[i];
			return views[count - 1];
		}

		private void close() {
			for (int i = 0; i < count; i++) {
				views[i].close();
				textures[i].close();
			}
			count = 0;
			ready = false;
		}
	}

	/** Frames of a picture on their way to the GPU; next is the first one not uploaded yet. */
	private static final class Decoded {
		final Picture picture;
		final NativeImage[] frames;
		final int[] ends;
		final float aspect;
		int next;

		Decoded(Picture picture, NativeImage[] frames, int[] ends, float aspect) {
			this.picture = picture;
			this.frames = frames;
			this.ends = ends;
			this.aspect = aspect;
		}
	}

	/** The picture of an image wallpaper in the given size; null for other kinds. Loading starts on the first call. */
	@Nullable
	public static Picture get(Wallpaper wallpaper, boolean small) {
		if (wallpaper.file == null) return null;
		// The frames of an animated picture are small already: cards share them.
		Map<String, Picture> map = small && !wallpaper.gif() ? SMALL : FULL;
		Picture picture = map.get(wallpaper.id);
		if (picture == null) {
			picture = new Picture(wallpaper.file, small);
			map.put(wallpaper.id, picture);
			load(picture, wallpaper.gif());
		}
		picture.lastUsed = System.nanoTime();
		return picture.failed ? null : picture;
	}

	/** Forgets a picture (its file was deleted or replaced). */
	static void forget(String id) {
		Picture full = FULL.remove(id);
		if (full != null) WallpaperRenderer.retire(full::close);
		Picture small = SMALL.remove(id);
		if (small != null) WallpaperRenderer.retire(small::close);
	}

	/** Lets pictures that could not be read try again (the folder was refreshed). */
	static void retryFailed() {
		FULL.values().removeIf(picture -> picture.failed);
		SMALL.values().removeIf(picture -> picture.failed);
	}

	/** Once per frame, after it was drawn: uploads decoded frames and frees pictures nothing drew for a while. */
	static void tick() {
		if (FULL.isEmpty() && SMALL.isEmpty() && UPLOADING.isEmpty() && DECODED.isEmpty()) return;
		for (Decoded decoded; (decoded = DECODED.poll()) != null; ) UPLOADING.add(decoded);
		int budget = UPLOADS_PER_FRAME;
		for (int i = 0; i < UPLOADING.size() && budget > 0; ) {
			Decoded decoded = UPLOADING.get(i);
			budget -= upload(decoded, budget);
			if (decoded.next >= decoded.frames.length) UPLOADING.remove(i);
			else i++;
		}
		long now = System.nanoTime();
		if (now < nextEviction) return;
		nextEviction = now + 1_000_000_000L;
		evict(FULL, now);
		evict(SMALL, now);
	}

	private static void evict(Map<String, Picture> map, long now) {
		for (Iterator<Picture> it = map.values().iterator(); it.hasNext(); ) {
			Picture picture = it.next();
			if (now - picture.lastUsed < KEEP_NANOS) continue;
			picture.close();
			picture.failed = true;
			it.remove();
		}
	}

	/** Uploads up to budget frames of a decoded picture; returns how many it uploaded. */
	private static int upload(Decoded decoded, int budget) {
		Picture picture = decoded.picture;
		NativeImage[] frames = decoded.frames;
		if (picture.failed) {
			// Evicted or forgotten while it was decoding.
			for (int i = decoded.next; i < frames.length; i++) frames[i].close();
			decoded.next = frames.length;
			return 0;
		}
		if (decoded.next == 0) {
			picture.textures = new GpuTexture[frames.length];
			picture.views = new GpuTextureView[frames.length];
			picture.ends = decoded.ends;
			picture.aspect = decoded.aspect;
		}
		int done = 0;
		while (done < budget && decoded.next < frames.length) {
			int i = decoded.next++;
			NativeImage image = frames[i];
			GpuTexture texture = RenderSystem.getDevice().createTexture("V1zir wallpaper", 15, TextureFormat.RGBA8, image.getWidth(), image.getHeight(), 1, 1);
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
			image.close();
			picture.textures[i] = texture;
			picture.views[i] = RenderSystem.getDevice().createTextureView(texture);
			picture.count = i + 1;
			done++;
		}
		if (decoded.next >= frames.length) picture.ready = true;
		return done;
	}

	private static synchronized ExecutorService worker() {
		if (worker == null) {
			worker = Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "V1zir Wallpapers");
				thread.setDaemon(true);
				thread.setPriority(Thread.MIN_PRIORITY);
				return thread;
			});
		}
		return worker;
	}

	private static void load(Picture picture, boolean gif) {
		worker().execute(() -> {
			Decoded decoded = null;
			try {
				if (Files.size(picture.file) <= MAX_BYTES) {
					if (gif) {
						try {
							decoded = decodeGif(picture);
						} catch (Exception | LinkageError e) {
							V1zir.LOGGER.warn("Could not play wallpaper {}, showing its first frame", picture.file, e);
						}
					}
					if (decoded == null) decoded = decodeStill(picture, Files.readAllBytes(picture.file));
				}
			} catch (Exception | LinkageError e) {
				V1zir.LOGGER.warn("Could not load wallpaper {}", picture.file, e);
			}
			if (decoded != null) DECODED.add(decoded);
			else picture.failed = true;
		});
	}

	@Nullable
	private static Decoded decodeStill(Picture picture, byte[] bytes) {
		ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
		try {
			encoded.put(bytes).flip();
			int[] w = new int[1];
			int[] h = new int[1];
			int[] channels = new int[1];
			if (!STBImage.stbi_info_from_memory(encoded, w, h, channels) || w[0] > MAX_SIDE || h[0] > MAX_SIDE) return null;
			ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, w, h, channels, 4);
			if (pixels == null) return null;
			int[] argb = new int[w[0] * h[0]];
			try {
				for (int i = 0; i < argb.length; i++) {
					int p = i * 4;
					argb[i] = (pixels.get(p + 3) & 0xFF) << 24 | (pixels.get(p) & 0xFF) << 16 | (pixels.get(p + 1) & 0xFF) << 8 | pixels.get(p + 2) & 0xFF;
				}
			} finally {
				STBImage.stbi_image_free(pixels);
			}
			float scale = Math.min(1f, (picture.small ? SMALL_SIZE : FULL_SIZE) / (float) Math.max(w[0], h[0]));
			NativeImage image = scale(argb, w[0], h[0], Math.max(1, Math.round(w[0] * scale)), Math.max(1, Math.round(h[0] * scale)));
			return new Decoded(picture, new NativeImage[]{image}, new int[]{1}, w[0] / (float) h[0]);
		} finally {
			MemoryUtil.memFree(encoded);
		}
	}

	/**
	 * Composites the frames of an animated GIF (frame offsets, transparency and disposal) on a canvas of its logical
	 * size and keeps a scaled copy of each. Pixels are copied by hand: java.awt's drawing is never touched, so no
	 * toolkit starts next to GLFW. A GIF with a single frame, or a runtime without javax.imageio, falls back to stb_image.
	 */
	@Nullable
	private static Decoded decodeGif(Picture picture) throws Exception {
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
		if (!readers.hasNext()) return null;
		ImageReader reader = readers.next();
		try (InputStream input = Files.newInputStream(picture.file); ImageInputStream stream = ImageIO.createImageInputStream(input)) {
			reader.setInput(stream, false);
			int total = Math.min(reader.getNumImages(true), GIF_FRAMES);
			if (total <= 1) return null;
			Node screen = child(reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
			int width = attribute(screen, "logicalScreenWidth", 0);
			int height = attribute(screen, "logicalScreenHeight", 0);
			if (width <= 0 || height <= 0) {
				width = reader.getWidth(0);
				height = reader.getHeight(0);
			}
			if (width > MAX_SIDE || height > MAX_SIDE) return null;
			float scale = Math.min(1f, GIF_SIZE / (float) Math.max(width, height));
			scale = Math.min(scale, (float) Math.sqrt(GIF_PIXELS / (double) total / ((long) width * height)));
			int outW = Math.max(1, Math.round(width * scale));
			int outH = Math.max(1, Math.round(height * scale));

			int[] canvas = new int[width * height];
			int[] saved = null;
			int[] row = new int[0];
			NativeImage[] frames = new NativeImage[total];
			int[] ends = new int[total];
			int time = 0;
			try {
				for (int i = 0; i < total; i++) {
					Node meta = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
					Node descriptor = child(meta, "ImageDescriptor");
					Node control = child(meta, "GraphicControlExtension");
					int left = attribute(descriptor, "imageLeftPosition", 0);
					int top = attribute(descriptor, "imageTopPosition", 0);
					String disposal = text(control, "disposalMethod");
					int delay = attribute(control, "delayTime", 10);
					if ("restoreToPrevious".equals(disposal)) saved = canvas.clone();

					BufferedImage frame = reader.read(i);
					int fw = Math.min(frame.getWidth(), width - left);
					int fh = Math.min(frame.getHeight(), height - top);
					if (row.length < frame.getWidth()) row = new int[frame.getWidth()];
					for (int y = 0; y < fh; y++) {
						frame.getRGB(0, y, frame.getWidth(), 1, row, 0, frame.getWidth());
						int base = (top + y) * width + left;
						for (int x = 0; x < fw; x++) if (row[x] >>> 24 != 0) canvas[base + x] = row[x];
					}
					frames[i] = scale(canvas, width, height, outW, outH);
					// Like browsers: a delay under 20 ms means the default 100 ms.
					time += delay < 2 ? 100 : delay * 10;
					ends[i] = time;

					if ("restoreToBackgroundColor".equals(disposal)) {
						for (int y = 0; y < fh; y++) Arrays.fill(canvas, (top + y) * width + left, (top + y) * width + left + Math.max(fw, 0), 0);
					} else if ("restoreToPrevious".equals(disposal) && saved != null) {
						canvas = saved;
					}
				}
			} catch (Exception | LinkageError e) {
				for (NativeImage frame : frames) if (frame != null) frame.close();
				throw e;
			}
			return new Decoded(picture, frames, ends, width / (float) height);
		} finally {
			reader.dispose();
		}
	}

	@Nullable
	private static Node child(@Nullable Node node, String name) {
		if (node == null) return null;
		for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (name.equals(child.getNodeName())) return child;
		}
		return null;
	}

	private static String text(@Nullable Node node, String name) {
		NamedNodeMap attributes = node != null ? node.getAttributes() : null;
		Node value = attributes != null ? attributes.getNamedItem(name) : null;
		return value != null ? value.getNodeValue() : "";
	}

	private static int attribute(@Nullable Node node, String name, int fallback) {
		try {
			return Integer.parseInt(text(node, name));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Box-filters ARGB pixels down to (at most) the given size; a picture smaller than that is copied as is. */
	private static NativeImage scale(int[] argb, int width, int height, int outW, int outH) {
		outW = Math.min(outW, width);
		outH = Math.min(outH, height);
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, outW, outH, false);
		for (int ty = 0; ty < outH; ty++) {
			int y0 = (int) ((long) ty * height / outH);
			int y1 = Math.max(y0 + 1, (int) ((long) (ty + 1) * height / outH));
			for (int tx = 0; tx < outW; tx++) {
				int x0 = (int) ((long) tx * width / outW);
				int x1 = Math.max(x0 + 1, (int) ((long) (tx + 1) * width / outW));
				int a = 0, r = 0, g = 0, b = 0;
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						int c = argb[y * width + x];
						a += c >>> 24;
						r += c >> 16 & 0xFF;
						g += c >> 8 & 0xFF;
						b += c & 0xFF;
					}
				}
				int n = (y1 - y0) * (x1 - x0);
				image.setColorArgb(tx, ty, (a / n) << 24 | (r / n) << 16 | (g / n) << 8 | b / n);
			}
		}
		return image;
	}
}
