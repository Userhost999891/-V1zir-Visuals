package dev.v1zir.visuals.module.impl.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.browser.Browser;
import dev.v1zir.visuals.browser.DiscordAlerts;
import dev.v1zir.visuals.gui.messages.MessageBanners;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.gui.browser.BrowserScreen;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.texture.TextureSetup;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * A small Chromium browser on the HUD, as MCEF gives: a live view of the page in a window, with Search, YouTube and
 * Discord at hand in its bar. With the chat open, a click on the page opens it full screen to use it (BrowserScreen);
 * Escape brings it back to the HUD. Chromium is MCEF Modern's, bundled in the mod (the first start downloads it).
 */
public final class BrowserModule extends HudModule {
	/** Room kept between the window and the edges of the game's window. */
	private static final float MARGIN = 6f;
	private static final float BAR = 14f;
	private static final String[] LINKS = {"Google", "YouTube", "Discord"};
	private static final int[] LINK_ICONS = {Icons.SEARCH, Icons.PLAY, Icons.MESSAGE_CIRCLE};

	public final ModeSetting home = add(new ModeSetting("Home", "YouTube", LINKS));
	/** The page's width as a share of the game window's; its height follows at 16:9, so it looks the same at any window size. */
	public final NumberSetting size = add(new NumberSetting("Width", 30, 12, 70, 1).suffix("%"));
	/**
	 * How wide the page is laid out in the window, scaled down to fit it: a page keeps the layout it has on a computer
	 * (Discord, YouTube) instead of a squeezed one with scrollbars. Lower shows the text larger.
	 */
	public final NumberSetting pageWidth = add(new NumberSetting("Page width", 1280, 640, 1920, 40).suffix(" px"));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 1.0, 0.4, 1.0, 0.05));
	/**
	 * Messages written to the player on Discord as banners in the game (DiscordAlerts): Discord's app runs in a hidden
	 * page while the browser is on, logged in once in the browser, and its notifications (direct messages and
	 * mentions, as the account's settings say) show as banners; a click on one opens that conversation full screen.
	 */
	public final BoolSetting discordAlerts = add(new BoolSetting("Discord alerts", true));
	/** Opens the browser full screen from the game, besides a click on it with the chat open. */
	public final KeySetting openKey = add(new KeySetting("Full screen key", GLFW.GLFW_KEY_UNKNOWN));

	/** Where the page was drawn last (HUD coordinates), and where the quick links are. */
	private float viewX;
	private float viewY;
	private float viewW;
	private float viewH;
	private final float[] linkX = new float[LINKS.length];
	private final float[] linkW = new float[LINKS.length];
	private float linkY;

	public BrowserModule() {
		super("Browser", "A small Chromium browser on the HUD; click it with the chat open to use it full screen", 0.995f, 0.1f);
		setSubCategory("Media");
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void onEnable() {
		Browser.start(Browser.home(home.get()));
	}

	@Override
	protected void onDisable() {
		if (mc.currentScreen instanceof BrowserScreen) mc.setScreen(null);
		Browser.close();
	}

	@Override
	public void onTick() {
		DiscordAlerts.tick(discordAlerts.on());
	}

	@Override
	public void onKeyPressed(int key) {
		if (openKey.isSet() && key == openKey.key()) openFullScreen();
	}

	/** Discord full screen in the browser (a click on a Discord banner); Discord's banners go. */
	public static void openDiscord() {
		BrowserModule module = ModuleManager.get(BrowserModule.class);
		if (module == null || !module.isEnabled() || !Browser.ready()) return;
		if (!host(Browser.url()).endsWith("discord.com")) Browser.load(Browser.DISCORD);
		MessageBanners.clearDiscord();
		if (!(mc.currentScreen instanceof BrowserScreen)) module.openFullScreen();
	}

	/** Opens the page full screen, growing out of the HUD window. */
	public void openFullScreen() {
		if (!Browser.ready()) return;
		mc.setScreen(new BrowserScreen(viewX, viewY, viewW, viewH));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
		for (int i = 0; i < LINKS.length; i++) {
			if (mx >= linkX[i] && mx <= linkX[i] + linkW[i] && my >= linkY && my <= linkY + 10f) {
				Browser.load(Browser.home(LINKS[i]));
				return true;
			}
		}
		if (mx >= viewX && mx <= viewX + viewW && my >= viewY && my <= viewY + viewH) {
			openFullScreen();
			return true;
		}
		return false;
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		Browser.start(Browser.home(home.get()));
		// Sized to the game window: a share of its width, the page at 16:9, never more than the window has room for.
		float screenW = mc.getWindow().getScaledWidth();
		float screenH = mc.getWindow().getScaledHeight();
		float pageW = Math.max(96f, screenW * size.f() / 100f);
		pageW = Math.min(pageW, screenW - MARGIN * 2f - 6f);
		float pageH = pageW * 9f / 16f;
		float roomH = screenH - MARGIN * 2f - BAR - 3f;
		if (pageH > roomH) {
			pageH = Math.max(54f, roomH);
			pageW = pageH * 16f / 9f;
		}
		width = pageW + 6f;
		height = pageH + BAR + 3f;
		Render2D.setAlpha(opacity.f());
		panel(ctx, x, y, width, height, 7f);
		// The bar: the quick links, then where the page is.
		boolean pointer = mc.currentScreen instanceof ChatScreen || editing();
		float mx = pointer ? (float) mc.mouse.getScaledX(mc.getWindow()) : -1f;
		float my = pointer ? (float) mc.mouse.getScaledY(mc.getWindow()) : -1f;
		float lx = x + 4f;
		linkY = y + 2f;
		String current = Browser.url();
		for (int i = 0; i < LINKS.length; i++) {
			float w = Fonts.medium().width(LINKS[i], 5.5f) + 15f;
			boolean hover = mx >= lx && mx <= lx + w && my >= linkY && my <= linkY + 10f;
			boolean here = current.startsWith(Browser.home(LINKS[i]));
			Render2D.round(ctx, lx, linkY, w, 10f, 5f, here ? ColorUtil.withAlpha(Theme.accent(), 70) : hover ? 0x22FFFFFF : 0x10FFFFFF);
			Render2D.icon(ctx, LINK_ICONS[i], lx + 5.5f, linkY + 5f, 6f, here ? Theme.accent() : Theme.textDim());
			Render2D.textMiddle(ctx, Fonts.medium(), LINKS[i], lx + 10f, linkY + 5f, 5.5f, here ? Theme.text() : Theme.textDim());
			// Unread direct messages and mentions on Discord, from its title.
			int unread = "Discord".equals(LINKS[i]) ? DiscordAlerts.unread() : 0;
			if (unread > 0) {
				String count = unread > 99 ? "99+" : String.valueOf(unread);
				float bw = Math.max(8f, Fonts.semibold().width(count, 4.8f) + 4f);
				Render2D.round(ctx, lx + w - bw / 2f - 1f, linkY - 2.5f, bw, 7f, 3.5f, 0xFFED4245);
				Render2D.textCentered(ctx, Fonts.semibold(), count, lx + w - 1f, Render2D.middle(Fonts.semibold(), linkY + 1f, 4.8f), 4.8f, 0xFFFFFFFF);
			}
			linkX[i] = lx;
			linkW[i] = w;
			lx += w + 3f;
		}
		String host = host(current);
		float hostW = x + width - 5f - lx;
		if (hostW > 20f) {
			String shown = Fonts.medium().trim(host, 5f, hostW);
			Render2D.textMiddle(ctx, Fonts.medium(), shown, x + width - 5f - Fonts.medium().width(shown, 5f), linkY + 5f, 5f, Theme.textFaint());
		}
		viewX = x + 3f;
		viewY = y + BAR;
		viewW = width - 6f;
		viewH = height - BAR - 3f;
		// The page at the HUD window's own resolution (full screen sets its own size while it is open).
		if (!(mc.currentScreen instanceof BrowserScreen)) {
			double scale = mc.getWindow().getScaleFactor();
			Browser.resize((int) Math.round(viewW * scale), (int) Math.round(viewH * scale));
			Browser.fitWidth(pageWidth.i());
		}
		GpuTextureView view = Browser.view();
		if (view != null) {
			Render2D.image(ctx, TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)), viewX, viewY, viewW, viewH, 5f, 0xFFFFFFFF);
			if (pointer && mx >= viewX && mx <= viewX + viewW && my >= viewY && my <= viewY + viewH) {
				Render2D.outline(ctx, viewX, viewY, viewW, viewH, 5f, 1.2f, ColorUtil.withAlpha(Theme.accent(), 200));
				Render2D.textCentered(ctx, Fonts.semibold(), "Click to open", viewX + viewW / 2f, viewY + viewH - 9f, 6f, 0xEEFFFFFF);
			}
		} else {
			loading(ctx);
		}
		Render2D.setAlpha(1f);
	}

	/**
	 * The window while the browser starts: how far the download (or unpacking, or start) of Chromium has got, in big
	 * numbers and a bar, with the time left; or why it failed.
	 */
	private void loading(DrawContext ctx) {
		Render2D.round(ctx, viewX, viewY, viewW, viewH, 5f, 0xFF0B0B0D);
		float cx = viewX + viewW / 2f;
		float cy = viewY + viewH / 2f;
		String failure = Browser.failure();
		if (failure != null) {
			Render2D.icon(ctx, Icons.INFO, cx, cy - 14f, 12f, 0xFFFF6B6B);
			Render2D.textCentered(ctx, Fonts.semibold(), "The browser could not start", cx, cy - 3f, 6f, Theme.text());
			List<String> lines = wrap(failure, 5f, viewW - 16f);
			for (int i = 0; i < Math.min(3, lines.size()); i++) {
				Render2D.textCentered(ctx, Fonts.medium(), lines.get(i), cx, cy + 7f + i * 7f, 5f, Theme.textDim());
			}
			return;
		}
		float progress = Browser.progress();
		boolean measured = progress >= 0f;
		float s = Math.min(1.4f, viewH / 112f);
		if (measured) {
			Render2D.textCentered(ctx, Fonts.semibold(), Math.round(progress * 100f) + "%", cx, cy - 13f * s, 15f * s, Theme.text());
		} else {
			Render2D.icon(ctx, Icons.GLOBE, cx, cy - 13f * s, 14f * s, Theme.textDim());
		}
		Render2D.textCentered(ctx, Fonts.medium(), Browser.stage(), cx, cy + 1f * s, 6f, Theme.textDim());
		float bw = viewW * 0.72f;
		float bh = 4f;
		float bx = cx - bw / 2f;
		float by = cy + 8f * s;
		Render2D.round(ctx, bx, by, bw, bh, bh / 2f, 0x22FFFFFF);
		if (measured) {
			Render2D.round(ctx, bx, by, Math.max(bh, bw * progress), bh, bh / 2f, Theme.accent());
		} else {
			// No measure yet: a segment sweeping along the bar.
			float seg = bw * 0.3f;
			float t = (V1zir.time() * 0.55f) % 1f;
			float from = Math.max(bx, bx - seg + (bw + seg) * t);
			float to = Math.min(bx + bw, bx + (bw + seg) * t);
			if (to - from > 1f) Render2D.round(ctx, from, by, to - from, bh, bh / 2f, Theme.accent());
		}
		int left = Browser.secondsLeft();
		String detail;
		if (Browser.downloading()) {
			detail = "About 150 MB, only the first time" + (left >= 0 ? " \u00b7 " + timeLeft(left) : "");
		} else {
			detail = left >= 0 ? timeLeft(left) : "Chromium starts in the background";
		}
		Render2D.textCentered(ctx, Fonts.medium(), Fonts.medium().trim(detail, 5f, viewW - 12f), cx, by + bh + 7f, 5f, Theme.textFaint());
	}

	/** Words over lines no wider than max (a word longer than a line is cut short). */
	private static List<String> wrap(String text, float size, float max) {
		List<String> lines = new ArrayList<>();
		String line = "";
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && Fonts.medium().width(next, size) > max) {
				lines.add(Fonts.medium().trim(line, size, max));
				line = word;
			} else {
				line = next;
			}
		}
		if (!line.isEmpty()) lines.add(Fonts.medium().trim(line, size, max));
		return lines;
	}

	private static String timeLeft(int seconds) {
		if (seconds < 5) return "almost done";
		if (seconds < 60) return "about " + (seconds + 4) / 5 * 5 + " s left";
		int minutes = Math.round(seconds / 60f);
		return "about " + minutes + " min left";
	}

	private static String host(String url) {
		try {
			String host = URI.create(url).getHost();
			if (host != null) return host.startsWith("www.") ? host.substring(4) : host;
		} catch (IllegalArgumentException ignored) {
		}
		return url;
	}
}
