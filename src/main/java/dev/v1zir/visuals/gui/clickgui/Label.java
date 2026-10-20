package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.render.font.SdfFont;

/**
 * Width of a string, measured again only when the text, font or size changes. Measuring walks
 * every glyph pair (kerning goes through stb_truetype), so labels drawn every frame keep one.
 */
public final class Label {
	private String text;
	private SdfFont font;
	private float size;
	private float width;

	public float width(SdfFont font, String text, float size) {
		if (font != this.font || size != this.size || !text.equals(this.text)) {
			this.font = font;
			this.text = text;
			this.size = size;
			this.width = font.width(text, size);
		}
		return width;
	}
}
