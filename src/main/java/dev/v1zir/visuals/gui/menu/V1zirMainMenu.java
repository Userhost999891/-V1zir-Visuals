package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.ui.ShapeElement;
import dev.v1zir.visuals.render.wallpaper.WallpaperRenderer;
import dev.v1zir.visuals.render.wallpaper.WallpaperStyle;
import dev.v1zir.visuals.render.wallpaper.Wallpapers;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Replacement title screen: an animated wallpaper, the time with a greeting and a row of round frosted icon buttons.
 * It opens on the {@link LockScreen}, which has a wallpaper of its own; once that slides away the menu comes up from
 * below. Holding the mouse button on an empty spot of either screen opens the {@link WallpaperEditor} for it.
 */
public final class V1zirMainMenu extends Screen {
	private static final int DANGER = 0xFFFF4D5E;
	/** Header clock style, refilled every frame instead of allocated. */
	private static final Render2D.TextStyle TIME_STYLE = new Render2D.TextStyle().weight(0f);
	private static boolean playedIntro;

	private final List<RoundButton> buttons = new ArrayList<>();
	private final LockScreen lock = new LockScreen();
	private final MenuClock clock = new MenuClock();
	private final LongPress hold = new LongPress();
	private final WallpaperEditor editor = new WallpaperEditor();
	/** The press that opened the wallpaper picker is still held: letting go of it does nothing. */
	private boolean swallowRelease;
	private final Anim parallaxX = new Anim(0f, 3.5f);
	private final Anim parallaxY = new Anim(0f, 3.5f);
	/** 1 for the first intro of the session, shorter when coming back to the menu later. */
	private final float introScale;
	private long introStart = -1L;
	private Particle[] particles = new Particle[0];
	private long lastFrame = System.nanoTime();
	/** The button the left mouse button went down on; it fires when released over it. */
	@Nullable
	private RoundButton pressed;
	/** Button selected with the keyboard (Tab or the arrow keys), or -1. */
	private int focused = -1;

	public V1zirMainMenu() {
		super(Text.literal(V1zir.NAME));
		introScale = playedIntro ? 0.45f : 1f;
		playedIntro = true;
	}

