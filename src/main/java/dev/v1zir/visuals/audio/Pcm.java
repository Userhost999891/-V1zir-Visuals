package dev.v1zir.visuals.audio;

/** Decoded audio: 16-bit samples, interleaved when there is more than one channel. */
public record Pcm(short[] samples, int channels, int rate) {
	/** Length in seconds. */
	public float seconds() {
		return samples.length / (float) Math.max(1, channels) / Math.max(1, rate);
	}
}
