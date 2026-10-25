package dev.v1zir.visuals.module.impl.hud;

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
import java.util.ArrayList;
import java.util.List;

/** Top bar: logo, player, server, FPS, ping and clock in one pill. */
public final class WatermarkModule extends HudModule {
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

	public final BoolSetting showName = add(new BoolSetting("Name", true));
	public final BoolSetting showServer = add(new BoolSetting("Server", true));
	public final BoolSetting showFps = add(new BoolSetting("FPS", true));
	public final BoolSetting showPing = add(new BoolSetting("Ping", true));
	public final BoolSetting showTime = add(new BoolSetting("Time", true));

	public WatermarkModule() {
		super("Watermark", "Logo with session information", 0.008f, 0.012f);
		setSubCategory("Info");
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
		List<String[]> sections = new ArrayList<>();
		if (showName.on()) sections.add(new String[]{Icons.str(Icons.USER), mc.getSession().getUsername()});
		if (showServer.on()) {
			ServerInfo server = mc.getCurrentServerEntry();
			String name = mc.isInSingleplayer() ? "Singleplayer" : server != null ? server.address : "Offline";
			sections.add(new String[]{Icons.str(Icons.SERVER), name});
		}
		if (showFps.on()) sections.add(new String[]{Icons.str(Icons.MONITOR), mc.getCurrentFps() + " fps"});
		if (showPing.on() && !mc.isInSingleplayer() && mc.getNetworkHandler() != null && mc.player != null) {
			PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
			if (entry != null) sections.add(new String[]{Icons.str(Icons.WIFI), entry.getLatency() + " ms"});
		}
		if (showTime.on()) sections.add(new String[]{Icons.str(Icons.CLOCK), LocalTime.now().format(CLOCK)});

		String title = "V1zir";
		float logo = h;
		float titleW = bold.width(title, 8f);
		float w = logo + 4f + titleW + 8f;
		for (String[] s : sections) w += 10f + 11f + font.width(s[1], size);
		w += 4f;
		width = w;
		height = h;

		panel(ctx, x, y, w, h, h / 2f);
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		Logo.draw(ctx, x + 3f, y + 3f, h - 6f);
		float cx = x + logo + 3f;
		Render2D.textMiddle(ctx, bold, title, cx, y + h / 2f, 8f, Render2D.TextStyle.of(accent).gradient(accent2));
		cx += titleW + 6f;
		for (String[] s : sections) {
			Render2D.rect(ctx, cx, y + 5f, 1f, h - 10f, ColorUtil.withAlpha(0xFFFFFF, 30));
			cx += 6f;
			Render2D.icon(ctx, s[0].codePointAt(0), cx + 4f, y + h / 2f, 8f, Theme.accentWave(cx * 0.004f));
			cx += 11f;
			Render2D.textMiddle(ctx, font, s[1], cx, y + h / 2f, size, Theme.text());
			cx += font.width(s[1], size) + 4f;
		}
	}
}
