package dev.v1zir.visuals.gui;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.audio.AlPlayer;
import dev.v1zir.visuals.audio.AudioFiles;
import dev.v1zir.visuals.audio.Pcm;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.util.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The PS5 style theme's sounds: a soft tick as the selection moves, an airy brush between tabs, a warm bloom to confirm,
 * a falling tone to go back, two quiet bells for a message and a swell when the game has loaded. The client's own are
 * made for it (assets/v1zir/sounds/ui); the player's own files in config/v1zir/ui-sounds (MP3, OGG or WAV) take their
 * place, each picked under Theme or, on Auto, by a word in its name (navigation, select, back, tab, notification,
 * startup...). They play only with that style theme and Theme > UI sounds on.
 */
public final class UiSounds {
	public static final String MOVE = "move";
	public static final String TAB = "tab";
	public static final String CONFIRM = "confirm";
	public static final String BACK = "back";
	public static final String NOTIFY = "notify";
	public static final String BOOT = "boot";
	/** The choices of each sound besides the files: guessed from the names, or the client's own. */
	public static final String AUTO = "Auto";
	public static final String BUILT_IN = "Built-in";
	private static final String[] SOUNDS = {BOOT, NOTIFY, BACK, TAB, CONFIRM, MOVE};
	/** Words in a file's name that tell which sound it is, in the order they are looked for (English and Polish). */
	private static final Map<String, String[]> WORDS = Map.of(
		BOOT, new String[]{"boot", "start", "logo", "intro", "welcome", "poweron", "turnon", "uruch", "wlacz", "powitan"},
		NOTIFY, new String[]{"notif", "message", "msg", "trophy", "alert", "ping", "powiad", "wiadom", "trofe"},
		BACK, new String[]{"back", "cancel", "close", "return", "exit", "wstecz", "powrot", "cofn", "anul", "zamkn"},
		TAB, new String[]{"tab", "switch", "category", "swipe", "slide", "page", "karta", "zaklad", "przelacz"},
		CONFIRM, new String[]{"confirm", "select", "enter", "accept", "ok", "click", "press", "launch", "wybierz", "wybor", "zatwierdz", "akcept", "klik"},
		MOVE, new String[]{"move", "nav", "scroll", "cursor", "tick", "hover", "focus", "ruch", "przesun", "kursor"});
	private static final String README = """
		Your own sounds for the PS5 style theme of V1zir Visuals: MP3, OGG or WAV files.

		With a sound on Auto (Theme in the click GUI) a file is picked by a word in its name:
		  moving the selection - navigation, nav, move, scroll, tick, hover
		  switching tabs       - tab, switch, swipe, slide
		  confirming           - select, confirm, enter, accept, click
		  going back           - back, cancel, close, return
		  a message            - notification, message, trophy, alert
		  the game has loaded  - startup, start, boot, logo, intro
		Or pick a file for each sound under Theme. Short sounds work best (a few seconds at most).
		""";

