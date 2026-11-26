package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.account.Accounts;
import dev.v1zir.visuals.controller.KeyNavigation;
import dev.v1zir.visuals.gui.UiSounds;
import dev.v1zir.visuals.gui.clickgui.Ui;
import dev.v1zir.visuals.gui.loading.Backdrop;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Heads;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Adding an account, as a console's own page for it (the PS5 style's start, Add, and its accounts panel): on the dark
 * stage, the new account's head at the left as its name is typed (the skin an offline name plays with), and at the
 * right the name's field, a keyboard on the screen for a controller (the D-pad moves over its keys, Cross types; the
 * real keyboard types too) and Add and Cancel. Add makes the account and plays as it, then goes back; Cancel or
 * Escape goes back as it was.
 */
public final class AddAccountScreen extends Screen implements KeyNavigation {
	private static final int MAX_NAME = 16;
	private static final String[] ROWS = {"1234567890", "qwertyuiop", "asdfghjkl_", "^zxcvbnm<"};
	private static final int INK = 0xFF121215;
	private static final int DANGER = 0xFFFF5A6A;

	private final Screen parent;
	private final Runnable added;
	private final Map<String, Supplier<SkinTextures>> skins = new HashMap<>();
	private final long openedAt = System.nanoTime();
	private String draft = "";
	private boolean caps;
	/** What the keys are on: a key of the keyboard (row, column), or the buttons' row (row 4: 0 Add, 1 Cancel). */
	private int row = 1;
	private int col;
	/** The keys were used last (the focus shows); the pointer moving hides it. */
	private boolean keyed;
	private long errorUntil;
	private double lastMouseX = Double.NaN;
	private double lastMouseY = Double.NaN;
	private final Anim shake = new Anim(0f, 30f);
	// Layout of the last frame, for the mouse.
	private float keysX;
	private float keysY;
	private float keyW;
	private float keyH;
	private float keyGap;
	private float buttonsY;
	private float addX;
	private float cancelX;
	private float buttonW;
	private float buttonH;

	/** parent comes back after it; added runs once an account was added (it is the one in use then). */
	public AddAccountScreen(Screen parent, Runnable added) {
		this(parent, added, false);
	}

