package dev.v1zir.visuals.gui.messages;

import dev.v1zir.visuals.render.font.SdfFont;

import java.util.ArrayList;
import java.util.List;

/** Breaks text into lines that fit a width, at spaces, and inside a word only when the word alone is too long. */
final class TextWrap {
	private TextWrap() {
	}

	static List<String> wrap(SdfFont font, String text, float size, float maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ", -1)) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (font.width(candidate, size) <= maxWidth) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
				line.setLength(0);
			}
			// A word wider than a line on its own is cut where it fills one.
			String rest = word;
			while (font.width(rest, size) > maxWidth && rest.codePointCount(0, rest.length()) > 1) {
				int end = fit(font, rest, size, maxWidth);
				lines.add(rest.substring(0, end));
				rest = rest.substring(end);
			}
			line.append(rest);
		}
		lines.add(line.toString());
		return lines;
	}

	/** The longest start of text (at least one character) that fits the width. */
	private static int fit(SdfFont font, String text, float size, float maxWidth) {
		int end = text.offsetByCodePoints(0, 1);
		while (end < text.length()) {
			int next = text.offsetByCodePoints(end, 1);
			if (font.width(text.substring(0, next), size) > maxWidth) break;
			end = next;
		}
		return end;
	}

	/** The text cut to fit the width, with an ellipsis where it was cut. */
	static String ellipsize(SdfFont font, String text, float size, float maxWidth) {
		if (font.width(text, size) <= maxWidth) return text;
		String dots = "…";
		float room = maxWidth - font.width(dots, size);
		if (room <= 0f) return dots;
		return text.substring(0, fit(font, text, size, room)).stripTrailing() + dots;
	}
}
