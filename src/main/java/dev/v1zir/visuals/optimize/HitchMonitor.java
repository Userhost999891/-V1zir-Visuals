package dev.v1zir.visuals.optimize;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.hud.Notifications;
import dev.v1zir.visuals.render.font.Icons;
import net.minecraft.client.MinecraftClient;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Locale;

/**
 * Finds what is behind sudden drops of the frame rate (hitches): a frame that takes far longer than those around it.
 * The client's own work is timed in a few parts each frame (its tick, the world's effects, the post effects, the
 * HUD), and Java's garbage collection reports its pauses. A hitch is written to the log (latest.log) with the time of
 * each part in that frame and any pause of the collector that fell in it, at most once every few seconds, and once a
 * minute a summary. When hitches come again and again, a notification says once what is behind them: the collector
 * (give the game more memory), the client's own work (which part), or the game itself (chunks, the server).
 * Render thread only, but for the collector's reports.
 */
public final class HitchMonitor {
	public static final int TICK = 0;
	public static final int WORLD = 1;
	public static final int POST = 2;
	public static final int HUD = 3;
	private static final String[] PARTS = {"client tick", "world effects", "post effects", "HUD"};
	/** A frame is a hitch from this long, and when it takes this many times the usual. */
	private static final long MIN_HITCH_NANOS = 50_000_000L;
	private static final float HITCH_FACTOR = 3f;
	private static final long LOG_EVERY_NANOS = 5_000_000_000L;
	private static final long SUMMARY_NANOS = 60_000_000_000L;
	/** The type of the collector's reports (com.sun.management), read through the plain management interfaces. */
	private static final String GC_NOTIFICATION = "com.sun.management.gc.notification";
	/** Collector pauses this long count as stutter. */
	private static final long LONG_PAUSE_MS = 60L;

	private static final long[] spent = new long[PARTS.length];
	private static long lastFrame;
	private static float usual = 16_000_000f;
	private static long lastLogged;
	private static long windowStart;
	private static int hitches;
	private static long worst;
	private static volatile long pauseNanos;
	private static volatile long pauseWorstMs;
	private static volatile int longPauses;
	private static volatile String lastPause = "";
	private static boolean warned;
	/** In this minute's hitches: how much of their time was the client's own work, and which part the most. */
	private static long hitchTime;
	private static long hitchOurs;
	private static final long[] hitchParts = new long[PARTS.length];
	private static boolean started;

	private HitchMonitor() {
	}

