package dev.v1zir.visuals.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emoji in Messages (this client's own chat), as on Discord: typed as :shortcode: (Discord's names, such as :sob: and
 * :skull:, and the common aliases), sent as the real Unicode emoji, and shown in colour (Twemoji, CC-BY 4.0) by
 * EmojiText. The game's own chat is left alone.
 * <p>
 * Every emoji has a glyph code point in the Supplementary Private Use Area-A (U+F0000 plus its index, the place of its
 * image in assets/v1zir/textures/font/emoji.png): text to be drawn has its emoji sequences swapped for those (toGlyphs),
 * which nothing else uses. The data (sequences, names, categories) is assets/v1zir/emoji/emoji.json, the index being
 * the place in it.
 */
public final class Emojis {
	private static final int BASE = 0xF0000;
	private static final Pattern SHORTCODE = Pattern.compile(":([a-z0-9_+\\-]{1,40}):");

	/** One emoji: its glyph's index, its sequences (fully qualified first), its names and its category. */
	public record Emoji(int index, String unicode, List<String> names, String category) {
		/** The glyph code point that stands for it in text to be drawn (EmojiText draws its image). */
		public String glyph() {
			return Character.toString(BASE + index);
		}

		/** Its main name, as :name:. */
		public String code() {
			return ":" + names.get(0) + ":";
		}
	}

	private static List<Emoji> all;
	private static Map<String, Emoji> byName;
	private static Map<String, Emoji> bySequence;
	private static int longest;
	private static boolean failed;

	private Emojis() {
	}

	/** Every emoji in the order of the Unicode emoji list (smileys first), loaded the first time. */
	public static List<Emoji> all() {
		load();
		return all;
	}

	private static synchronized void load() {
		if (all != null || failed) return;
		List<Emoji> list = new ArrayList<>();
		Map<String, Emoji> names = new HashMap<>();
		Map<String, Emoji> sequences = new HashMap<>();
		int max = 0;
		try (InputStream in = Emojis.class.getResourceAsStream("/assets/" + V1zir.MOD_ID + "/emoji/emoji.json")) {
			if (in == null) throw new IllegalStateException("emoji.json is missing");
			JsonArray array = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
			for (int i = 0; i < array.size(); i++) {
				JsonObject o = array.get(i).getAsJsonObject();
				List<String> seqs = new ArrayList<>();
				for (JsonElement s : o.getAsJsonArray("s")) seqs.add(s.getAsString());
				List<String> ns = new ArrayList<>();
				for (JsonElement n : o.getAsJsonArray("n")) ns.add(n.getAsString());
				String category = o.get("c").getAsString();
				// Flags go by flag_xx as on Discord, not by the bare country code (":so" is not Somalia).
				if ("Flags".equals(category)) ns.removeIf(n -> n.length() == 2);
				if (seqs.isEmpty() || ns.isEmpty()) continue;
				Emoji emoji = new Emoji(i, seqs.get(0), List.copyOf(ns), category);
				list.add(emoji);
				for (String n : ns) names.putIfAbsent(n, emoji);
				for (String s : seqs) {
					sequences.putIfAbsent(s, emoji);
					max = Math.max(max, s.length());
				}
			}
		} catch (Exception e) {
			V1zir.LOGGER.warn("Emoji are off: {}", e.toString());
			failed = true;
			return;
		}
		byName = names;
		bySequence = sequences;
		longest = max;
		all = List.copyOf(list);
	}

	/** The emoji with this name (without colons), or null. */
	public static Emoji named(String name) {
		load();
		return byName == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
	}

	/** Every :shortcode: in the text as its emoji (unknown names stay as typed). */
	public static String replaceShortcodes(String text) {
		load();
		if (all == null || text.indexOf(':') < 0) return text;
		Matcher m = SHORTCODE.matcher(text);
		StringBuilder out = new StringBuilder(text.length());
		while (m.find()) {
			Emoji emoji = byName.get(m.group(1));
			m.appendReplacement(out, Matcher.quoteReplacement(emoji != null ? emoji.unicode() : m.group()));
		}
		m.appendTail(out);
		return out.toString();
	}

	/**
	 * When the text ends with a :name: of an emoji that starts a word (the closing colon just typed), the text with that
	 * emoji in its place; else null. As on Discord, the emoji shows as soon as its name is closed.
	 */
	public static String closeShortcode(String text) {
		load();
		if (all == null || !text.endsWith(":") || text.length() < 3) return null;
		int open = text.lastIndexOf(':', text.length() - 2);
		if (open < 0) return null;
		if (open > 0 && !Character.isWhitespace(text.charAt(open - 1)) && text.charAt(open - 1) < 0x2000) return null;
		String name = text.substring(open + 1, text.length() - 1);
		if (name.isEmpty() || !SHORTCODE.matcher(":" + name + ":").matches()) return null;
		Emoji emoji = byName.get(name.toLowerCase(Locale.ROOT));
		return emoji == null ? null : text.substring(0, open) + emoji.unicode();
	}

	/** The index of the emoji a glyph code point stands for, or -1 for any other code point. */
	public static int glyphIndex(int codePoint) {
		int index = codePoint - BASE;
		return index >= 0 && all != null && index < all.size() ? index : -1;
	}

