package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.gui.clickgui.Label;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Movement speed pill: blocks per second (or km/h), smoothed, with a tiny graph of the last seconds. */
public final class SpeedModule extends HudModule {
	private static final float GRAPH_W = 34f;
	/** Anything faster per tick is a teleport or a respawn, not movement. */
	private static final double TELEPORT = 10.0;

	public final ModeSetting unit = add(new ModeSetting("Unit", "b/s", "b/s", "km/h"));
	public final BoolSetting vertical = add(new BoolSetting("Vertical", false));
	public final BoolSetting graph = add(new BoolSetting("Graph", true));

	private final Sparkline history = new Sparkline(24);
	private final Anim shown = new Anim(0f, 6f);
	private final Anim panelWidth = new Anim(0f, 10f);
	private final NumberText value = new NumberText(1, "");
	private final Label unitWidth = new Label();
	private boolean tracking;
	private double lastX;
	private double lastY;
	private double lastZ;
	private float speed;

	public SpeedModule() {
		super("Speed", "Movement speed with a small graph", 0.008f, 0.9f);
		setSubCategory("Info");
		width = 90f;
		height = 18f;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void onEnable() {
		tracking = false;
		speed = 0f;
		history.clear();
	}

	@Override
	public void onTick() {
		if (mc.player == null) {
			tracking = false;
			return;
		}
		double x = mc.player.getX();
		double y = mc.player.getY();
		double z = mc.player.getZ();
		if (tracking) {
			double dx = x - lastX;
			double dy = vertical.on() ? y - lastY : 0.0;
			double dz = z - lastZ;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			speed = distance > TELEPORT ? 0f : (float) (distance * 20.0);
			history.push(speed);
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		tracking = true;
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.medium();
		float h = 18f;
		float factor = unit.is("km/h") ? 3.6f : 1f;
		value.set(shown.target(speed * factor).get());
		float valueW = value.width(bold, 8f);
		String unitText = unit.get();
		float unitW = unitWidth.width(font, unitText, 6.5f);
		float target = 19f + valueW + 2.5f + unitW + (graph.on() ? 8f + GRAPH_W + 6f : 7f);
		// The pill eases to its new width instead of twitching with every digit.
		if (panelWidth.target() == 0f) panelWidth.snap(target);
		float w = panelWidth.target(target).get();
		width = w;
		height = h;

		panel(ctx, x, y, w, h, h / 2f);
		int accent = Theme.accent();
		Render2D.icon(ctx, Icons.GAUGE, x + 10f, y + h / 2f, 8f, accent);
		float cx = x + 19f;
		cx += Render2D.textMiddle(ctx, bold, value.text(), cx, y + h / 2f, 8f, Theme.text()) + 2.5f;
		Render2D.textMiddle(ctx, font, unitText, cx, y + h / 2f, 6.5f, Theme.textDim());
		if (graph.on()) {
			float gx = x + w - 6f - GRAPH_W;
			float gy = y + 4.5f;
			float gh = h - 9f;
			Render2D.rect(ctx, gx - 4f, y + 5f, 1f, h - 10f, ColorUtil.withAlpha(0xFFFFFF, 30));
			Render2D.round(ctx, gx, gy + gh - 1f, GRAPH_W, 1f, 0.5f, ColorUtil.withAlpha(0xFFFFFF, 18));
			// Speeds come once per tick; the tick progress scrolls the bars smoothly in between.
			history.draw(ctx, gx, gy, GRAPH_W, gh, 6f, tickDelta, ColorUtil.withAlpha(Theme.accent2(), 170), accent);
		}
	}
}
