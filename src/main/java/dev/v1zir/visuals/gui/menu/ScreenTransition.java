package dev.v1zir.visuals.gui.menu;

import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * How a screen opened from the main menu comes in: out of a dark veil, settling from a slight zoom (ScreenMixin draws it
 * around the screen). The time advances by at most a thirtieth of a second per frame, so a screen that is slow to open
 * the first time (classes loading, world or server icons decoding) still plays its whole entrance after the hitch
 * instead of jumping to its end; the veil covers the moment the hitch happens.
 */
public final class ScreenTransition {
	/** How long the main menu takes to leave, and the next screen to come in. */
	static final float LEAVE_SECONDS = 0.18f;
	private static final float ENTER_SECONDS = 0.32f;
	private static final float MAX_STEP = 1f / 30f;

	@Nullable
	private static Screen entering;
	private static float progress;
	private static long lastFrame;

	private ScreenTransition() {
	}

	/** The screen about to be set comes in with the entrance; called right before it is set. */
	static void enter(Screen screen) {
		entering = screen;
		progress = 0f;
		lastFrame = 0L;
	}

	/** Per frame, from ScreenMixin: how far this screen has come in, 1 when it is not coming in (or done). */
	public static float progress(Screen screen) {
		if (screen != entering) return 1f;
		long now = System.nanoTime();
		if (lastFrame != 0L) progress += Math.min((now - lastFrame) / 1.0e9f, MAX_STEP) / ENTER_SECONDS;
		lastFrame = now;
		if (progress >= 1f) {
			entering = null;
			return 1f;
		}
		return progress;
	}
}
