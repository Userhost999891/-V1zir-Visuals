package dev.v1zir.visuals.gui.messages;

import dev.v1zir.visuals.chat.Conversation;
import dev.v1zir.visuals.chat.Message;
import dev.v1zir.visuals.chat.Messages;
import dev.v1zir.visuals.gui.clickgui.Ui;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MessagesModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.KeyNames;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * New messages as banners in the top right corner, like a phone's: who wrote (their picture with the Messages badge),
 * the message, and a second message from them updates their banner instead of stacking another. They slide in, stay a
 * few seconds (longer while the mouse is on one) and slide out. With a screen open a click opens the conversation at
 * that message; in game the Open key does (it shows on the banner).
 */
public final class MessageBanners {
	private static final float W = 200f;
	private static final float H = 40f;
	private static final float GAP = 6f;
	private static final float MARGIN = 8f;
	private static final long SHOW_MS = 5_500L;
	private static final int MAX = 3;
	private static final List<Banner> BANNERS = new ArrayList<>();

	public static final class Banner {
		private final Conversation conversation;
		private Message message;
		private int count = 1;
		private long shown;
		private boolean leaving;
		private final Anim in = new Anim(0f, 11f);
		private final Anim slot = new Anim(MARGIN, 12f);
		private float x = -1000f;
		private float y = -1000f;

		private Banner(Conversation conversation, Message message) {
			this.conversation = conversation;
			this.message = message;
			this.shown = System.currentTimeMillis();
		}

		public Conversation conversation() {
			return conversation;
		}

		public Message message() {
			return message;
		}
	}

	private MessageBanners() {
	}

	public static void push(Conversation conversation, Message message) {
		long now = System.currentTimeMillis();
		for (Banner banner : BANNERS) {
			if (banner.conversation == conversation && !banner.leaving) {
				banner.message = message;
				banner.count++;
				banner.shown = now;
				return;
			}
		}
		BANNERS.add(0, new Banner(conversation, message));
		int live = 0;
		for (Banner banner : BANNERS) {
			if (banner.leaving) continue;
			if (++live > MAX) banner.leaving = true;
		}
	}

	/** The newest banner still showing, or null. */
	public static Banner newest() {
		long now = System.currentTimeMillis();
		for (Banner banner : BANNERS) if (!banner.leaving && now - banner.shown < SHOW_MS) return banner;
		return null;
	}

	/** Lets the banners of this conversation go (it was opened). */
	public static void clear(Conversation conversation) {
		for (Banner banner : BANNERS) if (banner.conversation == conversation) banner.leaving = true;
	}

	public static boolean any() {
		return !BANNERS.isEmpty();
	}

	/** Draws them along the right edge; interactive when a screen is open (the mouse can hover and click them). */
	public static void render(DrawContext ctx, float screenW, int mouseX, int mouseY, boolean interactive) {
		if (BANNERS.isEmpty()) return;
		long now = System.currentTimeMillis();
		float stack = MARGIN;
		float alpha = Render2D.alpha();
		for (Iterator<Banner> it = BANNERS.iterator(); it.hasNext(); ) {
			Banner banner = it.next();
			boolean over = interactive && !banner.leaving && Ui.inside(mouseX, mouseY, banner.x, banner.y, W, H);
			// The mouse on it keeps it a little longer.
			if (over) banner.shown = Math.max(banner.shown, now - SHOW_MS + 1_500L);
			boolean going = banner.leaving || now - banner.shown > SHOW_MS;
			float t = banner.in.target(going ? 0f : 1f).get();
			if (going && t < 0.01f) {
				it.remove();
				continue;
			}
			float e = Anim.easeOutCubic(t);
			banner.x = screenW - MARGIN - W + (1f - e) * (W + MARGIN + 8f);
			banner.y = banner.slot.target(stack).get();
			Render2D.setAlpha(alpha * Math.min(1f, e * 1.4f));
			draw(ctx, banner, banner.x, banner.y, over, interactive, now);
			stack += (H + GAP) * e;
		}
		Render2D.setAlpha(alpha);
	}

	private static void draw(DrawContext ctx, Banner banner, float x, float y, boolean over, boolean interactive, long now) {
		Render2D.shadow(ctx, x, y + 3f, W, H, 12f, 16f, 0x73000000);
		Render2D.panel(ctx, x, y, W, H, 12f);
		if (over) Render2D.round(ctx, x, y, W, H, 12f, ColorUtil.withAlpha(0xFFFFFF, 16));
		String name = banner.conversation.contact().displayName();
		float head = 26f;
		float hx = x + 7f;
		float hy = y + 7f;
		Avatar.draw(ctx, banner.conversation.contact(), hx, hy, head, 8f);
		// The Messages badge on the picture's corner.
		Render2D.circle(ctx, hx + head - 1f, hy + head - 1f, 6f, 0xFF15171C);
		Render2D.circle(ctx, hx + head - 1f, hy + head - 1f, 4.8f, Theme.accent());
		Render2D.icon(ctx, Icons.MESSAGE_CIRCLE, hx + head - 1f, hy + head - 1f, 5.4f, 0xFFFFFFFF);
		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.regular();
		float tx = hx + head + 8f;
		float right = x + W - 9f;
		String when = now - banner.message.time() < 60_000L ? "now" : MessagesScreen.clock(banner.message.time());
		float whenW = font.width(when, 5.4f);
		Render2D.textMiddle(ctx, font, when, right - whenW, y + 13f, 5.4f, 0x80FFFFFF);
		String keyLabel = interactive ? "" : openKey();
		float keyW = 0f;
		if (!keyLabel.isEmpty()) {
			keyW = font.width(keyLabel, 5.2f) + 7f;
			float kx = right - keyW;
			Render2D.round(ctx, kx, y + 21f, keyW, 9f, 3f, ColorUtil.withAlpha(0xFFFFFF, 26));
			Render2D.textCentered(ctx, font, keyLabel, kx + keyW / 2f, Render2D.middle(font, y + 25.5f, 5.2f), 5.2f, 0xCCFFFFFF);
			keyW += 5f;
		}
		String title = banner.count > 1 ? name + " · " + banner.count + " messages" : name;
		title = TextWrap.ellipsize(bold, title, 7.2f, right - whenW - 6f - tx);
		Render2D.textMiddle(ctx, bold, title, tx, y + 13f, 7.2f, 0xF7FFFFFF);
		String body = TextWrap.ellipsize(font, banner.message.text(), 6.3f, right - keyW - tx);
		Render2D.textMiddle(ctx, font, body, tx, y + 25.5f, 6.3f, 0xD9FFFFFF);
	}

	/** A click on a banner opens its conversation at its message; true when one was clicked. */
	public static boolean click(double mx, double my) {
		for (Banner banner : BANNERS) {
			if (banner.leaving || !Ui.inside(mx, my, banner.x, banner.y, W, H)) continue;
			banner.leaving = true;
			Messages.open(MinecraftClient.getInstance().currentScreen, banner.conversation, banner.message);
			return true;
		}
		return false;
	}

	private static String openKey() {
		MessagesModule module = ModuleManager.get(MessagesModule.class);
		if (module == null || !module.openKey.isSet()) return "";
		String key = KeyNames.name(module.openKey.key());
		return key.length() > 8 ? key.substring(0, 8) : key.toUpperCase(Locale.ROOT);
	}
}
