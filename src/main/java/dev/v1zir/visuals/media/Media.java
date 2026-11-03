package dev.v1zir.visuals.media;

import dev.v1zir.visuals.V1zir;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What the system media player is playing (Spotify preferred), with zero configuration.
 *
 * <p>Everything that talks to the player runs on the daemon "V1zir Media" thread and publishes
 * immutable {@link NowPlaying} snapshots; the render thread only reads the latest one. The backend
 * runs while at least one module holds it ({@link #acquire} / {@link #release}) and its helper
 * processes end when the last module lets go or the game exits.
 */
public final class Media {
	public enum Command {
		PLAY_PAUSE, NEXT, PREVIOUS
	}

	/** Reported positions this close to the interpolated one are jitter and do not move the timeline. */
	private static final long JITTER_MS = 300L;

	private static final Set<Object> USERS = new HashSet<>();
	private static volatile NowPlaying current = NowPlaying.NONE;
	private static volatile String status;
	private static volatile MediaSource source;
	private static ScheduledExecutorService worker;
	private static ExecutorService io;
	private static HttpClient http;
	private static boolean hooked;

	private Media() {
	}

	/** Latest snapshot; never null, {@link NowPlaying#NONE} when nothing plays. */
	public static NowPlaying now() {
		return current;
	}

	/** Why there is no information (a missing tool...), or null. */
	public static String status() {
		return status;
	}

	/** Starts the backend for a module (idempotent per user). Never blocks: the helper starts on the media thread. */
	public static synchronized void acquire(Object user) {
		if (!USERS.add(user) || source != null) return;
		if (!hooked) {
			hooked = true;
			Runtime.getRuntime().addShutdownHook(new Thread(Media::stop, "V1zir Media shutdown"));
		}
		worker = Executors.newSingleThreadScheduledExecutor(daemon("V1zir Media"));
		MediaSource backend = MediaSource.forPlatform(System.getProperty("os.name", "").toLowerCase(Locale.ROOT), worker);
		source = backend;
		worker.execute(backend::start);
	}

	/** Releases a module's hold; the last one stops the backend and frees the album art. Render thread. */
	public static synchronized void release(Object user) {
		if (!USERS.remove(user) || !USERS.isEmpty()) return;
		stop();
		AlbumArt.clear();
	}

	private static synchronized void stop() {
		MediaSource backend = source;
		if (backend == null) return;
		source = null;
		backend.stop();
		worker.shutdownNow();
		worker = null;
		current = NowPlaying.NONE;
		status = null;
	}

	/** Sends play/pause, next or previous to the player, off the render thread. */
	public static void command(Command command) {
		MediaSource backend;
		ScheduledExecutorService thread;
		synchronized (Media.class) {
			backend = source;
			thread = worker;
		}
		if (backend == null) return;
		// Flip the play state right away; the next report confirms it.
		NowPlaying now = current;
		if (command == Command.PLAY_PAUSE && now.track() != null) current = now.withPlaying(!now.playing());
		try {
			thread.execute(() -> backend.command(command));
		} catch (RejectedExecutionException ignored) {
			// Stopped in the meantime.
		}
	}

	/** A backend's report. Keeps the track instance (and the smooth timeline) while the song stays the same. */
	static void publish(MediaSource from, Track track, String art, long positionMs, boolean playing) {
		if (from != source) return;
		status = null;
		if (track == null) {
			current = NowPlaying.NONE;
			return;
		}
		NowPlaying prev = current;
		long now = System.nanoTime();
		if (track.equals(prev.track())) {
			track = prev.track();
			if (art.equals(prev.art())) art = prev.art();
			if (playing && prev.playing() && Math.abs(prev.position(now) - positionMs) < JITTER_MS) {
				positionMs = prev.positionMs();
				now = prev.sampledAt();
			}
		}
		current = new NowPlaying(track, art, Math.max(0L, positionMs), playing, now);
	}

	/** A backend cannot run (tool missing, failed to start). */
	static void unavailable(MediaSource from, String reason) {
		if (from != source) return;
		current = NowPlaying.NONE;
		status = reason;
	}

	/** Shared daemon pool for downloads, disk caches and image decoding. */
	static synchronized ExecutorService io() {
		if (io == null) io = Executors.newFixedThreadPool(2, daemon("V1zir Media IO"));
		return io;
	}

	static synchronized HttpClient http() {
		if (http == null) {
			http = HttpClient.newBuilder()
				.executor(io())
				.connectTimeout(Duration.ofSeconds(8))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		}
		return http;
	}

	static String userAgent() {
		return V1zir.NAME.replace(' ', '-') + "/" + V1zir.version() + " (Minecraft mod; now playing widget and synced lyrics)";
	}

	private static ThreadFactory daemon(String name) {
		AtomicInteger count = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, count.getAndIncrement() == 0 ? name : name + " " + count.get());
			thread.setDaemon(true);
			return thread;
		};
	}
}
