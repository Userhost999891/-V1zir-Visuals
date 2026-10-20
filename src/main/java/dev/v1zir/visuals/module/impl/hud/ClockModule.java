package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.gui.clickgui.Label;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.TimeZone;

/** Real time and the length of the current session in one pill. */
public final class ClockModule extends HudModule {
	public final BoolSetting hours24 = add(new BoolSetting("24 hour", true));
	public final BoolSetting seconds = add(new BoolSetting("Seconds", false));
	public final BoolSetting session = add(new BoolSetting("Session", true));

	private final TimeZone zone = TimeZone.getDefault();
	private final StringBuilder sb = new StringBuilder(12);
	private final Label timeWidth = new Label();
	private final Label sessionWidth = new Label();
	private long shownTime = Long.MIN_VALUE;
	private long shownSession = -1L;
	private String time = "";
	private String sessionText = "";
	private Object connection;
	private long joined = System.currentTimeMillis();

	public ClockModule() {
		super("Clock", "Real time and session time", 0.008f, 0.09f);
		setSubCategory("Info");
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	public void onTick() {
		// The session is the connection: changing dimension keeps it, joining another server starts anew.
		if (mc.getNetworkHandler() != connection) {
			connection = mc.getNetworkHandler();
			joined = System.currentTimeMillis();
		}
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		long now = System.currentTimeMillis();
		// The text is rebuilt only when what it shows changes: every second or every minute.
		long key = (seconds.on() ? now / 1000L : now / 60000L) * 4L + (hours24.on() ? 2L : 0L) + (seconds.on() ? 1L : 0L);
		if (key != shownTime) {
			shownTime = key;
			time = formatTime(now);
		}
		long elapsed = Math.max(0L, (now - joined) / 1000L);
		if (session.on() && elapsed != shownSession) {
			shownSession = elapsed;
			sessionText = formatDuration(elapsed);
		}

		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.medium();
		float h = 18f;
		float timeW = timeWidth.width(bold, time, 8f);
		float w = h + 3f + timeW + 7f;
		float sessionW = session.on() ? sessionWidth.width(font, sessionText, 7.5f) : 0f;
		if (session.on()) w += 11f + sessionW + 7f;
		width = w;
		height = h;

		panel(ctx, x, y, w, h, h / 2f);
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		float bubble = h - 6f;
		Render2D.glow(ctx, x + 3f, y + 3f, bubble, bubble, bubble / 2f, 6f, ColorUtil.withAlpha(accent, 110), ColorUtil.withAlpha(accent2, 110));
		Render2D.round(ctx, x + 3f, y + 3f, bubble, bubble, bubble / 2f, accent, accent2, accent2, accent);
		Render2D.icon(ctx, Icons.CLOCK, x + h / 2f, y + h / 2f, 7.5f, 0xFFFFFFFF);
		float cx = x + h + 3f;
		Render2D.textMiddle(ctx, bold, time, cx, y + h / 2f, 8f, Theme.text());
		if (session.on()) {
			cx += timeW + 7f;
			Render2D.rect(ctx, cx - 3.5f, y + 5f, 1f, h - 10f, ColorUtil.withAlpha(0xFFFFFF, 30));
			Render2D.icon(ctx, Icons.TIMER, cx + 4.5f, y + h / 2f, 8f, Theme.accentWave(0.3f));
			Render2D.textMiddle(ctx, font, sessionText, cx + 11f, y + h / 2f, 7.5f, Theme.textDim());
		}
	}

	private String formatTime(long now) {
		long local = now + zone.getOffset(now);
		int secondOfDay = (int) Math.floorMod(local / 1000L, 86400L);
		int hour = secondOfDay / 3600;
		sb.setLength(0);
		if (hours24.on()) {
			two(hour);
		} else {
			sb.append(hour % 12 == 0 ? 12 : hour % 12);
		}
		sb.append(':');
		two(secondOfDay / 60 % 60);
		if (seconds.on()) {
			sb.append(':');
			two(secondOfDay % 60);
		}
		if (!hours24.on()) sb.append(hour < 12 ? " AM" : " PM");
		return sb.toString();
	}

	private String formatDuration(long elapsed) {
		sb.setLength(0);
		long hours = elapsed / 3600L;
		if (hours > 0) sb.append(hours).append(':');
		int minutes = (int) (elapsed / 60L % 60L);
		if (hours > 0) two(minutes);
		else sb.append(minutes);
		sb.append(':');
		two((int) (elapsed % 60L));
		return sb.toString();
	}

	private void two(int value) {
		if (value < 10) sb.append('0');
		sb.append(value);
	}
}
