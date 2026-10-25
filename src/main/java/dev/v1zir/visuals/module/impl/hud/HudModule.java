package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/**
 * A movable HUD element. The position is stored as a fraction of the screen: when the element
 * sits on the right half, fx describes its right edge so it grows towards the centre.
 */
public abstract class HudModule extends Module {
	private float fx;
	private float fy;
	protected float width = 40;
	protected float height = 14;

	protected HudModule(String name, String description, float fx, float fy) {
		super(name, description, Category.HUD);
		this.fx = fx;
		this.fy = fy;
	}

	public float fx() {
		return fx;
	}

	public float fy() {
		return fy;
	}

	public void setPosition(float fx, float fy) {
		this.fx = Math.max(0f, Math.min(1f, fx));
		this.fy = Math.max(0f, Math.min(1f, fy));
	}

	public float width() {
		return width;
	}

	public float height() {
		return height;
	}

	public boolean anchoredRight() {
		return fx > 0.5f;
	}

	public boolean anchoredBottom() {
		return fy > 0.5f;
	}

	public float x(int screenWidth) {
		float anchor = fx * screenWidth;
		float x = anchoredRight() ? anchor - width : anchor;
		return Math.max(2f, Math.min(screenWidth - width - 2f, x));
	}

	public float y(int screenHeight) {
		float anchor = fy * screenHeight;
		float y = anchoredBottom() ? anchor - height : anchor;
		return Math.max(2f, Math.min(screenHeight - height - 2f, y));
	}

	/** Moves the element so its top-left lands at (x, y). */
	public void moveTo(float x, float y, int screenWidth, int screenHeight) {
		float cx = x + width / 2f;
		float cy = y + height / 2f;
		float nfx = cx > screenWidth / 2f ? (x + width) / screenWidth : x / screenWidth;
		float nfy = cy > screenHeight / 2f ? (y + height) / screenHeight : y / screenHeight;
		setPosition(nfx, nfy);
	}

	public void renderAt(DrawContext ctx, float tickDelta, int screenWidth, int screenHeight) {
		render(ctx, tickDelta, x(screenWidth), y(screenHeight));
	}

	/** Draws the element at (x, y) and updates width/height. */
	protected abstract void render(DrawContext ctx, float tickDelta, float x, float y);

	/** A click on the screen while the chat or the HUD editor is open (HUD coordinates); true if the element used it. */
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	/** Whether there is something to show right now (TargetHud hides without a target). */
	public boolean hasContent() {
		return true;
	}

	// ------------------------------------------------------------------ shared drawing helpers

	protected static final float RADIUS = 5f;

	protected static void panel(DrawContext ctx, float x, float y, float w, float h) {
		panel(ctx, x, y, w, h, RADIUS);
	}

	protected static void panel(DrawContext ctx, float x, float y, float w, float h, float radius) {
		Render2D.shadow(ctx, x, y + 1f, w, h, radius, 8f, 0x55000000);
		Render2D.panel(ctx, x, y, w, h, radius);
	}

	/** Panel with a header strip carrying an icon and a title. Returns the y where content starts. */
	protected static float headerPanel(DrawContext ctx, float x, float y, float w, float h, int icon, String title) {
		panel(ctx, x, y, w, h);
		float headerH = 15f;
		Render2D.round(ctx, x, y, w, headerH, RADIUS, Render2D.CORNERS_TOP, ColorUtil.withAlpha(0xFFFFFF, 8));
		Render2D.gradientH(ctx, x + 6f, y + headerH - 0.5f, w - 12f, 1f, 0.5f,
			ColorUtil.withAlpha(Theme.accent(), 200), ColorUtil.withAlpha(Theme.accent2(), 0));
		Render2D.icon(ctx, icon, x + 10f, y + headerH / 2f, 8.5f, Theme.accent());
		Render2D.textMiddle(ctx, dev.v1zir.visuals.render.font.Fonts.semibold(), title, x + 18f, y + headerH / 2f, 7.5f, Theme.text());
		return y + headerH + 3f;
	}
}
