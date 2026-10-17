package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Coordinates pill with the facing direction. */
public final class CoordinatesModule extends HudModule {
	private static final String[] FACING = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	public final BoolSetting facing = add(new BoolSetting("Facing", true));

	public CoordinatesModule() {
		super("Coordinates", "Shows your position", 0.008f, 0.985f);
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		if (mc.player == null) return;
		SdfFont font = Fonts.medium();
		SdfFont bold = Fonts.semibold();
		float size = 7.5f;
		float h = 16f;
		String[] labels = {"X", "Y", "Z"};
		String[] values = {
			String.valueOf(MathHelper.floor(mc.player.getX())),
			String.valueOf(MathHelper.floor(mc.player.getY())),
			String.valueOf(MathHelper.floor(mc.player.getZ()))
		};
		float w = 20f;
		for (int i = 0; i < 3; i++) w += bold.width(labels[i], size) + 3f + font.width(values[i], size) + 7f;
		String dir = null;
		if (facing.on()) {
			int index = Math.floorMod(Math.round(MathHelper.wrapDegrees(mc.player.getYaw()) / 45f), 8);
			dir = FACING[index];
			w += bold.width(dir, size) + 10f;
		}
		width = w;
		height = h;
		panel(ctx, x, y, w, h, h / 2f);
		Render2D.icon(ctx, Icons.NAVIGATION, x + 10f, y + h / 2f, 8f, Theme.accent());
		float cx = x + 19f;
		for (int i = 0; i < 3; i++) {
			cx += Render2D.textMiddle(ctx, bold, labels[i], cx, y + h / 2f, size, Theme.accentWave(i * 0.2f)) + 3f;
			cx += Render2D.textMiddle(ctx, font, values[i], cx, y + h / 2f, size, Theme.text()) + 7f;
		}
		if (dir != null) {
			Render2D.rect(ctx, cx - 3f, y + 4f, 1f, h - 8f, 0x33FFFFFF);
			Render2D.textMiddle(ctx, bold, dir, cx + 3f, y + h / 2f, size, Theme.textDim());
		}
	}
}
