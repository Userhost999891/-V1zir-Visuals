package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.account.Accounts;
import dev.v1zir.visuals.gui.clickgui.Ui;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Heads;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.ui.ShapeElement;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.SkinTextures;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The accounts dropdown of the main menu's name pill ({@link Accounts}): every account with its head, the one in use
 * ticked; a click plays as another, the cross on an offline one forgets it. Under them a field takes a new nickname
 * (typed or pasted; Enter adds it and plays as it), and a line says where offline names work. It drops in under the
 * pill, scrolls when the accounts do not fit, and closes with a click outside it or Escape.
 */
final class AccountsPanel {
	static final float WIDTH = 184f;
	private static final float ROW = 22f;
	private static final float HEADER = 20f;
	private static final float FIELD = 20f;
	private static final float HINT = 16f;
	private static final float PAD = 6f;
	private static final int MAX_NAME = 16;
	private static final int DANGER = 0xFFFF5A6A;

	private final Anim open = new Anim(0f, 14f);
	private final Anim shake = new Anim(0f, 30f);
	private final Map<String, Supplier<SkinTextures>> skins = new HashMap<>();
	private boolean shown;
	private boolean typing;
	private String draft = "";
	/** A rejected name makes the field flash red until then (System.nanoTime). */
	private long errorUntil;
	/** The first account row shown, when they do not all fit. */
	private int first;
	// Layout of the last frame.
	private float x;
	private float y;
	private float h;
	private float listY;
	private float fieldY;
	private int visible;

	boolean shown() {
		return shown;
	}

	void toggle() {
		if (shown) close();
		else shown = true;
	}

	void close() {
		shown = false;
		typing = false;
	}

	/** Draws it dropping in under (px, py) on a screen that tall; nothing once it has closed. */
	void render(DrawContext ctx, MinecraftClient client, float px, float py, float screenH, int mouseX, int mouseY) {
		float a = open.target(shown ? 1f : 0f).get();
		if (a <= 0.004f) {
			h = 0f;
			return;
		}
		List<Accounts.Account> accounts = Accounts.all();
		int count = accounts.size();
		float fixed = HEADER + PAD + FIELD + HINT + PAD;
		// As many rows as fit under the pill, at least one (none when there is no account).
		visible = Math.min(count, Math.max(1, (int) ((screenH - py - fixed - 8f) / ROW)));
		first = Math.max(0, Math.min(first, count - visible));
		x = px;
		y = py - (1f - Anim.easeOutCubic(a)) * 6f;
		h = fixed + visible * ROW;
		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * a);
		Render2D.shadow(ctx, x, y + 2f, WIDTH, h, 10f, 14f, 0x66000000);
		int base = Theme.preset().base();
		if (BackdropBlur.available()) Render2D.glass(ctx, x, y, WIDTH, h, 10f, 0, ShapeElement.MATTE, ColorUtil.withAlpha(base, 170));
		else Render2D.matte(ctx, x, y, WIDTH, h, 10f, ColorUtil.withAlpha(base, 230), 0);
		Render2D.outline(ctx, x, y, WIDTH, h, 10f, 0.8f, ColorUtil.withAlpha(0xFFFFFF, 30));

		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.medium();
		Render2D.icon(ctx, Icons.USERS, x + PAD + 6f, y + HEADER / 2f + 1f, 8f, Theme.accent());
		Render2D.textMiddle(ctx, bold, "Accounts", x + PAD + 14f, y + HEADER / 2f + 1f, 7.5f, 0xF2FFFFFF);
		String total = String.valueOf(count);
		Render2D.textMiddle(ctx, font, total, x + WIDTH - PAD - 4f - font.width(total, 6f), y + HEADER / 2f + 1f, 6f, 0x80FFFFFF);

