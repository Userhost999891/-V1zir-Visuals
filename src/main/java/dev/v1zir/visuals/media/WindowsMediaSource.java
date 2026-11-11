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
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Windows 10/11: one long-lived Windows PowerShell (no window, multi-threaded apartment) running now_playing.ps1,
 * copied into the game's config folder, which reads the system media transport controls through WinRT. It prints a
 * JSON line about every 300 ms and takes commands on stdin; closing stdin ends it.
 * <p>
 * When the bridge cannot run (blocked by an antivirus or a script policy, missing) or reports nothing while Spotify
 * plays, Spotify's own window title takes over ({@link SpotifyWindow}), so the song and lyrics still show. What went
 * wrong with the bridge is written to the game log.
 * <p>
 * The media controls can go stale: they keep reporting a song that changed by itself (Spotify's next song) until
 * playback is paused or resumed. Two checks catch that. Spotify's window title: when it names another playing song
 * than the bridge for a moment, the window's song shows, the bridge is asked to look again and, if that does not
 * help, started afresh (a new process sees what the players report now). And the bridge's own timeline: a song that
 * keeps playing past its end, or whose timeline moves on to another song under the same title, starts it afresh too.
 */
final class WindowsMediaSource extends MediaSource {
	private static final String SCRIPT = "/assets/" + V1zir.MOD_ID + "/media/now_playing.ps1";
	/** Starts in a row that ended without a single report before the bridge counts as unavailable. */
	private static final int MAX_FAILURES = 3;
	/** The window fallback speaks when the bridge has not reported a song for this long. */
	private static final long BRIDGE_QUIET_NANOS = 2_500_000_000L;
	/** The window naming another song than the bridge for this long means the bridge is stale. */
	private static final long STALE_NANOS = 1_200_000_000L;
	/** Still stale this long after it was asked to refresh, the bridge is started afresh. */
	private static final long RENEW_AFTER_NANOS = 2_500_000_000L;
	/** A song reported playing at its very end for this long has really ended: the report is stale. */
	private static final long AT_END_NANOS = 2_500_000_000L;
	/** The bridge is started afresh at most this often, and at most MAX_RENEWS times for one stale song. */
	private static final long RENEW_GAP_NANOS = 8_000_000_000L;
	private static final int MAX_RENEWS = 2;

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
	/** The song the bridge reported last, and the one the window names while it stands in for a stale bridge. */
	private volatile Track bridgeTrack;
	private volatile Track windowTrack;
	/** Window thread: since when the window names another song, and whether the bridge was asked to look again. */
	private long mismatchSince;
	private boolean refreshed;
	/** Reader thread: the song and timeline of the bridge's last report, and since when it plays at its very end. */
	private String lastSong = "";
	private long lastPosition;
	private long lastDuration;
	private long atEndSince;
	/** When the bridge was last started afresh, for which stale song and how many times. */
	private long lastRenew;
	private String renewedFor = "";
	private int renews;
	private boolean windowBroken;
	private boolean loggedFailure;
	private boolean loggedStale;
	private boolean loggedRenew;
	/** For the debug lines: the bridge's last report and how often it was started afresh. */
	private volatile String bridgeInfo = "no report yet";
	private volatile int renewCount;

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
			// A multi-threaded apartment: the media controls' notifications reach the bridge without a message loop.
			started = new ProcessBuilder(powershell(), "-NoLogo", "-NoProfile", "-NonInteractive", "-MTA",
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

	/** Spotify's window title stands in while the bridge is quiet, down or stale. */
	private void pollWindow() {
		if (stopped || window == null) return;
		try {
			NowPlaying state = window.poll();
			long now = System.nanoTime();
			try {
				if (now - bridgeSongAt < BRIDGE_QUIET_NANOS) checkBridge(state, now);
				else speakForQuietBridge(state);
			} finally {
				updateDebug();
			}
		} catch (Throwable e) {
			// JNA missing or refused: stop trying, the bridge still works if it can.
			window = null;
			windowBroken = true;
			V1zir.LOGGER.warn("Spotify window fallback failed: {}", e.toString());
		}
	}

	/**
	 * The bridge speaks; the window checks it. A playing song in the window that the bridge does not name for a moment
	 * means the media controls missed a change: the window's song shows until they catch up.
	 */
	private void checkBridge(NowPlaying state, long now) {
		Track bridge = bridgeTrack;
		boolean stale = state.track() != null && state.playing() && bridge != null && !sameSong(bridge, state.track());
		if (!stale) {
			mismatchSince = 0L;
			refreshed = false;
			windowSpeaking = false;
			windowTrack = null;
			return;
		}
		if (mismatchSince == 0L) mismatchSince = now;
		long behind = now - mismatchSince;
		if (behind < STALE_NANOS) return;
		if (!refreshed) {
			refreshed = true;
			if (!loggedStale) {
				loggedStale = true;
				V1zir.LOGGER.info("Media controls report \"{}\" while Spotify plays \"{}\": showing Spotify's song and refreshing them",
					bridge.title(), state.track().title());
			}
			send("refresh\n");
		} else if (behind > STALE_NANOS + RENEW_AFTER_NANOS) {
			renew(bridge.artist() + '\n' + bridge.title(), "Spotify's window names another song");
		}
		windowSpeaking = true;
		windowTrack = state.track();
		publish(state.track(), state.art(), state.positionMs(), state.playing());
	}

	/** The bridge is quiet (down, blocked or nothing in the media controls): the window speaks for it. */
	private void speakForQuietBridge(NowPlaying state) {
		windowTrack = null;
		if (state.track() != null || windowSpeaking) {
			windowSpeaking = state.track() != null;
			publish(state.track(), state.art(), state.positionMs(), state.playing());
		}
	}

	/** The debug lines: where the shown song comes from and what each source says. */
	private void updateDebug() {
		boolean bridgeSpeaks = System.nanoTime() - bridgeSongAt < BRIDGE_QUIET_NANOS;
		String from = windowSpeaking ? bridgeSpeaks ? "Spotify window (media controls lag behind)" : "Spotify window (media controls quiet)"
			: bridgeSpeaks ? "Windows media controls" : "nothing";
		SpotifyWindow w = window;
		String windowLine = w != null ? w.describe() : windowBroken ? "unavailable" : "starting";
		debug("Shown from: " + from + "\nMedia controls: " + bridgeInfo + "\nSpotify window: " + windowLine
			+ "\nBridge restarts: " + renewCount);
	}

	/** Reader thread: one report per line until the process ends (or is replaced by a fresh one). */
	private void read(Process from) {
		boolean reported = false;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(from.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				// A bridge that was replaced still has stale reports buffered: they are dropped.
				if (!running(from)) break;
				reported |= handle(line);
			}
		} catch (IOException ignored) {
			// The process ended.
		}
		if (stopped || !running(from)) return;
		if (!windowSpeaking) publish(null, "", 0L, false);
		if (!reported) logFailure(from);
		restart(reported);
	}

	private synchronized boolean running(Process from) {
		return from == process;
	}

	/**
	 * Replaces the bridge with a fresh one, whose media controls see what the players report now; the old one gets its
	 * stdin closed (it deletes its cover file and exits) and is ended if it lingers. Not more often than every few
	 * seconds, nor more than twice for one stale song (when the media controls themselves are stale, a new bridge sees
	 * the same, and the window's song shows instead).
	 */
	private void renew(String song, String why) {
		Process old;
		Writer oldInput;
		synchronized (this) {
			long now = System.nanoTime();
			if (stopped || process == null || now - lastRenew < RENEW_GAP_NANOS) return;
			if (!song.equals(renewedFor)) {
				renewedFor = song;
				renews = 0;
			}
			if (renews >= MAX_RENEWS) return;
			renews++;
			lastRenew = now;
			old = process;
			oldInput = input;
			process = null;
			input = null;
		}
		if (!loggedRenew) {
			loggedRenew = true;
			renewCount++;
		V1zir.LOGGER.info("Media bridge went stale ({}): starting it again", why);
		} else {
			V1zir.LOGGER.debug("Media bridge went stale ({}): starting it again", why);
		}
		try {
			oldInput.close();
		} catch (IOException ignored) {
			// Already gone.
		}
		old.onExit().completeOnTimeout(old, 2, TimeUnit.SECONDS).thenAccept(Process::destroy);
		try {
			worker.execute(this::start);
		} catch (RejectedExecutionException ignored) {
			// Stopped.
		}
	}

	/**
	 * Reader thread: whether the report's timeline gives the media controls away as stale. A song reported playing at
	 * its very end for a few seconds has ended (the next one plays unreported); the same title with another length, or
	 * back at its start from well into it, means the timeline moved on to the next song under the old title.
	 */
	private boolean timelineStale(String song, long position, long duration, boolean playing) {
		long now = System.nanoTime();
		boolean same = song.equals(lastSong);
		boolean moved = same && playing
			&& (lastDuration > 0L && duration > 0L && Math.abs(duration - lastDuration) > 2_000L || lastPosition > 10_000L && position < 3_000L);
		lastSong = song;
		lastPosition = position;
		lastDuration = duration;
		boolean atEnd = playing && duration > 0L && position >= duration - 250L;
		if (!atEnd) atEndSince = 0L;
		else if (atEndSince == 0L) atEndSince = now;
		return moved || atEndSince != 0L && now - atEndSince > AT_END_NANOS;
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
				bridgeInfo = "nothing playing";
				// Nothing in the media controls; the window fallback may still see Spotify playing.
				if (!windowSpeaking) publish(null, "", 0L, false);
			} else {
				bridgeSongAt = System.nanoTime();
				long position = o.get("p").getAsLong();
				long duration = o.get("d").getAsLong();
				boolean playing = o.get("s").getAsInt() == 1;
				Track track = new Track(title, MacMediaSource.text(o, "a"), MacMediaSource.text(o, "al"), duration);
				bridgeTrack = track;
				int cover = o.has("as") ? o.get("as").getAsInt() : -1;
				bridgeInfo = "\"" + title + "\" by " + track.artist() + (playing ? "" : " (paused)") + ", cover: " + switch (cover) {
					case 0 -> "none from the player";
					case 1 -> "reading";
					case 2 -> "saved";
					case 3 -> "could not read";
					default -> "?";
				};
				String song = track.artist() + '\n' + track.title();
				if (timelineStale(song, position, duration, playing)) renew(song, "its timeline left the song it names");
				// Still the song the window has moved on from: the window keeps speaking until the bridge catches up.
				Track window = windowTrack;
				if (windowSpeaking && window != null && !sameSong(track, window)) return true;
				windowSpeaking = false;
				windowTrack = null;
				publish(track, MacMediaSource.text(o, "art"), position, playing);
			}
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * Whether two reports name the same song: the same title, ignoring case and spacing, or one holding the other (the
	 * window title splits "Artist - Title" at its first dash, so a title with a dash of its own still matches).
	 */
	static boolean sameSong(Track a, Track b) {
		String x = a.title().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
		String y = b.title().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
		if (x.isEmpty() || y.isEmpty()) return true;
		return x.equals(y) || x.contains(y) || y.contains(x);
	}

	/** Writes a line to the bridge, if it runs. */
	private synchronized void send(String line) {
		if (input == null) return;
		try {
			input.write(line);
			input.flush();
		} catch (IOException ignored) {
			// The bridge is restarting.
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
