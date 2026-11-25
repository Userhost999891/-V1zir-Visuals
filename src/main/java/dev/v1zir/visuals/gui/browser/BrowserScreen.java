package dev.v1zir.visuals.gui.browser;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.browser.Browser;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The Browser HUD full screen: the page grows out of its HUD window to fill the screen, with a bar over it (back,
 * forward, reload, the address, Search, YouTube and Discord, and a button to shrink it back). Clicks, scrolling and
 * typing go to the page; the address bar takes the keys while it is focused (Enter opens what was typed, or searches
 * for it). Escape shrinks the page back into the HUD. The game keeps running behind it.
 */
public final class BrowserScreen extends Screen {
	private static final float BAR = 22f;
	private static final float MARGIN = 6f;
	private static final String[] LINKS = {"Google", "YouTube", "Discord"};
	private static final int[] LINK_ICONS = {Icons.SEARCH, Icons.PLAY, Icons.MESSAGE_CIRCLE};

	private final float fromX;
	private final float fromY;
	private final float fromW;
	private final float fromH;
	private final Anim open = new Anim(0f, 9f);
	private boolean closing;
	private TextFieldWidget address;
	private String shownUrl = "";
	/** The page as laid out this frame (screen coordinates), where clicks land. */
	private float pageX;
	private float pageY;
	private float pageW;
	private float pageH;
	private final float[] buttonX = new float[7];
	private float buttonY;

	public BrowserScreen(float x, float y, float w, float h) {
		super(Text.literal("Browser"));
		fromX = x;
		fromY = y;
		fromW = Math.max(w, 40f);
		fromH = Math.max(h, 24f);
	}

