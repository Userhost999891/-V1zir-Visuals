package dev.v1zir.visuals.media;

/**
 * Immutable snapshot of the media player, replaced by the media thread on every report. The track
 * instance stays the same while the song does, so readers can detect a new song by identity.
 *
 * @param art        album art: a file path, a file:// or http(s) URL, or "" when there is none
 * @param positionMs playback position at sampledAt (System.nanoTime)
 */
public record NowPlaying(Track track, String art, long positionMs, boolean playing, long sampledAt) {
	public static final NowPlaying NONE = new NowPlaying(null, "", 0L, false, 0L);

	/** Current position in milliseconds: the last report moved on by the time since, while playing. */
	public long position() {
		return position(System.nanoTime());
	}

	long position(long nanos) {
		if (!playing || track == null) return positionMs;
		long pos = positionMs + (nanos - sampledAt) / 1_000_000L;
		return track.durationMs() > 0 ? Math.min(pos, track.durationMs()) : pos;
	}

	/** Played fraction of the song, 0 when its length is unknown. */
	public float progress() {
		if (track == null || track.durationMs() <= 0) return 0f;
		return Math.max(0f, Math.min(1f, position() / (float) track.durationMs()));
	}

	NowPlaying withPlaying(boolean playing) {
		return new NowPlaying(track, art, position(), playing, System.nanoTime());
	}
}
