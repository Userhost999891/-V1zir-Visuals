package dev.v1zir.visuals.media;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lyrics of one song. Synced lyrics have a start time per line; plain-only lyrics have no times
 * and are spread over the song. An empty line inside synced lyrics is an instrumental break.
 */
public final class Lyrics {
	public enum State {
		/** Still being looked up. */
		LOADING,
		/** Has lines (check {@link #synced()}). */
		FOUND,
		/** The song is marked instrumental. */
		INSTRUMENTAL,
		/** LRCLIB has nothing for the song. */
		MISSING,
		/** The lookup failed (offline...). */
		ERROR
	}

	public static final Lyrics LOADING = new Lyrics(State.LOADING, new String[0], null);
	public static final Lyrics INSTRUMENTAL = new Lyrics(State.INSTRUMENTAL, new String[0], null);
	public static final Lyrics MISSING = new Lyrics(State.MISSING, new String[0], null);
	public static final Lyrics ERROR = new Lyrics(State.ERROR, new String[0], null);

	private final State state;
	private final String[] lines;
	private final long[] times;

	private Lyrics(State state, String[] lines, long[] times) {
		this.state = state;
		this.lines = lines;
		this.times = times;
	}

	public State state() {
		return state;
	}

	public boolean hasLines() {
		return lines.length > 0;
	}

	public boolean synced() {
		return times != null;
	}

	public int size() {
		return lines.length;
	}

	public String line(int index) {
		return lines[index];
	}

	/** Start of a line in milliseconds (plain lyrics: spread evenly over the song). */
	public long time(int index, long durationMs) {
		if (times != null) return times[index];
		return durationMs * index / Math.max(1, lines.length);
	}

	/**
	 * End of what is sung on a line: the next line's start, cut short before a long gap so a
	 * line does not hang around through an instrumental part.
	 */
	public long end(int index, long durationMs) {
		long start = time(index, durationMs);
		long next = index + 1 < lines.length ? time(index + 1, durationMs) : Math.max(durationMs, start + 4000L);
		long sung = 2500L + lines[index].length() * 90L;
		return Math.min(next, start + Math.max(sung, 3500L));
	}

	/** Index of the line playing at the position, -1 before the first one. */
	public int index(long positionMs, long durationMs) {
		if (lines.length == 0) return -1;
		if (times == null) {
			if (durationMs <= 0) return 0;
			return (int) Math.max(0, Math.min(lines.length - 1, positionMs * lines.length / durationMs));
		}
		int i = Arrays.binarySearch(times, positionMs);
		if (i < 0) i = -i - 2;
		// Equal times (several stamps on one moment) resolve to the last of them.
		while (i >= 0 && i + 1 < times.length && times[i + 1] <= positionMs) i++;
		return i;
	}

	/** Builds lyrics from LRCLIB's fields: synced LRC text first, plain text as the fallback. */
	static Lyrics of(String synced, String plain, boolean instrumental) {
		if (synced != null && !synced.isBlank()) {
			Lyrics lyrics = parseLrc(synced);
			if (lyrics != null) return lyrics;
		}
		if (plain != null && !plain.isBlank()) {
			List<String> lines = new ArrayList<>();
			for (String line : plain.split("\\R")) {
				String text = line.strip();
				if (!text.isEmpty()) lines.add(text);
			}
			if (!lines.isEmpty()) return new Lyrics(State.FOUND, lines.toArray(new String[0]), null);
		}
		return instrumental ? INSTRUMENTAL : MISSING;
	}

	/**
	 * Parses LRC: "[mm:ss.xx]text" lines, several stamps per line allowed, metadata tags ignored
	 * except [offset:ms]; word stamps of enhanced LRC ("&lt;mm:ss.xx&gt;") are removed.
	 */
	static Lyrics parseLrc(String lrc) {
		List<long[]> stamps = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		long offset = 0;
		for (String raw : lrc.split("\\R")) {
			String line = raw.strip();
			int first = stamps.size();
			int pos = 0;
			while (pos < line.length() && line.charAt(pos) == '[') {
				int close = line.indexOf(']', pos);
				if (close < 0) break;
				String tag = line.substring(pos + 1, close);
				long time = parseTime(tag);
				if (time >= 0) {
					stamps.add(new long[]{time, texts.size()});
				} else if (tag.startsWith("offset:")) {
					try {
						offset = Long.parseLong(tag.substring(7).strip().replace("+", ""));
					} catch (NumberFormatException ignored) {
						// Not a number: no offset.
					}
				}
				pos = close + 1;
			}
			if (stamps.size() > first) texts.add(line.substring(pos).replaceAll("<\\d+:\\d+(?:[.:]\\d+)?>", "").strip());
		}
		if (stamps.isEmpty()) return null;
		stamps.sort((a, b) -> Long.compare(a[0], b[0]));
		String[] lines = new String[stamps.size()];
		long[] times = new long[stamps.size()];
		for (int i = 0; i < lines.length; i++) {
			long[] stamp = stamps.get(i);
			// A positive offset shows the lyrics earlier.
			times[i] = Math.max(0L, stamp[0] - offset);
			lines[i] = texts.get((int) stamp[1]);
		}
		// A trailing empty stamp only marks the end of the song.
		int count = lines.length;
		while (count > 0 && lines[count - 1].isEmpty()) count--;
		if (count == 0) return null;
		return new Lyrics(State.FOUND, Arrays.copyOf(lines, count), Arrays.copyOf(times, count));
	}

	/** "mm:ss", "mm:ss.xx" or "mm:ss:xx" in milliseconds, -1 when the tag is not a time. */
	private static long parseTime(String tag) {
		int colon = tag.indexOf(':');
		if (colon <= 0) return -1;
		try {
			long minutes = Long.parseLong(tag.substring(0, colon));
			String rest = tag.substring(colon + 1);
			int dot = Math.max(rest.indexOf('.'), rest.indexOf(':'));
			long seconds = Long.parseLong(dot < 0 ? rest : rest.substring(0, dot));
			long millis = 0;
			if (dot >= 0) {
				String fraction = rest.substring(dot + 1);
				if (fraction.isEmpty() || fraction.length() > 3) return -1;
				millis = Long.parseLong(fraction);
				for (int i = fraction.length(); i < 3; i++) millis *= 10;
			}
			return (minutes * 60L + seconds) * 1000L + millis;
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
