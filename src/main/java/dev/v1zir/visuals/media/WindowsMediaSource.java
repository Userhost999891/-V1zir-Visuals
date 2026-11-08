package dev.v1zir.visuals.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Windows 10/11: one long-lived Windows PowerShell (no window) running now_playing.ps1, copied into the game's config
 * folder, which reads the system media transport controls through WinRT. It prints a JSON line about every 300 ms
 * and takes commands on stdin; closing stdin ends it.
 * <p>
 * When the bridge cannot run (blocked by an antivirus or a script policy, missing) or reports nothing while Spotify
 * plays, Spotify's own window title takes over ({@link SpotifyWindow}), so the song and lyrics still show. What went
 * wrong with the bridge is written to the game log.
 */
final class WindowsMediaSource extends MediaSource {
	private static final String SCRIPT = "/assets/" + V1zir.MOD_ID + "/media/now_playing.ps1";
	/** Starts in a row that ended without a single report before the bridge counts as unavailable. */
	private static final int MAX_FAILURES = 3;
	/** The window fallback speaks when the bridge has not reported a song for this long. */
	private static final long BRIDGE_QUIET_NANOS = 2_500_000_000L;

	private volatile boolean stopped;
	private Process process;
	private Writer input;
	private Path script;
	private int failures;
	/** When the bridge last reported a song (System.nanoTime), 0 if never. */
	private volatile long bridgeSongAt;
	private SpotifyWindow window;
	/** The window fallback published the state last. */
	private volatile boolean windowSpeaking;
	private boolean windowBroken;
	private boolean loggedFailure;

	WindowsMediaSource(ScheduledExecutorService worker) {
		super(worker);
	}

	@Override
	void start() {
		if (stopped) return;
		if (window == null && !windowBroken) {
			try {
				window = new SpotifyWindow();
				worker.scheduleWithFixedDelay(this::pollWindow, 1L, 700L, TimeUnit.MILLISECONDS);
			} catch (Throwable e) {
				windowBroken = true;
				V1zir.LOGGER.warn("Spotify window fallback unavailable: {}", e.toString());
			}
		}
		Process started;
		try {
			if (script == null) script = copyScript();
			started = new ProcessBuilder(powershell(), "-NoLogo", "-NoProfile", "-NonInteractive",
				"-ExecutionPolicy", "Bypass", "-File", script.toString())
				.redirectError(log().toFile())
				.start();
		} catch (IOException e) {
			V1zir.LOGGER.warn("Could not start the media bridge (PowerShell): {}", e.toString());
			restart(false);
			return;
		}
		synchronized (this) {
			if (stopped) {
				started.destroy();
				return;
			}
			process = started;
			input = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
		}
		Thread reader = new Thread(() -> read(started), "V1zir Media Reader");
		reader.setDaemon(true);
		reader.start();
	}

	/** Windows PowerShell by its full path (PATH can be changed or shadowed), else whatever PATH finds. */
	private static String powershell() {
		String root = System.getenv("SystemRoot");
		if (root != null) {
			Path path = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
			if (Files.isRegularFile(path)) return path.toString();
		}
		return "powershell.exe";
	}

