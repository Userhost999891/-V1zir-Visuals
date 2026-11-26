package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.media.AlbumArt;
import dev.v1zir.visuals.media.Media;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Now playing widget ({@link MediaCard}): the song's cover over a blurred copy of it filling the card,
 * title and artist (scrolling when too long), a progress bar with elapsed and total time, the play
 * state and prev / play / next buttons that work with the chat or the HUD editor open. Card and
 * Compact styles; the accent can follow the album art.
 */
public final class SpotifyModule extends HudModule {
	public final ModeSetting style = add(new ModeSetting("Style", "Card", "Card", "Compact"));
	public final BoolSetting albumColors = add(new BoolSetting("Album colors", true));
	public final BoolSetting controls = add(new BoolSetting("Controls", true));
	public final BoolSetting hideIdle = add(new BoolSetting("Hide when idle", true));
	public final KeySetting playPauseKey = add(new KeySetting("Play/Pause key", GLFW.GLFW_KEY_UNKNOWN));
	public final KeySetting nextKey = add(new KeySetting("Next key", GLFW.GLFW_KEY_UNKNOWN));
	public final KeySetting previousKey = add(new KeySetting("Previous key", GLFW.GLFW_KEY_UNKNOWN));
	/** Lines under the card saying where the song and the cover come from, to tell what goes wrong with a player. */
	public final BoolSetting debugInfo = add(new BoolSetting("Debug info", false));

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
			width = 176f;
			height = 26f;
			panel(ctx, x, y, width, height, height / 2f);
			card.compact(ctx, x, y, width, height, buttons, accent, accent2, mx, my);
		} else {
			width = 196f;
			height = buttons ? MediaCard.HEIGHT : MediaCard.HEIGHT_PLAIN;
			panel(ctx, x, y, width, height, 10f);
			// The cover, blurred, fills the card in its own colours.
			card.backdrop(ctx, x, y, width, height, 10f, accent);
			float art = height - 16f;
			card.cover(ctx, x + 8f, y + 8f, art, 8f, accent, accent2);
			float tx = x + art + 18f;
			card.details(ctx, tx, y, x + width - 10f - tx, buttons, accent, accent2, mx, my);
		}
		if (debugInfo.on()) debugLines(ctx, x, y + height + 4f);
		Render2D.setAlpha(1f);
	}

	/** What the media backend sees and which cover is in use, in small lines on a dark strip. */
	private void debugLines(DrawContext ctx, float x, float y) {
		String art = Media.now().art();
		String cover = art.isEmpty() ? "none (placeholder)" : art.startsWith("http") ? "found online" : "from the player";
		String text = Media.debug();
		String[] lines = (text.isEmpty() ? "No details from this player" : text).split("\n");
		SdfFont font = Fonts.medium();
		float size = 5.5f;
		float lineH = 7.5f;
		float w = 0f;
		String[] shown = new String[lines.length + 1];
		for (int i = 0; i < lines.length; i++) shown[i] = font.trim(lines[i], size, 320f);
		shown[lines.length] = "Cover in use: " + cover + (AlbumArt.hasCover() ? ", loaded" : ", not loaded");
		for (String line : shown) w = Math.max(w, font.width(line, size));
		float h = shown.length * lineH + 6f;
		Render2D.round(ctx, x, y, w + 10f, h, 5f, 0xB0000000);
		for (int i = 0; i < shown.length; i++) Render2D.text(ctx, font, shown[i], x + 5f, y + 3f + i * lineH, size, 0xE6FFFFFF);
	}
}
