package dev.v1zir.visuals.compat.voicechat;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.audio.AlPlayer;
import dev.v1zir.visuals.audio.AudioFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The music Voice Chat Music sends: the files of a playlist decoded one after another on a thread of their own into
 * frames of voice chat audio (48 kHz mono, 20 ms), which Simple Voice Chat takes one by one as it sends the
 * microphone (VoiceChatBridge). So the music runs at the pace voice chat sends at, and only while it is connected.
 * Each frame taken is also played to the player, quieter, when they want to hear it themselves.
 */
public final class MusicFeed {
	/** Simple Voice Chat's audio: 48 kHz mono, 960 samples (20 ms) a frame. */
	public static final int RATE = 48_000;
	public static final int FRAME = 960;
	private static final ArrayBlockingQueue<short[]> FRAMES = new ArrayBlockingQueue<>(25);
	private static final AlPlayer.Stream MONITOR = new AlPlayer.Stream();
	private static final Object LOCK = new Object();
	private static Thread decoder;
	private static volatile boolean running;
	/** Counts the starts, so a decoder left over from an earlier one never stops a later one. */
	private static volatile int generation;
	private static volatile boolean paused;
	private static volatile boolean skip;
	private static volatile float gain = 0.5f;
	private static volatile float monitorGain;
	private static volatile long lastPull;
	private static volatile String playing = "";
	/** A track that began since the module last asked (for its notification), or null. */
	private static volatile String started;

	private MusicFeed() {
	}

	/** Starts playing the files, shuffled or in order, over and over if repeat. */
	public static void start(List<Path> files, boolean shuffle, boolean repeat) {
		stop();
		if (files.isEmpty()) return;
		List<Path> playlist = new ArrayList<>(files);
		synchronized (LOCK) {
			running = true;
			paused = false;
			skip = false;
			int gen = ++generation;
			decoder = new Thread(() -> decode(gen, playlist, shuffle, repeat), "V1zir voice chat music");
			decoder.setDaemon(true);
			decoder.start();
		}
	}

	public static void stop() {
		Thread thread;
		synchronized (LOCK) {
			running = false;
			thread = decoder;
			decoder = null;
		}
		if (thread != null) {
			thread.interrupt();
			try {
				thread.join(500L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		FRAMES.clear();
		MONITOR.stop();
		playing = "";
	}

	public static boolean running() {
		return running;
	}

	public static void next() {
		skip = true;
		FRAMES.clear();
	}

	public static void togglePause() {
		paused = !paused;
	}

	public static boolean paused() {
		return paused;
	}

	/** What others hear (0..1) and what the player hears of it (0 for nothing), the game's master volume included. */
	public static void volumes(float send, float monitor) {
		gain = send;
		monitorGain = monitor;
	}

	/** The track playing now, by its file name without the extension ("" for none). */
	public static String playing() {
		return playing;
	}

	/** A track that began since the last call, or null. */
	public static String takeStarted() {
		String name = started;
		started = null;
		return name;
	}

	/** Whether Simple Voice Chat asked for audio in the last second (it is installed and connected). */
	public static boolean connected() {
		return System.nanoTime() - lastPull < 1_000_000_000L;
	}

	/**
	 * Voice chat sends a frame (every 20 ms, on its own thread): the next frame of music at the volume set, or null
	 * when there is none (paused, between tracks, nothing playing).
	 */
	static short[] pull() {
		lastPull = System.nanoTime();
		if (!running || paused) return null;
		short[] frame = FRAMES.poll();
		if (frame == null) return null;
		float hear = monitorGain;
		if (hear > 0.001f) {
			try {
				MONITOR.queue(frame, RATE, hear);
			} catch (RuntimeException e) {
				// Hearing it is a nicety; the others still get it.
				monitorGain = 0f;
				V1zir.LOGGER.warn("Could not play the voice chat music to you", e);
			}
		}
		float g = gain;
		short[] out = new short[frame.length];
		for (int i = 0; i < frame.length; i++) out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(frame[i] * g)));
		return out;
	}

	private static boolean alive(int gen) {
		return running && generation == gen;
	}

	private static void decode(int gen, List<Path> playlist, boolean shuffle, boolean repeat) {
		int failures = 0;
		do {
			List<Path> order = new ArrayList<>(playlist);
			if (shuffle) Collections.shuffle(order);
			for (Path file : order) {
				if (!alive(gen)) return;
				skip = false;
				String name = file.getFileName().toString();
				int dot = name.lastIndexOf('.');
				playing = dot > 0 ? name.substring(0, dot) : name;
				started = playing;
				try (AudioFiles.MonoStream stream = AudioFiles.open(file, RATE)) {
					failures = 0;
					short[] frame = new short[FRAME];
					int filled = 0;
					while (alive(gen) && !skip) {
						int n = stream.read(frame, filled, FRAME - filled);
						if (n < 0) break;
						filled += n;
						if (filled < FRAME) continue;
						if (!offer(gen, frame)) return;
						frame = new short[FRAME];
						filled = 0;
					}
					if (alive(gen) && !skip && filled > 0 && !offer(gen, frame)) return;
				} catch (IOException | RuntimeException e) {
					V1zir.LOGGER.warn("Could not play {}", file.getFileName(), e);
					if (++failures >= playlist.size()) break;
				}
			}
		} while (alive(gen) && repeat && failures < playlist.size());
		synchronized (LOCK) {
			if (generation == gen) {
				running = false;
				playing = "";
			}
		}
	}

	/** Queues a frame, waiting while the queue is full; false once this playback stopped. */
	private static boolean offer(int gen, short[] frame) {
		try {
			while (alive(gen) && !skip) {
				if (FRAMES.offer(frame, 100L, TimeUnit.MILLISECONDS)) return true;
			}
			return alive(gen);
		} catch (InterruptedException e) {
			return false;
		}
	}
}
