package dev.v1zir.visuals.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Sound files the player brings: Ogg Vorbis (stb_vorbis, as the game itself reads them), MP3 (JLayer) and WAV, AIFF or
 * AU (Java's own audio). They are decoded whole for short sounds, or streamed as mono at a fixed rate for music.
 */
public final class AudioFiles {
	private static final String[] EXTENSIONS = {".ogg", ".mp3", ".wav", ".aiff", ".aif", ".au"};
	/** Files larger than this are not read whole (a hit sound is a few kilobytes). */
	private static final long MAX_CLIP = 8L << 20;

	private AudioFiles() {
	}

	public static boolean supported(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		for (String extension : EXTENSIONS) if (name.endsWith(extension)) return true;
		return false;
	}

	/** The playable files in a folder, by name (none when it is missing). */
	public static List<Path> list(Path folder) {
		List<Path> files = new ArrayList<>();
		if (!Files.isDirectory(folder)) return files;
		try (Stream<Path> entries = Files.list(folder)) {
			entries.filter(f -> Files.isRegularFile(f) && supported(f)).sorted().forEach(files::add);
		} catch (IOException | RuntimeException ignored) {
			// Nothing to list.
		}
		return files;
	}

	/** Decodes a whole file. */
	public static Pcm decode(Path file) throws IOException {
		if (Files.size(file) > MAX_CLIP) throw new IOException(file.getFileName() + " is too long for a sound effect");
		return decode(Files.readAllBytes(file), file.getFileName().toString());
	}

	/** Decodes a whole file read into memory; name tells the format by its extension. */
	public static Pcm decode(byte[] bytes, String name) throws IOException {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".ogg")) {
			// Through the same stream as music, into buffers of its own: what stb_vorbis allocates for a whole-file
			// decode belongs to LWJGL's allocator, and freeing it any other way crashes the game.
			try (OggSource source = new OggSource(bytes)) {
				return whole(source, bytes.length * 4);
			}
		}
		if (lower.endsWith(".mp3")) {
			try (Mp3Source source = new Mp3Source(new ByteArrayInputStream(bytes))) {
				return whole(source, bytes.length * 12);
			}
		}
		return sampled(new ByteArrayInputStream(bytes));
	}

	/** All of a source's frames; guess is roughly how many samples to expect. */
	private static Pcm whole(Source source, int guess) throws IOException {
		int channels = source.channels();
		short[] chunk = new short[4096 * channels];
		short[] samples = new short[Math.max(4096, Math.min(guess, 1 << 24))];
		int length = 0;
		for (int frames; (frames = source.frames(chunk)) > 0; ) {
			int n = frames * channels;
			if (length + n > samples.length) samples = Arrays.copyOf(samples, Math.max(samples.length * 2, length + n));
			System.arraycopy(chunk, 0, samples, length, n);
			length += n;
		}
		return new Pcm(Arrays.copyOf(samples, length), channels, source.rate());
	}

	private static Pcm sampled(InputStream in) throws IOException {
		try (AudioInputStream pcm = pcm16(AudioSystem.getAudioInputStream(new BufferedInputStream(in)))) {
			AudioFormat format = pcm.getFormat();
			byte[] bytes = pcm.readAllBytes();
			short[] samples = new short[bytes.length / 2];
			ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
			return new Pcm(samples, format.getChannels(), Math.round(format.getSampleRate()));
		} catch (UnsupportedAudioFileException | IllegalArgumentException e) {
			throw new IOException("Unsupported audio file", e);
		}
	}

	/** The stream as signed 16-bit little endian samples, converted when it is anything else. */
	private static AudioInputStream pcm16(AudioInputStream source) {
		AudioFormat in = source.getFormat();
		AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, in.getSampleRate(), 16, in.getChannels(), in.getChannels() * 2,
			in.getSampleRate(), false);
		return in.matches(target) ? source : AudioSystem.getAudioInputStream(target, source);
	}

	// ------------------------------------------------------------------ streaming

	/** Audio read in order as mono at a fixed rate, whatever the file's own. */
	public interface MonoStream extends Closeable {
		/** Fills out from offset with up to count samples; returns how many, or -1 at the end. */
		int read(short[] out, int offset, int count) throws IOException;

		@Override
		void close();
	}

	/** Opens a file to stream it as mono at rate samples a second. */
	public static MonoStream open(Path file, int rate) throws IOException {
		Source source;
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".ogg")) {
			source = new OggSource(Files.readAllBytes(file));
		} else if (name.endsWith(".mp3")) {
			source = new Mp3Source(new BufferedInputStream(Files.newInputStream(file), 1 << 16));
		} else {
			try {
				source = new SampledSource(pcm16(AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(file)))));
			} catch (UnsupportedAudioFileException | IllegalArgumentException e) {
				throw new IOException("Unsupported audio file " + file.getFileName(), e);
			}
		}
		return new Resampled(source, rate);
	}

	/** Frames of a file in its own format. */
	private interface Source extends Closeable {
		int channels();

		int rate();

		/** Reads interleaved frames into out (room for 4096 frames); returns how many, 0 at the end. */
		int frames(short[] out) throws IOException;

		@Override
		void close();
	}

	private static final class OggSource implements Source {
		private final ByteBuffer data;
		private final long handle;
		private final int channels;
		private final int rate;
		private final ShortBuffer chunk;

		OggSource(byte[] bytes) throws IOException {
			data = MemoryUtil.memAlloc(bytes.length);
			data.put(bytes).flip();
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer error = stack.mallocInt(1);
				handle = STBVorbis.stb_vorbis_open_memory(data, error, null);
				if (handle == 0L) {
					MemoryUtil.memFree(data);
					throw new IOException("Not an Ogg Vorbis file (error " + error.get(0) + ")");
				}
				STBVorbisInfo info = STBVorbisInfo.malloc(stack);
				STBVorbis.stb_vorbis_get_info(handle, info);
				channels = Math.max(1, info.channels());
				rate = info.sample_rate();
			}
			chunk = MemoryUtil.memAllocShort(4096 * channels);
		}

		@Override
		public int channels() {
			return channels;
		}

		@Override
		public int rate() {
			return rate;
		}

		@Override
		public int frames(short[] out) {
			chunk.clear();
			int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, chunk);
			int n = Math.min(frames * channels, out.length);
			for (int i = 0; i < n; i++) out[i] = chunk.get(i);
			return n / channels;
		}

		@Override
		public void close() {
			STBVorbis.stb_vorbis_close(handle);
			MemoryUtil.memFree(data);
			MemoryUtil.memFree(chunk);
		}
	}

	/**
	 * MPEG audio (MP3) through JLayer, a frame at a time. The channel count is the first frame's: a later frame with
	 * another count is mixed down or spread to it, and frames that do not decode are skipped.
	 */
	private static final class Mp3Source implements Source {
		/** Frames in a row that may fail to decode before the file is taken as broken. */
		private static final int MAX_BAD_FRAMES = 64;
		private final InputStream in;
		private final Bitstream bitstream;
		private final Decoder decoder = new Decoder();
		private final int channels;
		private final int rate;
		private short[] pending = new short[2304];
		private int pendingLength;
		private int pendingOffset;
		private boolean ended;

		Mp3Source(InputStream in) throws IOException {
			this.in = in;
			this.bitstream = new Bitstream(in);
			SampleBuffer first = next();
			if (first == null) {
				close();
				throw new IOException("Not an MP3 file");
			}
			channels = Math.max(1, Math.min(2, first.getChannelCount()));
			rate = Math.max(8000, first.getSampleFrequency());
			take(first);
		}

		@Override
		public int channels() {
			return channels;
		}

		@Override
		public int rate() {
			return rate;
		}

		/** The next frame that decodes, null at the end. */
		private SampleBuffer next() throws IOException {
			if (ended) return null;
			try {
				for (int bad = 0; bad < MAX_BAD_FRAMES; bad++) {
					Header header = bitstream.readFrame();
					if (header == null) break;
					try {
						return (SampleBuffer) decoder.decodeFrame(header, bitstream);
					} catch (DecoderException | ArrayIndexOutOfBoundsException e) {
						// A damaged frame: on to the next one.
					} finally {
						bitstream.closeFrame();
					}
				}
			} catch (BitstreamException e) {
				if (pendingLength == 0 && pendingOffset == 0) throw new IOException("Broken MP3 file", e);
				// A damaged end of an otherwise good file: what was read plays.
			}
			ended = true;
			return null;
		}

		/** Keeps a decoded frame (its buffer is the decoder's own, used again for the next one) in this source's channels. */
		private void take(SampleBuffer frame) {
			short[] samples = frame.getBuffer();
			int length = Math.min(frame.getBufferLength(), samples.length);
			int own = Math.max(1, frame.getChannelCount());
			int count = length / own;
			if (pending.length < count * channels) pending = new short[count * channels];
			if (own == channels) {
				System.arraycopy(samples, 0, pending, 0, count * channels);
			} else if (channels == 1) {
				for (int i = 0; i < count; i++) pending[i] = (short) ((samples[i * own] + samples[i * own + 1]) / 2);
			} else {
				for (int i = 0; i < count; i++) pending[i * 2] = pending[i * 2 + 1] = samples[i * own];
			}
			pendingLength = count * channels;
			pendingOffset = 0;
		}

		@Override
		public int frames(short[] out) throws IOException {
			int capacity = out.length / channels * channels;
			int written = 0;
			while (written < capacity) {
				if (pendingOffset >= pendingLength) {
					SampleBuffer frame = next();
					if (frame == null) break;
					take(frame);
					continue;
				}
				int n = Math.min(pendingLength - pendingOffset, capacity - written);
				System.arraycopy(pending, pendingOffset, out, written, n);
				pendingOffset += n;
				written += n;
			}
			return written / channels;
		}

		@Override
		public void close() {
			try {
				bitstream.close();
			} catch (BitstreamException ignored) {
				// Closing anyway.
			}
			try {
				in.close();
			} catch (IOException ignored) {
				// Closing anyway.
			}
		}
	}

	private static final class SampledSource implements Source {
		private final AudioInputStream in;
		private final int channels;
		private final int rate;
		private final byte[] bytes;

		SampledSource(AudioInputStream in) {
			this.in = in;
			this.channels = Math.max(1, in.getFormat().getChannels());
			this.rate = Math.round(in.getFormat().getSampleRate());
			this.bytes = new byte[4096 * channels * 2];
		}

		@Override
		public int channels() {
			return channels;
		}

		@Override
		public int rate() {
			return rate;
		}

		@Override
		public int frames(short[] out) throws IOException {
			int frameBytes = channels * 2;
			int read = in.readNBytes(bytes, 0, Math.min(bytes.length, out.length * 2) / frameBytes * frameBytes);
			int samples = read / 2;
			for (int i = 0; i < samples; i++) out[i] = (short) (bytes[i * 2] & 0xFF | bytes[i * 2 + 1] << 8);
			return samples / channels;
		}

		@Override
		public void close() {
			try {
				in.close();
			} catch (IOException ignored) {
				// Closing anyway.
			}
		}
	}

	/** Mixes the source down to mono and resamples it linearly to the rate asked for. */
	private static final class Resampled implements MonoStream {
		private final Source source;
		private final double step;
		private final short[] chunk;
		private float[] mono = new float[8192];
		private int length;
		private double position;
		private boolean ended;

		Resampled(Source source, int rate) {
			this.source = source;
			this.step = source.rate() / (double) rate;
			this.chunk = new short[4096 * source.channels()];
		}

		@Override
		public int read(short[] out, int offset, int count) throws IOException {
			int written = 0;
			while (written < count) {
				int i = (int) position;
				if (i + 1 >= length) {
					if (ended || !refill()) break;
					continue;
				}
				double frac = position - i;
				float v = (float) (mono[i] * (1.0 - frac) + mono[i + 1] * frac);
				out[offset + written++] = (short) Math.max(-32768, Math.min(32767, Math.round(v)));
				position += step;
			}
			return written == 0 && ended ? -1 : written;
		}

		/** Keeps what is still ahead and appends the next chunk of the source; false at its end. */
		private boolean refill() throws IOException {
			int keep = Math.min((int) position, length);
			System.arraycopy(mono, keep, mono, 0, length - keep);
			length -= keep;
			position -= keep;
			int frames = source.frames(chunk);
			if (frames <= 0) {
				ended = true;
				return false;
			}
			if (length + frames > mono.length) {
				float[] bigger = new float[Math.max(mono.length * 2, length + frames)];
				System.arraycopy(mono, 0, bigger, 0, length);
				mono = bigger;
			}
			int channels = source.channels();
			for (int f = 0; f < frames; f++) {
				float sum = 0f;
				for (int c = 0; c < channels; c++) sum += chunk[f * channels + c];
				mono[length++] = sum / channels;
			}
			return true;
		}

		@Override
		public void close() {
			source.close();
		}
	}
}