	@Override
	protected void init() {
		buttons.clear();
		pressed = null;
		float size = Math.max(26f, Math.min(42f, height * 0.072f));
		float gap = size * 0.42f;
		Runnable singleplayer = () -> client.setScreen(new SelectWorldScreen(this));
		Runnable multiplayer = () -> client.setScreen(new MultiplayerScreen(this));
		lock.init(client, singleplayer, multiplayer, () -> LastPlayed.join(client, this));
		List<RoundButton> row = new ArrayList<>();
		row.add(new RoundButton(Icons.USER, singleplayer, false));
		row.add(new RoundButton(Icons.USERS, multiplayer, false));
		if (!client.isDemo()) row.add(new RoundButton(Icons.GLOBE, () -> client.setScreen(new RealmsMainScreen(this)), false));
		if (FabricLoader.getInstance().isModLoaded("modmenu")) row.add(new RoundButton(Icons.PUZZLE, this::openModMenu, false));
		row.add(new RoundButton(Icons.SETTINGS, () -> client.setScreen(new OptionsScreen(this, client.options)), false));
		row.add(new RoundButton(Icons.POWER, () -> client.scheduleStop(), true));

		float total = row.size() * size + (row.size() - 1) * gap;
		float x = width / 2f - total / 2f + size / 2f;
		float y = height * 0.6f;
		for (RoundButton button : row) {
			button.cx = x;
			button.cy = y;
			button.radius = size / 2f;
			buttons.add(button);
			x += size + gap;
		}

		RoundButton language = new RoundButton(Icons.LANGUAGES,
			() -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager())), false);
		language.radius = size * 0.34f;
		language.cx = width - 12f - language.radius;
		language.cy = height - 12f - language.radius;
		buttons.add(language);
		if (focused >= buttons.size()) focused = -1;

		MainMenuModule module = ModuleManager.get(MainMenuModule.class);
		int count = module != null && module.particles.on() ? module.particleCount.i() : 0;
		Random random = new Random(1337);
		particles = new Particle[count];
		for (int i = 0; i < count; i++) particles[i] = new Particle(random, width, height);
	}

	private void openModMenu() {
		try {
			Class<?> screen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			client.setScreen((Screen) screen.getConstructor(Screen.class).newInstance(this));
		} catch (ReflectiveOperationException | ClassCastException e) {
			V1zir.LOGGER.warn("Could not open Mod Menu", e);
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		// The shader background is drawn in render().
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme != null) theme.apply();
		MainMenuModule module = ModuleManager.get(MainMenuModule.class);
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.1f);
		lastFrame = now;
		// The loading overlay renders this screen with a fake mouse at (0, 0) while it fades out,
		// so mouse input is ignored until it is gone. The intro starts on the first real frame.
		boolean overlay = client.getOverlay() != null;
		boolean firstFrame = introStart < 0L && !overlay;
		if (firstFrame) introStart = now;
		float time = introStart < 0L ? 0f : (now - introStart) / 1.0e9f;
		float t = time / introScale;

		boolean parallax = module == null || module.parallax.on();
		float targetX = parallax ? (mouseX / (float) Math.max(width, 1) - 0.5f) * 2f : 0f;
		float targetY = parallax ? (0.5f - mouseY / (float) Math.max(height, 1)) * 2f : 0f;
		if (firstFrame) {
			parallaxX.snap(targetX);
			parallaxY.snap(targetY);
		} else if (!overlay) {
			parallaxX.target(targetX);
			parallaxY.target(targetY);
		}
		float px = parallaxX.get();
		float py = parallaxY.get();

		WallpaperRenderer.advance(module != null ? module.speed.f() : 1f);
		Render2D.setAlpha(1f);

		clock.update(client, module);
		boolean widgets = module == null || module.widgets.on();
		float open = lock.update(module == null || module.lockScreen.on(), widgets);
		if (!overlay) lock.drag(mouseY, height);
		if (lock.dragged()) hold.cancel();
		if (hold.update(mouseX, mouseY)) startEditing();

		// The picker draws first: while it is open (or zooming) its cards stand in for the screen's wallpaper.
		boolean picker = editor.render(ctx, width, height, mouseX, mouseY, px, py);
		Wallpapers.Screen editing = picker ? editor.screen() : null;
		float fade = picker ? editor.contentAlpha() : 1f;
		boolean interactive = !overlay && !lock.locked() && !editor.active();
		float shown = Anim.clamp01(open);
		if (shown > 0.001f) {
			if (editing != Wallpapers.Screen.HOME) {
				WallpaperStyle style = Wallpapers.style(Wallpapers.Screen.HOME);
				if (WallpaperRenderer.queueScreen(WallpaperRenderer.HOME, Wallpapers.selected(Wallpapers.Screen.HOME), style, px, py)) {
					WallpaperRenderer.backdrop(WallpaperRenderer.HOME);
					Render2D.wallpaper(ctx, WallpaperRenderer.HOME.textureSetup(), 0f, 0f, width, height, 0f, 0, style.tint(WallpaperEditor.LEVEL));
				} else {
					Render2D.rect(ctx, 0f, 0f, width, height, 0xFF050505);
				}
			}
			if (editing == Wallpapers.Screen.HOME) editor.pushContentTransform(ctx);
			if (fade > 0.004f) {
				Render2D.setAlpha(fade);
				renderParticles(ctx, dt, px, py);
				renderMenu(ctx, interactive ? mouseX : -10000, interactive ? mouseY : -10000, t, px, py, shown * fade);
			}
			if (editing == Wallpapers.Screen.HOME) ctx.getMatrices().popMatrix();
		}

		// Intro: the background rises out of black under the lock screen's clock, or under the menu's header and
		// buttons as they fade in.
		boolean locked = lock.visible();
		if (locked && editing != Wallpapers.Screen.LOCK) {
			WallpaperStyle style = Wallpapers.style(Wallpapers.Screen.LOCK);
			boolean ready = WallpaperRenderer.queueScreen(WallpaperRenderer.LOCK, Wallpapers.selected(Wallpapers.Screen.LOCK), style, px, py);
			// While its content shows, the lock screen's glass blurs its own wallpaper rather than the home screen's.
			if (ready && lock.contentShown()) WallpaperRenderer.backdrop(WallpaperRenderer.LOCK);
			lock.renderSheet(ctx, width, height, ready ? WallpaperRenderer.LOCK.textureSetup() : null, style.tint(WallpaperEditor.LEVEL));
		}
		float intro = Anim.easeInOutSine(Anim.progress(t, 0f, 1.1f));
		if (intro < 1f) Render2D.rect(ctx, 0, 0, width, height, ColorUtil.withAlpha(0x000000, Math.round((1f - intro) * 255)));
		if (locked) {
			if (editing == Wallpapers.Screen.LOCK) editor.pushContentTransform(ctx);
			boolean input = !overlay && !editor.active();
			lock.renderContent(ctx, client, width, height, t, time, clock, fade, input ? mouseX : -10000, input ? mouseY : -10000,
				widgets, module == null || module.notification.on());
			if (editing == Wallpapers.Screen.LOCK) ctx.getMatrices().popMatrix();
		}
		if (picker) editor.renderControls(ctx, mouseX, mouseY);
		hold.render(ctx);
		Render2D.setAlpha(1f);
	}

	/** The hold completed: the picker opens for the screen it was made on. */
	private void startEditing() {
		lock.cancelPress();
		pressed = null;
		swallowRelease = true;
		editor.open(lock.locked() ? Wallpapers.Screen.LOCK : Wallpapers.Screen.HOME, clock.polish());
	}

	/**
	 * Header, buttons and footer. While the lock screen slides away (shown below 1) the menu comes up from below
	 * and settles from a slightly smaller scale, like a phone's home screen.
	 */
	private void renderMenu(DrawContext ctx, int mouseX, int mouseY, float t, float px, float py, float shown) {
		Matrix3x2fStack matrices = ctx.getMatrices();
		boolean moving = shown < 1f;
		if (moving) {
			float scale = 0.92f + 0.08f * shown;
			matrices.pushMatrix();
			matrices.translate(width / 2f, height / 2f + (1f - shown) * height * 0.08f);
			matrices.scale(scale, scale);
			matrices.translate(-width / 2f, -height / 2f);
		}
		// Soft dark scrim behind the header and the buttons keeps them readable on bright presets.
		Render2D.setAlpha(shown);
		float scrimW = Math.min(width * 0.62f, 560f);
		float scrimH = height * 0.5f;
		Render2D.shadow(ctx, width / 2f - scrimW / 2f, height * 0.24f, scrimW, scrimH, scrimH / 2f, scrimH * 0.45f, 0x8C000000);

		renderHeader(ctx, Anim.easeOutQuart(Anim.progress(t, 0.25f, 0.9f)) * shown, px, py);

		Render2D.setAlpha(Anim.easeOutCubic(Anim.progress(t, 0.8f, 0.55f)) * shown);
		for (int i = 0; i < buttons.size(); i++) {
			RoundButton button = buttons.get(i);
			button.render(ctx, mouseX, mouseY, i == focused, button == pressed);
		}
		renderFooter(ctx);
		Render2D.setAlpha(1f);
		if (moving) matrices.popMatrix();
	}

	/** The time with the greeting (or the date) under it, where the big title used to be. */
	private void renderHeader(DrawContext ctx, float appear, float px, float py) {
		if (appear <= 0.004f) return;
		Render2D.setAlpha(appear);
		float size = Math.max(24f, Math.min(52f, height * 0.12f));
		float cx = width / 2f - px * 3f;
		float baseline = height * 0.3f + clock.digitHeight(size) / 2f + py * 2f + (1f - appear) * 8f;
		TIME_STYLE.color = 0xF2FFFFFF;
		clock.drawTime(ctx, cx, baseline, size, TIME_STYLE);
		float sub = Math.max(7f, Math.min(10f, height * 0.026f));
		float subY = baseline + size * 0.14f + sub;
		if (!clock.greeting.isEmpty()) {
			clock.drawGreeting(ctx, cx, subY, sub, 0xCCFFFFFF);
		} else {
			SdfFont font = Fonts.medium();
			Render2D.textCentered(ctx, font, clock.date, cx, Render2D.middle(font, subY, sub), sub, 0xCCFFFFFF);
		}
	}

	private void renderFooter(DrawContext ctx) {
		SdfFont font = Fonts.medium();
		float size = 7f;
		int dim = 0x99FFFFFF;
		Render2D.text(ctx, font, "Minecraft 1.21.11  ·  Fabric", 10f, height - 10f - font.lineHeight(size), size, dim);
		String version = V1zir.NAME + " " + V1zir.version();
		float w = font.width(version, size);
		Render2D.text(ctx, font, version, width - 10f - w, 10f, size, dim);

		String user = client.getSession().getUsername();
		SdfFont nameFont = Fonts.semibold();
		float nameSize = 7.5f;
		float pillH = 16f;
		float pillW = nameFont.width(user, nameSize) + pillH + 12f;
		float x = 10f;
		float y = 10f;
		Render2D.round(ctx, x, y, pillW, pillH, pillH / 2f, 0x55000000);
		Render2D.outline(ctx, x, y, pillW, pillH, pillH / 2f, 1f, 0x22FFFFFF);
		Render2D.icon(ctx, Icons.USER, x + pillH / 2f + 1f, y + pillH / 2f, 9f, 0xCCFFFFFF);
		Render2D.textMiddle(ctx, nameFont, user, x + pillH + 2f, y + pillH / 2f, nameSize, 0xEEFFFFFF);
	}

	private void renderParticles(DrawContext ctx, float dt, float px, float py) {
		for (Particle p : particles) {
			p.update(dt, width, height);
			float depth = p.depth;
			float x = p.x - px * 12f * depth;
			float y = p.y + py * 8f * depth;
			float twinkle = 0.55f + 0.45f * (float) Math.sin(p.phase + System.nanoTime() / 1.0e9 * p.twinkleSpeed);
			int color = ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFF, Theme.accent2(), p.tint), Math.round(170 * twinkle * depth));
			float r = p.size * depth;
			if (p.size > 1.3f) Render2D.shadow(ctx, x - r, y - r, r * 2, r * 2, r, r * 3f, ColorUtil.fade(color, 0.35f));
			Render2D.circle(ctx, x, y, r, color);
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (client.getOverlay() != null) return true;
		boolean left = click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		if (editor.active()) {
			if (left) editor.mouseClicked(click.x(), click.y());
			return true;
		}
		if (lock.locked()) {
			if (left) {
				lock.press(click.x(), click.y(), height);
				// Held still on the wallpaper (not on a widget or button), it opens the wallpaper picker.
				if (!lock.occupied(click.x(), click.y())) hold.start(click.x(), click.y());
			}
			return true;
		}
		if (left) {
			for (RoundButton button : buttons) {
				if (button.hovered(click.x(), click.y())) {
					pressed = button;
					focused = -1;
					return true;
				}
			}
			// An empty spot: held long enough, it opens the wallpaper picker.
			hold.start(click.x(), click.y());
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (editor.active()) {
			editor.mouseDragged(click.x());
			return true;
		}
		if (lock.locked()) {
			lock.drag(click.y(), height);
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			hold.cancel();
			if (swallowRelease) {
				swallowRelease = false;
				return true;
			}
			if (editor.active()) {
				editor.mouseReleased(click.x(), click.y());
				return true;
			}
			lock.release(click.x(), click.y(), height);
			RoundButton button = pressed;
			pressed = null;
			// It fires when let go over it, so the press can be seen (and taken back by moving away).
			if (button != null && button.hovered(click.x(), click.y())) {
				button.action.run();
				return true;
			}
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (editor.active()) {
			editor.mouseScrolled(verticalAmount != 0 ? verticalAmount : -horizontalAmount);
			return true;
		}
		if (lock.locked()) {
			if (verticalAmount != 0) lock.unlock();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (editor.active()) {
			editor.keyPressed(input.key());
			return true;
		}
		if (lock.locked()) {
			// Any key opens it, once the loading overlay is gone and the lock screen can be seen.
			if (client.getOverlay() == null) lock.unlock();
			return true;
		}
		int key = input.key();
		boolean back = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP
			|| key == GLFW.GLFW_KEY_TAB && (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
		if (back || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB) {
			int n = buttons.size();
			focused = focused < 0 ? (back ? n - 1 : 0) : Math.floorMod(focused + (back ? -1 : 1), n);
			return true;
		}
		if (focused >= 0 && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)) {
			buttons.get(focused).action.run();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void tick() {
		lock.tick();
	}

	@Override
	public void removed() {
		editor.dispose();
		lock.dispose();
		swallowRelease = false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/**
	 * Round icon button filled with frost: the blurred background tinted like the Click GUI's surfaces. Hovered, the
	 * fill brightens and lifts a little with a soft inner light that leans towards the cursor; pressed, it sinks and
	 * darkens. There is no outline ring.
	 */
	private static final class RoundButton {
		final int icon;
		final Runnable action;
		final boolean danger;
		float cx;
		float cy;
		float radius;
		/** About 150 ms to light up. */
		private final Anim hover = new Anim(0f, 20f);
		private final Anim press = new Anim(0f, 24f);
		private final Anim lightX = new Anim(0f, 9f);
		private final Anim lightY = new Anim(0f, 9f);

		RoundButton(int icon, Runnable action, boolean danger) {
			this.icon = icon;
			this.action = action;
			this.danger = danger;
		}

		boolean hovered(double mx, double my) {
			double dx = mx - cx;
			double dy = my - cy;
			return dx * dx + dy * dy <= radius * radius;
		}

		void render(DrawContext ctx, int mouseX, int mouseY, boolean focused, boolean held) {
			boolean over = hovered(mouseX, mouseY);
			float h = hover.target(over || focused ? 1f : 0f).get();
			float p = press.target(held && over ? 1f : 0f).get();
			float reach = radius * 0.22f;
			float lx = lightX.target(over ? Math.max(-1f, Math.min(1f, (mouseX - cx) / radius)) * reach : 0f).get();
			float ly = lightY.target(over ? Math.max(-1f, Math.min(1f, (mouseY - cy) / radius)) * reach : 0f).get();

			float r = radius * (1f + 0.04f * h - 0.08f * p);
			float d = r * 2f;
			float x = cx - r;
			float y = cy - r;
			Render2D.shadow(ctx, x, y + 1.5f + h, d, d, r, r * (0.7f + 0.25f * h), ColorUtil.withAlpha(0x000000, Math.round(70 + 25 * h)));
			int base = Theme.preset().base();
			if (BackdropBlur.available()) {
				int fill = ColorUtil.lerp(ColorUtil.withAlpha(base, 110), ColorUtil.withAlpha(ColorUtil.lerp(base, 0xFFFFFF, 0.3f), 118), h);
				Render2D.glass(ctx, x, y, d, d, r, 0, ShapeElement.MATTE, ColorUtil.lerp(fill, ColorUtil.withAlpha(base, 170), p * 0.7f));
			} else {
				int fill = ColorUtil.lerp(ColorUtil.withAlpha(ColorUtil.brighten(base, 0.06f), 150),
					ColorUtil.withAlpha(ColorUtil.lerp(base, 0xFFFFFF, 0.26f), 175), h);
				Render2D.matte(ctx, x, y, d, d, r, ColorUtil.lerp(fill, ColorUtil.withAlpha(base, 200), p * 0.7f), 0);
			}
			if (h > 0.01f) {
				// The light stays inside the button: its core plus its soft edge never reach past the rim.
				int tint = ColorUtil.lerp(0xFFFFFFFF, danger ? DANGER : Theme.accent(), 0.45f);
				float core = r * 0.2f;
				Render2D.shadow(ctx, cx + lx - core, cy + ly - core, core * 2f, core * 2f, core, r * 0.55f,
					ColorUtil.withAlpha(tint, Math.round(115 * h * (1f - 0.5f * p))));
			}
			Render2D.icon(ctx, icon, cx, cy, r * 0.92f, ColorUtil.lerp(0xC8FFFFFF, 0xFFFFFFFF, h));
		}
	}

	private static final class Particle {
		float x;
		float y;
		final float speed;
		final float size;
		final float depth;
		final float phase;
		final float twinkleSpeed;
		final float drift;
		final float tint;

		Particle(Random random, int width, int height) {
			x = random.nextFloat() * width;
			y = random.nextFloat() * height;
			depth = 0.35f + random.nextFloat() * 0.65f;
			speed = (4f + random.nextFloat() * 10f) * depth;
			size = 0.6f + random.nextFloat() * random.nextFloat() * 1.8f;
			phase = random.nextFloat() * 6.283f;
			twinkleSpeed = 0.8f + random.nextFloat() * 2.2f;
			drift = (random.nextFloat() - 0.5f) * 4f;
			tint = random.nextFloat() * 0.6f;
		}

		void update(float dt, int width, int height) {
			y -= speed * dt;
			x += drift * dt;
			if (y < -6) {
				y = height + 6;
				x = (x * 7.31f) % Math.max(width, 1);
			}
			if (x < -6) x = width + 6;
			if (x > width + 6) x = -6;
		}
	}
}
