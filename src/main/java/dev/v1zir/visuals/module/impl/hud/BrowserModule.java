package dev.v1zir.visuals.module.impl.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.browser.Browser;
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

/**
 * A small Chromium browser on the HUD, as MCEF gives: a live view of the page in a window, with Search, YouTube and
 * Discord at hand in its bar. With the chat open, a click on the page opens it full screen to use it (BrowserScreen);
 * Escape brings it back to the HUD. Chromium is MCEF Modern's, bundled in the mod (the first start downloads it).
 */
public final class BrowserModule extends HudModule {
	private static final float BASE_W = 200f;
	private static final float BASE_H = 112.5f;
	private static final float BAR = 14f;
	private static final String[] LINKS = {"Google", "YouTube", "Discord"};
	private static final int[] LINK_ICONS = {Icons.SEARCH, Icons.PLAY, Icons.MESSAGE_CIRCLE};

	public final ModeSetting home = add(new ModeSetting("Home", "YouTube", LINKS));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.6, 2.2, 0.05).suffix("x"));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 1.0, 0.4, 1.0, 0.05));
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
	public void onKeyPressed(int key) {
		if (openKey.isSet() && key == openKey.key()) openFullScreen();
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
		float s = size.f();
		width = BASE_W * s;
		height = BASE_H * s + BAR + 3f;
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
		}
		GpuTextureView view = Browser.view();
		if (view != null) {
			Render2D.image(ctx, TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)), viewX, viewY, viewW, viewH, 5f, 0xFFFFFFFF);
			if (pointer && mx >= viewX && mx <= viewX + viewW && my >= viewY && my <= viewY + viewH) {
				Render2D.outline(ctx, viewX, viewY, viewW, viewH, 5f, 1.2f, ColorUtil.withAlpha(Theme.accent(), 200));
				Render2D.textCentered(ctx, Fonts.semibold(), "Click to open", viewX + viewW / 2f, viewY + viewH - 9f, 6f, 0xEEFFFFFF);
			}
		} else {
			Render2D.round(ctx, viewX, viewY, viewW, viewH, 5f, 0xFF0B0B0D);
			Render2D.icon(ctx, Icons.GLOBE, viewX + viewW / 2f, viewY + viewH / 2f - 8f, 14f, Theme.textDim());
			String status = Browser.status();
			Render2D.textCentered(ctx, Fonts.medium(), Fonts.medium().trim(status, 6f, viewW - 12f), viewX + viewW / 2f, viewY + viewH / 2f + 6f, 6f,
				Theme.textDim());
			float progress = Browser.progress();
			if (progress >= 0f) {
				float bw = viewW * 0.6f;
				float bx = viewX + (viewW - bw) / 2f;
				float by = viewY + viewH / 2f + 14f;
				Render2D.round(ctx, bx, by, bw, 3f, 1.5f, 0x22FFFFFF);
				Render2D.round(ctx, bx, by, Math.max(3f, bw * progress), 3f, 1.5f, Theme.accent());
			}
		}
		Render2D.setAlpha(1f);
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
