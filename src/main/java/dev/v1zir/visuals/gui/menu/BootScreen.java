package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.account.Accounts;
import dev.v1zir.visuals.controller.Controller;
import dev.v1zir.visuals.controller.KeyNavigation;
import dev.v1zir.visuals.gui.UiSounds;
import dev.v1zir.visuals.gui.loading.Backdrop;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Heads;
import dev.v1zir.visuals.render.ui.Logo;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The PS5 style's start, as a console boots. The loading screen shows the mark on black while the game loads; the
 * mark stays a while longer, fades, a health notice shows over a deep teal glow, soft lights drift through it, and a
 * dark stage under a spotlight comes up with dust floating in its light, where "Welcome back to V1zir" asks who is
 * playing: Add an account at the left, then the accounts by their heads, the one in focus larger with its name.
 * Picking one plays as it: it moves into the middle as the rest fades, and the home comes in (ConsoleMenu, its row
 * spreading from the middle over the same stage). Before the question any key or click skips to it. Only when the game
 * starts: once any main menu has shown, the menu is the home.
 */
public final class BootScreen extends Screen implements KeyNavigation {
	/** The mark's box, as tall as this part of the screen (the loading screen draws it the same). */
	public static final float MARK = 0.1f;
	/** When the question comes, in seconds after the loading screen has gone. */
	private static final float ASK = 9.6f;
	private static final String[] NOTICE = {
		"PHOTOSENSITIVE EPILEPSY: SOME PEOPLE MAY HAVE SEIZURES WHEN THEY SEE FLASHING LIGHTS OR PATTERNS, EVEN WITH NO",
		"HISTORY OF EPILEPSY. IF YOU OR ANYONE IN YOUR FAMILY HAS EVER HAD SEIZURES, ASK A DOCTOR BEFORE PLAYING, AND",
		"STOP AT ONCE IF YOU FEEL DIZZY, YOUR SIGHT CHANGES OR YOUR MUSCLES TWITCH."
	};
	/** Tests stand the boot's clock here (seconds) to take each moment of it; NaN lets it run. */
	public static float testTime = Float.NaN;
	/** It has played, or a main menu has shown, in this start of the game: the menu is the home from then on. */
	private static boolean played;

	private final long start = System.nanoTime();
	/** Seconds a skip moved the clock on. */
	private float skip;
	/** Add (null) at the left when accounts can be added, then the accounts. */
	private final List<Accounts.Account> people = new ArrayList<>();
	private final List<Anim> grow = new ArrayList<>();
	private final Map<String, Supplier<SkinTextures>> skins = new HashMap<>();
	private int focus;
	private int picked = -1;
	private float pickedAt;
	private boolean left;
	private final Anim powerHover = new Anim(0f, 20f);
	/** Where each one is, as last drawn (middle x, then its diameter), and the row's middle; the power button. */
	private float[] centers = new float[0];
	private float[] sizes = new float[0];
	private float rowY;
	private float powerX;
	private float powerY;
	private float powerR;

	public BootScreen() {
		super(Text.literal(V1zir.NAME));
		played = true;
	}

	/** Whether the start is still to play: the game has just started and no main menu has shown yet (MainMenus). */
	public static boolean due() {
		return !played;
	}

	/** A main menu shows (either style's): the start is for the game's start only, never later. */
	public static void menuShown() {
		played = true;
	}

	@Override
	protected void init() {
		if (!people.isEmpty()) return;
		Accounts.init(client);
		if (Accounts.available()) people.add(null);
		people.addAll(Accounts.all());
		focus = Math.max(0, people.indexOf(Accounts.current()));
		for (int i = 0; i < people.size(); i++) grow.add(new Anim(i == focus ? 1f : 0f, 12f));
		centers = new float[people.size()];
		sizes = new float[people.size()];
	}

