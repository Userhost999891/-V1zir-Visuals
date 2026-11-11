package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.account.Accounts;
import dev.v1zir.visuals.gui.clickgui.Ui;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.render.BackdropBlur;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Heads;
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
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.player.SkinTextures;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Replacement title screen: an animated wallpaper, the time with a greeting and a row of round frosted icon buttons.
 * It opens on the {@link LockScreen}, which has a wallpaper of its own; once that slides away the menu comes up from
 * below, and a swipe up from an empty spot brings the lock screen back. Holding the mouse button on an empty spot of
 * either screen opens the {@link WallpaperEditor} for it. The name pill in the corner opens the {@link AccountsPanel}
 * to play as another account.
 * <p>
 * A button leaves the menu with a short animation (its content grows a little and fades while the wallpaper dims)
 * before the next screen comes in out of the dark ({@link ScreenTransition}). The classes of the screens the buttons
 * open are loaded in the background while the menu shows, so opening them the first time does not stall a frame.
 */
public final class V1zirMainMenu extends Screen {
	private static final int DANGER = 0xFFFF4D5E;
	/** Header clock style, refilled every frame instead of allocated. */
	private static final Render2D.TextStyle TIME_STYLE = new Render2D.TextStyle().weight(0f);
	private static boolean playedIntro;
	private static boolean warmedUp;

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
	/** The screen the menu is leaving for (null when staying), how far it has left (0 to 1) and whether it is set. */
	@Nullable
	private Supplier<Screen> leavingTo;
	private float leave;
	private boolean left;
	/** The world or server played last, offered under the buttons ("" when there is none), and its pill. */
	private String continueText = "";
	private int continueIcon;
	private final Anim continueHover = new Anim(0f, 20f);
	private final Anim continuePress = new Anim(0f, 24f);
	private float continueX;
	private float continueY;
	private float continueW;
	private float continueH;
	private boolean continuePressed;
	/** The accounts dropdown and the name pill that opens it (its bounds as last drawn). */
	private final AccountsPanel accounts = new AccountsPanel();
	private final Anim pillHover = new Anim(0f, 20f);
	private final Anim pillOpen = new Anim(0f, 16f);
	private float pillX;
	private float pillY;
	private float pillW;
	private float pillH;

	public V1zirMainMenu() {
		super(Text.literal(V1zir.NAME));
		introScale = playedIntro ? 0.45f : 1f;
		playedIntro = true;
	}

	@Override
	protected void init() {
		buttons.clear();
		pressed = null;
		if (left) {
			// Back from the screen it left for: the menu comes in again (the short intro) instead of staying faded.
			left = false;
			introStart = -1L;
		}
		leavingTo = null;
		leave = 0f;
		warmUp();
		// The account chosen last comes back before anything shows the player's name or head.
		Accounts.init(client);
		float size = Math.max(28f, Math.min(46f, height * 0.08f));
		float gap = size * 0.5f;
		Runnable singleplayer = () -> open(() -> new SelectWorldScreen(this));
		Runnable multiplayer = () -> open(() -> new MultiplayerScreen(this));
		lock.init(client, singleplayer, multiplayer, () -> LastPlayed.join(client, this));
		List<RoundButton> row = new ArrayList<>();
		row.add(new RoundButton(Icons.USER, singleplayer, false).label(label("menu.singleplayer")));
		row.add(new RoundButton(Icons.USERS, multiplayer, false).label(label("menu.multiplayer")));
		if (!client.isDemo()) row.add(new RoundButton(Icons.GLOBE, () -> open(() -> new RealmsMainScreen(this)), false).label(label("menu.online")));
		if (FabricLoader.getInstance().isModLoaded("modmenu")) row.add(new RoundButton(Icons.PUZZLE, this::openModMenu, false).label("Mods"));
		row.add(new RoundButton(Icons.SETTINGS, () -> open(() -> new OptionsScreen(this, client.options)), false).label(label("menu.options")));
		row.add(new RoundButton(Icons.POWER, () -> client.scheduleStop(), true).label(label("menu.quit")));

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
			() -> open(() -> new LanguageOptionsScreen(this, client.options, client.getLanguageManager())), false);
		language.radius = size * 0.34f;
		language.cx = width - 12f - language.radius;
		language.cy = height - 12f - language.radius;
		buttons.add(language);
		if (focused >= buttons.size()) focused = -1;

