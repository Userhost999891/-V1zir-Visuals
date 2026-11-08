package dev.v1zir.visuals.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import dev.v1zir.visuals.V1zir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Windows fallback that needs no PowerShell: the Spotify desktop app's main window title, read through user32 (JNA,
 * which Minecraft ships). Spotify titles its window "Artist - Title" while a song plays and "Spotify…" while paused or
 * idle, so this knows the song and whether it plays; the position is counted from when the song appeared, and the
 * length and cover come from a public catalogue search. Media keys stand in for the player's controls.
 * <p>
 * Media thread only.
 */
final class SpotifyWindow {
	/** VK_MEDIA_* virtual key codes. */
	private static final int VK_NEXT = 0xB0;
	private static final int VK_PREVIOUS = 0xB1;
	private static final int VK_PLAY_PAUSE = 0xB3;
	private static final int KEYEVENTF_KEYUP = 0x0002;
	private static final Map<String, Info> CATALOGUE = new ConcurrentHashMap<>();

	/** Length and cover of a song from the catalogue; durationMs 0 and art "" until (or unless) it answers. */
	private record Info(long durationMs, String art) {
		static final Info UNKNOWN = new Info(0L, "");
	}

	private int spotifyPid;
	private String artist;
	private String title;
	private boolean playing;
	/** Position at the last change of state, and when that was (System.nanoTime). */
	private long positionMs;
	private long since;

	/** Spotify's main window title, or null when Spotify is not running. */
	private String windowTitle() {
		String[] found = new String[1];
		User32.INSTANCE.EnumWindows((hwnd, data) -> {
			IntByReference pid = new IntByReference();
			User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
			if (spotifyPid != 0 ? pid.getValue() != spotifyPid : !isSpotify(pid.getValue())) return true;
			char[] cls = new char[64];
			User32.INSTANCE.GetClassName(hwnd, cls, cls.length);
			if (!Native.toString(cls).startsWith("Chrome_WidgetWin")) return true;
			int length = User32.INSTANCE.GetWindowTextLength(hwnd);
			if (length <= 0) return true;
			char[] text = new char[length + 1];
			User32.INSTANCE.GetWindowText(hwnd, text, text.length);
			String value = Native.toString(text).strip();
			if (value.isEmpty()) return true;
			spotifyPid = pid.getValue();
			found[0] = value;
			return false;
		}, null);
		if (found[0] == null) spotifyPid = 0;
		return found[0];
	}

	private static boolean isSpotify(int pid) {
		WinNT.HANDLE process = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
		if (process == null) return false;
		try {
			char[] path = new char[1024];
			IntByReference size = new IntByReference(path.length);
			if (!Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, path, size)) return false;
			String exe = new String(path, 0, size.getValue()).toLowerCase(Locale.ROOT);
			return exe.endsWith("\\spotify.exe");
		} finally {
			Kernel32.INSTANCE.CloseHandle(process);
		}
	}

	/**
	 * Reads the window and returns the state to publish: the track (null when there is none), its cover, position and
	 * play state.
	 */
	NowPlaying poll() {
		String text = windowTitle();
		long now = System.nanoTime();
		if (text == null) {
			artist = null;
			title = null;
			return NowPlaying.NONE;
		}
		int dash = text.indexOf(" - ");
		boolean song = dash > 0 && !text.equalsIgnoreCase("Spotify") && !text.toLowerCase(Locale.ROOT).startsWith("spotify ");
		if (song) {
			String a = text.substring(0, dash).strip();
			String t = text.substring(dash + 3).strip();
			if (!a.equals(artist) || !t.equals(title)) {
				artist = a;
				title = t;
				positionMs = 0L;
				since = now;
				playing = true;
				lookUp(a, t);
			} else if (!playing) {
				since = now;
				playing = true;
			}
		} else if (title != null && playing) {
			// Paused (or an advert): freeze the position on the song shown last.
			positionMs += (now - since) / 1_000_000L;
			since = now;
			playing = false;
		}
		if (title == null) return NowPlaying.NONE;
		Info info = CATALOGUE.getOrDefault(key(artist, title), Info.UNKNOWN);
		long position = positionMs + (playing ? (now - since) / 1_000_000L : 0L);
		if (info.durationMs > 0) position = Math.min(position, info.durationMs);
		return new NowPlaying(new Track(title, artist, "", info.durationMs), info.art, position, playing, now);
	}

	private static String key(String artist, String title) {
		return artist.toLowerCase(Locale.ROOT) + '\n' + title.toLowerCase(Locale.ROOT);
	}

	/** Length and cover from the iTunes Search API (public, no key), once per song. */
	private static void lookUp(String artist, String title) {
		String key = key(artist, title);
		if (CATALOGUE.putIfAbsent(key, Info.UNKNOWN) != null) return;
		String term = URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8);
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://itunes.apple.com/search?media=music&entity=song&limit=8&term=" + term))
			.timeout(Duration.ofSeconds(10))
			.header("User-Agent", Media.userAgent())
			.GET()
			.build();
		Media.http().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenAccept(response -> {
			if (response.statusCode() != 200) return;
			JsonElement root = JsonParser.parseString(response.body());
			if (!root.isJsonObject() || !root.getAsJsonObject().has("results")) return;
			JsonArray results = root.getAsJsonObject().getAsJsonArray("results");
			JsonObject best = null;
			int bestScore = -1;
			for (JsonElement element : results) {
				if (!element.isJsonObject()) continue;
				JsonObject o = element.getAsJsonObject();
				int score = similar(text(o, "trackName"), title) * 2 + similar(text(o, "artistName"), artist);
				if (score > bestScore) {
					bestScore = score;
					best = o;
				}
			}
			if (best == null || bestScore < 2) return;
			long duration = best.has("trackTimeMillis") ? best.get("trackTimeMillis").getAsLong() : 0L;
			String art = text(best, "artworkUrl100").replace("100x100bb", "600x600bb");
			CATALOGUE.put(key, new Info(duration, art));
		}).exceptionally(error -> {
			V1zir.LOGGER.debug("Catalogue lookup failed for {} - {}", artist, title, error);
			return null;
		});
	}

	/** 2 for the same text, 1 when one contains the other, 0 otherwise (case and spacing ignored). */
	private static int similar(String a, String b) {
		String x = a.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
		String y = b.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
		if (x.isEmpty() || y.isEmpty()) return 0;
		if (x.equals(y)) return 2;
		return x.contains(y) || y.contains(x) ? 1 : 0;
	}

	private static String text(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	/** Presses the media key for the command; Spotify and the system handle it. */
	static void command(Media.Command command) {
		int vk = switch (command) {
			case PLAY_PAUSE -> VK_PLAY_PAUSE;
			case NEXT -> VK_NEXT;
			case PREVIOUS -> VK_PREVIOUS;
		};
		WinUser.INPUT[] inputs = (WinUser.INPUT[]) new WinUser.INPUT().toArray(2);
		for (int i = 0; i < 2; i++) {
			inputs[i].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
			inputs[i].input.setType("ki");
			inputs[i].input.ki.wVk = new WinDef.WORD(vk);
			inputs[i].input.ki.dwFlags = new WinDef.DWORD(i == 0 ? 0 : KEYEVENTF_KEYUP);
		}
		User32.INSTANCE.SendInput(new WinDef.DWORD(2), inputs, inputs[0].size());
	}
}