	private float time() {
		if (!Float.isNaN(testTime)) return testTime;
		return (System.nanoTime() - start) / 1.0e9f + skip;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		// All of it is drawn in render().
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme != null) theme.apply();
		float t = time();
		ctx.fill(0, 0, width, height, 0xFF000000);
		glow(ctx, t);
		float stage = smooth((t - 7.6f) / 1.2f);
		if (stage > 0.004f) {
			float scale = (float) client.getWindow().getScaleFactor();
			Matrix3x2fStack m = ctx.getMatrices();
			m.pushMatrix();
			m.scale(1f / scale, 1f / scale);
			Backdrop.stage(ctx, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), scale, stage);
			m.popMatrix();
		}
		mark(ctx, t);
		notice(ctx, t);
		if (t >= ASK - 0.05f) welcome(ctx, t, mouseX, mouseY);
		if (picked >= 0 && t - pickedAt > 1.05f && !left) {
			left = true;
			ConsoleMenu.fromBoot = true;
			ConsoleMenu.addAccount = people.get(picked) == null;
			client.setScreen(MainMenus.create());
		}
	}

	// ------------------------------------------------------------------ the start

	/** The mark on black, as the loading screen left it, a while longer, then fading. */
	private void mark(DrawContext ctx, float t) {
		float a = 1f - smooth((t - 2.0f) / 0.8f);
		if (a <= 0.004f) return;
		float size = height * MARK;
		Render2D.setAlpha(a);
		Logo.draw(ctx, (width - size) / 2f, (height - size) / 2f, size);
		Render2D.setAlpha(1f);
	}

	/** The health notice, small, left aligned in a block in the middle. */
	private void notice(DrawContext ctx, float t) {
		float a = smooth((t - 3.2f) / 0.5f) * (1f - smooth((t - 6.7f) / 0.5f));
		if (a <= 0.004f) return;
		SdfFont font = Fonts.medium();
		float size = Math.max(4f, height * 0.019f);
		float widest = 0f;
		for (String line : NOTICE) widest = Math.max(widest, font.width(line, size));
		float x = (width - widest) / 2f;
		float y = height * 0.5f - size * 1.55f;
		for (int i = 0; i < NOTICE.length; i++) Render2D.textMiddle(ctx, font, NOTICE[i], x, y + i * size * 1.55f, size, ColorUtil.fade(0xFFC6C9CE, a));
	}

	/** A deep teal glow swelling behind the notice, then soft lights drifting through it, all gone as the stage comes up. */
	private void glow(DrawContext ctx, float t) {
		float a = smooth((t - 3.4f) / 2.4f) * (1f - smooth((t - 7.4f) / 1.0f));
		if (a <= 0.004f) return;
		float w = width, h = height;
		float swell = 1f + 0.08f * smooth((t - 3.4f) / 4.5f);
		disc(ctx, w * 0.6f, h * 0.5f, h * 0.62f * swell, 0.55f, 0.9f, 0xFF10343F, 0.85f * a);
		disc(ctx, w * 0.3f, h * 0.72f, h * 0.45f * swell, 0.55f, 0.9f, 0xFF0B2830, 0.6f * a * smooth((t - 4.0f) / 1.5f));
		float lights = smooth((t - 5.6f) / 0.9f) * (1f - smooth((t - 7.2f) / 0.9f));
		if (lights <= 0.004f) return;
		for (int i = 0; i < 9; i++) {
			float h1 = hash(i * 1.7f + 0.4f), h2 = hash(i * 4.3f + 2.2f), h3 = hash(i * 2.9f + 7.1f);
			float r = h * (0.06f + 0.09f * h1);
			float x = w * (0.25f + 0.55f * h2) + (t - 5.6f) * w * 0.05f * (h3 - 0.5f);
			float y = h * (0.12f + 0.6f * h3) - (t - 5.6f) * h * 0.02f;
			disc(ctx, x, y, r, 0.85f, 0.3f, 0xFF557680, (0.16f + 0.2f * h1) * lights);
		}
	}

	/** A soft disc of light: core (part of r) sharp, the rest of r its blur. */
	private static void disc(DrawContext ctx, float cx, float cy, float r, float core, float blur, int color, float alpha) {
		if (alpha <= 0.004f) return;
		float c = r * core;
		Render2D.shadow(ctx, cx - c, cy - c, c * 2f, c * 2f, c, r * blur, ColorUtil.fade(color, alpha));
	}

	// ------------------------------------------------------------------ who is playing

	private void welcome(DrawContext ctx, float t, int mouseX, int mouseY) {
		float w = width, h = height;
		float since = t - ASK;
		float pickT = picked >= 0 ? t - pickedAt : -1f;
		// As one is picked the rest fades, then all of it.
		float rest = picked >= 0 ? 1f - smooth(pickT / 0.3f) : 1f;
		float all = picked >= 0 ? 1f - smooth((pickT - 0.65f) / 0.35f) : 1f;
		SdfFont font = Fonts.medium();
		SdfFont semibold = Fonts.semibold();
		float titleSize = Math.max(9f, Math.min(40f, h * 0.044f));
		float titleIn = smooth(since / 0.4f) * rest * all;
		Render2D.textCentered(ctx, font, "Welcome back to V1zir", w / 2f, Render2D.middle(font, h * 0.167f, titleSize), titleSize,
			ColorUtil.fade(0xFFFFFFFF, titleIn));
		float subSize = Math.max(6f, Math.min(20f, h * 0.024f));
		float subIn = smooth((since - 0.5f) / 0.35f) * rest * all;
		Render2D.textCentered(ctx, font, "Who's playing?", w / 2f, Render2D.middle(font, h * 0.23f, subSize), subSize, ColorUtil.fade(0x99FFFFFF, subIn));
		float small = Math.max(6f, h * 0.028f);
		float cornerIn = smooth((since - 0.4f) / 0.3f) * all;
		String clock = ConsoleMenu.clock();
		Render2D.textMiddle(ctx, font, clock, w - w * 0.03f - font.width(clock, small), h * 0.067f, small, ColorUtil.fade(0xFFFFFFFF, cornerIn));

		int n = people.size();
		float spacing = Math.min(w * 0.184f, w * 0.86f / Math.max(1, n));
		float little = h * 0.178f, big = h * 0.267f;
		rowY = h * 0.5f;
		float rowIn = smooth((since - 0.05f) / 0.35f);
		// The one in focus grows a moment after the row has come, as on the console.
		float focusIn = smooth((since - 0.75f) / 0.3f);
		float nameSize = Math.max(6f, Math.min(22f, h * 0.026f));
		for (int i = 0; i < n; i++) {
			float g = grow.get(i).target(i == focus ? 1f : 0f).get() * focusIn;
			float d = little + (big - little) * g;
			float x = w / 2f + (i - (n - 1) / 2f) * spacing;
			float a = rowIn * (i == picked ? all : rest * all);
			if (i == picked) {
				float p = smooth(pickT / 0.45f);
				x += (w / 2f - x) * p;
				d *= 1f + 0.12f * p;
			}
			centers[i] = x;
			sizes[i] = d;
			if (a <= 0.004f) continue;
			avatar(ctx, people.get(i), x, rowY, d, a, g);
			Accounts.Account person = people.get(i);
			String name = person == null ? "Add Account" : person.name();
			float labelIn = smooth((since - 0.75f) / 0.3f) * (i == picked ? rest : 1f) * a;
			Render2D.textCentered(ctx, i == focus ? semibold : font, name, x, Render2D.middle(font, rowY + big / 2f + h * 0.045f, nameSize), nameSize,
				ColorUtil.fade(i == focus ? 0xFFFFFFFF : 0x99FFFFFF, labelIn));
			if (i == focus && !Controller.name().isEmpty()) {
				// The controller's mark over whoever it is on, as the console shows it.
				Render2D.icon(ctx, Icons.GAMEPAD_2, x, rowY - big / 2f - h * 0.045f, nameSize * 1.1f, ColorUtil.fade(0xE6FFFFFF, labelIn));
			}
		}

		float bottomIn = smooth((since - 0.6f) / 0.3f) * rest * all;
		powerR = Math.max(6f, h * 0.026f);
		powerX = w / 2f;
		powerY = h * 0.935f;
		boolean overPower = picked < 0 && Math.hypot(mouseX - powerX, mouseY - powerY) <= powerR * 1.4f;
		float hv = powerHover.target(overPower ? 1f : 0f).get();
		if (hv > 0.01f) Render2D.circle(ctx, powerX, powerY, powerR * 1.35f, ColorUtil.fade(0x26FFFFFF, hv * bottomIn));
		Render2D.icon(ctx, Icons.POWER, powerX, powerY, powerR * 1.3f, ColorUtil.fade(ColorUtil.lerp(0xB3FFFFFF, 0xFFFFFFFF, hv), bottomIn));
		// "Select" at the bottom right, by the button that does it.
		float hint = Math.max(5.5f, h * 0.022f);
		String select = "Select";
		float sx = w - w * 0.025f - font.width(select, hint);
		float sy = h * 0.945f;
		Render2D.textMiddle(ctx, font, select, sx, sy, hint, ColorUtil.fade(0xE6FFFFFF, bottomIn));
		float br = hint * 0.62f;
		Render2D.ring(ctx, sx - br * 1.7f, sy, br, Math.max(0.8f, br * 0.16f), ColorUtil.fade(0xE6FFFFFF, bottomIn));
		Render2D.icon(ctx, Icons.X, sx - br * 1.7f, sy, br * 1.15f, ColorUtil.fade(0xE6FFFFFF, bottomIn));
	}

	private void avatar(DrawContext ctx, @Nullable Accounts.Account person, float cx, float cy, float d, float alpha, float focused) {
		float r = d / 2f;
		Render2D.shadow(ctx, cx - r, cy - r + r * 0.06f, d, d, r, r * 0.35f, ColorUtil.fade(0x66000000, alpha));
		if (person == null) {
			Render2D.circle(ctx, cx, cy, r, ColorUtil.fade(0x2EFFFFFF, alpha));
			Render2D.icon(ctx, Icons.PLUS, cx, cy, d * 0.34f, ColorUtil.fade(0xF2FFFFFF, alpha));
			return;
		}
		SkinTextures skin = skin(person);
		int tint = ColorUtil.fade(ColorUtil.lerp(0xFFD0D0D0, 0xFFFFFFFF, focused), alpha);
		if (skin != null) {
			Heads.skin(ctx, skin, cx - r, cy - r, d, r, tint);
		} else {
			Render2D.circle(ctx, cx, cy, r, ColorUtil.fade(0x33FFFFFF, alpha));
			Render2D.icon(ctx, Icons.CIRCLE_USER, cx, cy, d * 0.9f, tint);
		}
	}

	@Nullable
	private SkinTextures skin(Accounts.Account account) {
		String key = (account.launcher() ? "launcher:" : "offline:") + account.name().toLowerCase(Locale.ROOT);
		try {
			return skins.computeIfAbsent(key, k -> client.getSkinProvider().supplySkinTextures(Accounts.profile(account), false)).get();
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ input

	private boolean asking() {
		return time() >= ASK;
	}

	/** Any key or click before the question skips to it (the stage coming up). */
	private void skipToQuestion() {
		float t = time();
		if (t < ASK - 0.8f) skip += ASK - 0.8f - t;
	}

	private void move(int by) {
		int next = Math.max(0, Math.min(people.size() - 1, focus + by));
		if (next == focus) return;
		focus = next;
		UiSounds.play(UiSounds.MOVE);
	}

	private void pick(int i) {
		if (i < 0 || i >= people.size()) return;
		picked = i;
		focus = i;
		pickedAt = time();
		UiSounds.play(UiSounds.CONFIRM);
		Accounts.Account person = people.get(i);
		if (person != null && !person.equals(Accounts.current())) Accounts.use(client, person);
	}

	/** The one at (x, y) as last drawn, or -1. */
	private int at(double x, double y) {
		for (int i = 0; i < centers.length; i++) {
			float r = Math.max(sizes[i], height * 0.178f) / 2f;
			if (Math.hypot(x - centers[i], y - rowY) <= r) return i;
		}
		return -1;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (picked >= 0) return true;
		if (!asking()) {
			skipToQuestion();
			return true;
		}
		switch (input.key()) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> move(-1);
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> move(1);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> pick(focus);
			default -> {
				return super.keyPressed(input);
			}
		}
		return true;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (picked >= 0) return true;
		if (!asking()) {
			skipToQuestion();
			return true;
		}
		if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
		int i = at(click.x(), click.y());
		if (i >= 0) {
			pick(i);
			return true;
		}
		if (Math.hypot(click.x() - powerX, click.y() - powerY) <= powerR * 1.4f) {
			UiSounds.play(UiSounds.CONFIRM);
			client.scheduleStop();
		}
		return true;
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (picked >= 0 || !asking()) return;
		int i = at(mouseX, mouseY);
		if (i >= 0 && i != focus) {
			focus = i;
			UiSounds.play(UiSounds.MOVE);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static float smooth(float t) {
		t = Anim.clamp01(t);
		return t * t * (3f - 2f * t);
	}

	/** Pseudo random 0..1 from a float. */
	private static float hash(float v) {
		float n = (float) (Math.sin(v * 12.9898) * 43758.5453);
		return n - (float) Math.floor(n);
	}
}
