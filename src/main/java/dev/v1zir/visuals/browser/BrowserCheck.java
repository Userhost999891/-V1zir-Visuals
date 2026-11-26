package dev.v1zir.visuals.browser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * CI's check of the Browser HUD in a real game (Loom's production run, -Dv1zir.prodcheck=true): whether the browser's
 * libraries are there and where the loader took them from, then Chromium downloaded and started, its progress
 * printed every second, and the game closed. Lines start with V1ZIR-PROD.
 */
public final class BrowserCheck {
	/** Ten minutes at most for the download and the start. */
	private static final int TIMEOUT_TICKS = 20 * 60 * 10;
	private static final String[] CLASSES = {"net.dimaskama.mcef.api.MCEFApi", "me.friwi.jcefmaven.IProgressHandler",
		"me.friwi.jcefmaven.CefAppBuilder", "org.cef.CefApp", "org.cef.browser.CefBrowser"};

	private static int ticks = -1;
	private static int painted;
	/** Ticks since the test notification was sent, or -1 before. */
	private static int noted = -1;
	private static String last = "";

	private BrowserCheck() {
	}

	public static void register() {
		if (!Boolean.getBoolean("v1zir.prodcheck")) return;
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
				// Keys as the full screen browser sends them: Backspace, Enter and Ctrl+A, with their scan codes.
				Browser.focus(true);
				for (int[] key : new int[][]{{GLFW.GLFW_KEY_BACKSPACE, 14, 0}, {GLFW.GLFW_KEY_ENTER, 28, 0}, {GLFW.GLFW_KEY_A, 30, GLFW.GLFW_MOD_CONTROL}}) {
					KeyInput input = new KeyInput(key[0], key[1], key[2]);
					Browser.keyPressed(input);
					Browser.keyReleased(input);
				}
				print("keys sent " + (BrowserKeys.works() ? "with scan codes" : "MCEF's way"));
				finish(client, "browser ok");
			} else if (noted >= 0 && ++noted > 400) {
				finish(client, "browser ok, but the test notification did not arrive");
			}
		}
		if (status.startsWith("Browser failed")) finish(client, "browser failed: " + status);
		if (ticks > TIMEOUT_TICKS) finish(client, "browser timed out: " + line);
	}

	private static void finish(MinecraftClient client, String result) {
		if (ticks < 0) return;
		print(result);
		ticks = -1;
		Browser.close();
		client.scheduleStop();
	}

	private static void print(String line) {
		System.out.println("V1ZIR-PROD " + line);
	}
}
