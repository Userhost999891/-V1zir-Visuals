package dev.v1zir.visuals.gui.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.util.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pictures of the worlds and servers played, for the PS5 style home screen: as a console shows a game's art behind its
 * tile, the menu shows the world or server picked as it looked when it was played last. The client takes the picture
 * itself, a while after joining and again every quarter of an hour of play, at a moment the view is clear (no screen
 * open, looking ahead, not under water), right after the world is drawn: no hand, no HUD, no chat. It goes into
 * config/v1zir/menu-pictures, one file per world folder or server address, at most 1280 pixels wide.
 */
public final class MenuShots {
	private static final int MAX_WIDTH = 1280;
	/** Ticks in a world before its first picture: the terrain around has loaded by then. */
	private static final int FIRST_DELAY = 20 * 20;
	/** Ticks of play between two pictures of the same world or server. */
	private static final int REFRESH = 20 * 60 * 15;
	/** However often the world changes (dimensions, a server's lobbies), a new picture of the same place waits this long. */
	private static final long MIN_AGE_MILLIS = 5L * 60L * 1000L;
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "V1zir menu pictures");
		thread.setDaemon(true);
		return thread;
	});
	/** Whether pictures are taken on their own (the game test takes them when it wants). */
	public static boolean auto = true;
	@Nullable
	private static ClientWorld world;
	private static int ticks;
	private static int nextAt = FIRST_DELAY;
	@Nullable
	private static String pending;
	/** Pictures saved since the game started (the game test waits on it). */
	private static volatile int saved;

	private MenuShots() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("menu-pictures");
	}

	/** The name a server's picture goes by: its address, the default port left out. */
	public static String serverKey(String address) {
		String a = address.strip().toLowerCase(Locale.ROOT);
		if (a.endsWith(":25565")) a = a.substring(0, a.length() - 6);
		return "server-" + clean(a);
	}

	/** The name a world's picture goes by: its folder's. */
	public static String worldKey(String folder) {
		return "world-" + clean(folder.toLowerCase(Locale.ROOT));
	}

	public static Path file(String key) {
		return folder().resolve(key + ".png");
	}

	public static int saved() {
		return saved;
	}

	private static String clean(String text) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length() && out.length() < 80; i++) {
			char c = text.charAt(i);
			out.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '-' ? c : '_');
		}
		return out.toString();
	}

	private static boolean enabled() {
		if (!Theme.ps5()) return false;
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		return theme == null || theme.menuPictures.on();
	}

	/** The name of the picture of what is being played, null when it is neither a world of this computer nor a server. */
	@Nullable
	private static String currentKey(MinecraftClient client) {
		IntegratedServer server = client.getServer();
		if (server != null) {
			Path name = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName();
			return name == null ? null : worldKey(name.toString());
		}
		ServerInfo info = client.getCurrentServerEntry();
		if (info != null && !info.isRealm()) return serverKey(info.address);
		return null;
	}

	/** Every client tick: waits for a good moment to take the next picture. */
	public static void tick(MinecraftClient client) {
		if (client.world != world) {
			world = client.world;
			ticks = 0;
			nextAt = FIRST_DELAY;
			pending = null;
		}
		if (world == null || client.player == null) return;
		ticks++;
		if (!auto || pending != null || ticks < nextAt || !enabled() || !clearView(client)) return;
		String key = currentKey(client);
		if (key == null || recent(key)) {
			nextAt = ticks + REFRESH;
			return;
		}
		pending = key;
	}

	/** Whether the place has a picture taken only a few minutes ago. */
	private static boolean recent(String key) {
		try {
			Path file = file(key);
			return Files.isRegularFile(file) && System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < MIN_AGE_MILLIS;
		} catch (IOException e) {
			return false;
		}
	}

	/** Nothing in the way of the world: no screen, looking ahead rather than at the ground or the sky, not under water. */
	private static boolean clearView(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.currentScreen != null || client.isPaused()) return false;
		if (!player.isAlive() || player.isSleeping() || player.isSubmergedInWater() || player.isInLava()) return false;
		if (player.hasStatusEffect(StatusEffects.BLINDNESS) || player.hasStatusEffect(StatusEffects.DARKNESS)) return false;
		float pitch = player.getPitch();
		return pitch > -30f && pitch < 25f;
	}

	/** Takes a picture of what is being played as the next frame is drawn; false when there is nothing to picture. */
	public static boolean request(MinecraftClient client) {
		String key = currentKey(client);
		if (key == null) return false;
		pending = key;
		return true;
	}

	/** Right after the world is drawn, before the hand and the HUD (GameRendererMixin): the picture, when one is due. */
	public static void afterWorld() {
		String key = pending;
		if (key == null) return;
		pending = null;
		nextAt = ticks + REFRESH;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen != null) return;
		try {
			ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> IO.execute(() -> save(image, key)));
		} catch (RuntimeException e) {
			V1zir.LOGGER.warn("Could not take the menu picture", e);
		}
	}

	/** Scales the picture down (averaging the pixels it folds together) and writes it, replacing the last one at once. */
	private static void save(NativeImage image, String key) {
		try (image) {
			int w = image.getWidth(), h = image.getHeight();
			int f = Math.max(1, (w + MAX_WIDTH - 1) / MAX_WIDTH);
			int ow = Math.max(1, w / f), oh = Math.max(1, h / f);
			try (NativeImage out = new NativeImage(NativeImage.Format.RGBA, ow, oh, false)) {
				int n = f * f;
				for (int y = 0; y < oh; y++) {
					for (int x = 0; x < ow; x++) {
						int r = 0, g = 0, b = 0;
						for (int j = 0; j < f; j++) {
							for (int i = 0; i < f; i++) {
								int c = image.getColorArgb(x * f + i, y * f + j);
								r += c >> 16 & 255;
								g += c >> 8 & 255;
								b += c & 255;
							}
						}
						out.setColorArgb(x, y, 0xFF000000 | r / n << 16 | g / n << 8 | b / n);
					}
				}
				Files.createDirectories(folder());
				Path file = file(key);
				Path temp = file.resolveSibling(key + ".tmp");
				out.writeTo(temp);
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
				saved++;
				V1zir.LOGGER.info("Menu picture of {} ({}x{})", key, ow, oh);
			}
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.warn("Could not save the menu picture of {}", key, e);
		}
	}

	/** Forgets a picture (the menu's "Remove picture"). */
	public static void remove(String key) {
		try {
			Files.deleteIfExists(file(key));
		} catch (IOException e) {
			V1zir.LOGGER.warn("Could not remove the menu picture of {}", key, e);
		}
	}

	/** Starts reading a picture for the menu; null when there is none. */
	@Nullable
	public static Picture load(String key) {
		Path file = file(key);
		return Files.isRegularFile(file) ? new Picture(file) : null;
	}

	/** A picture read in the background, then made a texture on the render thread the first time it is asked for. */
	public static final class Picture {
		private final CompletableFuture<NativeImage> reading;
		@Nullable
		private NativeImageBackedTexture texture;
		@Nullable
		private TextureSetup setup;
		private int width;
		private int height;
		private boolean failed;
		private boolean released;
		/** The image read has been made a texture or freed here: it is not freed again. */
		private boolean taken;

		private Picture(Path file) {
			reading = CompletableFuture.supplyAsync(() -> {
				try {
					return NativeImage.read(Files.readAllBytes(file));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}, IO);
		}

		/** The texture once the picture has been read; null until then, or when it could not be read. */
		@Nullable
		public TextureSetup setup() {
			if (setup != null || failed || released || !reading.isDone()) return setup;
			NativeImage image;
			try {
				image = reading.join();
			} catch (RuntimeException e) {
				failed = true;
				V1zir.LOGGER.debug("Could not read a menu picture", e);
				return null;
			}
			taken = true;
			try {
				width = image.getWidth();
				height = image.getHeight();
				texture = new NativeImageBackedTexture(() -> "V1zir menu picture", image);
				setup = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			} catch (RuntimeException e) {
				failed = true;
				if (texture == null) image.close();
				V1zir.LOGGER.debug("Could not upload a menu picture", e);
			}
			return setup;
		}

		/** Whether the picture could not be read or uploaded. */
		public boolean failed() {
			return failed;
		}

		/** Width over height of the picture, 16:9 until it is known. */
		public float aspect() {
			return width > 0 && height > 0 ? width / (float) height : 16f / 9f;
		}

		public void release() {
			if (released) return;
			released = true;
			if (texture != null) texture.close();
			texture = null;
			setup = null;
			// Still being read when the menu goes: freed as soon as it is read.
			if (!taken) reading.thenAccept(image -> {
				if (image != null) image.close();
			});
		}
	}
}
