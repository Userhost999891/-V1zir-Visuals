package dev.v1zir.visuals.media;

/** Metadata of one song as the media player reports it; durationMs is 0 when the player does not know it. */
public record Track(String title, String artist, String album, long durationMs) {
	/** Key of the song for the lyrics caches. */
	public String key() {
		return artist + '\n' + title + '\n' + album + '\n' + Math.round(durationMs / 1000.0);
	}
}