	/** Listens to the collector's pauses (once, at start). */
	public static void start() {
		if (started) return;
		started = true;
		try {
			for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
				if (!(bean instanceof NotificationEmitter emitter)) continue;
				emitter.addNotificationListener((notification, handback) -> {
					if (!GC_NOTIFICATION.equals(notification.getType()) || !(notification.getUserData() instanceof CompositeData data)) return;
					String name = String.valueOf(data.get("gcName"));
					// Concurrent collectors report whole cycles, most of which run beside the game: only pauses count.
					if (name.contains("Concurrent") || name.contains("Cycles")) return;
					if (!(data.get("gcInfo") instanceof CompositeData gc) || !(gc.get("duration") instanceof Long duration)) return;
					long ms = duration;
					pauseNanos += ms * 1_000_000L;
					if (ms > pauseWorstMs) pauseWorstMs = ms;
					if (ms >= LONG_PAUSE_MS) {
						longPauses++;
						Runtime runtime = Runtime.getRuntime();
						lastPause = String.format(Locale.ROOT, "%s %d ms, heap %d of %d MB", name, ms,
							(runtime.totalMemory() - runtime.freeMemory()) >> 20, runtime.maxMemory() >> 20);
						V1zir.LOGGER.info("Hitch: garbage collection paused the game ({})", lastPause);
					}
				}, null, null);
			}
		} catch (Throwable e) {
			// A Java without the management extensions: frames are still timed.
			V1zir.LOGGER.debug("Garbage collection pauses are not reported here", e);
		}
	}

	public static long begin() {
		return System.nanoTime();
	}

	/** Adds the time since begin to a part of this frame. */
	public static void end(int part, long begun) {
		spent[part] += System.nanoTime() - begun;
	}

	/** Once per finished frame (Optimizations.endFrame). */
	public static void endFrame() {
		long now = System.nanoTime();
		long frame = lastFrame == 0L ? 0L : now - lastFrame;
		lastFrame = now;
		if (windowStart == 0L) windowStart = now;
		MinecraftClient client = MinecraftClient.getInstance();
		// Loading, a paused or hidden window and the frames around them say nothing about smoothness.
		boolean counts = frame > 0L && client.getOverlay() == null && client.isWindowFocused() && client.world != null;
		if (counts) {
			if (frame > MIN_HITCH_NANOS && frame > usual * HITCH_FACTOR) hitch(now, frame);
			else usual += (Math.min(frame, MIN_HITCH_NANOS) - usual) * 0.05f;
		}
		for (int i = 0; i < spent.length; i++) spent[i] = 0L;
		pauseNanos = 0L;
		if (now - windowStart >= SUMMARY_NANOS) {
			if (hitches > 0) {
				V1zir.LOGGER.info("Hitches in the last minute: {}, the worst {} ms; usual frame {} ms; collector pauses of {} ms or more: {} (worst {} ms)",
					hitches, worst / 1_000_000L, String.format(Locale.ROOT, "%.1f", usual / 1.0e6f), LONG_PAUSE_MS, longPauses, pauseWorstMs);
			}
			// Not in the game test (software rendering there hitches all the time; its log has the lines anyway).
			if (hitches >= 5 && !warned && !"loopback".equals(System.getProperty("v1zir.messages.relay"))) {
				warned = true;
				Notifications.push(Icons.CPU, "Lag spikes: " + hitches + " in a minute", cause(), false);
				V1zir.LOGGER.info("Hitches: {}", cause());
			}
			windowStart = now;
			hitchTime = 0L;
			hitchOurs = 0L;
			for (int i = 0; i < hitchParts.length; i++) hitchParts[i] = 0L;
			hitches = 0;
			worst = 0L;
			longPauses = 0;
			pauseWorstMs = 0L;
		}
	}

	/** What the hitches of this minute were mostly made of, in words for the player. */
	private static String cause() {
		if (longPauses >= 2) return "Java stops to free memory: give Minecraft more RAM (4-6 GB) in the launcher";
		if (hitchTime > 0L && hitchOurs * 3L > hitchTime) {
			int most = 0;
			for (int i = 1; i < hitchParts.length; i++) if (hitchParts[i] > hitchParts[most]) most = i;
			return "V1zir's " + PARTS[most] + " (turn off heavy modules there)";
		}
		return "Minecraft itself (chunks loading, the server, other mods), not V1zir";
	}

	private static void hitch(long now, long frame) {
		hitches++;
		worst = Math.max(worst, frame);
		hitchTime += frame;
		for (int i = 0; i < spent.length; i++) {
			hitchOurs += spent[i];
			hitchParts[i] += spent[i];
		}
		if (now - lastLogged < LOG_EVERY_NANOS) return;
		lastLogged = now;
		StringBuilder parts = new StringBuilder();
		long ours = 0L;
		for (int i = 0; i < spent.length; i++) {
			ours += spent[i];
			if (spent[i] >= 1_000_000L) parts.append(", ").append(PARTS[i]).append(' ').append(spent[i] / 1_000_000L).append(" ms");
		}
		long pause = pauseNanos;
		V1zir.LOGGER.info("Hitch: a frame took {} ms (usually {} ms); V1zir's own work {} ms{}{}", frame / 1_000_000L,
			String.format(Locale.ROOT, "%.1f", usual / 1.0e6f), ours / 1_000_000L, parts, pause > 0L ? "; garbage collection " + pause / 1_000_000L + " ms" : "");
	}
}