		listY = y + HEADER;
		Accounts.Account current = Accounts.current();
		for (int row = 0; row < visible; row++) {
			Accounts.Account account = accounts.get(first + row);
			float ry = listY + row * ROW;
			boolean over = Ui.inside(mouseX, mouseY, x + 3f, ry, WIDTH - 6f, ROW - 2f);
			boolean using = account.equals(current);
			if (using || over) {
				Render2D.round(ctx, x + 3f, ry, WIDTH - 6f, ROW - 2f, 6f,
					using ? ColorUtil.withAlpha(Theme.accent(), over ? 70 : 50) : ColorUtil.withAlpha(0xFFFFFF, 16));
			}
			float head = ROW - 8f;
			float hx = x + 8f;
			float hy = ry + 3f;
			SkinTextures skin = skin(client, account);
			if (skin != null) Heads.skin(ctx, skin, hx, hy, head, 3f, 0xFFFFFFFF);
			else Render2D.icon(ctx, Icons.USER, hx + head / 2f, hy + head / 2f, head * 0.7f, 0xCCFFFFFF);
			float tx = hx + head + 6f;
			float maxText = WIDTH - (tx - x) - 24f;
			Render2D.textMiddle(ctx, bold, bold.trim(account.name(), 7f, maxText), tx, ry + ROW / 2f - 4.5f, 7f, 0xF2FFFFFF);
			Render2D.textMiddle(ctx, font, account.launcher() ? "Launcher account" : "Offline · cracked servers", tx, ry + ROW / 2f + 3.5f,
				5.5f, 0x8CFFFFFF);
			float ix = x + WIDTH - 15f;
			float iy = ry + (ROW - 2f) / 2f;
			if (over && !account.launcher()) {
				boolean onCross = Ui.inside(mouseX, mouseY, ix - 7f, iy - 7f, 14f, 14f);
				Render2D.circle(ctx, ix, iy, 6f, onCross ? DANGER : ColorUtil.withAlpha(0xFFFFFF, 26));
				Render2D.icon(ctx, Icons.X, ix, iy, 6f, onCross ? 0xFFFFFFFF : 0xCCFFFFFF);
			} else if (using) {
				Render2D.icon(ctx, Icons.CHECK, ix, iy, 7f, Theme.accent());
			}
		}
		if (visible < count) {
			// Where the shown rows are among all of them.
			float trackH = visible * ROW - 4f;
			float barH = Math.max(10f, trackH * visible / count);
			float barY = listY + 1f + (trackH - barH) * first / (float) (count - visible);
			Render2D.round(ctx, x + WIDTH - 4f, barY, 2f, barH, 1f, ColorUtil.withAlpha(0xFFFFFF, 60));
		}