	/** Glyphs back into the emoji they stand for. */
	public static String fromGlyphs(String text) {
		load();
		if (all == null) return text;
		StringBuilder out = null;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			int n = Character.charCount(cp);
			int index = cp - BASE;
			if (index >= 0 && index < all.size()) {
				if (out == null) out = new StringBuilder(text.length()).append(text, 0, i);
				out.append(all.get(index).unicode());
			} else if (out != null) {
				out.appendCodePoint(cp);
			}
			i += n;
		}
		return out == null ? text : out.toString();
	}

	/**
	 * Emoji in a string as their glyphs: the longest emoji sequence at each place (skin tones fall back to the plain
	 * emoji, variation selectors are dropped). Text without emoji comes back as it was.
	 */
	public static String toGlyphs(String text) {
		load();
		if (all == null || !mayHaveEmoji(text)) return text;
		StringBuilder out = new StringBuilder(text.length());
		int i = 0;
		while (i < text.length()) {
			int cp = text.codePointAt(i);
			if (isStart(text, i, cp)) {
				Emoji match = null;
				int length = 0;
				String plain = stripSkin(text, i);
				for (int len = Math.min(longest, plain.length()); len >= 1; len--) {
					Emoji e = bySequence.get(plain.substring(0, len));
					if (e != null) {
						match = e;
						length = len;
						break;
					}
				}
				if (match != null) {
					out.append(match.glyph());
					i += consumed(text, i, plain, length);
					// A variation selector after it shows nothing.
					while (i < text.length() && text.charAt(i) == '️') i++;
					continue;
				}
			}
			out.appendCodePoint(cp);
			i += Character.charCount(cp);
		}
		return out.toString();
	}

	/**
	 * The emoji whose names start with what was typed, best first: the exact name, then the shortest names (":so" gives
	 * :sob: before :soccer:), the more common emoji first among equals; then those whose names contain it elsewhere.
	 * At most limit.
	 */
	public static List<Emoji> suggest(String typed, int limit) {
		load();
		List<Emoji> found = new ArrayList<>();
		if (all == null || typed.isEmpty()) return found;
		String q = typed.toLowerCase(Locale.ROOT);
		Emoji exact = byName.get(q);
		if (exact != null) found.add(exact);
		List<Emoji> starting = new ArrayList<>();
		for (Emoji e : all) {
			if (e != exact && shortestStarting(e, q) > 0) starting.add(e);
		}
		// Shorter matching names first; the sort is stable, so the emoji list's order (the common ones first) breaks ties.
		starting.sort(java.util.Comparator.comparingInt(e -> shortestStarting(e, q)));
		for (Emoji e : starting) {
			if (found.size() >= limit) return found;
			found.add(e);
		}
		for (Emoji e : all) {
			if (found.size() >= limit) return found;
			if (found.contains(e)) continue;
			for (String n : e.names()) {
				if (n.contains(q)) {
					found.add(e);
					break;
				}
			}
		}
		return found;
	}

	/** The length of the emoji's shortest name that starts with q, or 0 when none does. */
	private static int shortestStarting(Emoji e, String q) {
		int best = 0;
		for (String n : e.names()) {
			if (n.startsWith(q) && (best == 0 || n.length() < best)) best = n.length();
		}
		return best;
	}

	/** Every emoji (keycaps and text symbols shown as emoji too, by their variation selector) has a char past U+2000. */
	private static boolean mayHaveEmoji(String text) {
		for (int i = 0; i < text.length(); i++) if (text.charAt(i) >= 0x2000) return true;
		return false;
	}

	/** Whether an emoji can start here: anything past the basic symbols, or a keycap or text symbol shown as emoji. */
	private static boolean isStart(String text, int i, int cp) {
		if (cp >= 0x2000) return true;
		if (cp == '#' || cp == '*' || cp >= '0' && cp <= '9' || cp == 0xA9 || cp == 0xAE) {
			return i + 1 < text.length() && (text.charAt(i + 1) == '️' || text.charAt(i + 1) == '⃣');
		}
		return false;
	}

	/** The text from i with skin tone modifiers taken out (the atlas has the plain emoji only). */
	private static String stripSkin(String text, int i) {
		int end = Math.min(text.length(), i + longest + 12);
		StringBuilder b = new StringBuilder(end - i);
		for (int j = i; j < end; ) {
			int cp = text.codePointAt(j);
			if (cp < 0x1F3FB || cp > 0x1F3FF) b.appendCodePoint(cp);
			j += Character.charCount(cp);
		}
		return b.toString();
	}

	/** How many chars of the original text the first length chars of its skin-tone-free copy cover. */
	private static int consumed(String text, int i, String plain, int length) {
		int kept = 0;
		int j = i;
		while (j < text.length() && kept < length) {
			int cp = text.codePointAt(j);
			int n = Character.charCount(cp);
			if (cp < 0x1F3FB || cp > 0x1F3FF) kept += n;
			j += n;
		}
		// Skin tone modifiers right after it belong to it too.
		while (j < text.length()) {
			int cp = text.codePointAt(j);
			if (cp < 0x1F3FB || cp > 0x1F3FF) break;
			j += Character.charCount(cp);
		}
		return j - i;
	}
}
