package dev.v1zir.visuals.browser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * CI's check of the Browser HUD in a real game (Loom's production run, -Dv1zir.prodcheck=true): whether the browser's
 * libraries are there and where the loader took them from, then Chromium downloaded and started, its progress
 * printed every second, a page painted, a notification from a page received (Discord alerts), keys typed into a page,
 * and the game closed. Lines start with V1ZIR-PROD.
 */
public final class BrowserCheck {
	/** Ten minutes at most for the download and the start. */
	private static final int TIMEOUT_TICKS = 20 * 60 * 10;
	/** One field that takes focus, in a form that goes to example.com with what was typed in it as q. */
	private static final String TYPING_PAGE = "data:text/html," + java.net.URLEncoder.encode(
		"<form action='https://example.com/'><input name='q' autofocus></form>", java.nio.charset.StandardCharsets.UTF_8)
		.replace("+", "%20");
	private static final String[] CLASSES = {"net.dimaskama.mcef.api.MCEFApi", "me.friwi.jcefmaven.IProgressHandler",
		"me.friwi.jcefmaven.CefAppBuilder", "org.cef.CefApp", "org.cef.browser.CefBrowser"};

	private static int ticks = -1;
	private static int painted;
	/** Ticks since the test notification was sent, or -1 before and after. */
	private static int noted = -1;
	/** Ticks since the typing page was asked for, or -1 before. */
	private static int typing = -1;
	private static String last = "";

	private BrowserCheck() {
	}

	public static void register() {
		if (!Boolean.getBoolean("v1zir.prodcheck")) return;
		// Errors in Chromium's callbacks are printed by JNI to System.err, which the game's logging loses on that thread:
		// for the check it writes straight to the process's error stream.
		System.setErr(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err), true,
			java.nio.charset.StandardCharsets.UTF_8));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			boolean all = true;
			for (String name : CLASSES) {
				String found;
				try {
					Class.forName(name, false, BrowserCheck.class.getClassLoader());
					found = "found";
				} catch (Throwable t) {
					found = "MISSING (" + t + ")";
					all = false;
				}
				print("class " + name + ": " + found);
			}
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
				String id = mod.getMetadata().getId();
				if (!id.contains("jcef") && !id.contains("mcef")) continue;
				String parent = mod.getOrigin().getKind() == net.fabricmc.loader.api.metadata.ModOrigin.Kind.NESTED
					? " nested in " + mod.getOrigin().getParentModId() : "";
				print("mod " + id + " " + mod.getMetadata().getVersion().getFriendlyString() + parent);
			}
			print(all ? "classes ok" : "classes missing");
			ticks = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(BrowserCheck::tick);
	}

	private static void tick(MinecraftClient client) {
		if (ticks < 0) return;
		ticks++;
		if (ticks < 40) return;
		// Asked every tick, as the HUD window does every frame: the page opens once Chromium is ready.
		Browser.start(Browser.GOOGLE);
		String status = Browser.ready() ? "ready" : Browser.status();
		float progress = Browser.progress();
		String line = status + (progress >= 0f ? String.format(Locale.ROOT, " (%.0f%%)", progress * 100f) : "");
		if (ticks % 20 == 0 && !line.equals(last)) {
			print("browser " + line);
			last = line;
		}
		if (Browser.ready()) {
			Browser.resize(640, 360);
			Browser.fitWidth(1280);
			DiscordAlerts.tick(true);
			if (Browser.view() != null && ++painted == 40) {
				print("browser painted " + Browser.url());
				// A notification from a page, as Discord's would come: it must reach the mod by the message router.
				Browser.execute(DiscordAlerts.SHIM + "new Notification('CI check',{body:'Discord alerts work'});");
				noted = 0;
			}
			if (noted >= 0 && DiscordAlerts.received() > 0) {
				print("discord alert received");
				noted = -1;
				Browser.load(TYPING_PAGE);
				typing = 0;
			} else if (noted >= 0 && ++noted > 400) {
				finish(client, "browser ok, but the test notification did not arrive");
				return;
			}
			if (typing >= 0) type(client);
		}
		if (status.startsWith("Browser failed")) finish(client, "browser failed: " + status);
		if (ticks > TIMEOUT_TICKS) finish(client, "browser timed out: " + line);
	}

	/**
	 * Typing as in the full screen browser, on a page of one field that takes focus: "abc", Ctrl+A, "v1zirx", Backspace
	 * and Enter, which sends the form. The address it goes to shows what arrived: q=v1zir when every key worked.
	 */
	private static void type(MinecraftClient client) {
		typing++;
		if (typing == 60) {
			Browser.focus(true);
			typeText("abc");
			press(GLFW.GLFW_KEY_A, 30, GLFW.GLFW_MOD_CONTROL);
			typeText("v1zirx");
			press(GLFW.GLFW_KEY_BACKSPACE, 14, 0);
			press(GLFW.GLFW_KEY_ENTER, 28, 0);
			print("keys sent " + (BrowserKeys.works() ? "with scan codes" : "MCEF's way"));
		}
		if (typing <= 60) return;
		String url = Browser.url();
		if (url.contains("v1zir")) {
			String verdict = url.contains("abcv1zir") ? "Ctrl+A did NOT select the text"
				: url.contains("v1zirx") ? "Backspace did NOT work" : "Ctrl+A, letters, Backspace and Enter all work";
			print("typing reached the page: " + verdict + " (" + url + ")");
			finish(client, "browser ok");
		} else if (typing > 60 + 300) {
			print("typing did not reach the page (" + url + ")");
			finish(client, "browser ok");
		}
	}

	/** Letters and digits as the game sends them: the key down, its character, the key up. */
	private static void typeText(String text) {
		for (char c : text.toCharArray()) {
			KeyInput key = new KeyInput(Character.toUpperCase(c), 0, 0);
			Browser.keyPressed(key);
			Browser.charTyped(new CharInput(c, 0));
			Browser.keyReleased(key);
		}
	}

	private static void press(int key, int scanCode, int modifiers) {
		KeyInput input = new KeyInput(key, scanCode, modifiers);
		Browser.keyPressed(input);
		Browser.keyReleased(input);
	}

	private static void finish(MinecraftClient client, String result) {
		if (ticks < 0) return;
		// Each focus a page asks for while it already has it (FocusLoop): without the cut, an overflowed stack.
		print("focus loops cut: " + FocusLoop.cut());
		print(result);
		ticks = -1;
		Browser.close();
		client.scheduleStop();
	}

	private static void print(String line) {
		System.out.println("V1ZIR-PROD " + line);
	}
}
