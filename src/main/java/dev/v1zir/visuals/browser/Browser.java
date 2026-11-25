package dev.v1zir.visuals.browser;

import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.V1zir;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The one Chromium browser of the Browser HUD, run by MCEF Modern (DimasKama, LGPL-2.1, bundled in the mod's jar).
 * MCEF starts only when the browser is first wanted: the first time, it downloads Chromium for this system (about 150
 * MB, into config/mcef-modern), which status() reports. Everything here runs on the render thread; coordinates are
 * the browser's own pixels.
 */
public final class Browser {
	public static final String GOOGLE = "https://www.google.com";
	public static final String YOUTUBE = "https://www.youtube.com";
	public static final String DISCORD = "https://discord.com/app";

	@Nullable
	private static MCEFBrowser browser;
	@Nullable
	private static String failure;
	private static boolean started;
	private static String first = GOOGLE;
	private static int width = -1;
	private static int height = -1;
	/** Progress and when it was sampled, and how fast it goes (per second, smoothed), for secondsLeft. */
	private static float sampleProgress = -1f;
	private static long sampleTime;
	private static float rate = -1f;

	private Browser() {
	}

	/** The address of a home page by its name (Google, YouTube, Discord). */
	public static String home(String name) {
		return switch (name) {
			case "YouTube" -> YOUTUBE;
			case "Discord" -> DISCORD;
			default -> GOOGLE;
		};
	}

	/** Asks for the browser at url: starts MCEF the first time, and opens the page once it is ready. */
	public static void start(String url) {
		if (browser != null || failure != null) return;
		if (!started) first = url;
		try {
			started = true;
			CompletableFuture<MCEFApi> future = MCEFApi.initialize().getFuture();
			if (future.isCompletedExceptionally()) {
				failure = reason(future);
				V1zir.LOGGER.warn("Browser could not start: {}", failure);
				return;
			}
			MCEFApi api = future.getNow(null);
			if (api == null) return;
			browser = api.createBrowser(first, false);
			width = -1;
			height = -1;
		} catch (Throwable t) {
			failure = describe(t);
			V1zir.LOGGER.warn("Browser could not start", t);
		}
	}

	public static boolean ready() {
		return browser != null;
	}

	/** What is happening while the browser is not ready yet (downloading, starting), or why it failed; empty once ready. */
	public static String status() {
		if (failure != null) return "Browser failed: " + failure;
		if (browser != null) return "";
		float progress = progress();
		return stage() + (progress >= 0f ? String.format(Locale.ROOT, " %d%%", Math.round(progress * 100f)) : "");
	}

	/** Why the browser could not start, or null. */
	@Nullable
	public static String failure() {
		return failure;
	}

	/** The step the browser's start is at: downloading, unpacking or installing Chromium, or starting it. */
	public static String stage() {
		if (!started) return "Starting the browser";
		try {
			return switch (MCEFApi.initialize().getStage()) {
				case DOWNLOADING -> "Downloading Chromium";
				case EXTRACTING -> "Unpacking Chromium";
				case INSTALL -> "Installing Chromium";
				case INITIALIZING -> "Starting Chromium";
				case DONE -> "Opening the page";
				default -> "Looking for Chromium";
			};
		} catch (Throwable t) {
			return "Starting the browser";
		}
	}

	/** Whether Chromium is being downloaded, which happens only the first time. */
	public static boolean downloading() {
		if (!started || browser != null || failure != null) return false;
		try {
			return MCEFApi.initialize().getStage() == MCEFApi.Initialization.Stage.DOWNLOADING;
		} catch (Throwable t) {
			return false;
		}
	}

	/** 0 to 1 while Chromium downloads, unpacks or installs, -1 when there is no measure. */
	public static float progress() {
		if (browser != null || failure != null || !started) return -1f;
		try {
			float pct = MCEFApi.initialize().getPercentage();
			return pct < 0f ? -1f : Math.min(1f, pct / 100f);
		} catch (Throwable t) {
			return -1f;
		}
	}

	/**
	 * Roughly how many seconds the current step has left, from how fast its progress has gone lately; -1 while that is
	 * not known yet. Called every frame the loading window is drawn.
	 */
	public static int secondsLeft() {
		float p = progress();
		long now = System.nanoTime();
		if (p < 0f || sampleProgress < 0f || p < sampleProgress) {
			// No measure, the first one, or a new step that starts again from zero.
			sampleProgress = p;
			sampleTime = now;
			rate = -1f;
			return -1;
		}
		double elapsed = (now - sampleTime) / 1.0e9;
		if (elapsed >= 0.5) {
			float latest = (float) ((p - sampleProgress) / elapsed);
			rate = rate < 0f ? latest : rate * 0.75f + latest * 0.25f;
			sampleProgress = p;
			sampleTime = now;
		}
		if (rate <= 1.0e-4f) return -1;
		return Math.round((1f - p) / rate);
	}

	/** The browser's page as a texture, or null before it has painted. */
	@Nullable
	public static GpuTextureView view() {
		return browser != null ? browser.getTextureView() : null;
	}

	/** The page's size in pixels; only an actual change goes to Chromium. */
	public static void resize(int w, int h) {
		if (browser == null || w < 8 || h < 8 || w == width && h == height) return;
		width = w;
		height = h;
		browser.resize(w, h);
	}

	public static void close() {
		if (browser != null) {
			try {
				browser.close();
			} catch (Throwable t) {
				V1zir.LOGGER.warn("Browser did not close cleanly", t);
			}
		}
		browser = null;
		width = -1;
		height = -1;
	}

	// ------------------------------------------------------------------ navigation

	public static void load(String url) {
		if (browser == null) {
			first = url;
			return;
		}
		browser.getCefBrowser().loadURL(url);
	}

	/** What was typed in the address bar: an address as it is (https:// added if missing), anything else searched for. */
	public static void go(String typed) {
		String text = typed.trim();
		if (text.isEmpty()) return;
		boolean address = text.startsWith("http://") || text.startsWith("https://")
			|| !text.contains(" ") && text.contains(".") && !text.endsWith(".");
		if (address) load(text.startsWith("http") ? text : "https://" + text);
		else load(GOOGLE + "/search?q=" + java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8));
	}

	public static void back() {
		if (browser != null && browser.getCefBrowser().canGoBack()) browser.getCefBrowser().goBack();
	}

	public static void forward() {
		if (browser != null && browser.getCefBrowser().canGoForward()) browser.getCefBrowser().goForward();
	}

	public static void reload() {
		if (browser != null) browser.getCefBrowser().reload();
	}

	public static String url() {
		if (browser == null) return first;
		String url = browser.getCefBrowser().getURL();
		return url == null || url.isEmpty() ? first : url;
	}

	// ------------------------------------------------------------------ input (the browser's pixels)

	public static void focus(boolean focused) {
		if (browser != null) browser.setFocus(focused);
	}

	public static void mouseMoved(int x, int y) {
		if (browser != null) browser.onMouseMoved(x, y);
	}

	public static void mouseClicked(int x, int y, MouseInput button, boolean doubled) {
		if (browser != null) browser.onMouseClicked(new Click(x, y, button), doubled);
	}

	public static void mouseReleased(int x, int y, MouseInput button) {
		if (browser != null) browser.onMouseReleased(new Click(x, y, button));
	}

	public static void mouseScrolled(int x, int y, double amount) {
		if (browser != null) browser.onMouseScrolled(x, y, amount);
	}

	public static void keyPressed(KeyInput input) {
		if (browser != null) browser.onKeyPressed(input);
	}

	public static void keyReleased(KeyInput input) {
		if (browser != null) browser.onKeyReleased(input);
	}

	public static void charTyped(CharInput input) {
		if (browser != null) browser.onCharTyped(input);
	}

	private static String reason(CompletableFuture<?> future) {
		try {
			future.join();
			return "unknown";
		} catch (Throwable t) {
			return describe(t);
		}
	}

	/** The innermost cause of a failure (MCEF and the future wrap it twice), as its kind and message. */
	private static String describe(Throwable t) {
		Throwable root = t;
		while (root.getCause() != null && root.getCause() != root) root = root.getCause();
		return root.getClass().getSimpleName() + (root.getMessage() != null ? ": " + root.getMessage() : "");
	}
}
