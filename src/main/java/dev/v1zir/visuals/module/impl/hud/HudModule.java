package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * A movable HUD element. The position is stored as a fraction of the screen: when the element
 * sits on the right half, fx describes its right edge so it grows towards the centre. Centred
 * elements (see {@link #centered()}) store their middle instead.
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

	/** Elements that grow evenly to both sides, such as the compass ribbon. */
	protected boolean centered() {
		return false;
	}

	public boolean anchoredBottom() {
		return fy > 0.5f;
	}

	public float x(int screenWidth) {
		float anchor = fx * screenWidth;
		float x = centered() ? anchor - width / 2f : anchoredRight() ? anchor - width : anchor;
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
		float nfx;
		if (centered()) {
			// Snaps to the middle of the screen, so a nudged element is easy to centre again.
			nfx = Math.abs(cx - screenWidth / 2f) < 4f ? 0.5f : cx / screenWidth;
		} else {
			nfx = cx > screenWidth / 2f ? (x + width) / screenWidth : x / screenWidth;
		}
		float nfy = cy > screenHeight / 2f ? (y + height) / screenHeight : y / screenHeight;
		setPosition(nfx, nfy);
	}

	public void renderAt(DrawContext ctx, float tickDelta, int screenWidth, int screenHeight) {
		render(ctx, tickDelta, x(screenWidth), y(screenHeight));
	}

	/** Draws the element at (x, y) and updates width/height. */
	protected abstract void render(DrawContext ctx, float tickDelta, float x, float y);

	/** Whether there is something to show right now (TargetHud hides without a target). */
	public boolean hasContent() {
		return true;
	}

	/** True while the HUD editor is open: elements then show a preview instead of hiding. */
	protected static boolean editing() {
		return mc.currentScreen instanceof ClickGuiScreen screen && screen.isHudTab();
	}

	// ------------------------------------------------------------------ shared drawing helpers

	protected static final float RADIUS = 5f;
	private static final int GOOD = 0xFF5BE38A;
	private static final int WARN = 0xFFFFC94D;
	private static final int BAD = 0xFFFF5A5A;

	protected static void panel(DrawContext ctx, float x, float y, float w, float h) {
		panel(ctx, x, y, w, h, RADIUS);
	}

	protected static void panel(DrawContext ctx, float x, float y, float w, float h, float radius) {
		shadow(ctx, x, y, w, h, radius);
		Render2D.panel(ctx, x, y, w, h, radius);
	}

	/** A panel's soft drop shadow, drawn first on its own where stacked panels must not shade each other. */
	protected static void shadow(DrawContext ctx, float x, float y, float w, float h, float radius) {
		Render2D.shadow(ctx, x, y + 1f, w, h, radius, 8f, 0x55000000);
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

	/** Green when t is 0 (good), through yellow, to red at 1: ping, tick rate, durability. */
	protected static int quality(float t) {
		t = Anim.clamp01(t);
		return t < 0.5f ? ColorUtil.lerp(GOOD, WARN, t * 2f) : ColorUtil.lerp(WARN, BAD, t * 2f - 1f);
	}

	/** Draws a 16x16 item icon with its top-left at (x, y), scaled around its centre. */
	protected static void item(DrawContext ctx, ItemStack stack, float x, float y, float scale) {
		Matrix3x2fStack matrices = ctx.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x + 8f, y + 8f);
		if (scale != 1f) matrices.scale(scale, scale);
		ctx.drawItem(stack, -8, -8);
		matrices.popMatrix();
	}
}
