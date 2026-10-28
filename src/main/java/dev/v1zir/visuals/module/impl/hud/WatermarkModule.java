package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.gui.clickgui.Label;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Logo;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Top bar: logo, player, server, FPS, ping and clock in one pill. */
public final class WatermarkModule extends HudModule {
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

	public final BoolSetting showName = add(new BoolSetting("Name", true));
	public final BoolSetting showServer = add(new BoolSetting("Server", true));
	public final BoolSetting showFps = add(new BoolSetting("FPS", true));
	public final BoolSetting showPing = add(new BoolSetting("Ping", true));
	public final BoolSetting showTime = add(new BoolSetting("Time", true));

	private static final String TITLE = "V1zir";
	private static final int SECTIONS = 5;
	private static final Render2D.TextStyle TITLE_STYLE = new Render2D.TextStyle();
	/** This frame's sections (icon and text); texts are cached strings, so building them allocates nothing. */
	private final int[] icons = new int[SECTIONS];
	private final String[] texts = new String[SECTIONS];
	private final Label[] textWidth = new Label[SECTIONS];
	private final Label titleWidth = new Label();
	private final NumberText fps = new NumberText(0, " fps");
	private final NumberText ping = new NumberText(0, " ms");
	private long clockMinute = -1L;
	private String clockText = "";

	public WatermarkModule() {
		super("Watermark", "Logo with session information", 0.008f, 0.012f);
		setSubCategory("Info");
		for (int i = 0; i < SECTIONS; i++) textWidth[i] = new Label();
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		SdfFont bold = Fonts.bold();
		SdfFont font = Fonts.medium();
		float size = 7.5f;
		float h = 18f;
		int count = 0;
		if (showName.on()) count = section(count, Icons.USER, mc.getSession().getUsername());
		if (showServer.on()) {
			ServerInfo server = mc.getCurrentServerEntry();
			count = section(count, Icons.SERVER, mc.isInSingleplayer() ? "Singleplayer" : server != null ? server.address : "Offline");
		}
		if (showFps.on()) count = section(count, Icons.MONITOR, fps.set(mc.getCurrentFps()));
		if (showPing.on() && !mc.isInSingleplayer() && mc.getNetworkHandler() != null && mc.player != null) {
			PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
			if (entry != null) count = section(count, Icons.WIFI, ping.set(entry.getLatency()));
		}
		if (showTime.on()) count = section(count, Icons.CLOCK, clock());

		float logo = h;
		float titleW = titleWidth.width(bold, TITLE, 8f);
		float w = logo + 4f + titleW + 8f;
		for (int i = 0; i < count; i++) w += 10f + 11f + textWidth[i].width(font, texts[i], size);
		w += 4f;
		width = w;
		height = h;

		panel(ctx, x, y, w, h, h / 2f);
		TITLE_STYLE.color = Theme.accent();
		TITLE_STYLE.colorEnd = Theme.accent2();
		Logo.draw(ctx, x + 3f, y + 3f, h - 6f);
		float cx = x + logo + 3f;
		Render2D.textMiddle(ctx, bold, TITLE, cx, y + h / 2f, 8f, TITLE_STYLE);
		cx += titleW + 6f;
		for (int i = 0; i < count; i++) {
			Render2D.rect(ctx, cx, y + 5f, 1f, h - 10f, ColorUtil.withAlpha(0xFFFFFF, 30));
			cx += 6f;
			Render2D.icon(ctx, icons[i], cx + 4f, y + h / 2f, 8f, Theme.accentWave(cx * 0.004f));
			cx += 11f;
			Render2D.textMiddle(ctx, font, texts[i], cx, y + h / 2f, size, Theme.text());
			cx += textWidth[i].width(font, texts[i], size) + 4f;
		}
	}

	private int section(int index, int icon, String text) {
		icons[index] = icon;
		texts[index] = text;
		return index + 1;
	}

	/** The local time as HH:mm, formatted once a minute. */
	private String clock() {
		long minute = System.currentTimeMillis() / 60_000L;
		if (minute != clockMinute) {
			clockMinute = minute;
			clockText = LocalTime.now().format(CLOCK);
		}
		return clockText;
	}
}
