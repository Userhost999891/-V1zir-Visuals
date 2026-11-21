package dev.v1zir.visuals.controller;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;

import java.util.Arrays;

/**
 * What a controller's D-pad moves to on the client's own screens. Everything such a screen tests the pointer against
 * while it draws (Ui.inside: buttons, toggles, chips, rows, cards, fields, icons) is something that reacts to it, so
 * those places are collected as the screen draws, in the screen's coordinates (a screen drawn scaled says its scale),
 * leaving out what is hidden (outside the scissor) and what is too large to be one thing (a panel, a list). The game's
 * own widgets and the sprites that are buttons on its inventories (ClickableWidgetMixin, DrawContextMixin) come in the
 * same way, wherever they are drawn. The D-pad then goes to the nearest of them that way: straight ahead first, then
 * the least off to the side. Render thread only.
 */
public final class Targets {
	private static final int MAX = 768;
	/** Larger than this part of the screen, a place is a panel or a list, not one thing to press. */
	private static final float MAX_SHARE = 0.25f;
	private static float[] building = new float[MAX * 6];
	private static int buildingCount;
	private static float[] shown = new float[MAX * 6];
	private static int shownCount;
	private static float scale = 1f;
	@Nullable
	private static DrawContext context;
	@Nullable
	private static Screen buildingScreen;
	@Nullable
	private static Screen shownScreen;
	private static int screenW;
	private static int screenH;

	private Targets() {
	}

	/** A screen starts drawing (Screen.renderWithTooltip). */
	public static void begin(Screen screen, DrawContext ctx) {
		buildingScreen = screen;
		context = ctx;
		buildingCount = 0;
		scale = 1f;
		screenW = screen.width;
		screenH = screen.height;
	}

	/** It is drawn: what it collected is what the D-pad moves to now. */
	public static void end() {
		if (buildingScreen == null) return;
		float[] swap = shown;
		shown = building;
		building = swap;
		shownCount = buildingCount;
		shownScreen = buildingScreen;
		buildingScreen = null;
		context = null;
	}

	/** The screen draws what follows scaled about its top left by s (its own coordinates times s are the screen's). */
	public static void scale(float s) {
		scale = s;
	}

	/** A place the screen tests the pointer against (its own coordinates). */
	public static void add(float x, float y, float w, float h) {
		put(x, y, w, h, x + w / 2f, y + h / 2f);
	}

	/**
	 * A place whose pointer should land somewhere else than its middle (a slider's knob, so pressing it keeps the
	 * value): the place tested last with these bounds takes it, or it is added.
	 */
	public static void anchor(float x, float y, float w, float h, float ax, float ay) {
		if (buildingScreen == null) return;
		float sx = x * scale, sy = y * scale, sw = w * scale, sh = h * scale;
		for (int i = buildingCount - 1; i >= Math.max(0, buildingCount - 8); i--) {
			int o = i * 6;
			if (building[o] == sx && building[o + 1] == sy && building[o + 2] == sw && building[o + 3] == sh) {
				building[o + 4] = ax * scale;
				building[o + 5] = ay * scale;
				return;
			}
		}
		put(x, y, w, h, ax, ay);
	}

	/** Whether a screen is drawing now, its places being collected. */
	public static boolean collecting() {
		return buildingScreen != null;
	}

	/** A place drawn now (a widget of the game, a sprite that is a button), where the draw's transform puts it. */
	public static void addDrawn(DrawContext ctx, float x, float y, float w, float h) {
		if (buildingScreen == null) return;
		Matrix3x2fStack m = ctx.getMatrices();
		Vector2f a = m.transformPosition(x, y, new Vector2f());
		Vector2f b = m.transformPosition(x + w, y + h, new Vector2f());
		float sx = Math.min(a.x, b.x), sy = Math.min(a.y, b.y), sw = Math.abs(b.x - a.x), sh = Math.abs(b.y - a.y);
		putScreen(sx, sy, sw, sh, sx + sw / 2f, sy + sh / 2f);
	}

	/** The places collected on the screen's last frame (six floats each, as pick takes them); none for another screen. */
	public static float[] shown(Screen screen) {
		return screen == shownScreen ? Arrays.copyOf(shown, shownCount * 6) : new float[0];
	}

	private static void put(float x, float y, float w, float h, float ax, float ay) {
		putScreen(x * scale, y * scale, w * scale, h * scale, ax * scale, ay * scale);
	}

	/** A place in the screen's coordinates, its pointer at (cx, cy). */
	private static void putScreen(float sx, float sy, float sw, float sh, float cx, float cy) {
		if (buildingScreen == null || buildingCount == MAX) return;
		if (sw < 2f || sh < 2f || sw * sh > MAX_SHARE * screenW * screenH) return;
		if (cx < 0f || cy < 0f || cx > screenW || cy > screenH) return;
		if (context != null && !context.scissorContains(sx + sw / 2f, sy + sh / 2f)) return;
		for (int i = buildingCount - 1; i >= Math.max(0, buildingCount - 8); i--) {
			int o = i * 6;
			if (building[o] == sx && building[o + 1] == sy && building[o + 2] == sw && building[o + 3] == sh) return;
		}
		int o = buildingCount++ * 6;
		building[o] = sx;
		building[o + 1] = sy;
		building[o + 2] = sw;
		building[o + 3] = sh;
		building[o + 4] = cx;
		building[o + 5] = cy;
	}

	/** Where the D-pad goes from (x, y) that way on this screen (its coordinates), or null when nothing is there. */
	@Nullable
	public static float[] next(Screen screen, double x, double y, int dx, int dy) {
		if (screen != shownScreen) return null;
		return pick(shown, shownCount, x, y, dx, dy);
	}

	/**
	 * The best of the places (x, y, w, h, anchor x, anchor y, six floats each) from (x, y) in direction (dx, dy):
	 * ahead of it, and of those the nearest, a place off to the side counting more than one straight ahead.
	 */
	@Nullable
	public static float[] pick(float[] places, int count, double x, double y, int dx, int dy) {
		float[] best = null;
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			int o = i * 6;
			float rx = places[o], ry = places[o + 1], rw = places[o + 2], rh = places[o + 3];
			// Where the pointer is now is not somewhere to go.
			if (x >= rx && x <= rx + rw && y >= ry && y <= ry + rh) continue;
			double ddx = places[o + 4] - x, ddy = places[o + 5] - y;
			double along = ddx * dx + ddy * dy;
			if (along <= 1.0) continue;
			double across = Math.abs(dx != 0 ? ddy : ddx);
			// A place across the pointer's line is straight ahead, however far its middle is to the side.
			if (dx != 0 ? y >= ry && y <= ry + rh : x >= rx && x <= rx + rw) across = 0.0;
			double score = along + across * 2.0;
			if (score < bestScore) {
				bestScore = score;
				best = new float[]{places[o + 4], places[o + 5]};
			}
		}
		return best;
	}
}
