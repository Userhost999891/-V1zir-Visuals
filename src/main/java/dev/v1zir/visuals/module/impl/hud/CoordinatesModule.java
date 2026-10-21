package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.gui.clickgui.Label;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Coordinates pill with the facing direction and the axes it moves along, plus a second pill with
 * the matching position in the other dimension (Nether in the Overworld and the other way round).
 */
public final class CoordinatesModule extends HudModule {
	private static final String[] LABELS = {"X", "Y", "Z"};
	private static final String[] FACING = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
	/** How the coordinates change when walking that way. */
	private static final String[] AXES = {"+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"};
	private static final float SIZE = 7.5f;
	private static final float SMALL = 7f;
	private static final int NETHER = 0xFFFF7A45;
	private static final int OVERWORLD = 0xFF5BC0FF;

	public final BoolSetting facing = add(new BoolSetting("Facing", true));
	public final BoolSetting converted = add(new BoolSetting("Nether coords", true));

	private final NumberText[] position = {new NumberText(0, ""), new NumberText(0, ""), new NumberText(0, "")};
	private final NumberText[] other = {new NumberText(0, ""), new NumberText(0, "")};
	private final Label[] labels = {new Label(), new Label(), new Label()};
	private final Label[] otherLabels = {new Label(), new Label()};
	private final Label facingWidth = new Label();
	private final Label axesWidth = new Label();

	public CoordinatesModule() {
		super("Coordinates", "Position, facing and Nether coordinates", 0.008f, 0.985f);
		setSubCategory("Info");
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		if (mc.player == null || mc.world == null) return;
		SdfFont font = Fonts.medium();
		SdfFont bold = Fonts.semibold();
		double px = mc.player.getX();
		double pz = mc.player.getZ();
		position[0].set(MathHelper.floor(px));
		position[1].set(MathHelper.floor(mc.player.getY()));
		position[2].set(MathHelper.floor(pz));

		float h = 16f;
		float w = 20f;
		for (int i = 0; i < 3; i++) w += labels[i].width(bold, LABELS[i], SIZE) + 3f + position[i].width(font, SIZE) + 7f;
		int dir = Math.floorMod(Math.round(MathHelper.wrapDegrees(mc.player.getYaw()) / 45f), 8);
		if (facing.on()) w += facingWidth.width(bold, FACING[dir], SIZE) + 3f + axesWidth.width(font, AXES[dir], 6.5f) + 10f;

		// Overworld and Nether positions differ by a factor of 8; the End has no counterpart.
		RegistryKey<World> dimension = mc.world.getRegistryKey();
		boolean inNether = dimension == World.NETHER;
		boolean showOther = converted.on() && (inNether || dimension == World.OVERWORLD);
		float h2 = 14f;
		float w2 = 0f;
		if (showOther) {
			double scale = inNether ? 8.0 : 0.125;
			other[0].set(MathHelper.floor(px * scale));
			other[1].set(MathHelper.floor(pz * scale));
			w2 = 18f + otherLabels[0].width(bold, LABELS[0], SMALL) + 3f + other[0].width(font, SMALL) + 7f
				+ otherLabels[1].width(bold, LABELS[2], SMALL) + 3f + other[1].width(font, SMALL) + 6f;
		}
		width = Math.max(w, w2);
		height = showOther ? h + 3f + h2 : h;

		boolean right = anchoredRight();
		float rx = right ? x + width - w : x;
		panel(ctx, rx, y, w, h, h / 2f);
		Render2D.icon(ctx, Icons.NAVIGATION, rx + 10f, y + h / 2f, 8f, Theme.accent());
		float cx = rx + 19f;
		for (int i = 0; i < 3; i++) {
			cx += Render2D.textMiddle(ctx, bold, LABELS[i], cx, y + h / 2f, SIZE, Theme.accentWave(i * 0.2f)) + 3f;
			cx += Render2D.textMiddle(ctx, font, position[i].text(), cx, y + h / 2f, SIZE, Theme.text()) + 7f;
		}
		if (facing.on()) {
			Render2D.rect(ctx, cx - 3f, y + 4f, 1f, h - 8f, 0x33FFFFFF);
			cx += 3f + Render2D.textMiddle(ctx, bold, FACING[dir], cx + 3f, y + h / 2f, SIZE, Theme.textDim()) + 3f;
			Render2D.textMiddle(ctx, font, AXES[dir], cx, y + h / 2f, 6.5f, Theme.textFaint());
		}

		if (showOther) {
			float oy = y + h + 3f;
			float ox = right ? x + width - w2 : x;
			int tint = inNether ? OVERWORLD : NETHER;
			panel(ctx, ox, oy, w2, h2, h2 / 2f);
			Render2D.icon(ctx, inNether ? Icons.EARTH : Icons.FLAME, ox + 9f, oy + h2 / 2f, 7.5f, tint);
			cx = ox + 17f;
			cx += Render2D.textMiddle(ctx, bold, LABELS[0], cx, oy + h2 / 2f, SMALL, tint) + 3f;
			cx += Render2D.textMiddle(ctx, font, other[0].text(), cx, oy + h2 / 2f, SMALL, Theme.textDim()) + 7f;
			cx += Render2D.textMiddle(ctx, bold, LABELS[2], cx, oy + h2 / 2f, SMALL, tint) + 3f;
			Render2D.textMiddle(ctx, font, other[1].text(), cx, oy + h2 / 2f, SMALL, Theme.textDim());
		}
	}
}