		// The field for a new nickname, with its add button; a rejected name shakes it red.
		fieldY = listY + visible * ROW + PAD;
		boolean error = System.nanoTime() < errorUntil;
		float jolt = shake.target(0f).get();
		float fx = x + PAD + (float) Math.sin(jolt * 18f) * jolt * 3f;
		float fw = WIDTH - PAD * 2f;
		boolean overField = Ui.inside(mouseX, mouseY, fx, fieldY, fw, FIELD);
		Render2D.round(ctx, fx, fieldY, fw, FIELD, FIELD / 2f, ColorUtil.withAlpha(0x000000, typing ? 90 : overField ? 72 : 55));
		Render2D.outline(ctx, fx, fieldY, fw, FIELD, FIELD / 2f, 0.8f,
			error ? DANGER : ColorUtil.withAlpha(typing ? Theme.accent() : 0xFFFFFF, typing ? 200 : overField ? 50 : 34));
		float cy = fieldY + FIELD / 2f;
		if (!typing && draft.isEmpty()) {
			Render2D.icon(ctx, Icons.PLUS, fx + 10f, cy, 6.5f, 0x99FFFFFF);
			Render2D.textMiddle(ctx, font, "Add a nickname", fx + 18f, cy, 6.5f, 0x80FFFFFF);
		} else {
			Render2D.textMiddle(ctx, bold, draft, fx + 9f, cy, 6.5f, error ? DANGER : 0xF2FFFFFF);
			boolean caret = typing && (System.nanoTime() / 500_000_000L) % 2L == 0L;
			if (caret) Render2D.rect(ctx, fx + 9.5f + bold.width(draft, 6.5f), cy - 4f, 0.8f, 8f, 0xE6FFFFFF);
			boolean ok = Accounts.valid(draft);
			float bx = fx + fw - FIELD / 2f;
			boolean overButton = ok && Ui.inside(mouseX, mouseY, bx - FIELD / 2f, fieldY, FIELD, FIELD);
			Render2D.circle(ctx, bx, cy, FIELD / 2f - 3f, ok ? ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, overButton ? 0.18f : 0f)
				: ColorUtil.withAlpha(0xFFFFFF, 26));
			Render2D.icon(ctx, Icons.PLUS, bx, cy, 6.5f, ok ? 0xFFFFFFFF : 0x80FFFFFF);
		}
		String hint = !Accounts.available() ? "Switching accounts is unavailable"
			: typing && !draft.isEmpty() && !Accounts.valid(draft) ? "3 to 16 letters, digits or _"
			: "Offline names join servers without premium login";
		Render2D.textMiddle(ctx, font, hint, x + PAD + 2f, fieldY + FIELD + HINT / 2f + 1f, 5.2f, 0x73FFFFFF);
		Render2D.setAlpha(alpha);
	}

	/** The account's skin as it will look in game (the launcher's own, an offline name's default), once it has loaded. */
	SkinTextures skin(MinecraftClient client, Accounts.Account account) {
		String key = (account.launcher() ? "launcher:" : "offline:") + account.name().toLowerCase(Locale.ROOT);
		try {
			return skins.computeIfAbsent(key, k -> client.getSkinProvider().supplySkinTextures(Accounts.profile(account), false)).get();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Whether the point is on the panel as last drawn. */
	boolean over(double mx, double my) {
		return shown && h > 0f && Ui.inside(mx, my, x, y, WIDTH, h);
	}

	/** A click while it shows: true when it was on the panel (and did what it hit); false means a click outside. */
	boolean mouseClicked(MinecraftClient client, double mx, double my) {
		if (!over(mx, my)) return false;
		List<Accounts.Account> accounts = Accounts.all();
		for (int row = 0; row < visible && first + row < accounts.size(); row++) {
			float ry = listY + row * ROW;
			if (!Ui.inside(mx, my, x + 3f, ry, WIDTH - 6f, ROW - 2f)) continue;
			Accounts.Account account = accounts.get(first + row);
			float ix = x + WIDTH - 15f;
			float iy = ry + (ROW - 2f) / 2f;
			if (!account.launcher() && Ui.inside(mx, my, ix - 7f, iy - 7f, 14f, 14f)) Accounts.remove(client, account);
			else Accounts.use(client, account);
			typing = false;
			return true;
		}
		if (Ui.inside(mx, my, x + PAD, fieldY, WIDTH - PAD * 2f, FIELD)) {
			// The add button at the field's right end, or the field itself to type in.
			if (!draft.isEmpty() && mx > x + WIDTH - PAD - FIELD) submit(client);
			else typing = true;
			return true;
		}
		typing = false;
		return true;
	}

	/** The wheel over the list scrolls it when the accounts do not all fit. */
	boolean mouseScrolled(double mx, double my, double amount) {
		if (!over(mx, my)) return false;
		if (amount != 0) first = Math.max(0, first - (int) Math.signum(amount));
		return true;
	}

	/** Keys while it shows; returns whether it used them. */
	boolean keyPressed(MinecraftClient client, int key, int modifiers) {
		if (!shown) return false;
		boolean shortcut = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
		switch (key) {
			case GLFW.GLFW_KEY_ESCAPE -> {
				if (typing) typing = false;
				else close();
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if (typing && !draft.isEmpty()) submit(client);
				else typing = true;
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (typing && !draft.isEmpty()) draft = shortcut ? "" : draft.substring(0, draft.length() - 1);
			}
			case GLFW.GLFW_KEY_V -> {
				if (!shortcut) return typing;
				charTyped(client.keyboard.getClipboard().strip());
			}
			default -> {
				return typing;
			}
		}
		return true;
	}

	/** Letters, digits and underscores of a nickname (up to 16): typing while it shows goes into the field. */
	boolean charTyped(String text) {
		if (!shown) return false;
		typing = true;
		StringBuilder name = new StringBuilder(draft);
		for (int i = 0; i < text.length() && name.length() < MAX_NAME; i++) {
			char c = text.charAt(i);
			if (c < 128 && (Character.isLetterOrDigit(c) || c == '_')) name.append(c);
		}
		draft = name.toString();
		return true;
	}

	private void submit(MinecraftClient client) {
		if (Accounts.add(client, draft)) {
			draft = "";
			typing = false;
			// Scrolled to the new account at the end of the list.
			first = Integer.MAX_VALUE;
		} else {
			errorUntil = System.nanoTime() + 700_000_000L;
			shake.snap(1f);
		}
	}
}
