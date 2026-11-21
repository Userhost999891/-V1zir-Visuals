package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.render.font.SdfFont;

import java.util.ArrayList;
import java.util.List;

/**
 * One lyric line laid out once when the lyrics arrive: wrapped into rows at word boundaries and
 * the x of every character boundary, all at text size 1, so frames only scale and look up.
 */
final class LyricLine {
	final String text;
	/** x before each character (length + 1 entries). */
	final float[] prefix;
	final String[] rows;
	/** Character range of each row, without the space at the break. */
	final int[] start;
	final int[] end;

	private LyricLine(String text, float[] prefix, String[] rows, int[] start, int[] end) {
		this.text = text;
		this.prefix = prefix;
		this.rows = rows;
		this.start = start;
		this.end = end;
	}

	/** Lays the text out for the font, wrapping rows wider than maxWidth (at size 1). */
	static LyricLine of(String text, SdfFont font, float maxWidth) {
		float[] prefix = new float[text.length() + 1];
		float k = font.k(1f);
		float pen = 0f;
		SdfFont.Glyph prev = null;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			SdfFont.Glyph g = font.glyph(cp);
			// Same metrics as the text renderer, fallback glyphs at their own scale.
			if (prev != null) pen += prev.font().kerning(prev, g) * k;
			prefix[i] = pen;
			pen += g.advance() * (g.font() == font ? k : g.font().k(1f));
			int next = i + Character.charCount(cp);
			for (int j = i + 1; j <= next; j++) prefix[j] = pen;
			i = next;
			prev = g;
		}
		List<int[]> ranges = new ArrayList<>(2);
		int rowStart = 0;
		int lastSpace = -1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == ' ') {
				lastSpace = i;
				continue;
			}
			if (prefix[i + 1] - prefix[rowStart] <= maxWidth || i == rowStart) continue;
			int cut = lastSpace > rowStart ? lastSpace : i;
			ranges.add(new int[]{rowStart, cut});
			rowStart = lastSpace > rowStart ? lastSpace + 1 : i;
			lastSpace = -1;
		}
		ranges.add(new int[]{rowStart, text.length()});
		String[] rows = new String[ranges.size()];
		int[] start = new int[rows.length];
		int[] end = new int[rows.length];
		for (int r = 0; r < rows.length; r++) {
			start[r] = ranges.get(r)[0];
			end[r] = ranges.get(r)[1];
			rows[r] = text.substring(start[r], end[r]);
		}
		return new LyricLine(text, prefix, rows, start, end);
	}

	int rowCount() {
		return rows.length;
	}

	boolean isBreak() {
		return text.isEmpty();
	}

	float width(int row) {
		return prefix[end[row]] - prefix[start[row]];
	}

	float widest() {
		float w = 0f;
		for (int r = 0; r < rows.length; r++) w = Math.max(w, width(r));
		return w;
	}

	/** x inside a row (size 1) at a fractional character position, clamped to the row. */
	float x(int row, float chars) {
		float c = Math.max(start[row], Math.min(end[row], chars));
		int i = Math.min((int) c, end[row]);
		float base = prefix[i];
		if (i < end[row]) base += (prefix[i + 1] - prefix[i]) * (c - i);
		return base - prefix[start[row]];
	}

	/** End of the word at a character position (next space or the end of the line). */
	int wordEnd(float chars) {
		int i = Math.max(0, (int) Math.ceil(chars));
		while (i < text.length() && text.charAt(i) != ' ') i++;
		return i;
	}
}
