package dev.v1zir.visuals.gui.messages;

import dev.v1zir.visuals.chat.Emojis;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Emoji suggestions over the Messages field, as on Discord: a ":" starting a word and two letters of a name (":so")
 * list the emoji whose names start with them (the shortest names first, then names that contain them), the letters
 * typed lit in the accent. Up and Down choose, Tab or Enter put the chosen one in (Enter does not send while the list
 * is open), Escape closes the list until something else is typed; a click on a row puts that one in. The field's
 * caret is always at the end of the draft, so the name being typed is the draft's last word.
 */
final class EmojiSuggestions {
	private static final int LIMIT = 8;
	private static final float ROW = 13f;
	private static final float HEADER = 14f;
	private static final float WIDTH = 170f;

	private List<Emojis.Emoji> shown = List.of();
	private int selected;
	/** Where the ":" of the name being typed is in the draft. */
	private int start = -1;
	private String query = "";
	/** The name Escape closed the list for. */
	private String dismissed;
	private float panelX;
	private float panelY;

	/** Follows the draft: the name being typed after a ":" at its end, if any. */
	void update(String draft) {
		int i = draft.length();
		while (i > 0 && nameChar(draft.charAt(i - 1))) i--;
		String typed = draft.substring(i);
		boolean opens = i > 0 && draft.charAt(i - 1) == ':'
			&& (i == 1 || Character.isWhitespace(draft.charAt(i - 2)) || draft.charAt(i - 2) >= 0x2000);
		if (!opens || typed.length() < 2) {
			close();
			return;
		}
		if (typed.equals(dismissed)) {
			shown = List.of();
			return;
		}
		dismissed = null;
		if (!typed.equals(query) || shown.isEmpty()) {
			query = typed;
			start = i - 1;
			shown = Emojis.suggest(typed, LIMIT);
			selected = 0;
		}
	}

	boolean open() {
		return !shown.isEmpty();
	}

	/** The list with its bottom edge at bottom, its left edge at x. */
	void render(DrawContext ctx, float x, float bottom, double mx, double my) {
		if (shown.isEmpty()) return;
		SdfFont font = Fonts.medium();
		float h = HEADER + shown.size() * ROW + 3f;
		panelX = x;
		panelY = bottom - h;
		Render2D.round(ctx, panelX, panelY, WIDTH, h, 6f, 0xFF0E0E11);
		Render2D.outline(ctx, panelX, panelY, WIDTH, h, 6f, 0.8f, 0x24FFFFFF);
		Render2D.textMiddle(ctx, font, "EMOJI MATCHING :" + query, panelX + 8f, panelY + HEADER / 2f + 1f, 5f, 0xFF8C8C96);
		int accent = Theme.accent();
		for (int r = 0; r < shown.size(); r++) {
			Emojis.Emoji emoji = shown.get(r);
			float y = panelY + HEADER + r * ROW;
			boolean hover = mx >= panelX && mx < panelX + WIDTH && my >= y && my < y + ROW;
			if (r == selected || hover) {
				Render2D.round(ctx, panelX + 3f, y, WIDTH - 6f, ROW - 1f, 4f, r == selected ? ColorUtil.withAlpha(accent, 64) : 0x14FFFFFF);
			}
			float cy = y + (ROW - 1f) / 2f;
			EmojiText.emoji(ctx, emoji.index(), panelX + 7f, cy, 9f, 255);
			// Its name as typed, the letters typed so far lit.
			String name = nameFor(emoji);
			float tx = panelX + 21f;
			int at = name.indexOf(query);
			tx += Render2D.textMiddle(ctx, font, ":", tx, cy, 6f, 0xFF8C8C96);
			if (at >= 0) {
				tx += Render2D.textMiddle(ctx, font, name.substring(0, at), tx, cy, 6f, 0xFFE8E8EC);
				tx += Render2D.textMiddle(ctx, font, query, tx, cy, 6f, accent);
				tx += Render2D.textMiddle(ctx, font, name.substring(at + query.length()), tx, cy, 6f, 0xFFE8E8EC);
			} else {
				tx += Render2D.textMiddle(ctx, font, name, tx, cy, 6f, 0xFFE8E8EC);
			}
			Render2D.textMiddle(ctx, font, ":", tx, cy, 6f, 0xFF8C8C96);
		}
	}

	/** Up, Down, Tab, Enter and Escape while the list is open: the new draft, the same draft, or null when not used. */
	String keyPressed(int key, String draft) {
		update(draft);
		if (shown.isEmpty()) return null;
		switch (key) {
			case GLFW.GLFW_KEY_UP -> selected = (selected + shown.size() - 1) % shown.size();
			case GLFW.GLFW_KEY_DOWN -> selected = (selected + 1) % shown.size();
			case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				return accept(draft, shown.get(selected));
			}
			case GLFW.GLFW_KEY_ESCAPE -> {
				dismissed = query;
				shown = List.of();
			}
			default -> {
				return null;
			}
		}
		return draft;
	}

	/** A click on a row puts that emoji in: the new draft; the same draft for a click elsewhere on the list; else null. */
	String mouseClicked(double mx, double my, String draft) {
		if (shown.isEmpty() || mx < panelX || mx >= panelX + WIDTH || my < panelY) return null;
		int r = (int) Math.floor((my - panelY - HEADER) / ROW);
		if (r >= shown.size()) return null;
		if (r < 0) return draft;
		return accept(draft, shown.get(r));
	}

	private String accept(String draft, Emojis.Emoji emoji) {
		if (start < 0 || start > draft.length()) return draft;
		String next = draft.substring(0, start) + emoji.unicode() + " ";
		close();
		return next;
	}

	private void close() {
		shown = List.of();
		query = "";
		start = -1;
	}

	/** The name of the emoji that matches what was typed (its shortest name starting with it, else one containing it). */
	private String nameFor(Emojis.Emoji emoji) {
		String best = null;
		for (String n : emoji.names()) if (n.startsWith(query) && (best == null || n.length() < best.length())) best = n;
		if (best != null) return best;
		for (String n : emoji.names()) if (n.contains(query)) return n;
		return emoji.names().get(0);
	}

	private static boolean nameChar(char c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '+' || c == '-';
	}
}
