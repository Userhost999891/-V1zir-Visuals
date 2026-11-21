package dev.v1zir.visuals.gui.messages;

import dev.v1zir.visuals.chat.Messages;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.ui.ShapeElement;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;

/**
 * Where Messages shows outside its own screen: the banners drawn over every screen (and clickable there), and a round
 * Messages button on the left of the pause menu, with the number of unread messages on it.
 */
public final class MessagesUi {
	private static final Anim HOVER = new Anim(0f, 20f);
	/**
	 * The pause menu's Messages button as the game's keys and a controller's D-pad know it: a button of the screen's own,
	 * over the round one and never drawn (PressableWidgetMixin), so the focus goes to it (left of the menu's buttons)
	 * and Enter or Cross opens Messages; the round button lights up while it has the focus.
	 */
	private static WeakReference<ButtonWidget> pauseKey = new WeakReference<>(null);

	private MessagesUi() {
	}

	/** Whether this is the pause menu's hidden Messages button (drawn as the round one, not as a button). */
	public static boolean isPauseKey(Object widget) {
		return widget != null && widget == pauseKey.get();
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof GameMenuScreen && Messages.service() != null) {
				float r = radius(screen);
				int size = Math.round(r * 2f);
				ButtonWidget key = ButtonWidget.builder(Text.literal("Messages"), b -> Messages.open(screen, null, null))
					.dimensions(Math.round(centerX(screen) - r), Math.round(centerY(screen) - r), size, size).build();
				Screens.getButtons(screen).add(key);
				pauseKey = new WeakReference<>(key);
			}
			ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> {
				if (s instanceof GameMenuScreen) renderPauseButton(ctx, s, mouseX, mouseY);
				MessageBanners.render(ctx, s.width, mouseX, mouseY, true);
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
				if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
				if (MessageBanners.click(click.x(), click.y())) return false;
				if (s instanceof GameMenuScreen && overPauseButton(s, click.x(), click.y())) {
					Messages.open(s, null, null);
					return false;
				}
				return true;
			});
		});
	}

	// ------------------------------------------------------------------ pause menu

	private static float radius(Screen screen) {
		return Math.max(11f, Math.min(15f, screen.height * 0.032f));
	}

	private static float centerX(Screen screen) {
		return 14f + radius(screen);
	}

	private static float centerY(Screen screen) {
		return screen.height / 2f - 6f;
	}

	private static boolean overPauseButton(Screen screen, double mx, double my) {
		double dx = mx - centerX(screen);
		double dy = my - centerY(screen);
		float r = radius(screen);
		return dx * dx + dy * dy <= r * r;
	}

	/** A round frosted button like the main menu's, with its name under it and the unread count on its corner. */
	private static void renderPauseButton(DrawContext ctx, Screen screen, int mouseX, int mouseY) {
		if (Messages.service() == null) return;
		float r0 = radius(screen);
		float cx = centerX(screen);
		float cy = centerY(screen);
		ButtonWidget key = pauseKey.get();
		boolean focused = key != null && key.isFocused();
		float h = HOVER.target(overPauseButton(screen, mouseX, mouseY) || focused ? 1f : 0f).get();
		float r = r0 * (1f + 0.05f * h);
		float d = r * 2f;
		Render2D.shadow(ctx, cx - r, cy - r + 1.5f + h, d, d, r, r * 0.8f, ColorUtil.withAlpha(0x000000, Math.round(80 + 25 * h)));
		int base = Theme.preset().base();
		int fill = ColorUtil.lerp(ColorUtil.withAlpha(base, 130), ColorUtil.withAlpha(ColorUtil.lerp(base, 0xFFFFFF, 0.3f), 150), h);
		if (BackdropBlur.available()) Render2D.glass(ctx, cx - r, cy - r, d, d, r, 0, ShapeElement.MATTE, fill);
		else Render2D.matte(ctx, cx - r, cy - r, d, d, r, ColorUtil.withAlpha(fill, Math.max(ColorUtil.alpha(fill), 190)), 0);
		int icon = ColorUtil.lerp(0xD9FFFFFF, 0xFFFFFFFF, h);
		if (focused) {
			// In focus from the keys or a controller: white with a dark icon, as a console marks what is in focus.
			Render2D.circle(ctx, cx, cy, r, ColorUtil.withAlpha(0xFFFFFF, Math.round(255 * h)));
			icon = ColorUtil.lerp(0xFFFFFFFF, 0xFF121215, h);
		} else if (h > 0.01f) {
			Render2D.ring(ctx, cx, cy, r - 0.5f, 1f, ColorUtil.withAlpha(Theme.accent(), Math.round(150 * h)));
		}
		Render2D.icon(ctx, Icons.MESSAGE_CIRCLE, cx, cy, r * 0.95f, icon);
		badge(ctx, cx + r * 0.72f, cy - r * 0.72f, Messages.unread());
		SdfFont font = Fonts.medium();
		Render2D.textCentered(ctx, font, "Messages", cx, Render2D.middle(font, cy + r + 8f, 6.2f), 6.2f, ColorUtil.lerp(0xB3FFFFFF, 0xFFFFFFFF, h));
	}

	/** The unread count on a button's corner, nothing when there is none. */
	public static void badge(DrawContext ctx, float cx, float cy, int count) {
		if (count <= 0) return;
		String text = count > 99 ? "99+" : String.valueOf(count);
		SdfFont font = Fonts.semibold();
		float size = 5.4f;
		float w = Math.max(10f, font.width(text, size) + 6f);
		Render2D.round(ctx, cx - w / 2f - 1f, cy - 6f, w + 2f, 12f, 6f, 0xFF15171C);
		Render2D.round(ctx, cx - w / 2f, cy - 5f, w, 10f, 5f, Theme.accent());
		Render2D.textCentered(ctx, font, text, cx, Render2D.middle(font, cy, size), size, 0xFFFFFFFF);
	}
}
