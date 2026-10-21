package dev.v1zir.visuals.gui.hud;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Small pill under the crosshair that shows the zoom factor or the camera distance while it changes. */
public final class CameraIndicator {
	private static final long HOLD_NANOS = 900_000_000L;
	private static final float TEXT_SIZE = 7f;
	private static final Anim VISIBLE = new Anim(0f, 12f);
	private static final Anim FILL = new Anim(0f, 14f);
	private static int icon;
	private static String text = "";
	private static float textWidth;
	private static float fraction;
	private static long shownAt;

	private CameraIndicator() {
	}

	/** Called when the value changes (not every frame), so building the label here is fine. */
	public static void show(int icon, String text, float fraction) {
		CameraIndicator.icon = icon;
		CameraIndicator.text = text;
		CameraIndicator.textWidth = Fonts.semibold().width(text, TEXT_SIZE);
		CameraIndicator.fraction = Anim.clamp01(fraction);
		shownAt = System.nanoTime();
	}

	public static void render(DrawContext ctx, int screenWidth, int screenHeight) {
		float v = VISIBLE.target(System.nanoTime() - shownAt < HOLD_NANOS ? 1f : 0f).get();
		if (v < 0.01f) return;
		SdfFont font = Fonts.semibold();
		float h = 16f;
		float w = textWidth + 32f;
		float cx = screenWidth / 2f;
		float cy = screenHeight / 2f + 28f;
		float x = cx - w / 2f;
		float y = cy - h / 2f;
		float s = 0.92f + 0.08f * v;
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(cx, cy);
		ctx.getMatrices().scale(s, s);
		ctx.getMatrices().translate(-cx, -cy);
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * v);
		int accent = Theme.accent();
		Render2D.shadow(ctx, x, y + 1f, w, h, h / 2f, 9f, 0x55000000);
		Render2D.panel(ctx, x, y, w, h, h / 2f);
		Render2D.circle(ctx, x + 9f, cy, 6f, ColorUtil.withAlpha(accent, 50));
		Render2D.icon(ctx, icon, x + 9f, cy, 7f, accent);
		Render2D.textMiddle(ctx, font, text, x + 19f, cy - 0.5f, TEXT_SIZE, Theme.text());
		float bar = (w - 26f) * FILL.target(fraction).get();
		Render2D.round(ctx, x + 19f, y + h - 3.5f, w - 26f, 1.2f, 0.6f, ColorUtil.withAlpha(0xFFFFFF, 22));
		Render2D.gradientH(ctx, x + 19f, y + h - 3.5f, Math.max(1.2f, bar), 1.2f, 0.6f, accent, Theme.accent2());
		Render2D.setAlpha(alpha);
		ctx.getMatrices().popMatrix();
	}
}
