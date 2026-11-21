package dev.v1zir.visuals.optimize;

import dev.v1zir.visuals.V1zir;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Laptops with two graphics cards often start Java on the weaker one, built into the processor, and the game then gets
 * a fraction of the frame rate it could have. At start this compares the card OpenGL draws with against the cards the
 * system has (as the game's crash reports list them) and, when a dedicated one sits unused, says once how to give it
 * to Minecraft. The cards are read off the render thread (it takes a moment).
 */
public final class GpuCheck {
	/** Cards built into the processor, whatever memory they report. */
	private static final Pattern INTEGRATED = Pattern.compile(
		"radeon\\(tm\\) graphics|radeon graphics|radeon \\d{3}m|vega \\d|\\buhd\\b|iris|hd graphics|intel\\(r\\) graphics|adreno|mali");
	/** Cards of their own. */
	private static final Pattern DEDICATED = Pattern.compile("geforce|rtx|gtx|quadro|radeon rx|radeon pro|firepro|\\barc a");
	private static final long DEDICATED_VRAM = 2L << 30;

	private GpuCheck() {
	}

	/** Once the game has started (render thread, its OpenGL context current). */
	public static void check(MinecraftClient client) {
		String renderer;
		try {
			renderer = GL11.glGetString(GL11.GL_RENDERER);
		} catch (RuntimeException | LinkageError e) {
			return;
		}
		if (renderer == null || renderer.isBlank()) return;
		Thread thread = new Thread(() -> {
			try {
				List<GraphicsCard> cards = new SystemInfo().getHardware().getGraphicsCards();
				String drawing = plain(renderer);
				if (drawing.isEmpty()) return;
				GraphicsCard used = null;
				GraphicsCard unused = null;
				for (GraphicsCard card : cards) {
					String name = plain(card.getName());
					if (!name.isEmpty() && (drawing.contains(name) || name.contains(drawing))) used = card;
					else if (dedicated(card) && unused == null) unused = card;
				}
				if (unused == null || used != null && dedicated(used) || used == null && DEDICATED.matcher(renderer.toLowerCase(Locale.ROOT)).find()) return;
				String weak = used != null ? used.getName() : renderer;
				String strong = unused.getName();
				V1zir.LOGGER.warn("Minecraft draws with {} although this computer has {}: set Java to High performance in the graphics settings", weak, strong);
				// The game's own toast: it wraps the words and shows in the menus as well, for ten seconds.
				client.execute(() -> client.getToastManager().add(SystemToast.create(client, SystemToast.Type.PERIODIC_NOTIFICATION,
					Text.literal("Minecraft is on the weaker graphics card"), Text.literal(weak + " instead of " + strong + ". " + advice()))));
			} catch (Throwable e) {
				V1zir.LOGGER.debug("Could not compare the graphics cards", e);
			}
		}, "V1zir graphics card check");
		thread.setDaemon(true);
		thread.start();
	}

	/** A card of its own: not one built into the processor, and a known name or memory of its own. */
	private static boolean dedicated(GraphicsCard card) {
		String name = card.getName() == null ? "" : card.getName().toLowerCase(Locale.ROOT);
		if (INTEGRATED.matcher(name).find()) return false;
		return DEDICATED.matcher(name).find() || card.getVRam() >= DEDICATED_VRAM;
	}

	/** What to change, on this system. */
	private static String advice() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return "Windows Settings > System > Display > Graphics: add your launcher's javaw.exe (or Java), Options > High performance, then restart the game.";
		}
		return "Start the game on the dedicated card (for example with prime-run), then restart it.";
	}

	/** A card's name without marks and spaces, for comparing what OpenGL and the system call it. */
	private static String plain(@Nullable String name) {
		if (name == null) return "";
		return name.toLowerCase(Locale.ROOT).replace("(tm)", "").replace("(r)", "").replaceAll("/.*$", "").replaceAll("[^a-z0-9]", "");
	}
}
