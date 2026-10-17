package dev.v1zir.visuals.gui.clickgui;

/** Shared metrics of the click GUI (all values in GUI pixels). */
public final class Ui {
	public static final float PANEL_WIDTH = 116f;
	public static final float HEADER_HEIGHT = 24f;
	public static final float ROW_HEIGHT = 17f;
	public static final float RADIUS = 7f;
	public static final float PAD = 7f;
	public static final float TEXT = 7.5f;
	public static final float SMALL = 6.5f;

	private Ui() {
	}

	public static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}
}