	/** As above; keys: it was opened with the keys or a controller, so the focus shows on the keyboard at once. */
	public AddAccountScreen(Screen parent, Runnable added, boolean keys) {
		super(Text.literal("Add an Account"));
		this.parent = parent;
		this.added = added;
		this.keyed = keys;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		// Drawn in render().
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme != null) theme.apply();
		float scale = (float) client.getWindow().getScaleFactor();
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.scale(1f / scale, 1f / scale);
		Backdrop.stage(ctx, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), scale, 1f);
		m.popMatrix();
		float t = (System.nanoTime() - openedAt) / 1.0e9f;
		float in = smooth(t / 0.35f);
		float w = width, h = height;
		SdfFont font = Fonts.medium();
		SdfFont bold = Fonts.semibold();
		float rise = (1f - in) * h * 0.02f;

		float titleSize = Math.max(10f, Math.min(36f, h * 0.05f));
		Render2D.textCentered(ctx, font, "Add an Account", w / 2f, Render2D.middle(font, h * 0.11f + rise, titleSize), titleSize,
			ColorUtil.fade(0xFFFFFFFF, in));
		float subSize = Math.max(6f, Math.min(18f, h * 0.024f));
		Render2D.textCentered(ctx, font, "A name to play as: 3 to 16 letters, digits or _. It joins servers that allow offline accounts.", w / 2f,
			Render2D.middle(font, h * 0.165f + rise, subSize), subSize, ColorUtil.fade(0x99FFFFFF, in));

		// At the left, who it will be: the head of the skin the name plays with, the name under it.
		float headD = h * 0.3f;
		float hx = w * 0.29f, hy = h * 0.46f + rise;
		Render2D.shadow(ctx, hx - headD / 2f, hy - headD / 2f + headD * 0.04f, headD, headD, headD / 2f, headD * 0.18f, ColorUtil.fade(0x80000000, in));
		SkinTextures skin = draft.length() >= 3 ? skin(draft) : null;
		if (skin != null) {
			Heads.skin(ctx, skin, hx - headD / 2f, hy - headD / 2f, headD, headD / 2f, ColorUtil.fade(0xFFFFFFFF, in));
		} else {
			Render2D.circle(ctx, hx, hy, headD / 2f, ColorUtil.fade(0x2EFFFFFF, in));
			Render2D.icon(ctx, Icons.USER, hx, hy, headD * 0.42f, ColorUtil.fade(0xB3FFFFFF, in));
		}
		float nameSize = Math.max(8f, Math.min(26f, h * 0.038f));
		String shownName = draft.isEmpty() ? "New account" : draft;
		Render2D.textCentered(ctx, bold, shownName, hx, Render2D.middle(bold, hy + headD / 2f + h * 0.06f, nameSize), nameSize,
			ColorUtil.fade(draft.isEmpty() ? 0x80FFFFFF : 0xFFFFFFFF, in));
		Render2D.textCentered(ctx, font, "Offline account", hx, Render2D.middle(font, hy + headD / 2f + h * 0.105f, subSize), subSize,
			ColorUtil.fade(0x80FFFFFF, in));

		// At the right: the field, the keyboard and the buttons.
		float right = w * 0.88f;
		float left = w * 0.5f;
		float colW = right - left;
		boolean error = System.nanoTime() < errorUntil;
		float jolt = shake.target(0f).get();
		float fieldH = Math.max(14f, h * 0.075f);
		float fx = left + (float) Math.sin(jolt * 18f) * jolt * 4f;
		float fy = h * 0.27f + rise;
		Render2D.round(ctx, fx, fy, colW, fieldH, fieldH / 2f, ColorUtil.fade(0x59000000, in));
		Render2D.outline(ctx, fx, fy, colW, fieldH, fieldH / 2f, 1f, ColorUtil.fade(error ? DANGER : 0x66FFFFFF, in));
		float fieldText = Math.max(7f, Math.min(22f, h * 0.032f));
		float tx = fx + fieldH * 0.5f;
		if (draft.isEmpty()) {
			Render2D.textMiddle(ctx, font, "Name", tx, fy + fieldH / 2f, fieldText, ColorUtil.fade(0x66FFFFFF, in));
		} else {
			Render2D.textMiddle(ctx, bold, draft, tx, fy + fieldH / 2f, fieldText, ColorUtil.fade(error ? DANGER : 0xFFFFFFFF, in));
		}
		if ((System.nanoTime() / 500_000_000L) % 2L == 0L) {
			float cx = tx + (draft.isEmpty() ? 0f : bold.width(draft, fieldText)) + 1f;
			Render2D.rect(ctx, cx, fy + fieldH * 0.25f, Math.max(1f, h * 0.002f), fieldH * 0.5f, ColorUtil.fade(0xE6FFFFFF, in));
		}
		String count = draft.length() + " / " + MAX_NAME;
		Render2D.textMiddle(ctx, font, count, fx + colW - fieldH * 0.5f - font.width(count, subSize), fy + fieldH / 2f, subSize, ColorUtil.fade(0x66FFFFFF, in));

		keyGap = Math.max(2f, h * 0.008f);
		keyW = (colW - keyGap * 9f) / 10f;
		keyH = Math.min(keyW, h * 0.075f);
		keysX = left;
		keysY = fy + fieldH + h * 0.04f;
		float keySize = Math.max(7f, Math.min(20f, keyH * 0.42f));
		for (int r = 0; r < ROWS.length; r++) {
			for (int c = 0; c < ROWS[r].length(); c++) {
				float kx = keysX + c * (keyW + keyGap);
				float ky = keysY + r * (keyH + keyGap);
				boolean over = !keyed && Ui.inside(mouseX, mouseY, kx, ky, keyW, keyH);
				boolean lit = over || keyed && row == r && col == c;
				char k = ROWS[r].charAt(c);
				Render2D.round(ctx, kx, ky, keyW, keyH, keyH * 0.22f, ColorUtil.fade(lit ? 0xFFFFFFFF : k == '^' && caps ? 0x59FFFFFF : 0x24FFFFFF, in));
				int ink = ColorUtil.fade(lit ? INK : 0xF2FFFFFF, in);
				float cx = kx + keyW / 2f, cy = ky + keyH / 2f;
				if (k == '^') Render2D.icon(ctx, Icons.CHEVRON_UP, cx, cy, keySize, ink);
				else if (k == '<') Render2D.icon(ctx, Icons.ARROW_LEFT, cx, cy, keySize, ink);
				else {
					String label = String.valueOf(caps ? Character.toUpperCase(k) : k);
					Render2D.textCentered(ctx, bold, label, cx, Render2D.middle(bold, cy, keySize), keySize, ink);
				}
			}
		}

		// Add (white once the name will do, as the console's main button) and Cancel.
		buttonH = Math.max(14f, h * 0.07f);
		buttonW = (colW - keyGap * 2f) / 2f;
		buttonsY = keysY + ROWS.length * (keyH + keyGap) + h * 0.04f;
		addX = left;
		cancelX = left + buttonW + keyGap * 2f;
		boolean ok = Accounts.valid(draft);
		button(ctx, bold, "Add", addX, buttonsY, ok, mouseX, mouseY, keyed && row == ROWS.length && col == 0, in);
		button(ctx, bold, "Cancel", cancelX, buttonsY, true, mouseX, mouseY, keyed && row == ROWS.length && col == 1, in);
	}

	private void button(DrawContext ctx, SdfFont bold, String label, float x, float y, boolean active, int mouseX, int mouseY, boolean keyedHere,
						float in) {
		boolean over = !keyed && active && Ui.inside(mouseX, mouseY, x, y, buttonW, buttonH);
		boolean lit = over || keyedHere;
		if (lit) {
			float o = buttonH * 0.12f;
			Render2D.outline(ctx, x - o, y - o, buttonW + o * 2f, buttonH + o * 2f, buttonH / 2f + o, 1.4f, ColorUtil.fade(0xFFFFFFFF, in));
		}
		int fill = !active ? 0x1AFFFFFF : lit ? 0xFFFFFFFF : 0x3DFFFFFF;
		Render2D.round(ctx, x, y, buttonW, buttonH, buttonH / 2f, ColorUtil.fade(fill, in));
		float size = Math.max(7f, Math.min(18f, buttonH * 0.4f));
		Render2D.textCentered(ctx, bold, label, x + buttonW / 2f, Render2D.middle(bold, y + buttonH / 2f, size), size,
			ColorUtil.fade(!active ? 0x66FFFFFF : lit ? INK : 0xFFFFFFFF, in));
	}

	@Nullable
	private SkinTextures skin(String name) {
		try {
			return skins.computeIfAbsent(name.toLowerCase(Locale.ROOT),
				k -> client.getSkinProvider().supplySkinTextures(Uuids.getOfflinePlayerProfile(name), false)).get();
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ typing

	private void type(char c) {
		if (draft.length() >= MAX_NAME) return;
		if (c < 128 && (Character.isLetterOrDigit(c) || c == '_')) draft += c;
	}

	private void erase() {
		if (!draft.isEmpty()) draft = draft.substring(0, draft.length() - 1);
	}

	/** The key or button the keys are on, pressed. */
	private void press(int r, int c) {
		if (r == ROWS.length) {
			if (c == 0) add();
			else cancel();
			return;
		}
		char k = ROWS[r].charAt(c);
		UiSounds.play(UiSounds.MOVE);
		if (k == '^') caps = !caps;
		else if (k == '<') erase();
		else type(caps ? Character.toUpperCase(k) : k);
	}

	private void add() {
		if (Accounts.add(client, draft)) {
			UiSounds.play(UiSounds.CONFIRM);
			client.setScreen(parent);
			added.run();
		} else {
			errorUntil = System.nanoTime() + 700_000_000L;
			shake.snap(1f);
		}
	}

	private void cancel() {
		UiSounds.play(UiSounds.BACK);
		client.setScreen(parent);
	}

	private void move(int dr, int dc) {
		if (!keyed) {
			// The first key only shows where the focus is.
			keyed = true;
			UiSounds.play(UiSounds.MOVE);
			return;
		}
		int rows = ROWS.length + 1;
		int r = Math.max(0, Math.min(rows - 1, row + dr));
		int width = r == ROWS.length ? 2 : ROWS[r].length();
		int c;
		if (dr != 0) {
			// Between the keys and the buttons: Add under the left half, Cancel under the right.
			c = r == ROWS.length ? (col < 5 ? 0 : 1) : row == ROWS.length ? (col == 0 ? 2 : 7) : col;
		} else {
			c = col + dc;
		}
		c = Math.max(0, Math.min(width - 1, c));
		if (r != row || c != col) UiSounds.play(UiSounds.MOVE);
		row = r;
		col = c;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		boolean shortcut = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
		switch (input.key()) {
			case GLFW.GLFW_KEY_ESCAPE -> cancel();
			case GLFW.GLFW_KEY_UP -> move(-1, 0);
			case GLFW.GLFW_KEY_DOWN -> move(1, 0);
			case GLFW.GLFW_KEY_LEFT -> move(0, -1);
			case GLFW.GLFW_KEY_RIGHT -> move(0, 1);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				// With the keys on the keyboard or the buttons, Enter presses what they are on; else it adds.
				if (keyed) press(row, col);
				else add();
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (shortcut) draft = "";
				else erase();
			}
			case GLFW.GLFW_KEY_V -> {
				if (!shortcut) return false;
				for (char c : client.keyboard.getClipboard().strip().toCharArray()) type(c);
			}
			default -> {
				return super.keyPressed(input);
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (!input.isValidChar()) return false;
		String text = input.asString();
		for (int i = 0; i < text.length(); i++) type(text.charAt(i));
		return true;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
		keyed = false;
		double mx = click.x(), my = click.y();
		for (int r = 0; r < ROWS.length; r++) {
			for (int c = 0; c < ROWS[r].length(); c++) {
				float kx = keysX + c * (keyW + keyGap), ky = keysY + r * (keyH + keyGap);
				if (Ui.inside(mx, my, kx, ky, keyW, keyH)) {
					press(r, c);
					return true;
				}
			}
		}
		if (Ui.inside(mx, my, addX, buttonsY, buttonW, buttonH)) add();
		else if (Ui.inside(mx, my, cancelX, buttonsY, buttonW, buttonH)) cancel();
		return true;
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (Math.abs(mouseX - lastMouseX) + Math.abs(mouseY - lastMouseY) > 1.5) keyed = false;
		lastMouseX = mouseX;
		lastMouseY = mouseY;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	private static float smooth(float t) {
		t = Anim.clamp01(t);
		return t * t * (3f - 2f * t);
	}

	@Override
	public String toString() {
		return V1zir.NAME + " add account";
	}
}
