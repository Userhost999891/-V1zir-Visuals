package dev.v1zir.visuals.optimize;

import dev.v1zir.visuals.V1zir;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * GPU in Video Settings: picks the graphics card Minecraft runs on, among this computer's (Auto: as Windows likes), by
 * setting Windows' graphics preference for the game's Java ({@link GpuPreference}): the card of its own for High
 * performance, the one in the processor for Power saving. It takes effect when the game starts again; the button says
 * so while the card picked is not the one drawing yet. It is the first row of the game's settings list (should the game
 * not let it in there, it sits in the screen's top right corner), and a page of its own in Sodium's Video Settings
 * ({@link SodiumGpuOption}), which take the place of the game's when Sodium is there.
 */
public final class GpuOption {
	/** The choices: Windows decides, the card of its own, the card in the processor. */
	public enum Gpu {
		AUTO(GpuPreference.AUTO),
		HIGH_PERFORMANCE(GpuPreference.HIGH_PERFORMANCE),
		POWER_SAVING(GpuPreference.POWER_SAVING);

		final int preference;

		Gpu(int preference) {
			this.preference = preference;
		}

		static Gpu of(int preference) {
			for (Gpu gpu : values()) if (gpu.preference == preference) return gpu;
			return AUTO;
		}
	}

	/** Adapters that are not cards to run a game on: Windows' fallback driver, remote and virtual displays. */
	private static final Pattern NOT_A_CARD = Pattern.compile(
		"(?i)basic (display|render)|virtual|remote|parsec|citrix|vmware|virtualbox|hyper-v|spacedesk|displaylink|mirror|indirect");
	/** Windows' preference for the game's Java as read (-1 until read), and the one picked here this session (-1: none). */
	private static volatile int stored = -1;
	private static volatile int picked = -1;
	/** The Video Settings screen whose list has the row already. */
	private static WeakReference<Screen> listed = new WeakReference<>(null);

	private GpuOption() {
	}

