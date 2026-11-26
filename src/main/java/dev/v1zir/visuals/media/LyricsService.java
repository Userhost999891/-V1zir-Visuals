package dev.v1zir.visuals.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Synced lyrics from LRCLIB (lrclib.net), looked up asynchronously and cached per song in memory and
 * on disk (config/v1zir/lyrics). /api/get matches the exact song (title, artist, album and length
 * within two seconds); when it has nothing, /api/search with a cleaned title and the main artist
 * picks the closest match by length, preferring synced lyrics.
 */
public final class LyricsService {
	private static final String API = "https://lrclib.net/api/";
	private static final int MEMORY_ENTRIES = 48;
	/** "Not found" answers are kept this long before LRCLIB is asked again. */
	private static final long MISSING_TTL_MS = 24L * 3600_000L;
	/** Search results further than this from the song's length are a different version. */
	private static final double MAX_DURATION_DIFF = 6.0;
	private static final Pattern TITLE_NOISE = Pattern.compile(
		"\\s*[(\\[](?:feat\\.?|ft\\.?|with)\\s[^)\\]]*[)\\]]|\\s+-\\s+(?:.*remaster.*|.*version.*|.*edit.*|live.*|mono|stereo)$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern ARTIST_SPLIT = Pattern.compile("\\s*(?:,|&|;|/|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sx\\s)\\s*", Pattern.CASE_INSENSITIVE);

	/** Lookup of one song; result is LOADING until the answer arrives. */
	public static final class Request {
		private volatile Lyrics result = Lyrics.LOADING;

		public Lyrics result() {
			return result;
		}
	}

	private static final Map<String, Request> MEMORY = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Request> eldest) {
			return size() > MEMORY_ENTRIES;
		}
	};

	private LyricsService() {
	}

	/** Lyrics of the track: cached, or looked up in the background. Call it once per song, not per frame. */
	public static Request request(Track track) {
		String key = track.key();
		Request request;
		synchronized (MEMORY) {
			request = MEMORY.get(key);
			if (request != null) return request;
			request = new Request();
			MEMORY.put(key, request);
		}
		Request pending = request;
		CompletableFuture.supplyAsync(() -> fromDisk(key), Media.io())
			.thenCompose(cached -> cached != null ? CompletableFuture.completedFuture(cached) : fetch(track))
			.whenComplete((lyrics, error) -> {
				if (error != null || lyrics == null) {
					pending.result = Lyrics.ERROR;
					// Not cached, so the next request of the song tries again.
					synchronized (MEMORY) {
						MEMORY.remove(key, pending);
					}
					if (error != null) V1zir.LOGGER.debug("Lyrics lookup failed for {}", key, error);
				} else {
					pending.result = lyrics;
				}
			});
		return request;
	}

	// ------------------------------------------------------------------ LRCLIB

	private static CompletableFuture<Lyrics> fetch(Track track) {
		CompletableFuture<JsonObject> exact = track.durationMs() > 0
			? get("get?artist_name=" + q(track.artist()) + "&track_name=" + q(track.title()) + "&album_name=" + q(track.album())
				+ "&duration=" + Math.round(track.durationMs() / 1000.0)).thenApply(body -> body != null && body.isJsonObject() ? body.getAsJsonObject() : null)
			: CompletableFuture.completedFuture(null);
		return exact.thenCompose(found -> found != null ? CompletableFuture.completedFuture(found) : search(track))
			.thenApply(found -> {
				JsonObject answer = found != null ? found : new JsonObject();
				toDisk(track.key(), answer, found == null);
				return found == null ? Lyrics.MISSING : lyrics(answer);
			});
	}

	private static CompletableFuture<JsonObject> search(Track track) {
		String title = TITLE_NOISE.matcher(track.title()).replaceAll("").strip();
		String artist = ARTIST_SPLIT.split(track.artist(), 2)[0].strip();
		if (title.isEmpty()) return CompletableFuture.completedFuture(null);
		String query = "search?track_name=" + q(title) + (artist.isEmpty() ? "" : "&artist_name=" + q(artist));
		return get(query).thenApply(body -> body != null && body.isJsonArray() ? best(body.getAsJsonArray(), track.durationMs() / 1000.0) : null);
	}

	/** Closest length wins; synced lyrics beat plain ones at any distance within the limit. */
	private static JsonObject best(JsonArray results, double duration) {
		JsonObject best = null;
		double bestScore = Double.MAX_VALUE;
		for (JsonElement element : results) {
			if (!element.isJsonObject()) continue;
			JsonObject o = element.getAsJsonObject();
			double diff = duration > 0 && o.has("duration") && o.get("duration").isJsonPrimitive()
				? Math.abs(o.get("duration").getAsDouble() - duration) : 0.0;
			if (diff > MAX_DURATION_DIFF) continue;
			double score = diff + (text(o, "syncedLyrics") != null ? 0.0 : 100.0)
				+ (text(o, "plainLyrics") != null || bool(o, "instrumental") ? 0.0 : 1000.0);
			if (score < bestScore) {
				bestScore = score;
				best = o;
			}
		}
		return best;
	}

	/** GETs an API path; the body as JSON, or null for 404 (not found). Other failures complete exceptionally. */
	private static CompletableFuture<JsonElement> get(String path) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(API + path))
			.timeout(Duration.ofSeconds(12))
			.header("User-Agent", Media.userAgent())
			.header("Accept", "application/json")
			.GET()
			.build();
		return Media.http().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(response -> {
			if (response.statusCode() == 404) return null;
			if (response.statusCode() != 200) throw new IllegalStateException("LRCLIB answered " + response.statusCode());
			return JsonParser.parseString(response.body());
		});
	}

	private static Lyrics lyrics(JsonObject o) {
		return Lyrics.of(text(o, "syncedLyrics"), text(o, "plainLyrics"), bool(o, "instrumental"));
	}

	private static String q(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String text(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && !e.getAsString().isBlank() ? e.getAsString() : null;
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}

	// ------------------------------------------------------------------ disk cache

	private static Path file(String key) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
			return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("lyrics").resolve(HexFormat.of().formatHex(hash) + ".json");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Cached lyrics, or null when there is no (fresh) entry. */
	private static Lyrics fromDisk(String key) {
		Path path = file(key);
		if (!Files.isRegularFile(path)) return null;
		try {
			JsonObject o = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			if (bool(o, "missing")) {
				long checked = o.has("checked") ? o.get("checked").getAsLong() : 0L;
				return System.currentTimeMillis() - checked < MISSING_TTL_MS ? Lyrics.MISSING : null;
			}
			return lyrics(o);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static void toDisk(String key, JsonObject answer, boolean missing) {
		JsonObject o = new JsonObject();
		if (missing) {
			o.addProperty("missing", true);
			o.addProperty("checked", System.currentTimeMillis());
		} else {
			for (String field : new String[]{"trackName", "artistName", "albumName", "duration", "instrumental", "plainLyrics", "syncedLyrics"}) {
				JsonElement value = answer.get(field);
				o.add(field, value != null ? value : JsonNull.INSTANCE);
			}
		}
		Path path = file(key);
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, o.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			V1zir.LOGGER.debug("Could not cache lyrics in {}", path, e);
		}
	}
}
