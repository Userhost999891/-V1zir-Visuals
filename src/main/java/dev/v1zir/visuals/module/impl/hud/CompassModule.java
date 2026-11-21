package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/**
 * Horizontal compass ribbon: cardinal and intercardinal letters, degrees every 15 and ticks every
 * 5, scrolling smoothly with the view and fading out towards both ends.
 */
public final class CompassModule extends HudModule {
	/** Marks every 15 degrees, starting at north. */
	private static final String[] MARKS = new String[24];
	private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
	private static final float RIBBON_H = 16f;
	private static final float PIXELS_PER_DEGREE = 1.5f;

	static {
		for (int i = 0; i < MARKS.length; i++) {
			int degrees = i * 15;
			MARKS[i] = degrees % 45 == 0 ? DIRECTIONS[degrees / 45] : Integer.toString(degrees);
		}
	}

	public final NumberSetting size = add(new NumberSetting("Width", 190, 120, 320, 10).suffix("px"));
	public final BoolSetting readout = add(new BoolSetting("Heading", true));

	private final Anim heading = new Anim(0f, 26f);
	private final NumberText degrees = new NumberText(0, "°");
	private final float[] markWidths = new float[MARKS.length];
	private final float[] directionWidths = new float[DIRECTIONS.length];
	private SdfFont markFont;
	private SdfFont cardinalFont;
	private boolean placed;

	public CompassModule() {
		super("Compass", "Compass ribbon at the top of the screen", 0.5f, 0.012f);
		setSubCategory("Info");
		width = size.f();
		height = RIBBON_H + 14f;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected boolean centered() {
		return true;
	}

	@Override
	protected void onEnable() {
		placed = false;
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		if (mc.player == null) return;
		// Minecraft's yaw 0 faces south; a compass reads 0 at north. Yaw is not wrapped, so the
		// spring follows it continuously; a jump of more than half a turn (a teleport) is taken the short way.
		float target = mc.player.getYaw(tickDelta) + 180f;
		if (!placed) {
			heading.snap(target);
			placed = true;
		}
		float current = heading.value();
		if (Math.abs(target - current) > 180f) heading.snap(current + Math.round((target - current) / 360f) * 360f);
		float yaw = heading.target(target).get();

		SdfFont font = Fonts.medium();
		SdfFont bold = Fonts.bold();
		measure(font, bold);
		float w = size.f();
		width = w;
		height = RIBBON_H + (readout.on() ? 14f : 0f);
		float cx = x + w / 2f;
		int bearing = Math.floorMod(Math.round(yaw), 360);
		int direction = Math.floorMod(Math.round(bearing / 45f), 8);
		float dw = 0f;
		float pw = 0f;
		float py = y + RIBBON_H + 2f;
		if (readout.on()) {
			degrees.set(bearing);
			dw = degrees.width(bold, 7f);
			pw = dw + 3f + directionWidths[direction] + 12f;
			// The heading pill hangs just below the ribbon: its shadow goes first so it does not shade the ribbon.
			shadow(ctx, cx - pw / 2f, py, pw, 12f, 6f);
		}
		panel(ctx, x, y, w, RIBBON_H, RIBBON_H / 2f);

		int accent = Theme.accent();
		float half = w / 2f - 9f;
		float mid = y + RIBBON_H / 2f - 0.5f;
		float range = half / PIXELS_PER_DEGREE;
		int first = (int) Math.ceil((yaw - range) / 5f);
		int last = (int) Math.floor((yaw + range) / 5f);
		for (int step = first; step <= last; step++) {
			float dx = (step * 5f - yaw) * PIXELS_PER_DEGREE;
			float edge = dx / half;
			float fade = 1f - edge * edge;
			if (fade <= 0.02f) continue;
			int angle = Math.floorMod(step * 5, 360);
			float mx = cx + dx;
			if (angle % 15 != 0) {
				Render2D.rect(ctx, mx - 0.5f, y + RIBBON_H - 5f, 1f, 2.5f, ColorUtil.withAlpha(0xFFFFFF, Math.round(70 * fade)));
				continue;
			}
			int index = angle / 15;
			boolean cardinal = angle % 90 == 0;
			boolean intercardinal = !cardinal && angle % 45 == 0;
			float textSize = cardinal ? 7.5f : intercardinal ? 6.5f : 5.5f;
			int color = angle == 0 ? accent : cardinal ? Theme.text() : intercardinal ? Theme.textDim() : Theme.textFaint();
			Render2D.textMiddle(ctx, cardinal ? bold : font, MARKS[index], mx - markWidths[index] / 2f, mid, textSize, ColorUtil.fade(color, fade));
		}
		// Centre notch on the bottom edge, where the heading is read.
		Render2D.glow(ctx, cx - 4f, y + RIBBON_H - 2.5f, 8f, 1.5f, 0.75f, 4f, ColorUtil.withAlpha(accent, 150), ColorUtil.withAlpha(Theme.accent2(), 150));
		Render2D.round(ctx, cx - 4f, y + RIBBON_H - 2.5f, 8f, 1.5f, 0.75f, accent);

		if (readout.on()) {
			float px = cx - pw / 2f;
			Render2D.panel(ctx, px, py, pw, 12f, 6f);
			Render2D.textMiddle(ctx, bold, degrees.text(), px + 6f, py + 6f, 7f, Theme.text());
			Render2D.textMiddle(ctx, font, DIRECTIONS[direction], px + 6f + dw + 3f, py + 6f, 6.5f, accent);
		}
	}

	/** Widths of the marks and directions, measured once per font instead of every frame. */
	private void measure(SdfFont font, SdfFont bold) {
		if (font == markFont && bold == cardinalFont) return;
		markFont = font;
		cardinalFont = bold;
		for (int i = 0; i < MARKS.length; i++) {
			int angle = i * 15;
			boolean cardinal = angle % 90 == 0;
			markWidths[i] = (cardinal ? bold : font).width(MARKS[i], cardinal ? 7.5f : angle % 45 == 0 ? 6.5f : 5.5f);
		}
		for (int i = 0; i < DIRECTIONS.length; i++) directionWidths[i] = font.width(DIRECTIONS[i], 6.5f);
	}
}
