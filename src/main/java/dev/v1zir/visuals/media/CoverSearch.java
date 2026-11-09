package dev.v1zir.visuals.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Covers for songs whose player gives none (a thumbnail the media controls leave out, Spotify's window title): the
 * iTunes Search API, then Deezer's, both public and keyless, each asked once per song for the session. The best
 * match must share the title and the artist (accents, case, "feat." parts and the like aside).
 */
final class CoverSearch {
	private static final String SEARCHING = "\u0000searching";
	private static final Pattern NOISE = Pattern.compile(
		"\\s*[(\\[](?:feat\\.?|ft\\.?|with|prod\\.?)\\s[^)\\]]*[)\\]]|\\s+-\\s+(?:.*remaster.*|.*version.*|.*edit.*|live.*|mono|stereo)$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern MARKS = Pattern.compile("\\p{M}+");
	private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
	private static final Map<String, String> FOUND = new ConcurrentHashMap<>();

	private CoverSearch() {
	}

	/** The cover URL for the song: "" while it is being looked for or when none was found. */
	static String find(Track track) {
		if (track.title().isBlank()) return "";
		String key = clean(track.artist()) + '\n' + clean(track.title());
		String known = FOUND.putIfAbsent(key, SEARCHING);
		if (known == null) {
			search(track).whenComplete((url, error) -> {
				FOUND.put(key, url != null ? url : "");
				if (error != null) V1zir.LOGGER.debug("Cover lookup failed for {} - {}", track.artist(), track.title(), error);
			});
			return "";
		}
		return SEARCHING.equals(known) ? "" : known;
	}

	private static CompletableFuture<String> search(Track track) {
		String title = NOISE.matcher(track.title()).replaceAll("").strip();
		String artist = track.artist().strip();
		return itunes(title, artist).thenCompose(url -> url != null ? CompletableFuture.completedFuture(url) : deezer(title, artist));
	}

	private static CompletableFuture<String> itunes(String title, String artist) {
		String term = URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8);
		return json("https://itunes.apple.com/search?media=music&entity=song&limit=10&term=" + term).thenApply(root -> {
			if (root == null || !root.isJsonObject() || !root.getAsJsonObject().has("results")) return null;
			JsonObject best = best(root.getAsJsonObject().getAsJsonArray("results"), title, artist, "trackName", "artistName", null);
			String art = best != null ? text(best, "artworkUrl100") : "";
			return art.isEmpty() ? null : art.replace("100x100bb", "600x600bb");
		}).exceptionally(error -> null);
	}

	private static CompletableFuture<String> deezer(String title, String artist) {
		String q = URLEncoder.encode("artist:\"" + artist + "\" track:\"" + title + "\"", StandardCharsets.UTF_8);
		return json("https://api.deezer.com/search?limit=10&q=" + q).thenCompose(root -> {
			String found = deezerCover(root, title, artist);
			if (found != null) return CompletableFuture.completedFuture(found);
			// The strict query misses spellings that differ a little: a plain one finds those.
			String plain = URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8);
			return json("https://api.deezer.com/search?limit=10&q=" + plain).thenApply(r -> deezerCover(r, title, artist));
		}).exceptionally(error -> null);
	}

	private static String deezerCover(JsonElement root, String title, String artist) {
		if (root == null || !root.isJsonObject() || !root.getAsJsonObject().has("data")) return null;
		JsonObject best = best(root.getAsJsonObject().getAsJsonArray("data"), title, artist, "title", null, "artist");
		if (best == null || !best.has("album") || !best.get("album").isJsonObject()) return null;
		JsonObject album = best.getAsJsonObject("album");
		String art = text(album, "cover_xl");
		if (art.isEmpty()) art = text(album, "cover_big");
		return art.isEmpty() ? null : art;
	}

	/**
	 * The result whose title and artist match the song best; null when none has both. The artist is a field of the
	 * result (artistField) or the "name" of an object in it (artistObject).
	 */
	private static JsonObject best(JsonArray results, String title, String artist, String titleField, String artistField, String artistObject) {
		String wantTitle = clean(title);
		String wantArtist = clean(artist);
		JsonObject best = null;
		int bestScore = 0;
		for (JsonElement element : results) {
			if (!element.isJsonObject()) continue;
			JsonObject o = element.getAsJsonObject();
			String name = artistField != null ? text(o, artistField)
				: o.has(artistObject) && o.get(artistObject).isJsonObject() ? text(o.getAsJsonObject(artistObject), "name") : "";
			int t = similar(clean(NOISE.matcher(text(o, titleField)).replaceAll("")), wantTitle);
			int a = similar(clean(name), wantArtist);
			if (t == 0 || a == 0) continue;
			int score = t * 2 + a;
			if (score > bestScore) {
				bestScore = score;
				best = o;
			}
		}
		return best;
	}

	/** 2 for the same words, 1 when one holds the other's words, 0 otherwise. */
	private static int similar(String a, String b) {
		if (a.isEmpty() || b.isEmpty()) return 0;
		if (a.equals(b)) return 2;
		return (" " + a + " ").contains(" " + b + " ") || (" " + b + " ").contains(" " + a + " ") ? 1 : 0;
	}

	/** Lower case words without accents or punctuation ("Beyoncé - Halo!" matches "beyonce halo"). */
	private static String clean(String text) {
		String plain = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
		return NOT_WORD.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
	}

	private static CompletableFuture<JsonElement> json(String url) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(10))
			.header("User-Agent", Media.userAgent())
			.header("Accept", "application/json")
			.GET()
			.build();
		return Media.http().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
			.thenApply(response -> response.statusCode() == 200 ? JsonParser.parseString(response.body()) : null);
	}

	private static String text(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}
}
