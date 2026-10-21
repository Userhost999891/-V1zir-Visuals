package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Smooth, resolution independent crosshair replacing the vanilla one. */
public final class CrosshairModule extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Cross", "Cross", "Dot", "Circle", "Cross + Dot"));
	public final NumberSetting size = add(new NumberSetting("Size", 4.0, 1.0, 12.0, 0.5));
	public final NumberSetting gap = add(new NumberSetting("Gap", 2.0, 0.0, 8.0, 0.5));
	public final NumberSetting thickness = add(new NumberSetting("Thickness", 1.0, 0.5, 3.0, 0.25));
	public final BoolSetting dynamic = add(new BoolSetting("Dynamic gap", true));
	public final BoolSetting outline = add(new BoolSetting("Outline", true));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", false));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFFFF).visibleIf(() -> !themeColor.on()));

	private final Anim spread = new Anim(0f, 10f);

	public CrosshairModule() {
		super("Crosshair", "Custom crosshair", Category.EFFECTS);
		setSubCategory("Combat");
	}

	public void render(DrawContext ctx, float tickDelta) {
		if (mc.player == null || !mc.options.getPerspective().isFirstPerson()) return;
		float cx = ctx.getScaledWindowWidth() / 2f;
		float cy = ctx.getScaledWindowHeight() / 2f;
		double speed = mc.player.getVelocity().horizontalLength();
		spread.target(dynamic.on() ? (float) Math.min(speed * 18.0, 6.0) + (mc.player.isOnGround() ? 0f : 2.5f) : 0f);
		float g = gap.f() + spread.get();
		float s = size.f();
		float t = thickness.f();
		int c = themeColor.on() ? Theme.accent() : color.argb();
		int o = ColorUtil.withAlpha(0x000000, Math.round(ColorUtil.alpha(c) * 0.6f));
		boolean cross = style.is("Cross") || style.is("Cross + Dot");
		boolean dot = style.is("Dot") || style.is("Cross + Dot");
		if (cross) {
			bar(ctx, cx - t / 2f, cy - g - s, t, s, c, o);
			bar(ctx, cx - t / 2f, cy + g, t, s, c, o);
			bar(ctx, cx - g - s, cy - t / 2f, s, t, c, o);
			bar(ctx, cx + g, cy - t / 2f, s, t, c, o);
		}
		if (dot) {
			float r = Math.max(t, 1f);
			if (outline.on()) Render2D.circle(ctx, cx, cy, r + 0.7f, o);
			Render2D.circle(ctx, cx, cy, r, c);
		}
		if (style.is("Circle")) {
			float r = g + s * 0.6f;
			if (outline.on()) Render2D.ring(ctx, cx, cy, r + 0.5f, t + 1f, o);
			Render2D.ring(ctx, cx, cy, r, t, c);
			Render2D.circle(ctx, cx, cy, Math.max(0.6f, t * 0.6f), c);
		}
	}

	private void bar(DrawContext ctx, float x, float y, float w, float h, int c, int o) {
		if (outline.on()) Render2D.round(ctx, x - 0.6f, y - 0.6f, w + 1.2f, h + 1.2f, 0.8f, o);
		Render2D.round(ctx, x, y, w, h, Math.min(w, h) / 2f, c);
	}
}
