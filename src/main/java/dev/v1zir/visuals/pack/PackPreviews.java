package dev.v1zir.visuals.pack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small textures of the Pack Mixer's tiles: each pack's version of a texture read from its pack (not the atlas, which
 * holds only one), decoded on a background thread, its first frame cut out (animations are vertical strips) and
 * uploaded on the render thread. A few hundred are kept, the least recently drawn are freed.
 */
public final class PackPreviews {
	private static final int MAX = 360;
	private static final int MAX_LOADING = 24;
	private static final ExecutorService DECODER = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "V1zir Pack Previews");
		thread.setDaemon(true);
		return thread;
	});

	/** A loaded preview: its texture and size in pixels. */
	public record Preview(TextureSetup texture, int width, int height) {
	}

	private record Decoded(String key, NativeImage image) {
	}

	private static final Map<String, Object> CACHE = new LinkedHashMap<>(64, 0.75f, true);
	private static final Map<String, Identifier> IDS = new LinkedHashMap<>();
	private static final Queue<Decoded> DONE = new ConcurrentLinkedQueue<>();
	private static final Object LOADING = new Object();
	private static final Object FAILED = new Object();
	private static final AtomicInteger COUNTER = new AtomicInteger();
	private static int loading;

	private PackPreviews() {
	}

	/** The preview of one pack's version of a texture, or null while it loads (or if it cannot be read). */
	public static Preview get(PackMixer.Entry entry, PackMixer.Version version) {
		drain();
		String key = entry.id() + "|" + version.packId();
		Object cached = CACHE.get(key);
		if (cached instanceof Preview preview) return preview;
		if (cached != null || loading >= MAX_LOADING) return null;
		CACHE.put(key, LOADING);
		loading++;
		DECODER.execute(() -> {
			NativeImage image = null;
			try (InputStream in = version.resource().getInputStream()) {
				NativeImage full = NativeImage.read(in);
				int w = full.getWidth();
				int h = full.getHeight();
				if (h > w && h % w == 0) {
					// An animation strip: its first frame.
					image = new NativeImage(w, w, false);
					for (int y = 0; y < w; y++) for (int x = 0; x < w; x++) image.setColorArgb(x, y, full.getColorArgb(x, y));
					full.close();
				} else {
					image = full;
				}
			} catch (Exception | OutOfMemoryError e) {
				if (image != null) image.close();
				image = null;
			}
			DONE.add(new Decoded(key, image));
		});
		return null;
	}

	/** Uploads what finished decoding and frees the least recently used previews over the limit. Render thread. */
	private static void drain() {
		Decoded decoded;
		while ((decoded = DONE.poll()) != null) {
			loading = Math.max(0, loading - 1);
			if (decoded.image() == null) {
				CACHE.put(decoded.key(), FAILED);
				continue;
			}
			if (CACHE.get(decoded.key()) != LOADING) {
				decoded.image().close();
				continue;
			}
			Identifier id = V1zir.id("pack_mixer/preview_" + COUNTER.getAndIncrement());
			NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "V1zir pack preview", decoded.image());
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
			IDS.put(decoded.key(), id);
			CACHE.put(decoded.key(), new Preview(TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST)),
				decoded.image().getWidth(), decoded.image().getHeight()));
		}
		if (CACHE.size() <= MAX) return;
		Queue<String> evict = new ArrayDeque<>();
		Iterator<Map.Entry<String, Object>> it = CACHE.entrySet().iterator();
		while (CACHE.size() - evict.size() > MAX && it.hasNext()) {
			Map.Entry<String, Object> e = it.next();
			if (e.getValue() == LOADING) continue;
			evict.add(e.getKey());
		}
		for (String key : evict) {
			CACHE.remove(key);
			Identifier id = IDS.remove(key);
			if (id != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
		}
	}

	/** Frees every preview (the packs were reloaded, their files may be gone). */
	public static void clear() {
		for (Identifier id : IDS.values()) MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
		IDS.clear();
		CACHE.entrySet().removeIf(e -> e.getValue() != LOADING);
	}
}
