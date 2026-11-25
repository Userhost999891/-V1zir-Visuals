package dev.v1zir.visuals.gui.messages;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.chat.Emojis;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

/**
 * Text of Messages with its emoji in colour: the Twemoji image of each emoji sits in the line, a little taller than a
 * capital letter and centred on it. Strings come with their emoji as glyph code points (Emojis.toGlyphs), which this
 * measures and draws; a string without them is plain text. The images are cells of one atlas,
 * assets/v1zir/textures/font/emoji.png (48 by 40 cells of 32 pixels).
 */
public final class EmojiText {
	private static final Identifier ATLAS = Identifier.of(V1zir.MOD_ID, "textures/font/emoji.png");
	private static final int COLUMNS = 48;
	private static final int ROWS = 40;
	/** The image's height against the text's size, and the room on either side of it. */
	private static final float SCALE = 1.3f;
	private static final float GAP = 0.08f;

	private EmojiText() {
	}

	/** The text with its emoji as glyphs, ready to be measured and drawn here. */
	public static String of(String text) {
		return Emojis.toGlyphs(text);
	}

	/** The width an emoji takes in a line of text at this size. */
	public static float advance(float size) {
		return size * (SCALE + GAP * 2f);
	}

	public static float width(SdfFont font, String text, float size) {
		if (!hasEmoji(text)) return font.width(text, size);
		float w = 0f;
		int start = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			int n = Character.charCount(cp);
			if (Emojis.glyphIndex(cp) >= 0) {
				if (i > start) w += font.width(text.substring(start, i), size);
				w += advance(size);
				start = i + n;
			}
			i += n;
		}
		if (start < text.length()) w += font.width(text.substring(start), size);
		return w;
	}

	/** Text vertically centred (by cap height) on centerY, its emoji in colour; returns its width. */
	public static float drawMiddle(DrawContext ctx, SdfFont font, String text, float x, float centerY, float size, int color) {
		if (!hasEmoji(text)) return Render2D.textMiddle(ctx, font, text, x, centerY, size, color);
		float at = x;
		int start = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			int n = Character.charCount(cp);
			int index = Emojis.glyphIndex(cp);
			if (index >= 0) {
				if (i > start) at += Render2D.textMiddle(ctx, font, text.substring(start, i), at, centerY, size, color);
				emoji(ctx, index, at + size * GAP, centerY, size * SCALE, color >>> 24);
				at += advance(size);
				start = i + n;
			}
			i += n;
		}
		if (start < text.length()) at += Render2D.textMiddle(ctx, font, text.substring(start), at, centerY, size, color);
		return at - x;
	}

	/** One emoji's image, height h with its left edge at x, centred on centerY, at the given opacity (0 to 255). */
	public static void emoji(DrawContext ctx, int index, float x, float centerY, float h, int alpha) {
		if (alpha <= 0) return;
		int column = index % COLUMNS;
		int row = index / COLUMNS;
		float u0 = column / (float) COLUMNS;
		float v0 = row / (float) ROWS;
		Render2D.image(ctx, atlas(), x, centerY - h / 2f, h, h, 0f, alpha << 24 | 0xFFFFFF,
			u0, v0, u0 + 1f / COLUMNS, v0 + 1f / ROWS, 0);
	}

	private static TextureSetup atlas() {
		var texture = MinecraftClient.getInstance().getTextureManager().getTexture(ATLAS);
		return TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
	}

	/** Whether the text has an emoji glyph in it (they all lie past U+F0000, as surrogate pairs). */
	private static boolean hasEmoji(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (Character.isHighSurrogate(text.charAt(i)) && Emojis.glyphIndex(text.codePointAt(i)) >= 0) return true;
		}
		return false;
	}
}
