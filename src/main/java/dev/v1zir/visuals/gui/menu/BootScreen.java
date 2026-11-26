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
 * mark stays a while longer, fades, a health notice shows over a deep teal glow of huge discs far out of focus; as it
 * goes, a few large discs of warm light come, and the lens pulls focus: they shrink and go as the dust they were comes
 * sharp, fine pale specks and a cloud of golden ones, a few near ones drifting by soft and bright; a dark stage under
 * a spotlight comes up under the dust, where "Welcome back to Aesthetic" asks who is
 * playing: Add an account at the left, then the accounts by their heads, coming one after another from the left, the
 * one in focus growing larger with its name, as the console's. Picking one plays as it: the others go at once, it grows
 * a little and fades with the words, and the home comes in (ConsoleMenu, its row spreading from the middle over the
 * same stage). Before the question any key or click skips to it. Only when the game
 * starts: once any main menu has shown, the menu is the home.
 */
public final class BootScreen extends Screen implements KeyNavigation {
	/** The mark's box, as tall as this part of the screen (the loading screen draws it the same). */
	public static final float MARK = 0.1f;
	/** When the question comes, in seconds after the loading screen has gone. */
	private static final float ASK = 11.6f;
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
		refreshPeople();
	}

	/** Add, then the accounts, the one in use in focus (the one just added, back from adding it). */
	private void refreshPeople() {
		people.clear();
		grow.clear();
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
		bokeh(ctx, t);
		float stage = smooth((t - 9.2f) / 0.75f);
		if (stage > 0.004f) {
			float scale = (float) client.getWindow().getScaleFactor();
			Matrix3x2fStack m = ctx.getMatrices();
			m.pushMatrix();
			m.scale(1f / scale, 1f / scale);
			Backdrop.stage(ctx, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), scale, stage);
			m.popMatrix();
		}
		// Over the stage as it comes up: the specks go into its dust.
		specks(ctx, t);
		mark(ctx, t);
		notice(ctx, t);
		if (t >= ASK - 0.05f) welcome(ctx, t, mouseX, mouseY);
		if (picked >= 0 && t - pickedAt > 0.55f && !left) {
			left = true;
			ConsoleMenu.fromBoot = true;
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

	/**
	 * The deep teal light behind the notice, as the console's: huge discs of light far out of focus, slowly growing and
	 * brightening, gone as the specks come into focus.
	 */
	private void glow(DrawContext ctx, float t) {
		float a = smooth((t - 2.95f) / 1.0f) * (0.42f + 0.58f * Anim.clamp01((t - 3.95f) / 4.4f)) * (1f - smooth((t - 9.0f) / 0.42f));
		if (a <= 0.004f) return;
		float w = width, h = height;
		float g = smooth((t - 3.3f) / 5.15f);
		disc(ctx, w * 0.53f, h * 0.5f, h * (0.62f + 0.23f * g), 0.93f, 0.08f, 0xFF000E15, a);
		disc(ctx, w * (0.6f - 0.02f * g), h * 0.47f, h * (0.5f + 0.2f * g), 0.92f, 0.1f, 0xFF011823, a);
		disc(ctx, w * 0.6f, h * (0.7f - 0.07f * g), h * (0.33f + 0.17f * g), 0.9f, 0.12f, 0xFF011D29, a * smooth((t - 5.0f) / 2.0f));
	}

	/**
	 * The specks just before they come into focus: a few large discs of warm light with an edge over the teal,
	 * shrinking as it sharpens, one after another gone.
	 */
	private void bokeh(DrawContext ctx, float t) {
		if (t < 8.4f || t > 9.35f) return;
		float w = width, h = height;
		float s = smooth((t - 8.55f) / 0.75f);
		for (int i = 0; i < 14; i++) {
			float h1 = hash(i * 3.17f + 0.7f), h2 = hash(i * 5.91f + 2.3f), h3 = hash(i * 2.29f + 4.1f), h4 = hash(i * 8.43f + 1.9f);
			float a = smooth((t - 8.42f - 0.15f * h4) / 0.3f) * (1f - smooth((t - 8.85f - 0.3f * h3) / 0.12f));
			if (a <= 0.004f) continue;
			float r = h * (0.19f + 0.08f * h3) * (1f - 0.75f * s);
			float x = w * (0.2f + 0.62f * h1) + (t - 8.4f) * h * 0.03f * (h4 - 0.5f);
			float y = h * (0.02f + 0.9f * (float) Math.pow(h2, 1.15)) - (t - 8.4f) * h * 0.02f;
			// The edge sharpens as it shrinks; a faint rim shows then.
			float blur = 0.2f - 0.13f * s;
			float alpha = a * (0.035f + 0.025f * h4) * (1f + 0.5f * s);
			disc(ctx, x, y, r, 1f - blur * 0.6f, blur, 0xFFFFEBC8, alpha);
			if (s > 0.3f) {
				Render2D.ring(ctx, x, y, r * 0.93f, Math.max(0.6f, r * 0.05f), ColorUtil.fade(0xFFFFEBC8, alpha * 0.25f * smooth((s - 0.3f) / 0.3f)));
			}
		}
	}

	/**
	 * The dust in focus, as the console's: fine pale specks all over and golden ones in a cloud right of the middle,
	 * each sharpening from a soft dot as it comes (some of the golden ones stay a little soft), then a few near ones,
	 * large, soft and bright, drifting up faster. The stage comes up under them and they go into its own dust.
	 */
	private void specks(DrawContext ctx, float t) {
		if (t < 8.8f || t > 11.2f) return;
		float w = width, h = height;
		float dt = t - 8.8f;
		float out = 1f - smooth((t - 9.5f) / 0.9f);
		if (out > 0.004f) {
			for (int i = 0; i < 520; i++) {
				float h1 = hash(i * 1.93f + 0.2f), h2 = hash(i * 4.71f + 1.4f), h3 = hash(i * 7.37f + 3.1f), h4 = hash(i * 2.63f + 5.9f);
				float p = smooth((t - 8.83f - 0.25f * h3) / 0.12f);
				if (p <= 0.004f) continue;
				float r = h * (0.0016f + 0.0012f * h4);
				float rr = r * (1f + 2f * (1f - p));
				float x = w * h1 + (float) Math.sin(dt * 0.6f + h2 * 6f) * h * 0.004f;
				float y = h * h2 - dt * h * (0.006f + 0.01f * h4);
				float alpha = p * out * (0.25f + 0.3f * h4) * (r / rr) * (r / rr);
				Render2D.circle(ctx, x, y, rr, ColorUtil.fade(0xFFDCE6EC, alpha));
			}
		}
		for (int i = 0; i < 130; i++) {
			float h1 = hash(i * 2.71f + 0.9f), h2 = hash(i * 6.13f + 2.2f), h3 = hash(i * 3.37f + 5.3f), h4 = hash(i * 9.11f + 0.4f),
				h5 = hash(i * 4.43f + 7.7f);
			float p = smooth((t - 8.85f - 0.28f * h5) / 0.15f);
			float o = 1f - smooth((t - 9.42f - 0.3f * h3) / 0.3f);
			if (p <= 0.004f || o <= 0.004f) continue;
			float x = w * (0.35f + 0.56f * (h1 + h3 + h5) / 3f) + (float) Math.sin(dt * 0.5f + h2 * 9f) * h * 0.005f;
			float y = h * (float) Math.pow(h2, 1.1) - dt * h * (0.008f + 0.012f * h5);
			int color = h3 < 0.4f ? 0xFFFFC27A : h3 < 0.8f ? 0xFFF5D4A0 : 0xFFFFF0DC;
			if (h4 > 0.55f) {
				// A little out of focus: a small soft disc.
				float r = h * (0.004f + 0.006f * h1);
				float rr = r * (1f + 1.5f * (1f - p));
				float alpha = p * o * (0.35f + 0.35f * h2) * (r / rr) * (r / rr);
				Render2D.shadow(ctx, x - rr * 0.8f, y - rr * 0.8f, rr * 1.6f, rr * 1.6f, rr * 0.8f, rr * 0.5f, ColorUtil.fade(color, alpha));
			} else {
				float r = h * (0.0016f + 0.0035f * h2 * h2);
				float rr = r * (1f + 3f * (1f - p));
				float alpha = p * o * (0.6f + 0.4f * h1) * (r / rr) * (r / rr);
				Render2D.circle(ctx, x, y, rr, ColorUtil.fade(color, alpha));
			}
		}
		for (int i = 0; i < 8; i++) {
			float h1 = hash(i * 5.17f + 0.6f), h2 = hash(i * 1.79f + 3.3f), h3 = hash(i * 6.67f + 8.1f), h4 = hash(i * 3.91f + 2.6f);
			float at = 9.15f + 0.2f * h3;
			float a = smooth((t - at) / 0.12f) * (1f - smooth((t - 9.9f - 0.7f * h4) / 0.5f));
			if (a <= 0.004f) continue;
			float r = h * (0.009f + 0.01f * h4);
			float since = t - at;
			float x = w * (0.5f + 0.45f * h1) + since * h * (-0.03f + 0.12f * h2);
			float y = h * (0.02f + 0.6f * h2) - since * h * (0.1f + 0.2f * h3);
			Render2D.shadow(ctx, x - r * 0.6f, y - r * 0.6f, r * 1.2f, r * 1.2f, r * 0.6f, r * 0.7f, ColorUtil.fade(0xFFFFDFA8, a * 0.85f));
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
		// As on the console: as one is picked the others go at once, then it and the words fade as it grows a little more.
		float rest = picked >= 0 ? 1f - smooth(pickT / 0.15f) : 1f;
		float all = picked >= 0 ? 1f - smooth((pickT - 0.12f) / 0.22f) : 1f;
		SdfFont font = Fonts.medium();
		SdfFont semibold = Fonts.semibold();
		float titleSize = Math.max(9f, Math.min(40f, h * 0.044f));
		float titleIn = smooth(since / 0.3f) * all;
		Render2D.textCentered(ctx, font, "Welcome back to Aesthetic", w / 2f, Render2D.middle(font, h * 0.167f, titleSize), titleSize,
			ColorUtil.fade(0xFFFFFFFF, titleIn));
		float subSize = Math.max(6f, Math.min(20f, h * 0.024f));
		float subIn = smooth((since - 0.6f) / 0.3f) * all;
		Render2D.textCentered(ctx, font, "Who's playing?", w / 2f, Render2D.middle(font, h * 0.23f, subSize), subSize, ColorUtil.fade(0x99FFFFFF, subIn));
		float small = Math.max(6f, h * 0.028f);
		float cornerIn = smooth((since - 0.3f) / 0.3f) * all;
		String clock = ConsoleMenu.clock();
		Render2D.textMiddle(ctx, font, clock, w - w * 0.03f - font.width(clock, small), h * 0.067f, small, ColorUtil.fade(0xFFFFFFFF, cornerIn));

		int n = people.size();
		float spacing = Math.min(w * 0.184f, w * 0.86f / Math.max(1, n));
		float little = h * 0.178f, big = h * 0.267f;
		rowY = h * 0.5f;
		// The one in focus grows a moment after they have all come, as on the console.
		float focusIn = smooth((since - 0.6f) / 0.3f);
		float nameSize = Math.max(6f, Math.min(22f, h * 0.026f));
		for (int i = 0; i < n; i++) {
			float g = grow.get(i).target(i == focus ? 1f : 0f).get() * focusIn;
			// One after another from the left, each growing in from small.
			float pop = smooth((since - 0.1f - 0.1f * i) / 0.3f);
			float d = (little + (big - little) * g) * (0.55f + 0.45f * pop);
			float x = w / 2f + (i - (n - 1) / 2f) * spacing;
			float a = pop * (i == picked ? all : rest * all);
			if (i == picked) d *= 1f + 0.15f * smooth(pickT / 0.15f) + 0.15f * smooth((pickT - 0.12f) / 0.22f);
			else if (picked >= 0) d *= 1f - 0.2f * smooth(pickT / 0.15f);
			centers[i] = x;
			sizes[i] = d;
			if (a <= 0.004f) continue;
			avatar(ctx, people.get(i), x, rowY, d, a, g);
			Accounts.Account person = people.get(i);
			String name = person == null ? "Add Account" : person.name();
			float labelIn = smooth((since - 0.6f) / 0.3f) * (i == picked ? rest : 1f) * a;
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

	private void pick(int i, boolean keys) {
		if (i < 0 || i >= people.size()) return;
		if (people.get(i) == null) {
			// Add: a page of its own to make the account, back here with it in focus.
			UiSounds.play(UiSounds.CONFIRM);
			client.setScreen(new AddAccountScreen(this, this::refreshPeople, keys));
			return;
		}
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
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> pick(focus, true);
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
			pick(i, false);
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
