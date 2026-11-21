package dev.v1zir.visuals.gui.messages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.chat.ChatService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Uuids;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The pictures of Messages: a player's face cut from their skin (looked up by name at Mojang, the way the game finds
 * skins: the account, its profile, the skin on Mojang's texture server), the picture someone chose for themselves (a
 * small PNG that comes with their profile), and picking one's own from a file. Faces are fetched in the background
 * and kept for the session; the textures are made on the render thread.
 */
public final class Faces {
	private static final String PROFILE_BY_NAME = "https://api.mojang.com/users/profiles/minecraft/";
	private static final String PROFILE_BY_ID = "https://sessionserver.mojang.com/session/minecraft/profile/";
	private static final String SKIN_HOST = "textures.minecraft.net";
	/** A lookup that found nothing (no account of that name, no connection) is tried again after this long. */
	private static final long RETRY_MS = 10L * 60_000L;
	private static final long MAX_SKIN_BYTES = 256L * 1024L;
	/** A picture chosen is made a square of this many pixels (smaller when its PNG would be too large). */
	private static final int PICTURE_SIZE = 96;
	private static final long MAX_SOURCE_BYTES = 40L * 1024L * 1024L;
	private static final int MAX_PICTURES = 48;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
		.followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final ExecutorService FETCH = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "V1zir faces");
		thread.setDaemon(true);
		return thread;
	});
	private static final Map<String, Face> FACES = new HashMap<>();
	private static final Map<String, Supplier<SkinTextures>> DEFAULTS = new HashMap<>();
	private static final Map<String, Picture> PICTURES = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Picture> eldest) {
			if (size() <= MAX_PICTURES) return false;
			eldest.getValue().close();
			return true;
		}
	};
	private static final AtomicReference<byte[]> PICKED = new AtomicReference<>();
	private static volatile boolean picking;
	private static volatile String pickError = "";

	private Faces() {
	}

	// ------------------------------------------------------------------ faces

	/** One player's face: being fetched, made, or not found (the default face then, looked for again later). */
	private static final class Face {
		@Nullable
		CompletableFuture<NativeImage> pending;
		@Nullable
		NativeImageBackedTexture texture;
		@Nullable
		TextureSetup setup;
		long failedAt;
	}

	/**
	 * Their face from their own skin (its hat over it), 8 pixels a side, to draw as pixel art; null while it is being
	 * fetched ({@link #loading}) and for names without a skin of their own (draw {@link #fallback} then).
	 */
	@Nullable
	public static TextureSetup face(String name, @Nullable UUID uuid) {
		String key = name.toLowerCase(Locale.ROOT);
		Face face = FACES.get(key);
		long now = System.currentTimeMillis();
		if (face == null || face.setup == null && face.pending == null && now - face.failedAt > RETRY_MS) {
			if (face == null) {
				face = new Face();
				FACES.put(key, face);
			}
			face.pending = CompletableFuture.supplyAsync(() -> fetch(name, uuid), FETCH);
		}
		if (face.pending != null && face.pending.isDone()) {
			NativeImage image;
			try {
				image = face.pending.join();
			} catch (RuntimeException e) {
				image = null;
			}
			face.pending = null;
			if (image == null) {
				face.failedAt = now;
			} else {
				try {
					face.texture = new NativeImageBackedTexture(() -> "V1zir face " + key, image);
					face.setup = TextureSetup.of(face.texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
				} catch (RuntimeException e) {
					image.close();
					face.failedAt = now;
				}
			}
		}
		return face.setup;
	}

	/** Whether their face is still on its way (a quiet placeholder looks better than the default face swapping out). */
	public static boolean loading(String name) {
		Face face = FACES.get(name.toLowerCase(Locale.ROOT));
		return face != null && face.pending != null;
	}

	/** The game's default skin for the name, as it shows players without a skin of their own. */
	public static SkinTextures fallback(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		Supplier<SkinTextures> skin = DEFAULTS.get(key);
		if (skin == null) {
			skin = MinecraftClient.getInstance().getSkinProvider().supplySkinTextures(Uuids.getOfflinePlayerProfile(name), false);
			DEFAULTS.put(key, skin);
		}
		return skin.get();
	}

	/** The account, its profile and its skin, then the face; null when any is missing. Background thread. */
	@Nullable
	private static NativeImage fetch(String name, @Nullable UUID known) {
		try {
			String id = known != null ? known.toString().replace("-", "") : lookup(name);
			if (id == null) return null;
			HttpResponse<String> profile = HTTP.send(request(URI.create(PROFILE_BY_ID + id)), HttpResponse.BodyHandlers.ofString());
			if (profile.statusCode() != 200) return null;
			String url = skinUrl(JsonParser.parseString(profile.body()).getAsJsonObject());
			if (url == null) return null;
			URI skin = URI.create(url.startsWith("http://") ? "https://" + url.substring(7) : url);
			// Only Mojang's own texture server: a profile cannot send the game anywhere else.
			if (!SKIN_HOST.equalsIgnoreCase(skin.getHost())) return null;
			HttpResponse<byte[]> png = HTTP.send(request(skin), HttpResponse.BodyHandlers.ofByteArray());
			if (png.statusCode() != 200 || png.body().length > MAX_SKIN_BYTES) return null;
			try (NativeImage image = NativeImage.read(png.body())) {
				return cut(image);
			}
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.debug("No face for {}", name, e);
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	/** The id of the account of this name (no dashes), null when there is none. */
	@Nullable
	private static String lookup(String name) throws IOException, InterruptedException {
		if (!ChatService.NAME.matcher(name).matches()) return null;
		HttpResponse<String> response = HTTP.send(request(URI.create(PROFILE_BY_NAME + URLEncoder.encode(name, StandardCharsets.UTF_8))),
			HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) return null;
		JsonElement id = JsonParser.parseString(response.body()).getAsJsonObject().get("id");
		return id != null && id.isJsonPrimitive() ? id.getAsString() : null;
	}

	/** The skin's address in a profile's textures, null when it has none of its own. */
	@Nullable
	private static String skinUrl(JsonObject profile) {
		if (!(profile.get("properties") instanceof com.google.gson.JsonArray properties)) return null;
		for (JsonElement element : properties) {
			if (!(element instanceof JsonObject property) || !"textures".equals(string(property, "name"))) continue;
			String value = string(property, "value");
			if (value == null) continue;
			JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject();
			if (textures.get("textures") instanceof JsonObject all && all.get("SKIN") instanceof JsonObject skin) return string(skin, "url");
		}
		return null;
	}

	@Nullable
	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static HttpRequest request(URI uri) {
		return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("User-Agent", "V1zir-Visuals").GET().build();
	}

	/**
	 * The face of a skin (64 x 64, or a legacy 64 x 32) with its hat layer over it, as the game draws a head from the
	 * front. A legacy skin's overlay that is opaque all over is no hat, as the game treats it.
	 */
	static NativeImage cut(NativeImage skin) {
		int w = skin.getWidth(), h = skin.getHeight();
		if (w < 64 || h < 32 || w % 64 != 0) throw new IllegalArgumentException("Not a skin: " + w + "x" + h);
		int s = w / 64;
		boolean legacy = h * 2 == w;
		boolean hat = !legacy || !opaque(skin, 32 * s, 0, 64 * s, 16 * s);
		int size = 8 * s;
		NativeImage face = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int base = skin.getColorArgb(8 * s + x, 8 * s + y) | 0xFF000000;
				if (hat) base = over(base, skin.getColorArgb(40 * s + x, 8 * s + y));
				face.setColorArgb(x, y, base);
			}
		}
		return face;
	}

	private static boolean opaque(NativeImage image, int x0, int y0, int x1, int y1) {
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) if ((image.getColorArgb(x, y) >>> 24) < 255) return false;
		}
		return true;
	}

	/** top over bottom (bottom opaque). */
	private static int over(int bottom, int top) {
		int a = top >>> 24;
		if (a == 0) return bottom;
		if (a == 255) return top;
		float t = a / 255f;
		int r = Math.round((top >> 16 & 255) * t + (bottom >> 16 & 255) * (1f - t));
		int g = Math.round((top >> 8 & 255) * t + (bottom >> 8 & 255) * (1f - t));
		int b = Math.round((top & 255) * t + (bottom & 255) * (1f - t));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	// ------------------------------------------------------------------ chosen pictures

	/** A chosen picture's texture, or a picture that could not be read. */
	private static final class Picture {
		@Nullable
		final NativeImageBackedTexture texture;
		@Nullable
		final TextureSetup setup;

		Picture(@Nullable NativeImageBackedTexture texture, @Nullable TextureSetup setup) {
			this.texture = texture;
			this.setup = setup;
		}

		void close() {
			if (texture != null) texture.close();
		}
	}

	/** A chosen picture (PNG) as a texture, smooth; null when it cannot be read. Render thread. */
	@Nullable
	public static TextureSetup picture(byte[] png) {
		String key = png.length + ":" + Arrays.hashCode(png);
		Picture picture = PICTURES.get(key);
		if (picture == null) {
			NativeImageBackedTexture texture = null;
			TextureSetup setup = null;
			try {
				NativeImage image = NativeImage.read(png);
				try {
					texture = new NativeImageBackedTexture(() -> "V1zir picture", image);
				} catch (RuntimeException e) {
					image.close();
					throw e;
				}
				setup = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			} catch (IOException | RuntimeException e) {
				V1zir.LOGGER.debug("A picture in Messages could not be read", e);
			}
			picture = new Picture(texture, setup);
			PICTURES.put(key, picture);
		}
		return picture.setup;
	}

	/**
	 * Asks for a picture with the system's file dialog (on a thread of its own: the dialog blocks) and makes it a small
	 * square PNG for a profile: cut to a square from the middle and shrunk. It comes through {@link #takePicked()}.
	 */
	public static void pick() {
		if (picking) return;
		picking = true;
		pickError = "";
		Thread thread = new Thread(() -> {
			try {
				String chosen;
				try (MemoryStack stack = MemoryStack.stackPush()) {
					String[] extensions = {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"};
					PointerBuffer filters = stack.mallocPointer(extensions.length);
					for (String extension : extensions) filters.put(stack.UTF8(extension));
					filters.flip();
					chosen = TinyFileDialogs.tinyfd_openFileDialog("Your picture for Messages", null, filters, "Pictures (PNG, JPEG, GIF, BMP)", false);
				}
				if (chosen == null) return;
				Path file = Path.of(chosen);
				if (Files.size(file) > MAX_SOURCE_BYTES) {
					pickError = "That picture is too large";
					return;
				}
				byte[] png = square(Files.readAllBytes(file));
				if (png == null) pickError = "That file is not a picture";
				else PICKED.set(png);
			} catch (Exception | LinkageError e) {
				V1zir.LOGGER.warn("Could not pick a picture for Messages", e);
				pickError = "No file dialog on this system";
			} finally {
				picking = false;
			}
		}, "V1zir picture picker");
		thread.setDaemon(true);
		thread.start();
	}

	/** The picture picked since the last call (a square PNG), or null. */
	@Nullable
	public static byte[] takePicked() {
		return PICKED.getAndSet(null);
	}

	public static boolean picking() {
		return picking;
	}

	/** Why the last pick gave nothing, empty when it did (or was cancelled). */
	public static String pickError() {
		return pickError;
	}

	/** Any picture stb_image reads (PNG, JPEG, GIF, BMP...) as a square PNG of PICTURE_SIZE, under the size a profile allows. */
	@Nullable
	static byte[] square(byte[] encoded) throws IOException {
		ByteBuffer buffer = MemoryUtil.memAlloc(encoded.length);
		int[] argb;
		int w, h;
		try {
			buffer.put(encoded).flip();
			int[] pw = new int[1], ph = new int[1], channels = new int[1];
			if (!STBImage.stbi_info_from_memory(buffer, pw, ph, channels) || pw[0] <= 0 || ph[0] <= 0 || (long) pw[0] * ph[0] > 64L * 1024 * 1024) return null;
			ByteBuffer pixels = STBImage.stbi_load_from_memory(buffer, pw, ph, channels, 4);
			if (pixels == null) return null;
			w = pw[0];
			h = ph[0];
			argb = new int[w * h];
			try {
				for (int i = 0; i < argb.length; i++) {
					int p = i * 4;
					argb[i] = (pixels.get(p + 3) & 0xFF) << 24 | (pixels.get(p) & 0xFF) << 16 | (pixels.get(p + 1) & 0xFF) << 8 | pixels.get(p + 2) & 0xFF;
				}
			} finally {
				STBImage.stbi_image_free(pixels);
			}
		} finally {
			MemoryUtil.memFree(buffer);
		}
		int side = Math.min(w, h);
		int x0 = (w - side) / 2, y0 = (h - side) / 2;
		for (int size = Math.min(PICTURE_SIZE, side); size >= 32; size -= 16) {
			byte[] png = png(shrink(argb, w, x0, y0, side, size), size);
			if (png.length <= ChatService.MAX_AVATAR_BYTES) return png;
		}
		return null;
	}

	/** The square (x0, y0, side) of an image shrunk to size by averaging the area of each new pixel (premultiplied). */
	private static int[] shrink(int[] argb, int w, int x0, int y0, int side, int size) {
		int[] out = new int[size * size];
		float step = (float) side / size;
		for (int oy = 0; oy < size; oy++) {
			float ya = oy * step, yb = ya + step;
			for (int ox = 0; ox < size; ox++) {
				float xa = ox * step, xb = xa + step;
				float a = 0f, r = 0f, g = 0f, b = 0f, total = 0f;
				for (int sy = (int) ya; sy < Math.min(side, (int) Math.ceil(yb)); sy++) {
					float cy = Math.min(yb, sy + 1f) - Math.max(ya, sy);
					if (cy <= 0f) continue;
					for (int sx = (int) xa; sx < Math.min(side, (int) Math.ceil(xb)); sx++) {
						float cover = cy * (Math.min(xb, sx + 1f) - Math.max(xa, sx));
						if (cover <= 0f) continue;
						int c = argb[(y0 + sy) * w + x0 + sx];
						float ca = (c >>> 24) / 255f * cover;
						a += ca;
						r += (c >> 16 & 255) * ca;
						g += (c >> 8 & 255) * ca;
						b += (c & 255) * ca;
						total += cover;
					}
				}
				int alpha = total > 0f ? Math.round(a / total * 255f) : 0;
				out[oy * size + ox] = a > 0f ? alpha << 24 | Math.round(r / a) << 16 | Math.round(g / a) << 8 | Math.round(b / a) : 0;
			}
		}
		return out;
	}

	/** Pixels as a PNG, written by the game's own image code. */
	private static byte[] png(int[] argb, int size) throws IOException {
		Path temp = Files.createTempFile("v1zir-picture", ".png");
		try (NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false)) {
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) image.setColorArgb(x, y, argb[y * size + x]);
			}
			image.writeTo(temp);
			return Files.readAllBytes(temp);
		} finally {
			Files.deleteIfExists(temp);
		}
	}
}