		// The world or server played last, if it is still there, one click away under the buttons.
		continueText = "";
		if (LastPlayed.available(client)) {
			continueText = LastPlayed.label();
			continueIcon = LastPlayed.server ? Icons.SERVER : Icons.PLAY;
			LastPlayedIcon.refresh(client);
		}
		continuePressed = false;

		MainMenuModule module = ModuleManager.get(MainMenuModule.class);
		int count = module != null && module.particles.on() ? module.particleCount.i() : 0;
		Random random = new Random(1337);
		particles = new Particle[count];
		for (int i = 0; i < count; i++) particles[i] = new Particle(random, width, height);
	}

	private void openModMenu() {
		try {
			Class<?> screen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			Screen mods = (Screen) screen.getConstructor(Screen.class).newInstance(this);
			open(() -> mods);
		} catch (ReflectiveOperationException | ClassCastException e) {
			V1zir.LOGGER.warn("Could not open Mod Menu", e);
		}
	}

	/** A button's name in the game's language, without the trailing dots some of them have ("Options..."). */
	private static String label(String key) {
		String text = Text.translatable(key).getString().strip();
		while (text.endsWith(".") || text.endsWith("…")) text = text.substring(0, text.length() - 1).strip();
		return text;
	}

	/** Leaves the menu for the screen: the menu's exit plays first, then the screen is made and comes in. */
	private void open(Supplier<Screen> screen) {
		if (leavingTo != null) return;
		leavingTo = screen;
		leave = 0f;
		pressed = null;
	}

	/**
	 * Loads the classes of the screens the menu opens, and what their declarations name, on a background thread once
	 * per game: the first opening of the world or server list then has nothing to load. Loading runs no static
	 * initialisers, so nothing of the game is touched off its thread.
	 */
	private static void warmUp() {
		if (warmedUp) return;
		warmedUp = true;
		Thread thread = new Thread(() -> {
			Class<?>[] screens = {SelectWorldScreen.class, WorldListWidget.class, CreateWorldScreen.class, MultiplayerScreen.class,
				MultiplayerServerListWidget.class, ServerList.class, AddServerScreen.class, DirectConnectScreen.class, OptionsScreen.class,
				LanguageOptionsScreen.class, RealmsMainScreen.class};
			for (Class<?> type : screens) {
				try {
					type.getDeclaredMethods();
					type.getDeclaredFields();
					for (Class<?> inner : type.getDeclaredClasses()) {
						inner.getDeclaredMethods();
						inner.getDeclaredFields();
					}
				} catch (Throwable ignored) {
					// Whatever did not load now loads when the screen opens, as it did before.
				}
			}
		}, "V1zir menu warm-up");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
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
		// Leaving for another screen: once the exit has played, the screen is made and set (outside this frame).
		if (leavingTo != null) {
			leave = Math.min(1f, leave + Math.min(dt, 1f / 30f) / ScreenTransition.LEAVE_SECONDS);
			if (leave >= 1f && !left) {
				left = true;
				Supplier<Screen> next = leavingTo;
				client.execute(() -> {
					Screen screen = next.get();
					ScreenTransition.enter(screen);
					client.setScreen(screen);
				});
			}
		}
		float exit = Anim.easeInCubic(leave);
		if (lock.dragged()) hold.cancel();
		if (hold.update(mouseX, mouseY)) startEditing();

		// The picker draws first: while it is open (or zooming) its cards stand in for the screen's wallpaper.
		boolean picker = editor.render(ctx, width, height, mouseX, mouseY, px, py);
		Wallpapers.Screen editing = picker ? editor.screen() : null;
		float fade = picker ? editor.contentAlpha() : 1f;
		boolean interactive = !overlay && !lock.locked() && !editor.active() && leavingTo == null;
		if (!interactive && !overlay) accounts.close();
		// The lock screen rising over the menu (a swipe up) dims it as it comes.
		float shown = Anim.clamp01(open) * (1f - 0.6f * lock.risen()) * (1f - exit);
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
				// Leaving, the content grows a little as it fades, as if passing through it.
				boolean zoom = exit > 0.001f;
				if (zoom) {
					float scale = 1f + 0.06f * exit;
					ctx.getMatrices().pushMatrix();
					ctx.getMatrices().translate(width / 2f, height / 2f);
					ctx.getMatrices().scale(scale, scale);
					ctx.getMatrices().translate(-width / 2f, -height / 2f);
				}
				Render2D.setAlpha(fade * (1f - exit));
				renderParticles(ctx, dt, px, py);
				renderMenu(ctx, interactive ? mouseX : -10000, interactive ? mouseY : -10000, t, px, py, shown * fade);
				if (zoom) ctx.getMatrices().popMatrix();
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
		// The wallpaper dims into the dark the next screen comes in from.
		if (exit > 0.001f) Render2D.rect(ctx, 0, 0, width, height, ColorUtil.withAlpha(0x000000, Math.round(200f * exit)));
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

		// Under the open accounts panel the buttons are not hovered.
		boolean panel = accounts.over(mouseX, mouseY);
		int bx = panel ? -10000 : mouseX;
		int by = panel ? -10000 : mouseY;
		float controls = Anim.easeOutCubic(Anim.progress(t, 0.8f, 0.55f)) * shown;
		Render2D.setAlpha(controls);
		for (int i = 0; i < buttons.size(); i++) {
			RoundButton button = buttons.get(i);
			button.render(ctx, bx, by, i == focused, button == pressed);
		}
		renderContinue(ctx, bx, by, Anim.easeOutCubic(Anim.progress(t, 0.95f, 0.55f)) * shown);
		Render2D.setAlpha(controls);
		renderFooter(ctx, mouseX, mouseY);
		accounts.render(ctx, client, pillX, pillY + pillH + 5f, height, mouseX, mouseY);
		Render2D.setAlpha(1f);
		if (moving) matrices.popMatrix();
	}

	/**
	 * "Continue" with the name of the world or server played last, in a frosted pill under the buttons; a click loads it
	 * (the lock screen's widget does the same).
	 */
	private void renderContinue(DrawContext ctx, int mouseX, int mouseY, float alpha) {
		continueW = 0f;
		if (continueText.isEmpty() || buttons.isEmpty() || alpha <= 0.004f) return;
		RoundButton first = buttons.getFirst();
		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.medium();
		String head = "Continue";
		float size = Math.max(6.5f, Math.min(8f, height * 0.019f));
		continueH = size * 2.6f;
		float headW = bold.width(head, size);
		String name = continueText;
		float maxName = Math.min(width * 0.4f, 220f);
		while (font.width(name, size) > maxName && name.length() > 2) name = name.substring(0, name.length() - 2);
		if (!name.equals(continueText)) name = name + "…";
		float nameW = font.width(name, size);
		continueW = continueH + headW + 8f + nameW + continueH * 0.55f;
		continueX = width / 2f - continueW / 2f;
		continueY = first.cy + first.radius + first.labelSpace() + 10f;
		boolean over = Ui.inside(mouseX, mouseY, continueX, continueY, continueW, continueH);
		float h = continueHover.target(over ? 1f : 0f).get();
		float p = continuePress.target(continuePressed && over ? 1f : 0f).get();
		Render2D.setAlpha(alpha);
		float lift = 1f + 0.03f * h - 0.05f * p;
		float w = continueW * lift;
		float ph = continueH * lift;
		float x = width / 2f - w / 2f;
		float y = continueY + (continueH - ph) / 2f;
		Render2D.shadow(ctx, x, y + 1.5f, w, ph, ph / 2f, 10f, ColorUtil.withAlpha(0x000000, Math.round(60 + 30 * h)));
		int base = Theme.preset().base();
		int fill = ColorUtil.lerp(ColorUtil.withAlpha(base, 120), ColorUtil.withAlpha(ColorUtil.lerp(base, 0xFFFFFF, 0.28f), 130), h);
		if (BackdropBlur.available()) Render2D.glass(ctx, x, y, w, ph, ph / 2f, 0, ShapeElement.MATTE, fill);
		else Render2D.matte(ctx, x, y, w, ph, ph / 2f, fill, 0);
		Render2D.outline(ctx, x, y, w, ph, ph / 2f, 0.8f, ColorUtil.withAlpha(0xFFFFFF, Math.round(26 + 30 * h)));
		float cy = y + ph / 2f;
		TextureSetup picture = LastPlayedIcon.get();
		if (picture != null) {
			// The world's picture or the server's icon, rounded like the pill.
			float side = ph * 0.72f;
			Render2D.image(ctx, picture, x + ph / 2f - side / 2f, cy - side / 2f, side, side, side * 0.3f, 0xFFFFFFFF);
		} else {
			Render2D.circle(ctx, x + ph / 2f, cy, ph * 0.36f, ColorUtil.lerp(ColorUtil.withAlpha(Theme.accent(), 200), Theme.accent(), h));
			Render2D.icon(ctx, continueIcon, x + ph / 2f + (continueIcon == Icons.PLAY ? ph * 0.03f : 0f), cy, ph * 0.38f, 0xFFFFFFFF);
		}
		float tx = x + ph + 2f;
		Render2D.textMiddle(ctx, bold, head, tx, cy, size, 0xF2FFFFFF);
		Render2D.textMiddle(ctx, font, name, tx + headW + 8f, cy, size, ColorUtil.lerp(0xA6FFFFFF, 0xE6FFFFFF, h));
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

	private void renderFooter(DrawContext ctx, int mouseX, int mouseY) {
		SdfFont font = Fonts.medium();
		float size = 7f;
		int dim = 0x99FFFFFF;
		Render2D.text(ctx, font, "Minecraft 1.21.11  ·  Fabric", 10f, height - 10f - font.lineHeight(size), size, dim);
		String version = V1zir.NAME + " " + V1zir.version();
		float w = font.width(version, size);
		Render2D.text(ctx, font, version, width - 10f - w, 10f, size, dim);
		renderPill(ctx, mouseX, mouseY);
	}

	/**
	 * The account in use, with its head, in a pill in the corner; a click on it opens the accounts panel under it (the
	 * chevron turns over while it is open).
	 */
	private void renderPill(DrawContext ctx, int mouseX, int mouseY) {
		String user = client.getSession().getUsername();
		SdfFont nameFont = Fonts.semibold();
		float nameSize = 7.5f;
		pillH = 18f;
		float head = pillH - 6f;
		pillW = 3f + head + 6f + nameFont.width(user, nameSize) + 6f + 8f + 6f;
		pillX = 10f;
		pillY = 9f;
		boolean over = Ui.inside(mouseX, mouseY, pillX, pillY, pillW, pillH);
		float hv = pillHover.target(over ? 1f : 0f).get();
		float op = pillOpen.target(accounts.shown() ? 1f : 0f).get();
		float lit = Math.max(hv, op);
		Render2D.round(ctx, pillX, pillY, pillW, pillH, pillH / 2f, ColorUtil.lerp(0x55000000, 0x80101010, lit));
		Render2D.outline(ctx, pillX, pillY, pillW, pillH, pillH / 2f, 1f,
			ColorUtil.lerp(0x22FFFFFF, ColorUtil.withAlpha(Theme.accent(), 170), op * 0.8f + hv * 0.2f));
		float hx = pillX + 3f;
		float hy = pillY + 3f;
		Accounts.Account account = Accounts.current();
		SkinTextures skin = account != null ? accounts.skin(client, account) : null;
		if (skin != null) Heads.skin(ctx, skin, hx, hy, head, head / 2f, 0xFFFFFFFF);
		else Render2D.icon(ctx, Icons.USER, hx + head / 2f, hy + head / 2f, 9f, 0xCCFFFFFF);
		float tx = hx + head + 6f;
		Render2D.textMiddle(ctx, nameFont, user, tx, pillY + pillH / 2f, nameSize, 0xEEFFFFFF);
		float cx = pillX + pillW - 10f;
		float cy = pillY + pillH / 2f;
		// The chevron turns over as the panel opens.
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(cx, cy);
		ctx.getMatrices().rotate((float) Math.PI * Anim.easeInOutSine(op));
		Render2D.icon(ctx, Icons.CHEVRON_DOWN, 0f, 0f, 6.5f, ColorUtil.lerp(0x99FFFFFF, 0xF2FFFFFF, lit));
		ctx.getMatrices().popMatrix();
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
		if (client.getOverlay() != null || leavingTo != null) return true;
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
			boolean onPill = pillW > 0f && Ui.inside(click.x(), click.y(), pillX, pillY, pillW, pillH);
			if (accounts.shown()) {
				// A click outside the open panel closes it and does nothing else.
				if (!accounts.mouseClicked(client, click.x(), click.y())) accounts.close();
				return true;
			}
			if (onPill) {
				accounts.toggle();
				hold.cancel();
				return true;
			}
			for (RoundButton button : buttons) {
				if (button.hovered(click.x(), click.y())) {
					pressed = button;
					focused = -1;
					return true;
				}
			}
			if (continueW > 0f && Ui.inside(click.x(), click.y(), continueX, continueY, continueW, continueH)) {
				continuePressed = true;
				return true;
			}
			// An empty spot: held long enough, it opens the wallpaper picker; swiped up, it brings the lock screen back.
			hold.start(click.x(), click.y());
			if (lockScreenOn()) lock.pressRaise(click.y());
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	private static boolean lockScreenOn() {
		MainMenuModule module = ModuleManager.get(MainMenuModule.class);
		return module == null || module.lockScreen.on();
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
		if (lock.dragRaise(click.y(), height)) {
			hold.cancel();
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
			lock.releaseRaise();
			if (continuePressed) {
				continuePressed = false;
				if (continueW > 0f && Ui.inside(click.x(), click.y(), continueX, continueY, continueW, continueH)) {
					LastPlayed.join(client, this);
					return true;
				}
			}
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
		if (accounts.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (leavingTo != null) return true;
		if (editor.active()) {
			editor.keyPressed(input.key());
			return true;
		}
		if (lock.locked()) {
			// Any key opens it, once the loading overlay is gone and the lock screen can be seen.
			if (client.getOverlay() == null) lock.unlock();
			return true;
		}
		if (accounts.keyPressed(client, input.key(), input.modifiers())) return true;
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

	/** Typing goes into the accounts panel's nickname field while it is open. */
	@Override
	public boolean charTyped(CharInput input) {
		if (input.isValidChar() && !lock.locked() && !editor.active() && accounts.charTyped(input.asString())) return true;
		return super.charTyped(input);
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
		/** The name under the button, or "" for none. */
		private String label = "";
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

		RoundButton label(String label) {
			this.label = label;
			return this;
		}

		/** Room the label takes under the button. */
		float labelSpace() {
			return label.isEmpty() ? 0f : labelSize() * 2.2f;
		}

		private float labelSize() {
			return Math.max(5.5f, Math.min(7f, radius * 0.3f));
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
			if (!label.isEmpty()) {
				// The name under it, shrunk to fit between its neighbours; it lights up with the button.
				SdfFont font = Fonts.medium();
				float size = labelSize();
				float max = radius * 3.1f;
				float w = font.width(label, size);
				if (w > max) {
					size *= max / w;
					w = max;
				}
				int color = ColorUtil.lerp(0x99FFFFFF, danger ? DANGER : 0xFFFFFFFF, h);
				Render2D.textCentered(ctx, font, label, cx, Render2D.middle(font, cy + radius + labelSpace() * 0.55f, size), size, color);
			}
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
