package dev.v1zir.visuals.gui.chat;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.chat.Emojis;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Emoji suggestions over the chat box, as on Discord: a ":" and at least two letters of a name (":so") open a list of
 * the emoji whose names start with them (then those whose names contain them), the letters typed lit in the accent.
 * Up and Down choose, Tab or Enter put the chosen one in (Enter does not send while the list is open), Escape closes
 * the list; a click on a row puts that one in. The emoji goes into the box as its glyph, in colour, and is sent as the
 * real emoji (Emojis.outgoing).
 */
public final class EmojiSuggestions {
	private static final int LIMIT = 8;
	private static final int ROW = 13;
	private static final int HEADER = 13;
	private static final int WIDTH = 176;

	private static List<Emojis.Emoji> shown = List.of();
	private static int selected;
	/** Where the ":" of the name being typed is in the box's text. */
	private static int start = -1;
	private static String query = "";
	/** The name Escape closed the list for: it stays closed until something else is typed. */
	private static String dismissed;
	private static int panelX;
	private static int panelY;

	private EmojiSuggestions() {
	}

	/** Follows the chat box: the name being typed after a ":" at the cursor, if any. */
	public static void update(TextFieldWidget field) {
		String text = field.getText();
		int cursor = Math.min(field.getCursor(), text.length());
		int i = cursor;
		while (i > 0 && nameChar(text.charAt(i - 1))) i--;
		String typed = text.substring(i, cursor);
		// The ":" starts a word (not "12:30" or a link), and two letters are typed.
		boolean opens = i > 0 && text.charAt(i - 1) == ':' && (i == 1 || Character.isWhitespace(text.charAt(i - 2)) || text.charAt(i - 2) >= 0x2000);
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

	public static boolean open() {
		return !shown.isEmpty();
	}

	public static void render(DrawContext ctx, TextFieldWidget field, TextRenderer font, int mouseX, int mouseY) {
		update(field);
		if (shown.isEmpty()) return;
		int rows = shown.size();
		int h = HEADER + rows * ROW + 3;
		panelX = field.getX() - 2;
		panelY = field.getY() - 6 - h;
		Render2D.round(ctx, panelX, panelY, WIDTH, h, 5f, 0xF20E0E11);
		Render2D.outline(ctx, panelX, panelY, WIDTH, h, 5f, 1f, 0x24FFFFFF);
		ctx.drawText(font, Text.literal("EMOJI MATCHING :" + query), panelX + 7, panelY + 4, 0xFF8C8C96, false);
		int accent = Theme.accent();
		for (int r = 0; r < rows; r++) {
			Emojis.Emoji emoji = shown.get(r);
			int y = panelY + HEADER + r * ROW;
			boolean hover = mouseX >= panelX && mouseX < panelX + WIDTH && mouseY >= y && mouseY < y + ROW;
			if (r == selected || hover) {
				Render2D.round(ctx, panelX + 3, y, WIDTH - 6, ROW - 1, 3f, r == selected ? ColorUtil.withAlpha(accent, 70) : 0x14FFFFFF);
			}
			// The emoji a little larger than the text beside it.
			var matrices = ctx.getMatrices();
			matrices.pushMatrix();
			matrices.translate(panelX + 7f, y + 1.5f);
			matrices.scale(1.2f, 1.2f);
			ctx.drawText(font, Text.literal(emoji.glyph()), 0, 0, 0xFFFFFFFF, false);
			matrices.popMatrix();
			// Its name as typed in chat, the letters typed so far lit.
			String name = nameFor(emoji);
			int x = panelX + 22;
			int at = name.indexOf(query);
			int ty = y + 2;
			x = draw(ctx, font, ":", x, ty, 0xFF8C8C96);
			if (at >= 0) {
				x = draw(ctx, font, name.substring(0, at), x, ty, 0xFFE8E8EC);
				x = draw(ctx, font, query, x, ty, accent);
				x = draw(ctx, font, name.substring(at + query.length()), x, ty, 0xFFE8E8EC);
			} else {
				x = draw(ctx, font, name, x, ty, 0xFFE8E8EC);
			}
			draw(ctx, font, ":", x, ty, 0xFF8C8C96);
		}
	}

	/** Up, Down, Tab, Enter and Escape while the list is open; true when the key was used here. */
	public static boolean keyPressed(int key, TextFieldWidget field) {
		update(field);
		if (shown.isEmpty()) return false;
		switch (key) {
			case GLFW.GLFW_KEY_UP -> selected = (selected + shown.size() - 1) % shown.size();
			case GLFW.GLFW_KEY_DOWN -> selected = (selected + 1) % shown.size();
			case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> accept(field, shown.get(selected));
			case GLFW.GLFW_KEY_ESCAPE -> {
				dismissed = query;
				shown = List.of();
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** A click on a row puts that emoji in. */
	public static boolean mouseClicked(double mx, double my, TextFieldWidget field) {
		if (shown.isEmpty()) return false;
		if (mx < panelX || mx >= panelX + WIDTH) return false;
		int r = (int) Math.floor((my - panelY - HEADER) / ROW);
		if (r < 0 || r >= shown.size()) return my >= panelY && my < panelY + HEADER;
		accept(field, shown.get(r));
		return true;
	}

	private static void accept(TextFieldWidget field, Emojis.Emoji emoji) {
		String text = field.getText();
		int cursor = Math.min(field.getCursor(), text.length());
		if (start < 0 || start > cursor) return;
		String inserted = emoji.glyph() + " ";
		field.setText(text.substring(0, start) + inserted + text.substring(cursor));
		field.setCursor(Math.min(start + inserted.length(), field.getText().length()), false);
		close();
		V1zir.LOGGER.debug("Emoji {} put in", emoji.code());
	}

	private static void close() {
		shown = List.of();
		query = "";
		start = -1;
	}

	/** The name of the emoji that matches what was typed (the first one starting with it, else the main one). */
	private static String nameFor(Emojis.Emoji emoji) {
		for (String n : emoji.names()) if (n.startsWith(query)) return n;
		for (String n : emoji.names()) if (n.contains(query)) return n;
		return emoji.names().get(0);
	}

	private static int draw(DrawContext ctx, TextRenderer font, String s, int x, int y, int color) {
		if (s.isEmpty()) return x;
		ctx.drawText(font, s, x, y, color, false);
		return x + font.getWidth(s);
	}

	private static boolean nameChar(char c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '+' || c == '-';
	}
}