	public static void register() {
		// Windows' preference, read once the game is up (reg.exe, a moment), so the settings show it at once.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			if (GpuPreference.supported()) background(() -> stored = GpuPreference.read());
		});
		// The fallback: a button in the top right corner when the list did not take the row.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof VideoOptionsScreen) || listed.get() == screen) return;
			int w = Math.min(160, width / 2 - client.textRenderer.getWidth(screen.getTitle()) / 2 - 14);
			if (w < 90) return;
			ButtonWidget button = button(w);
			button.setPosition(width - w - 6, 6);
			Screens.getButtons(screen).add(button);
		});
	}

	/** The row at the top of Video Settings, under a header of its own as the game's sections have (VideoOptionsScreenMixin). */
	public static void list(Screen screen, OptionListWidget body) {
		body.addHeader(Text.literal("Graphics Card"));
		body.addWidgetEntry(button(310), null);
		listed = new WeakReference<>(screen);
	}

	/** Whether this Video Settings screen has GPU as a row of its list. */
	public static boolean listedOn(Screen screen) {
		return listed.get() == screen;
	}

	/** The choice now: the one picked here, else Windows' (read now if it was not yet: a moment, once). */
	public static Gpu current() {
		if (picked >= 0) return Gpu.of(picked);
		if (stored < 0 && GpuPreference.supported()) stored = GpuPreference.read();
		return Gpu.of(Math.max(GpuPreference.AUTO, stored));
	}

	/** Picks a choice: Windows' preference is set off the render thread, and a toast says how it went. */
	public static void pick(Gpu gpu) {
		if (gpu == current()) return;
		picked = gpu.preference;
		MinecraftClient client = MinecraftClient.getInstance();
		background(() -> {
			boolean ok = GpuPreference.write(gpu.preference);
			V1zir.LOGGER.info("Graphics preference for {}: {} ({})", GpuPreference.executable(), gpu, ok ? "set" : "not set");
			client.execute(() -> client.getToastManager().add(SystemToast.create(client, SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.literal(ok ? "GPU: " + name(gpu) : "GPU not changed"),
				Text.literal(!ok ? "Windows did not take the graphics preference."
					: gpu == Gpu.AUTO ? "Windows picks the card again from the next start."
					: "Restart Minecraft: it starts on this graphics card from then on."))));
		});
	}

	/** Whether picking makes a difference here: Windows, and a card of its own besides the one in the processor. */
	public static boolean choosable() {
		return GpuPreference.supported() && card(true).isPresent() && card(false).isPresent();
	}

	/** A choice by the card it stands for, short ("RTX 5050", "Radeon 780M"), or by what it is when there is none. */
	public static String name(Gpu gpu) {
		return switch (gpu) {
			case AUTO -> "Auto";
			case HIGH_PERFORMANCE -> card(true).map(GpuOption::shortName).orElse("High performance");
			case POWER_SAVING -> card(false).map(GpuOption::shortName).orElse("Power saving");
		};
	}

	/** What a choice does, for its tooltip. */
	public static String describe(Gpu gpu) {
		String now = " Now Minecraft draws with " + (GpuCheck.drawing().isBlank() ? "an unknown card" : GpuCheck.drawing()) + ".";
		if (!GpuPreference.supported()) return "Picking the graphics card here works on Windows; elsewhere start the game on it"
			+ " (for example with prime-run)." + now;
		return switch (gpu) {
			case AUTO -> "Windows picks the graphics card for the game's Java (on a laptop often the one in the processor)." + now;
			case HIGH_PERFORMANCE -> "Minecraft starts on " + card(true).orElse("the graphics card of its own")
				+ ", the card of its own: the most frames per second. Used from the next start." + now;
			case POWER_SAVING -> "Minecraft starts on " + card(false).orElse("the graphics card in the processor")
				+ ", the card in the processor: cooler and longer on battery. Used from the next start." + now;
		};
	}

	/** The first card of the kind (of its own, or in the processor), by its full name. */
	private static Optional<String> card(boolean dedicated) {
		for (GpuCheck.Card card : GpuCheck.cards()) {
			if (card.name().isBlank() || NOT_A_CARD.matcher(card.name()).find()) continue;
			if (card.dedicated() == dedicated) return Optional.of(card.name());
		}
		return Optional.empty();
	}

	/** Auto, then each kind of card this computer has: of its own (High performance), in the processor (Power saving). */
	private static List<Gpu> choices() {
		List<Gpu> list = new ArrayList<>(List.of(Gpu.AUTO));
		if (card(true).isPresent()) list.add(Gpu.HIGH_PERFORMANCE);
		if (card(false).isPresent()) list.add(Gpu.POWER_SAVING);
		return list;
	}

	private static ButtonWidget button(int width) {
		ButtonWidget button = ButtonWidget.builder(label(), b -> {
			List<Gpu> list = choices();
			pick(list.get((list.indexOf(current()) + 1) % list.size()));
			b.setMessage(label());
		}).dimensions(0, 0, width, 20).tooltip(Tooltip.of(tooltip())).build();
		button.active = choosable();
		return button;
	}

	private static Text tooltip() {
		String now = GpuCheck.drawing().isBlank() ? "unknown" : GpuCheck.drawing();
		if (!GpuPreference.supported()) return Text.literal(describe(Gpu.AUTO));
		if (!choosable()) return Text.literal("Minecraft draws with " + now + ", the only graphics card here.");
		return Text.literal("The graphics card Minecraft runs on. Now: " + now
			+ ". Auto leaves it to Windows. A change takes effect when the game starts again.");
	}

	/** "GPU: " and the card, short; "(restart)" while it is not the one drawing yet. */
	private static Text label() {
		if (!choosable()) return Text.literal("GPU: " + shortName(GpuCheck.drawing()));
		Gpu gpu = current();
		if (gpu == Gpu.AUTO) return Text.literal("GPU: Auto");
		String card = card(gpu == Gpu.HIGH_PERFORMANCE).orElse("");
		boolean drawing = GpuCheck.cards().stream().anyMatch(c -> c.name().equals(card) && GpuCheck.drawsWith(c));
		return Text.literal("GPU: " + name(gpu) + (drawing ? "" : " (restart)"));
	}

	/** A card's name without the maker's long words: "RTX 5050", "Radeon 780M". */
	private static String shortName(String name) {
		if (name == null || name.isBlank()) return "?";
		String s = name.replaceAll("(?i)\\(tm\\)|\\(r\\)|nvidia|geforce|amd|intel|laptop gpu|graphics|gpu|/.*$", " ").replaceAll("\\s+", " ").strip();
		return s.isEmpty() ? name.strip() : s;
	}

	private static void background(Runnable task) {
		Thread thread = new Thread(task, "V1zir graphics preference");
		thread.setDaemon(true);
		thread.start();
	}
}
