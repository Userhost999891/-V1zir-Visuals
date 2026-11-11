package dev.v1zir.visuals.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.ScheduledExecutorService;

/**
 * macOS: asks the Spotify app through osascript (JavaScript for Automation, which unlike
 * AppleScript does not ask where Spotify is when it is not installed, and never launches it).
 */
final class MacMediaSource extends PolledMediaSource {
	private static final String QUERY = """
		function run() {
			var app = Application('Spotify');
			if (!app.running()) return '';
			var t = app.currentTrack();
			return JSON.stringify({t: t.name(), a: t.artist(), al: t.album(), d: t.duration(),
				p: Math.round(app.playerPosition() * 1000), s: app.playerState() == 'playing' ? 1 : 0, art: t.artworkUrl()});
		}""";

	MacMediaSource(ScheduledExecutorService worker) {
		super(worker);
	}

	@Override
	protected String[] query() {
		return new String[]{"osascript", "-l", "JavaScript", "-e", QUERY};
	}

	@Override
	protected void report(String output) {
		if (output == null || output.isEmpty()) {
			publish(null, "", 0L, false);
			return;
		}
		JsonObject o = JsonParser.parseString(output).getAsJsonObject();
		String title = text(o, "t");
		if (title.isEmpty()) {
			publish(null, "", 0L, false);
			return;
		}
		Track track = new Track(title, text(o, "a"), text(o, "al"), o.has("d") ? o.get("d").getAsLong() : 0L);
		publish(track, text(o, "art"), o.get("p").getAsLong(), o.get("s").getAsInt() == 1);
	}

	static String text(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	@Override
	protected String[] commandLine(Media.Command command) {
		String action = switch (command) {
			case PLAY_PAUSE -> "playpause";
			case NEXT -> "nextTrack";
			case PREVIOUS -> "previousTrack";
		};
		return new String[]{"osascript", "-l", "JavaScript", "-e", "Application('Spotify')." + action + "()"};
	}

	@Override
	protected String missing() {
		return "osascript is not available";
	}
}