	private static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("media");
	}

	private static Path log() {
		return folder().resolve("bridge.log");
	}

	/** The script in the game's config folder (not the temp folder, where scripts are often blocked), refreshed if it changed. */
	private static Path copyScript() throws IOException {
		Path path = folder().resolve("now_playing.ps1");
		Files.createDirectories(path.getParent());
		byte[] bytes;
		try (InputStream in = WindowsMediaSource.class.getResourceAsStream(SCRIPT)) {
			if (in == null) throw new IOException("Missing " + SCRIPT);
			bytes = in.readAllBytes();
		}
		if (!Files.isRegularFile(path) || !Arrays.equals(Files.readAllBytes(path), bytes)) Files.write(path, bytes);
		return path;
	}

	/** Spotify's window title stands in while the bridge is quiet or down. */
	private void pollWindow() {
		if (stopped || window == null) return;
		try {
			if (System.nanoTime() - bridgeSongAt < BRIDGE_QUIET_NANOS) {
				windowSpeaking = false;
				return;
			}
			NowPlaying state = window.poll();
			if (state.track() != null || windowSpeaking) {
				windowSpeaking = state.track() != null;
				publish(state.track(), state.art(), state.positionMs(), state.playing());
			}
		} catch (Throwable e) {
			// JNA missing or refused: stop trying, the bridge still works if it can.
			window = null;
			windowBroken = true;
			V1zir.LOGGER.warn("Spotify window fallback failed: {}", e.toString());
		}
	}

	/** Reader thread: one report per line until the process ends. */
	private void read(Process from) {
		boolean reported = false;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(from.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				reported |= handle(line);
			}
		} catch (IOException ignored) {
			// The process ended.
		}
		if (stopped) return;
		if (!windowSpeaking) publish(null, "", 0L, false);
		if (!reported) logFailure(from);
		restart(reported);
	}

	/** Once per session: why the bridge ended without a report, from its error output, into the game log. */
	private void logFailure(Process from) {
		if (loggedFailure) return;
		loggedFailure = true;
		String detail = "";
		try {
			List<String> lines = Files.readAllLines(log(), StandardCharsets.UTF_8);
			detail = String.join(" | ", lines.subList(Math.max(0, lines.size() - 8), lines.size())).strip();
		} catch (IOException | RuntimeException ignored) {
			// No log.
		}
		int exit = from.isAlive() ? -1 : from.exitValue();
		V1zir.LOGGER.warn("Media bridge (PowerShell) ended without reporting (exit {}){}; using Spotify's window title instead", exit,
			detail.isEmpty() ? "" : ": " + detail);
	}

	private boolean handle(String line) {
		try {
			JsonObject o = JsonParser.parseString(line).getAsJsonObject();
			String title = MacMediaSource.text(o, "t");
			if (o.has("none") || title.isEmpty()) {
				// Nothing in the media controls; the window fallback may still see Spotify playing.
				if (!windowSpeaking) publish(null, "", 0L, false);
			} else {
				bridgeSongAt = System.nanoTime();
				windowSpeaking = false;
				Track track = new Track(title, MacMediaSource.text(o, "a"), MacMediaSource.text(o, "al"), o.get("d").getAsLong());
				publish(track, MacMediaSource.text(o, "art"), o.get("p").getAsLong(), o.get("s").getAsInt() == 1);
			}
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Starts the bridge again after it ended; one that keeps dying without reporting backs off. */
	private void restart(boolean reported) {
		failures = reported ? 0 : failures + 1;
		if (failures >= MAX_FAILURES && (window == null || windowBroken)) {
			unavailable("Media controls blocked (see logs/latest.log)");
		}
		try {
			worker.schedule(this::start, failures >= MAX_FAILURES ? 60L : 3L, TimeUnit.SECONDS);
		} catch (RejectedExecutionException ignored) {
			// Stopped.
		}
	}

	@Override
	synchronized void command(Media.Command command) {
		if (windowSpeaking || input == null) {
			try {
				SpotifyWindow.command(command);
			} catch (Throwable ignored) {
				// No JNA: nothing to press.
			}
			return;
		}
		try {
			input.write(switch (command) {
				case PLAY_PAUSE -> "playpause\n";
				case NEXT -> "next\n";
				case PREVIOUS -> "previous\n";
			});
			input.flush();
		} catch (IOException ignored) {
			// The bridge is restarting.
		}
	}

	@Override
	synchronized void stop() {
		stopped = true;
		if (process == null) return;
		Process running = process;
		try {
			// Closing stdin lets the script delete its temp files and exit on its own...
			input.close();
		} catch (IOException ignored) {
			// Already gone.
		}
		// ...and anything still running a moment later is ended.
		running.onExit().completeOnTimeout(running, 2, TimeUnit.SECONDS).thenAccept(Process::destroy);
		process = null;
		input = null;
	}
}