	@Override
	protected void init() {
		String typed = address != null ? address.getText() : null;
		address = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.literal("Address"));
		address.setMaxLength(2048);
		address.setDrawsBackground(false);
		address.setText(typed != null ? typed : Browser.url());
		address.setCursorToStart(false);
		addSelectableChild(address);
		Browser.focus(true);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// The game shows behind, darkened as the page grows.
		Render2D.rect(ctx, 0, 0, width, height, ColorUtil.withAlpha(0, Math.round(150 * ease(open.get()))));
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		float t = ease(open.target(closing ? 0f : 1f).get());
		if (closing && t < 0.01f) {
			client.setScreen(null);
			return;
		}
		renderBackground(ctx, mouseX, mouseY, delta);
		float fullX = MARGIN;
		float fullY = MARGIN + BAR;
		float fullW = width - MARGIN * 2f;
		float fullH = height - MARGIN * 2f - BAR;
		pageX = lerp(fromX, fullX, t);
		pageY = lerp(fromY, fullY, t);
		pageW = lerp(fromW, fullW, t);
		pageH = lerp(fromH, fullH, t);
		// Once grown, the page is drawn at the screen's own resolution.
		if (t > 0.999f && !closing) {
			double scale = client.getWindow().getScaleFactor();
			Browser.resize((int) Math.round(pageW * scale), (int) Math.round(pageH * scale));
		}
		float frameY = pageY - BAR * t;
		Render2D.shadow(ctx, pageX - 3f, frameY - 3f, pageW + 6f, pageH + BAR * t + 6f, 10f, 14f, ColorUtil.withAlpha(0, Math.round(140 * t)));
		Render2D.round(ctx, pageX - 3f, frameY - 3f, pageW + 6f, pageH + BAR * t + 6f, 9f, ColorUtil.withAlpha(0xFF0B0B0E, Math.round(250 * Math.max(t, 0.6f))));
		GpuTextureView view = Browser.view();
		if (view != null) {
			Render2D.image(ctx, TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)), pageX, pageY, pageW, pageH, 6f, 0xFFFFFFFF);
		} else {
			Render2D.textCentered(ctx, Fonts.medium(), Browser.status(), pageX + pageW / 2f, pageY + pageH / 2f, 7f, Theme.textDim());
		}
		if (t > 0.6f) bar(ctx, mouseX, mouseY, delta, (t - 0.6f) / 0.4f);
	}

	/** Back, forward, reload, the address, the quick links and the shrink button, fading in as the page grows. */
	private void bar(DrawContext ctx, int mouseX, int mouseY, float delta, float alpha) {
		Render2D.setAlpha(alpha);
		buttonY = pageY - BAR + 4f;
		float x = pageX;
		int[] icons = {Icons.CHEVRON_LEFT, Icons.CHEVRON_RIGHT, Icons.REFRESH_CW};
		for (int i = 0; i < 3; i++) {
			buttonX[i] = x;
			button(ctx, x, buttonY, 15f, icons[i], mouseX, mouseY);
			x += 18f;
		}
		// The address bar.
		float linksW = 0f;
		for (String link : LINKS) linksW += Fonts.medium().width(link, 6f) + 20f;
		float fieldX = x + 2f;
		float fieldW = pageX + pageW - fieldX - linksW - 26f;
		boolean focused = address.isFocused();
		Render2D.round(ctx, fieldX, buttonY, fieldW, 15f, 7.5f, focused ? 0x26FFFFFF : 0x16FFFFFF);
		if (focused) Render2D.outline(ctx, fieldX, buttonY, fieldW, 15f, 7.5f, 1f, ColorUtil.withAlpha(Theme.accent(), 180));
		Render2D.icon(ctx, Icons.GLOBE, fieldX + 8f, buttonY + 7.5f, 7f, Theme.textDim());
		if (!focused) {
			String url = Browser.url();
			if (!url.equals(shownUrl)) {
				shownUrl = url;
				address.setText(url);
				address.setCursorToStart(false);
			}
		}
		address.setX(Math.round(fieldX + 15f));
		address.setY(Math.round(buttonY + 4f));
		address.setWidth(Math.max(20, Math.round(fieldW - 20f)));
		address.render(ctx, mouseX, mouseY, delta);
		// Search, YouTube, Discord.
		float lx = fieldX + fieldW + 4f;
		String current = Browser.url();
		for (int i = 0; i < LINKS.length; i++) {
			float w = Fonts.medium().width(LINKS[i], 6f) + 17f;
			boolean hover = mouseX >= lx && mouseX <= lx + w && mouseY >= buttonY && mouseY <= buttonY + 15f;
			boolean here = current.startsWith(Browser.home(LINKS[i]));
			Render2D.round(ctx, lx, buttonY, w, 15f, 7.5f, here ? ColorUtil.withAlpha(Theme.accent(), 80) : hover ? 0x26FFFFFF : 0x14FFFFFF);
			Render2D.icon(ctx, LINK_ICONS[i], lx + 7.5f, buttonY + 7.5f, 7f, here ? Theme.accent() : Theme.textDim());
			Render2D.textMiddle(ctx, Fonts.medium(), LINKS[i], lx + 13f, buttonY + 7.5f, 6f, here ? Theme.text() : Theme.textDim());
			buttonX[3 + i] = lx;
			lx += w + 3f;
		}
		buttonX[6] = pageX + pageW - 15f;
		button(ctx, buttonX[6], buttonY, 15f, Icons.CHEVRON_DOWN, mouseX, mouseY);
		Render2D.setAlpha(1f);
	}

	private void button(DrawContext ctx, float x, float y, float size, int icon, int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
		Render2D.round(ctx, x, y, size, size, size / 2f, hover ? 0x2AFFFFFF : 0x14FFFFFF);
		Render2D.icon(ctx, icon, x + size / 2f, y + size / 2f, 8f, hover ? Theme.text() : Theme.textDim());
	}

	private boolean onPage(double x, double y) {
		return x >= pageX && x < pageX + pageW && y >= pageY && y < pageY + pageH;
	}

	private int px(double x) {
		return (int) Math.round((x - pageX) * client.getWindow().getScaleFactor());
	}

	private int py(double y) {
		return (int) Math.round((y - pageY) * client.getWindow().getScaleFactor());
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (closing) return true;
		double x = click.x();
		double y = click.y();
		if (y >= buttonY && y <= buttonY + 15f) {
			if (hit(x, 0, 15f)) Browser.back();
			else if (hit(x, 1, 15f)) Browser.forward();
			else if (hit(x, 2, 15f)) Browser.reload();
			else if (hit(x, 6, 15f)) close();
			else {
				for (int i = 0; i < LINKS.length; i++) {
					float w = Fonts.medium().width(LINKS[i], 6f) + 17f;
					if (x >= buttonX[3 + i] && x <= buttonX[3 + i] + w) {
						Browser.load(Browser.home(LINKS[i]));
						address.setFocused(false);
						return true;
					}
				}
				boolean inField = x >= address.getX() - 15 && x <= address.getX() + address.getWidth();
				address.setFocused(inField);
				Browser.focus(!inField);
				if (inField) return super.mouseClicked(click, doubled);
			}
			return true;
		}
		if (onPage(x, y)) {
			address.setFocused(false);
			setFocused(null);
			Browser.focus(true);
			Browser.mouseClicked(px(x), py(y), click.buttonInfo(), doubled);
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	private boolean hit(double x, int i, float w) {
		return x >= buttonX[i] && x <= buttonX[i] + w;
	}

	@Override
	public boolean mouseReleased(Click click) {
		Browser.mouseReleased(px(click.x()), py(click.y()), click.buttonInfo());
		return super.mouseReleased(click);
	}

	@Override
	public void mouseMoved(double x, double y) {
		if (onPage(x, y)) Browser.mouseMoved(px(x), py(y));
		super.mouseMoved(x, y);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (onPage(x, y)) {
			Browser.mouseScrolled(px(x), py(y), vertical);
			return true;
		}
		return super.mouseScrolled(x, y, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (address.isFocused()) {
				address.setFocused(false);
				Browser.focus(true);
			} else {
				close();
			}
			return true;
		}
		if (address.isFocused()) {
			if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
				Browser.go(address.getText());
				address.setFocused(false);
				Browser.focus(true);
				return true;
			}
			return address.keyPressed(input) || super.keyPressed(input);
		}
		Browser.keyPressed(input);
		return true;
	}

	@Override
	public boolean keyReleased(KeyInput input) {
		if (!address.isFocused()) Browser.keyReleased(input);
		return true;
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (address.isFocused()) return address.charTyped(input);
		Browser.charTyped(input);
		return true;
	}

	/** Shrinks the page back into the HUD window (the screen closes when it is there). */
	@Override
	public void close() {
		if (closing) return;
		closing = true;
		Browser.focus(false);
	}

	@Override
	public void removed() {
		Browser.focus(false);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float ease(float t) {
		float c = Math.max(0f, Math.min(1f, t));
		return 1f - (1f - c) * (1f - c) * (1f - c);
	}
}
