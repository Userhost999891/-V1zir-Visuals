package dev.v1zir.visuals.audio;

import dev.v1zir.visuals.V1zir;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.system.MemoryUtil;

import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Sounds played straight through OpenAL, in the game's own audio context: the player's own sound files (the game's
 * sound system only plays the sounds of its resource packs) and the voice chat music heard by the player. Buffers are
 * made again if the game restarts its sound system (a new context). One-shot clips go through the client thread; a
 * {@link Stream} may be fed from another thread.
 */
public final class AlPlayer {
	private static final int SOURCES = 6;
	private static long context;
	private static final Map<String, Integer> BUFFERS = new HashMap<>();
	private static final int[] sources = new int[SOURCES];
	private static int next;

	private AlPlayer() {
	}

	/** The game's audio context, 0 when there is none (no sound device, or OpenAL not loaded at all). */
	static long current() {
		try {
			return ALC10.alcGetCurrentContext();
		} catch (RuntimeException | LinkageError e) {
			return 0L;
		}
	}

	/** Whether there is an audio context to play in (none when the game has no sound device). */
	private static boolean ready() {
		long current = current();
		if (current == 0L) return false;
		if (current != context) {
			// A new context: what was made in the old one is gone with it.
			context = current;
			BUFFERS.clear();
			for (int i = 0; i < SOURCES; i++) sources[i] = 0;
		}
		return true;
	}

	/** Plays a clip once, decoded and uploaded the first time its key is played; gain 0..1, pitch 0.5..2. */
	public static void play(String key, ClipLoader loader, float gain, float pitch) {
		if (!ready()) return;
		Integer buffer = BUFFERS.get(key);
		if (buffer == null) {
			try {
				buffer = upload(loader.load());
			} catch (Exception e) {
				V1zir.LOGGER.warn("Could not play {}", key, e);
				buffer = 0;
			}
			BUFFERS.put(key, buffer);
		}
		if (buffer == 0) return;
		int source = source();
		if (source == 0) return;
		AL10.alSourceStop(source);
		AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
		AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0f, gain));
		AL10.alSourcef(source, AL10.AL_PITCH, Math.max(0.5f, Math.min(2f, pitch)));
		AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
		AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
		AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f);
		AL10.alSourcePlay(source);
	}

	/** Forgets a clip (its file changed), so the next play reads it again. */
	public static void forget(String key) {
		Integer buffer = BUFFERS.remove(key);
		if (buffer != null && buffer != 0 && ready()) {
			for (int source : sources) {
				if (source != 0 && AL10.alGetSourcei(source, AL10.AL_BUFFER) == buffer) {
					AL10.alSourceStop(source);
					AL10.alSourcei(source, AL10.AL_BUFFER, 0);
				}
			}
			AL10.alDeleteBuffers(buffer);
		}
	}

	/** A free source of the few kept for clips, else the one used longest ago. */
	private static int source() {
		for (int i = 0; i < SOURCES; i++) {
			if (sources[i] == 0) {
				sources[i] = AL10.alGenSources();
				if (AL10.alGetError() != AL10.AL_NO_ERROR) {
					sources[i] = 0;
					return 0;
				}
				return sources[i];
			}
			if (AL10.alGetSourcei(sources[i], AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) return sources[i];
		}
		next = (next + 1) % SOURCES;
		return sources[next];
	}

	private static int upload(Pcm pcm) {
		if (pcm.samples().length == 0 || pcm.channels() < 1 || pcm.channels() > 2) return 0;
		int buffer = AL10.alGenBuffers();
		ShortBuffer data = MemoryUtil.memAllocShort(pcm.samples().length);
		try {
			data.put(pcm.samples()).flip();
			AL10.alBufferData(buffer, pcm.channels() == 2 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16, data, pcm.rate());
		} finally {
			MemoryUtil.memFree(data);
		}
		return buffer;
	}

	/** Reads a clip when it is first played. */
	@FunctionalInterface
	public interface ClipLoader {
		Pcm load() throws Exception;
	}

	/**
	 * One source playing mono frames queued one after another, as they come: a few are kept queued ahead, more are
	 * dropped, and it starts again by itself after running dry. Safe to feed from any thread.
	 */
	public static final class Stream {
		private static final int AHEAD = 10;
		private long streamContext;
		private int source;
		private final ArrayDeque<Integer> free = new ArrayDeque<>();

		public synchronized void queue(short[] frame, int rate, float gain) {
			long current = current();
			if (current == 0L) return;
			if (current != streamContext) {
				streamContext = current;
				source = 0;
				free.clear();
			}
			if (source == 0) {
				source = AL10.alGenSources();
				AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
				AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
				AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f);
			}
			for (int done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED); done > 0; done--) free.add(AL10.alSourceUnqueueBuffers(source));
			int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
			if (queued >= AHEAD) return;
			int buffer = free.isEmpty() ? AL10.alGenBuffers() : free.poll();
			ShortBuffer data = MemoryUtil.memAllocShort(frame.length);
			try {
				data.put(frame).flip();
				AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, rate);
			} finally {
				MemoryUtil.memFree(data);
			}
			AL10.alSourceQueueBuffers(source, buffer);
			AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0f, gain));
			// Two frames ahead before it starts, so it does not run dry at once.
			if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && queued + 1 >= 2) AL10.alSourcePlay(source);
		}

		/** Stops and frees the source. */
		public synchronized void stop() {
			if (source == 0 || current() != streamContext) {
				source = 0;
				free.clear();
				return;
			}
			AL10.alSourceStop(source);
			for (int done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED); done > 0; done--) free.add(AL10.alSourceUnqueueBuffers(source));
			AL10.alDeleteSources(source);
			for (int buffer : free) AL10.alDeleteBuffers(buffer);
			free.clear();
			source = 0;
		}
	}
}
