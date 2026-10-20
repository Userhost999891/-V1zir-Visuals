package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.gui.clickgui.Label;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.KeyNames;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

/** W A S D, Space and both mouse buttons, lighting up while held, with clicks per second. */
public final class KeystrokesModule extends HudModule {
	private static final float KEY = 20f;
	private static final float GAP = 2f;
	private static final float W = KEY * 3f + GAP * 2f;
	private static final float SPACE_H = 11f;
	private static final int FORWARD = 0;
	private static final int LEFT = 1;
	private static final int BACK = 2;
	private static final int RIGHT = 3;
	private static final int ATTACK = 4;
	private static final int USE = 5;
	private static final int JUMP = 6;
	/** Clicks remembered per button: more than anyone manages in a second. */
	private static final int HISTORY = 32;

	public final BoolSetting mouse = add(new BoolSetting("Mouse", true));
	public final BoolSetting cps = add(new BoolSetting("CPS", true).visibleIf(mouse::on));
	public final BoolSetting space = add(new BoolSetting("Space", true));

	private final Anim[] press = new Anim[JUMP + 1];
	private final String[] labels = {"W", "A", "S", "D", "LMB", "RMB", ""};
	private final String[] boundKeys = new String[4];
	private final Label[] labelWidths = new Label[JUMP];
	private final NumberText[] cpsText = {new NumberText(0, " CPS"), new NumberText(0, " CPS")};
	private final long[][] clicks = new long[2][HISTORY];
	private final int[] nextClick = new int[2];

	public KeystrokesModule() {
		super("Keystrokes", "Movement keys and clicks per second", 0.995f, 0.985f);
		setSubCategory("Widgets");
		for (int i = 0; i < press.length; i++) press[i] = new Anim(0f, 16f);
		for (int i = 0; i < labelWidths.length; i++) labelWidths[i] = new Label();
		for (long[] times : clicks) Arrays.fill(times, Long.MIN_VALUE);
	}

	@Override
	public boolean showInList() {
		return false;
	}

	/** Mouse button events (MouseClickMixin); only clicks made in game count. */
	public void onMouseButton(int button, int action) {
		if (button > 1 || action != GLFW.GLFW_PRESS || mc.currentScreen != null) return;
		clicks[button][nextClick[button]] = System.nanoTime();
		nextClick[button] = (nextClick[button] + 1) % HISTORY;
	}

	private int cps(int button) {
		long since = System.nanoTime() - 1_000_000_000L;
		int count = 0;
		for (long time : clicks[button]) if (time > since) count++;
		return count;
	}

	private static KeyBinding binding(int key) {
		return switch (key) {
			case FORWARD -> mc.options.forwardKey;
			case LEFT -> mc.options.leftKey;
			case BACK -> mc.options.backKey;
			case RIGHT -> mc.options.rightKey;
			case ATTACK -> mc.options.attackKey;
			case USE -> mc.options.useKey;
			default -> mc.options.jumpKey;
		};
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		for (int i = 0; i < press.length; i++) press[i].target(binding(i).isPressed() ? 1f : 0f);
		// Movement keys show what they are bound to (Z Q S D on AZERTY); rebuilt only on a rebind.
		for (int i = FORWARD; i <= RIGHT; i++) {
			String id = binding(i).getBoundKeyTranslationKey();
			if (!id.equals(boundKeys[i])) {
				boundKeys[i] = id;
				labels[i] = keyLabel(id);
			}
		}

		float h = KEY * 2f + GAP + (mouse.on() ? GAP + KEY : 0f) + (space.on() ? GAP + SPACE_H : 0f);
		width = W;
		height = h;
		SdfFont font = Fonts.semibold();
		key(ctx, font, FORWARD, x + KEY + GAP, y, KEY, KEY);
		float row = y + KEY + GAP;
		key(ctx, font, LEFT, x, row, KEY, KEY);
		key(ctx, font, BACK, x + KEY + GAP, row, KEY, KEY);
		key(ctx, font, RIGHT, x + (KEY + GAP) * 2f, row, KEY, KEY);
		row += KEY + GAP;
		if (mouse.on()) {
			float mw = (W - GAP) / 2f;
			key(ctx, font, ATTACK, x, row, mw, KEY);
			key(ctx, font, USE, x + mw + GAP, row, mw, KEY);
			row += KEY + GAP;
		}
		if (space.on()) key(ctx, font, JUMP, x, row, W, SPACE_H);
	}

	private void key(DrawContext ctx, SdfFont font, int index, float x, float y, float w, float h) {
		float p = press[index].get();
		// A held key sinks in a little and fills with the accent.
		float inset = p * 0.8f;
		x += inset;
		y += inset;
		w -= inset * 2f;
		h -= inset * 2f;
		Render2D.panel(ctx, x, y, w, h, 5f);
		if (p > 0.01f) {
			int accent = Theme.accent();
			int accent2 = Theme.accent2();
			Render2D.glow(ctx, x, y, w, h, 5f, 7f, ColorUtil.withAlpha(accent, Math.round(100 * p)), ColorUtil.withAlpha(accent2, Math.round(100 * p)));
			Render2D.round(ctx, x, y, w, h, 5f, ColorUtil.withAlpha(accent, Math.round(190 * p)), ColorUtil.withAlpha(accent2, Math.round(170 * p)),
				ColorUtil.withAlpha(accent2, Math.round(140 * p)), ColorUtil.withAlpha(accent, Math.round(160 * p)));
		}
		int color = ColorUtil.lerp(Theme.text(), 0xFFFFFFFF, p);
		if (index == JUMP) {
			Render2D.round(ctx, x + w / 2f - 9f, y + h / 2f - 0.75f, 18f, 1.5f, 0.75f, ColorUtil.withAlpha(color, 200));
			return;
		}
		boolean showCps = cps.on() && index >= ATTACK;
		float size = showCps ? 6.5f : 7.5f;
		float lw = labelWidths[index].width(font, labels[index], size);
		// Long key names (LShift, Num8) shrink to fit their cap.
		float fit = Math.min(1f, (w - 5f) / Math.max(1f, lw));
		float cy = showCps ? y + h / 2f - 3f : y + h / 2f;
		Render2D.textMiddle(ctx, font, labels[index], x + (w - lw * fit) / 2f, cy, size * fit, color);
		if (showCps) {
			NumberText text = cpsText[index - ATTACK];
			text.set(cps(index - ATTACK));
			SdfFont small = Fonts.medium();
			float tw = text.width(small, 5.5f);
			Render2D.textMiddle(ctx, small, text.text(), x + (w - tw) / 2f, y + h / 2f + 4f, 5.5f,
				ColorUtil.lerp(Theme.textDim(), 0xFFFFFFFF, p));
		}
	}

	private static String keyLabel(String id) {
		InputUtil.Key key = InputUtil.fromTranslationKey(id);
		// Mouse buttons have no short key name; the bound key's own text is too long for a key cap.
		return id.startsWith("key.mouse.") ? "M" + (key.getCode() + 1) : KeyNames.name(key.getCode());
	}
}