	/** The player's files are decoded here ahead of their first play, so playing one never waits on decoding it. */
	private static final ExecutorService DECODE = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "V1zir UI sounds");
		thread.setDaemon(true);
		return thread;
	});
	private static final Map<String, CompletableFuture<Pcm>> DECODED = new ConcurrentHashMap<>();
	/** Files (by version) handed to the audio device already: it keeps them, so they are not decoded again. */
	private static final java.util.Set<String> UPLOADED = new java.util.HashSet<>();
	/** Files up to this size are decoded ahead; a longer one only when it first plays. */
	private static final long DECODE_AHEAD = 1L << 20;
	/** The folder's files as last read, which sound each one is, and when the folder last changed. */
	private static List<Path> files = List.of();
	private static final Map<String, Path> guessed = new HashMap<>();
	private static long folderStamp = Long.MIN_VALUE;
	private static boolean made;
	private static volatile boolean importing;
	/** How many files the last import copied, until it is taken (-1: nothing to report). */
	private static final AtomicInteger imported = new AtomicInteger(-1);

	private UiSounds() {
	}

	/** Where the player's own sounds go. */
	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("ui-sounds");
	}

	/** Whether the PS5 style's sounds play. */
	public static boolean on() {
		if (!Theme.ps5()) return false;
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		return theme == null || theme.uiSounds.on();
	}

	public static void play(String name) {
		play(name, 1f);
	}

	public static void play(String name, float pitch) {
		if (!on()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		Path own = theme != null ? theme.ownSound(name) : null;
		if (own != null && playFile(mc, own, theme.ownVolume.f())) return;
		float volume = switch (name) {
			case MOVE -> 0.35f;
			case TAB, BACK -> 0.45f;
			case BOOT -> 0.8f;
			default -> 0.6f;
		};
		mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(V1zir.id("ui." + name)), pitch, volume));
	}

	/** Plays one of the player's files at its own pitch; false when it is gone. */
	private static boolean playFile(MinecraftClient mc, Path file, float volume) {
		long stamp;
		try {
			stamp = Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			return false;
		}
		float master = mc.options.getSoundVolume(SoundCategory.MASTER);
		// Keyed by the file's time too, so an edited file is read again.
		String key = file + "@" + stamp;
		if (UPLOADED.contains(key)) {
			AlPlayer.play(key, () -> AudioFiles.decode(file), volume * master, 1f);
			return true;
		}
		CompletableFuture<Pcm> decoding = decode(key, file);
		// Still being decoded (the folder just changed), or it cannot be: the client's own sound this time.
		if (!decoding.isDone() || decoding.isCompletedExceptionally()) return false;
		UPLOADED.add(key);
		// The audio device keeps it from now on; the decoded samples need not stay in memory.
		DECODED.remove(key);
		AlPlayer.play(key, decoding::join, volume * master, 1f);
		return true;
	}

	/** Decodes a file in the background, once for each version of it. */
	private static CompletableFuture<Pcm> decode(String key, Path file) {
		return DECODED.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
			try {
				return AudioFiles.decode(file);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, DECODE));
	}

	/**
	 * Asks for sound files with the system's file dialog (on a thread of its own: the dialog blocks) and copies the
	 * ones chosen into the folder, where they take the place of the client's own sounds (by their names, on Auto).
	 * Without a native dialog the folder opens instead.
	 */
	public static void importFiles() {
		if (importing) return;
		importing = true;
		Thread thread = new Thread(() -> {
			int count = 0;
			try {
				String chosen;
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer filters = stack.mallocPointer(3);
					filters.put(stack.UTF8("*.mp3"));
					filters.put(stack.UTF8("*.ogg"));
					filters.put(stack.UTF8("*.wav"));
					filters.flip();
					chosen = TinyFileDialogs.tinyfd_openFileDialog("Sounds for the PS5 style menus", null, filters, "Sounds (MP3, OGG, WAV)", true);
				}
				if (chosen != null) {
					Path folder = folder();
					Files.createDirectories(folder);
					for (String part : chosen.split("\\|")) {
						if (part.isBlank()) continue;
						Path source = Path.of(part.strip());
						if (!Files.isRegularFile(source) || !AudioFiles.supported(source)) continue;
						Path target = folder.resolve(source.getFileName().toString());
						if (!source.toAbsolutePath().equals(target.toAbsolutePath())) Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
						count++;
					}
				}
			} catch (Exception | LinkageError e) {
				V1zir.LOGGER.warn("No file dialog, opening the sounds folder instead", e);
				MinecraftClient.getInstance().execute(() -> Util.getOperatingSystem().open(folder()));
			} finally {
				imported.set(count);
				importing = false;
			}
		}, "V1zir UI sounds import");
		thread.setDaemon(true);
		thread.start();
	}

	/** How many files the last import copied, once (then -1 until the next import ends). */
	public static int takeImported() {
		return imported.getAndSet(-1);
	}

	/** The file picked for a sound on Auto: the first whose name says it is that sound, null when none does. */
	@Nullable
	public static Path guessed(String sound) {
		return guessed.get(sound);
	}

	/** The choices for one sound: Auto, Built-in, then the folder's files. */
	public static List<String> choices() {
		List<String> out = new ArrayList<>();
		out.add(AUTO);
		out.add(BUILT_IN);
		for (Path file : files) out.add(file.getFileName().toString());
		return out;
	}

	/**
	 * Reads the folder again when it changed (cheap otherwise); true when its files changed. Makes the folder, with a
	 * note on how the files are picked, the first time.
	 */
	public static boolean refresh() {
		Path folder = folder();
		if (!made) {
			made = true;
			try {
				Files.createDirectories(folder);
				Path readme = folder.resolve("README.txt");
				if (!Files.exists(readme)) Files.writeString(readme, README, StandardCharsets.UTF_8);
			} catch (IOException e) {
				V1zir.LOGGER.debug("Could not make {}", folder, e);
			}
		}
		long stamp;
		try {
			stamp = Files.getLastModifiedTime(folder).toMillis();
		} catch (IOException e) {
			stamp = -1L;
		}
		if (stamp == folderStamp) return false;
		folderStamp = stamp;
		files = AudioFiles.list(folder);
		guessed.clear();
		for (Path file : files) {
			String sound = guess(file.getFileName().toString());
			if (sound != null) guessed.putIfAbsent(sound, file);
		}
		// Decoded ahead, so the first play of each is as quick as the rest.
		DECODED.clear();
		for (Path file : files) {
			try {
				String key = file + "@" + Files.getLastModifiedTime(file).toMillis();
				if (!UPLOADED.contains(key) && Files.size(file) <= DECODE_AHEAD) decode(key, file);
			} catch (IOException ignored) {
				// Gone again: nothing to decode.
			}
		}
		return true;
	}

	/** Which sound a file is for, by the words of its name; null when no word fits. */
	@Nullable
	static String guess(String fileName) {
		String name = fileName.toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		if (dot > 0) name = name.substring(0, dot);
		name = name.replace('ą', 'a').replace('ć', 'c').replace('ę', 'e').replace('ł', 'l').replace('ń', 'n').replace('ó', 'o').replace('ś', 's')
			.replace('ź', 'z').replace('ż', 'z');
		String joined = name.replaceAll("[^a-z0-9]", "");
		String[] words = name.split("[^a-z0-9]+");
		for (String sound : SOUNDS) {
			for (String key : WORDS.get(sound)) {
				for (String word : words) {
					if (key.length() <= 2 ? word.equals(key) : word.startsWith(key)) return sound;
				}
				// Words run together (poweron, turnon) are looked for in the whole name.
				if (key.length() >= 6 && joined.contains(key)) return sound;
			}
		}
		return null;
	}
}
