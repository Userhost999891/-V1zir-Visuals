package dev.v1zir.visuals.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emoji in chat, as on Discord: typed as :shortcode: (Discord's names, such as :sob: and :skull:, and the common
 * aliases), sent as the real Unicode emoji, and shown in colour (Twemoji, CC-BY 4.0) wherever this client draws chat.
 * <p>
 * Every emoji has a glyph in the game's default font at a code point of the Supplementary Private Use Area-A
 * (U+F0000 plus its index; assets/minecraft/font/default.json, assets/v1zir/textures/font/emoji.png): nothing else
 * uses those code points, so the glyphs never clash with the game's own font or other packs, and a chat line is made
 * to show them by putting those code points where the emoji's sequence was. The data (sequences, names, categories)
 * is assets/v1zir/emoji/emoji.json, the index being the place in it.
 */
public final class Emojis {
	private static final int BASE = 0xF0000;
	private static final Pattern SHORTCODE = Pattern.compile(":([a-z0-9_+\\-]{1,40}):");
	/** Commands that carry chat to other players: their text gets emoji too; other commands are left as typed. */
	private static final String[] MESSAGE_COMMANDS = {"/msg ", "/tell ", "/w ", "/me ", "/r ", "/reply ", "/teammsg ", "/tm ", "/say "};

	/** One emoji: its glyph's index, its sequences (fully qualified first), its names and its category. */
	public record Emoji(int index, String unicode, List<String> names, String category) {
		/** The glyph that shows it in the game's font. */
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
				if (seqs.isEmpty() || ns.isEmpty()) continue;
				Emoji emoji = new Emoji(i, seqs.get(0), List.copyOf(ns), o.get("c").getAsString());
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

	/**
	 * A chat line on its way out: every :shortcode: becomes its emoji, and every glyph typed from the suggestions
	 * becomes the real emoji, so other players see them (as emoji, or as their game shows them). Commands other than
	 * the ones that carry chat to other players are left as typed.
	 */
	public static String outgoing(String text) {
		load();
		if (all == null || text.isEmpty()) return text;
		if (text.startsWith("/")) {
			boolean message = false;
			for (String command : MESSAGE_COMMANDS) if (text.startsWith(command)) message = true;
			if (!message) return text;
		}
		Matcher m = SHORTCODE.matcher(text);
		StringBuilder out = new StringBuilder(text.length());
		while (m.find()) {
			Emoji emoji = byName.get(m.group(1));
			m.appendReplacement(out, Matcher.quoteReplacement(emoji != null ? emoji.unicode() : m.group()));
		}
		m.appendTail(out);
		return fromGlyphs(out.toString());
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

	/** A chat line with its emoji as glyphs, every style (colours, clicks, hovers) kept. */
	public static Text toGlyphs(Text text) {
		load();
		if (all == null) return text;
		String plain = text.getString();
		if (!mayHaveEmoji(plain)) return text;
		MutableText out = Text.empty();
		text.visit((style, part) -> {
			out.append(Text.literal(toGlyphs(part)).setStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/**
	 * The emoji whose names start with what was typed (best first: an exact name, then the most common emoji), then
	 * those whose names contain it elsewhere; at most limit.
	 */
	public static List<Emoji> suggest(String typed, int limit) {
		load();
		List<Emoji> found = new ArrayList<>();
		if (all == null || typed.isEmpty()) return found;
		String q = typed.toLowerCase(Locale.ROOT);
		Emoji exact = byName.get(q);
		if (exact != null) found.add(exact);
		for (Emoji e : all) {
			if (found.size() >= limit) return found;
			if (e == exact) continue;
			for (String n : e.names()) {
				if (n.startsWith(q)) {
					found.add(e);
					break;
				}
			}
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
