package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.chat.Messages;
import dev.v1zir.visuals.gui.UiSounds;
import dev.v1zir.visuals.gui.custom.ConsoleUi;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Theme;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.lang.ref.WeakReference;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The pause menu in the PS5 style theme, laid out like a console's control center: the game stays in view and a panel
 * rises from the bottom with what is being played (the world or the server) and the clock, and a row of cards under
 * it: Back to Game first and wider, then the game menu's own buttons with an icon each (advancements, statistics,
 * options, LAN, feedback, quitting...) and Messages. The buttons are the game's own, only moved and drawn as cards
 * ({@link ConsoleUi#card}), so they do what they always did.
 */
public final class ConsolePause {
	/** The pause menu's buttons drawn as cards, with their icons. */
	private static final Map<ClickableWidget, Integer> CARDS = new WeakHashMap<>();
	/** The pause menu laid out last. */
	private static WeakReference<Screen> laidOut = new WeakReference<>(null);
	private static final float CARD_W = 74f;
	private static final float CARD_H = 64f;
	private static final float GAP = 8f;

	private ConsolePause() {
	}

	/** The icon a pause menu button is drawn with as a card, or null when it is drawn as a plain button. */
	public static Integer icon(ClickableWidget widget) {
		return CARDS.isEmpty() ? null : CARDS.get(widget);
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof GameMenuScreen && Theme.ps5()) layout(client, screen, width, height);
		});
	}

	private static float panelTop(float height) {
		return height - Math.max(118f, Math.min(176f, height * 0.42f));
	}

	/** Moves the game menu's buttons into the row of cards, and adds Messages to it. */
	private static void layout(MinecraftClient client, Screen screen, int width, int height) {
		List<ClickableWidget> all = Screens.getButtons(screen);
		List<ClickableWidget> buttons = new ArrayList<>();
		ClickableWidget resume = null;
		for (ClickableWidget widget : all) {
			if (!(widget instanceof ButtonWidget)) continue;
			if ("menu.returnToGame".equals(key(widget.getMessage()))) resume = widget;
			else buttons.add(widget);
		}
		// The menu without buttons (F3 + Esc) stays as it is.
		if (resume == null && buttons.isEmpty()) return;
		laidOut = new WeakReference<>(screen);
		ButtonWidget messages = ButtonWidget.builder(Text.literal("Messages"), b -> {
			UiSounds.play(UiSounds.CONFIRM);
			Messages.open(screen, null, null);
		}).dimensions(0, 0, 10, 10).build();
		all.add(messages);
		buttons.add(messages);
		// Quitting goes last, as the power button of a console does.
		buttons.sort((a, b) -> Boolean.compare(quits(a), quits(b)));

		float pad = Math.max(14f, width * 0.04f);
		float top = panelTop(height);
		float rowY = top + 40f;
		int count = buttons.size() + (resume != null ? 1 : 0);
		float wide = CARD_W * 1.7f;
		float room = width - pad * 2f;
		float need = (resume != null ? wide : 0f) + buttons.size() * CARD_W + (count - 1) * GAP;
		float shrink = need > room ? room / need : 1f;
		float x = pad;
		if (resume != null) {
			place(resume, x, rowY, wide * shrink, CARD_H, Icons.PLAY);
			x += wide * shrink + GAP * shrink;
		}
		for (ClickableWidget button : buttons) {
			place(button, x, rowY, CARD_W * shrink, CARD_H, icon(button));
			x += (CARD_W + GAP) * shrink;
		}
	}

	private static void place(ClickableWidget widget, float x, float y, float w, float h, int icon) {
		widget.setX(Math.round(x));
		widget.setY(Math.round(y));
		widget.setWidth(Math.round(w));
		widget.setHeight(Math.round(h));
		CARDS.put(widget, icon);
	}

	private static boolean quits(ClickableWidget widget) {
		String key = key(widget.getMessage());
		return "menu.returnToMenu".equals(key) || "menu.disconnect".equals(key);
	}

	private static String key(Text text) {
		return text != null && text.getContent() instanceof TranslatableTextContent translatable ? translatable.getKey() : "";
	}

	private static int icon(ClickableWidget widget) {
		String key = key(widget.getMessage());
		return switch (key) {
			case "gui.advancements" -> Icons.STAR;
			case "gui.stats" -> Icons.ACTIVITY;
			case "menu.options" -> Icons.SETTINGS;
			case "menu.shareToLan" -> Icons.WIFI;
			case "menu.playerReporting" -> Icons.SHIELD;
			case "menu.feedback", "menu.sendFeedback", "menu.reportBugs" -> Icons.INFO;
			case "menu.server_links" -> Icons.LIST;
			case "menu.returnToMenu", "menu.disconnect" -> Icons.POWER;
			default -> {
				String label = widget.getMessage().getString().toLowerCase(Locale.ROOT);
				if (label.equals("messages")) yield Icons.MESSAGE_CIRCLE;
				if (label.contains("mod")) yield Icons.PUZZLE;
				yield Icons.CIRCLE_DOT;
			}
		};
	}

	/**
	 * Drawn over the game's blurred view, under the buttons (ScreenMixin): the panel rising from the bottom, with what
	 * is being played and the clock above the row of cards.
	 */
	public static void background(DrawContext ctx, Screen screen) {
		if (laidOut.get() != screen || !Theme.ps5()) return;
		float w = screen.width, h = screen.height;
		float top = panelTop(h);
		// The panel fades in at its top edge so the game above stays in view.
		Render2D.gradientV(ctx, 0f, top - 30f, w, 30f, 0f, 0x000A0E24, 0xB30A0E24);
		Render2D.gradientV(ctx, 0f, top, w, h - top, 0f, 0xD90A0E24, 0xF2060918);
		Render2D.rect(ctx, 0f, top, w, 0.5f, 0x26FFFFFF);
		MinecraftClient client = MinecraftClient.getInstance();
		float pad = Math.max(14f, w * 0.04f);
		SdfFont bold = Fonts.bold();
		SdfFont font = Fonts.medium();
		String title;
		String subtitle;
		IntegratedServer server = client.getServer();
		ServerInfo entry = client.getCurrentServerEntry();
		if (server != null) {
			title = server.getSaveProperties().getLevelName();
			subtitle = "Singleplayer world";
		} else if (entry != null) {
			title = entry.name;
			subtitle = entry.address;
		} else {
			title = "Minecraft";
			subtitle = "";
		}
		Render2D.text(ctx, bold, title, pad, top + 10f, 11f, ConsoleUi.TEXT);
		if (!subtitle.isEmpty()) Render2D.text(ctx, font, subtitle, pad, top + 24f, 7f, 0xA6FFFFFF);
		String time = clock();
		Render2D.text(ctx, font, time, w - pad - font.width(time, 9f), top + 12f, 9f, ConsoleUi.TEXT);
	}

	private static String clock() {
		MainMenuModule module = ModuleManager.get(MainMenuModule.class);
		boolean twelve = module != null && module.timeFormat.is("12-hour");
		return LocalTime.now().format(DateTimeFormatter.ofPattern(twelve ? "h:mm" : "HH:mm", Locale.ROOT));
	}
}
