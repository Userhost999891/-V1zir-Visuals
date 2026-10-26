package dev.v1zir.visuals.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;

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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Windows 10/11: one long-lived hidden Windows PowerShell running now_playing.ps1 (copied to a temp
 * file), which reads the system media transport controls through WinRT. It prints a JSON line about
 * every 300 ms and takes commands on stdin; closing stdin ends it.
 */
final class WindowsMediaSource extends MediaSource {
	private static final String SCRIPT = "/assets/" + V1zir.MOD_ID + "/media/now_playing.ps1";
	/** Starts in a row that ended without a single report before the bridge counts as unavailable. */
	private static final int MAX_FAILURES = 3;

	private volatile boolean stopped;
	private Process process;
	private Writer input;
	private Path script;
	private int failures;

	WindowsMediaSource(ScheduledExecutorService worker) {
		super(worker);
	}

	@Override
	void start() {
		if (stopped) return;
		Process started;
		try {
			if (script == null) script = copyScript();
			started = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
				"-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script.toString())
				.redirectError(script.resolveSibling("v1zir-media.log").toFile())
				.start();
		} catch (IOException e) {
			V1zir.LOGGER.warn("Could not start the media bridge", e);
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

	private static Path copyScript() throws IOException {
		Path path = Files.createTempFile("v1zir-media", ".ps1");
		path.toFile().deleteOnExit();
		try (InputStream in = WindowsMediaSource.class.getResourceAsStream(SCRIPT)) {
			if (in == null) throw new IOException("Missing " + SCRIPT);
			Files.write(path, in.readAllBytes());
		}
		return path;
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
		publish(null, "", 0L, false);
		restart(reported);
	}

	private boolean handle(String line) {
		try {
			JsonObject o = JsonParser.parseString(line).getAsJsonObject();
			String title = MacMediaSource.text(o, "t");
			if (o.has("none") || title.isEmpty()) {
				publish(null, "", 0L, false);
			} else {
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
		if (failures >= MAX_FAILURES) unavailable("Windows media controls are unavailable");
		try {
			worker.schedule(this::start, failures >= MAX_FAILURES ? 60L : 3L, TimeUnit.SECONDS);
		} catch (RejectedExecutionException ignored) {
			// Stopped.
		}
	}

	@Override
	synchronized void command(Media.Command command) {
		if (input == null) return;
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
