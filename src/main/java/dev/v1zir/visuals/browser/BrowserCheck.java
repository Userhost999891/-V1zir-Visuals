package dev.v1zir.visuals.browser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;

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
		if (ticks == 40) Browser.start(Browser.GOOGLE);
		if (ticks < 40) return;
		String status = Browser.ready() ? "ready" : Browser.status();
		float progress = Browser.progress();
		String line = status + (progress >= 0f ? String.format(Locale.ROOT, " (%.0f%%)", progress * 100f) : "");
		if (ticks % 20 == 0 && !line.equals(last)) {
			print("browser " + line);
			last = line;
		}
		if (Browser.ready()) {
			Browser.resize(640, 360);
			if (Browser.view() != null && ++painted == 40) {
				print("browser painted " + Browser.url());
				finish(client, "browser ok");
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
