package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.media.AlbumArt;
import dev.v1zir.visuals.media.Media;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Now playing widget ({@link MediaCard}): album art, title and artist (scrolling when too long), a
 * progress bar with elapsed and total time, the play state and prev / play / next buttons that work
 * with the chat or the HUD editor open. Card and Compact styles; the accent can follow the album art.
 */
public final class SpotifyModule extends HudModule {
	public final ModeSetting style = add(new ModeSetting("Style", "Card", "Card", "Compact"));
	public final BoolSetting albumColors = add(new BoolSetting("Album colors", true));
	public final BoolSetting controls = add(new BoolSetting("Controls", true));
	public final BoolSetting hideIdle = add(new BoolSetting("Hide when idle", true));
	public final KeySetting playPauseKey = add(new KeySetting("Play/Pause key", GLFW.GLFW_KEY_UNKNOWN));
	public final KeySetting nextKey = add(new KeySetting("Next key", GLFW.GLFW_KEY_UNKNOWN));
	public final KeySetting previousKey = add(new KeySetting("Previous key", GLFW.GLFW_KEY_UNKNOWN));

	private final Anim appear = new Anim(0f, 10f);
	private final MediaCard card = new MediaCard();

	public SpotifyModule() {
		super("Spotify", "Now playing with album art and controls", 0.008f, 0.75f);
		setSubCategory("Media");
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void onEnable() {
		Media.acquire(this);
	}

	@Override
	protected void onDisable() {
		Media.release(this);
	}

	@Override
	public void onKeyPressed(int key) {
		if (key == playPauseKey.key()) Media.command(Media.Command.PLAY_PAUSE);
		else if (key == nextKey.key()) Media.command(Media.Command.NEXT);
		else if (key == previousKey.key()) Media.command(Media.Command.PREVIOUS);
	}

	@Override
	public boolean hasContent() {
		appear.target(visible() ? 1f : 0f);
		return appear.get() > 0.01f;
	}

	private boolean visible() {
		// A problem reading the player shows even when idle cards hide, so it is not mistaken for silence.
		return Media.now().track() != null || !hideIdle.on() || editing() || Media.status() != null;
	}

	/** The pointer can only reach the widget with the chat or the HUD editor open. */
	static boolean pointerActive() {
		return mc.currentScreen instanceof ChatScreen || editing();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return card.click(mouseX, mouseY, button);
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		boolean compact = style.is("Compact");
		card.update(compact);
		appear.target(visible() ? 1f : 0f);
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		if (albumColors.on()) {
			accent = AlbumArt.color(accent);
			accent2 = ColorUtil.lerp(accent, 0xFFFFFFFF, 0.35f);
		}
		float mx = -1f;
		float my = -1f;
		if (pointerActive()) {
			mx = (float) mc.mouse.getScaledX(mc.getWindow());
			my = (float) mc.mouse.getScaledY(mc.getWindow());
		}
		boolean buttons = controls.on() && card.hasTrack();

		Render2D.setAlpha(appear.get());
		if (compact) {
			width = 168f;
			height = 24f;
			panel(ctx, x, y, width, height, height / 2f);
			card.compact(ctx, x, y, width, height, buttons, accent, accent2, mx, my);
		} else {
			width = 176f;
			height = buttons ? MediaCard.HEIGHT : MediaCard.HEIGHT_PLAIN;
			panel(ctx, x, y, width, height, 8f);
			// A wash of the album colour from the left gives the card its tint.
			Render2D.gradientH(ctx, x, y, width * 0.75f, height, 8f, ColorUtil.withAlpha(accent, 34), ColorUtil.withAlpha(accent, 0));
			float art = height - 12f;
			card.cover(ctx, x + 6f, y + 6f, art, 7f, accent, accent2);
			float tx = x + art + 14f;
			card.details(ctx, tx, y, x + width - 9f - tx, buttons, accent, accent2, mx, my);
		}
		Render2D.setAlpha(1f);
	}
}
