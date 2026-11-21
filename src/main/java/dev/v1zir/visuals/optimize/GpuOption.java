package dev.v1zir.visuals.optimize;

import dev.v1zir.visuals.V1zir;
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
import java.util.regex.Pattern;

/**
 * GPU in Video Settings: picks the graphics card Minecraft runs on, among this computer's (Auto: as Windows likes), by
 * setting Windows' graphics preference for the game's Java ({@link GpuPreference}): the card of its own for High
 * performance, the one in the processor for Power saving. It takes effect when the game starts again; the button says
 * so while the card picked is not the one drawing yet. It is the first row of the settings list; should the game not
 * let it in there, it sits in the screen's top right corner.
 */
public final class GpuOption {
	/** Adapters that are not cards to run a game on: Windows' fallback driver, remote and virtual displays. */
	private static final Pattern NOT_A_CARD = Pattern.compile(
		"(?i)basic (display|render)|virtual|remote|parsec|citrix|vmware|virtualbox|hyper-v|spacedesk|displaylink|mirror|indirect");
	/** Windows' preference for the game's Java as read (-1 until read), and the one picked here this session (-1: none). */
	private static volatile int stored = -1;
	private static int picked = -1;
	/** The Video Settings screen whose list has the row already. */
	private static WeakReference<Screen> listed = new WeakReference<>(null);

	private GpuOption() {
	}

	private record Choice(String name, int preference) {
	}

	/** The fallback: a button in the top right corner when the list did not take the row. */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof VideoOptionsScreen) || listed.get() == screen) return;
			int w = Math.min(160, width / 2 - client.textRenderer.getWidth(screen.getTitle()) / 2 - 14);
			if (w < 90) return;
			ButtonWidget button = button(client, w);
			button.setPosition(width - w - 6, 6);
			Screens.getButtons(screen).add(button);
		});
	}

	/** The row at the top of Video Settings, under a header of its own as the game's sections have (VideoOptionsScreenMixin). */
	public static void list(Screen screen, OptionListWidget body) {
		body.addHeader(Text.literal("Graphics Card"));
		body.addWidgetEntry(button(MinecraftClient.getInstance(), 310), null);
		listed = new WeakReference<>(screen);
	}

	/** Whether this Video Settings screen has GPU as a row of its list. */
	public static boolean listedOn(Screen screen) {
		return listed.get() == screen;
	}

	private static ButtonWidget button(MinecraftClient client, int width) {
		ButtonWidget button = ButtonWidget.builder(label(), b -> next(client, b)).dimensions(0, 0, width, 20).tooltip(Tooltip.of(tooltip())).build();
		button.active = GpuPreference.supported() && choices().size() > 2;
		if (stored < 0 && GpuPreference.supported()) {
			Thread thread = new Thread(() -> {
				stored = GpuPreference.read();
				client.execute(() -> button.setMessage(label()));
			}, "V1zir graphics preference");
			thread.setDaemon(true);
			thread.start();
		}
		return button;
	}

	private static Text tooltip() {
		String now = GpuCheck.drawing().isBlank() ? "unknown" : GpuCheck.drawing();
		if (!GpuPreference.supported()) return Text.literal("Minecraft draws with " + now
			+ ". Picking the card here works on Windows; elsewhere start the game on it (for example with prime-run).");
		if (choices().size() <= 2) return Text.literal("Minecraft draws with " + now + ", the only graphics card here.");
		return Text.literal("The graphics card Minecraft runs on. Now: " + now
			+ ". Auto leaves it to Windows. A change takes effect when the game starts again.");
	}

	/** Auto, then each card once: the one of its own (High performance), the one in the processor (Power saving). */
	private static List<Choice> choices() {
		List<Choice> list = new ArrayList<>();
		list.add(new Choice("Auto", GpuPreference.AUTO));
		boolean high = false, saving = false;
		for (GpuCheck.Card card : GpuCheck.cards()) {
			if (card.name().isBlank() || NOT_A_CARD.matcher(card.name()).find()) continue;
			if (card.dedicated() && !high) {
				high = true;
				list.add(new Choice(card.name(), GpuPreference.HIGH_PERFORMANCE));
			} else if (!card.dedicated() && !saving) {
				saving = true;
				list.add(new Choice(card.name(), GpuPreference.POWER_SAVING));
			}
		}
		return list;
	}

	private static int current() {
		return picked >= 0 ? picked : Math.max(GpuPreference.AUTO, stored);
	}

	private static Choice chosen(List<Choice> list) {
		for (Choice choice : list) if (choice.preference() == current()) return choice;
		return list.get(0);
	}

	/** "GPU: " and the card, short; "(restart)" while it is not the one drawing yet. */
	private static Text label() {
		if (!GpuPreference.supported() || choices().size() <= 2) return Text.literal("GPU: " + shortName(GpuCheck.drawing()));
		Choice choice = chosen(choices());
		if (choice.preference() == GpuPreference.AUTO) return Text.literal("GPU: Auto");
		boolean waiting = GpuCheck.cards().stream().noneMatch(card -> card.name().equals(choice.name()) && GpuCheck.drawsWith(card));
		return Text.literal("GPU: " + shortName(choice.name()) + (waiting ? " (restart)" : ""));
	}

	private static void next(MinecraftClient client, ButtonWidget button) {
		List<Choice> list = choices();
		Choice next = list.get((list.indexOf(chosen(list)) + 1) % list.size());
		picked = next.preference();
		button.setMessage(label());
		Thread thread = new Thread(() -> {
			boolean ok = GpuPreference.write(next.preference());
			V1zir.LOGGER.info("Graphics preference for {}: {} ({})", GpuPreference.executable(), next.preference(), ok ? "set" : "not set");
			client.execute(() -> client.getToastManager().add(SystemToast.create(client, SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.literal(ok ? "GPU: " + next.name() : "GPU not changed"),
				Text.literal(!ok ? "Windows did not take the graphics preference."
					: next.preference() == GpuPreference.AUTO ? "Windows picks the card again from the next start."
					: "Restart Minecraft: it starts on this graphics card from then on."))));
		}, "V1zir graphics preference");
		thread.setDaemon(true);
		thread.start();
	}

	/** A card's name without the maker's long words: "RTX 5050", "Radeon 780M". */
	private static String shortName(String name) {
		if (name == null || name.isBlank()) return "?";
		String s = name.replaceAll("(?i)\\(tm\\)|\\(r\\)|nvidia|geforce|amd|intel|laptop gpu|graphics|gpu|/.*$", " ").replaceAll("\\s+", " ").strip();
		return s.isEmpty() ? name.strip() : s;
	}
}
