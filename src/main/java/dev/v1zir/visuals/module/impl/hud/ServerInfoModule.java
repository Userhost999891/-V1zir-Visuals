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
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;

import java.util.Arrays;

/**
 * Server address, ping and the server's tick rate. The tick rate is estimated from how far world
 * time advances between the time packets a server sends (vanilla: every second).
 */
public final class ServerInfoModule extends HudModule {
	private static final float W = 124f;
	private static final float ROW = 12f;
	/** Measuring windows averaged into the estimate, about a second each. */
	private static final int SAMPLES = 6;
	private static final long WINDOW = 900_000_000L;
	private static final long SILENCE = 30_000_000_000L;

	public final BoolSetting address = add(new BoolSetting("Address", true));
	public final BoolSetting ping = add(new BoolSetting("Ping", true));
	public final BoolSetting tps = add(new BoolSetting("TPS", true));

	// Written by the network thread (see onWorldTime), read while rendering.
	private final long[] sampleTicks = new long[SAMPLES];
	private final long[] sampleNanos = new long[SAMPLES];
	private int sampleNext;
	private Object lastPacket;
	private long windowStart;
	private long windowTime;
	private volatile long lastNanos;
	private volatile float tickRate = -1f;

	private final NumberText pingText = new NumberText(0, " ms");
	private final NumberText tpsText = new NumberText(1, "");
	private final Label dashWidth = new Label();
	private Object connection;
	private int latency;
	private String addressSource;
	private String addressShown;
	private SdfFont addressFont;

	public ServerInfoModule() {
		super("Server Info", "Server, ping and TPS", 0.008f, 0.62f);
		setSubCategory("Info");
		width = W;
		height = 18f + 3f * ROW + 3f;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void onEnable() {
		connection = null;
	}

	/** Called for every world time packet: first on the network thread, then again on the client thread. */
	public synchronized void onWorldTime(Object packet, long worldTime) {
		if (packet == lastPacket) return;
		lastPacket = packet;
		long now = System.nanoTime();
		lastNanos = now;
		// Time going back or a long silence is a new world rather than lag: start measuring anew.
		if (windowStart == 0L || worldTime < windowTime || now - windowStart > SILENCE) {
			windowStart = now;
			windowTime = worldTime;
			return;
		}
		// Packets closer together than a window (bursts, servers that send every tick) are measured as one.
		if (now - windowStart < WINDOW) return;
		sampleTicks[sampleNext] = worldTime - windowTime;
		sampleNanos[sampleNext] = now - windowStart;
		sampleNext = (sampleNext + 1) % SAMPLES;
		windowStart = now;
		windowTime = worldTime;
		// Ticks over time of all windows together, so a late packet and the early one after it cancel out.
		long ticks = 0L;
		long nanos = 0L;
		for (int i = 0; i < SAMPLES; i++) {
			ticks += sampleTicks[i];
			nanos += sampleNanos[i];
		}
		tickRate = Math.min(20f, ticks / (nanos / 1.0e9f));
	}

	private synchronized void resetTickRate() {
		lastPacket = null;
		lastNanos = 0L;
		windowStart = 0L;
		Arrays.fill(sampleTicks, 0L);
		Arrays.fill(sampleNanos, 0L);
		tickRate = -1f;
	}

	@Override
	public void onTick() {
		ClientPlayNetworkHandler handler = mc.getNetworkHandler();
		if (handler != connection) {
			connection = handler;
			resetTickRate();
		}
		PlayerListEntry entry = handler != null && mc.player != null ? handler.getPlayerListEntry(mc.player.getUuid()) : null;
		latency = entry != null ? entry.getLatency() : 0;
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		SdfFont font = Fonts.medium();
		SdfFont bold = Fonts.semibold();
		boolean singleplayer = mc.isInSingleplayer();
		boolean showPing = ping.on() && (!singleplayer || editing());
		int rows = (address.on() ? 1 : 0) + (showPing ? 1 : 0) + (tps.on() ? 1 : 0);
		float h = 18f + Math.max(1, rows) * ROW + 3f;
		width = W;
		height = h;
		float cy = headerPanel(ctx, x, y, W, h, Icons.SERVER, "Server");

		if (address.on()) {
			ServerInfo server = mc.getCurrentServerEntry();
			String source = singleplayer ? "Singleplayer" : server != null ? server.address : "Offline";
			if (source != addressSource || font != addressFont) {
				addressSource = source;
				addressFont = font;
				addressShown = font.trim(source, 7f, W - 24f);
			}
			Render2D.icon(ctx, Icons.GLOBE, x + 10f, cy + ROW / 2f, 7.5f, Theme.accentWave(0f));
			Render2D.textMiddle(ctx, font, addressShown, x + 17f, cy + ROW / 2f, 7f, Theme.text());
			cy += ROW;
		}
		if (showPing) {
			int color = quality((latency - 60f) / 240f);
			label(ctx, font, Icons.WIFI, "Ping", x, cy, 0.15f);
			pingText.set(latency);
			float tw = pingText.width(bold, 7f);
			float tx = x + W - 7f - tw;
			Render2D.textMiddle(ctx, bold, pingText.text(), tx, cy + ROW / 2f, 7f, color);
			// Signal bars, as many lit as the connection deserves.
			int level = latency < 60 ? 4 : latency < 120 ? 3 : latency < 200 ? 2 : 1;
			for (int i = 0; i < 4; i++) {
				float bh = 2.5f + i * 1.5f;
				Render2D.round(ctx, tx - 14f + i * 2.5f, cy + ROW / 2f + 3.5f - bh, 1.5f, bh, 0.75f,
					i < level ? color : ColorUtil.withAlpha(0xFFFFFF, 40));
			}
			cy += ROW;
		}
		if (tps.on()) {
			float rate = tickRate;
			// A server that stops sending time has stalled: past a second of grace for a late packet,
			// the estimate falls steadily while it stays silent.
			float silent = lastNanos == 0L ? 0f : (System.nanoTime() - lastNanos) / 1.0e9f;
			if (rate >= 0f && silent > 2f) rate = Math.min(rate, 20f / (silent - 1f));
			// No estimate yet (the first seconds on a server): the editor previews a healthy server.
			if (rate < 0f && editing()) rate = 20f;
			label(ctx, font, Icons.CPU, "TPS", x, cy, 0.3f);
			String text = rate < 0f ? "-" : tpsText.set(rate);
			float tw = rate < 0f ? dashWidth.width(bold, text, 7f) : tpsText.width(bold, 7f);
			Render2D.textMiddle(ctx, bold, text, x + W - 7f - tw, cy + ROW / 2f, 7f,
				rate < 0f ? Theme.textFaint() : quality((20f - rate) / 10f));
		}
	}

	private static void label(DrawContext ctx, SdfFont font, int icon, String label, float x, float cy, float wave) {
		Render2D.icon(ctx, icon, x + 10f, cy + ROW / 2f, 7.5f, Theme.accentWave(wave));
		Render2D.textMiddle(ctx, font, label, x + 17f, cy + ROW / 2f, 7f, Theme.textDim());
	}
}
