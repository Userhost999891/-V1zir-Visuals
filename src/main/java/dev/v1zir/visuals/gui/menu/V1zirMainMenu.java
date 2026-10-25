package dev.v1zir.visuals.gui.menu;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.sky.MenuSky;
import dev.v1zir.visuals.render.sky.SkyPreset;
import dev.v1zir.visuals.render.ui.Render2D;
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
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Replacement title screen: animated shader background, the V1zir Visuals logo and a row of
 * round icon buttons. Buttons have no tooltips and no hover animation - they simply light up.
 */
public final class V1zirMainMenu extends Screen {
	private static final int DANGER = 0xFFFF4D5E;
	/** Title text styles, refilled every frame instead of allocated. */
	private static final Render2D.TextStyle GLOW_STYLE = new Render2D.TextStyle();
	private static final Render2D.TextStyle TITLE_STYLE = new Render2D.TextStyle();
	private static final Render2D.TextStyle SUBTITLE_STYLE = Render2D.TextStyle.of(0xC8FFFFFF);
	private static boolean playedIntro;

	private final List<RoundButton> buttons = new ArrayList<>();
	private final Anim parallaxX = new Anim(0f, 3.5f);
	private final Anim parallaxY = new Anim(0f, 3.5f);
	/** 1 for the first intro of the session, shorter when coming back to the menu later. */
	private final float introScale;
	private long introStart = -1L;
	private Particle[] particles = new Particle[0];
	private long lastFrame = System.nanoTime();

	public V1zirMainMenu() {
		super(Text.literal(V1zir.NAME));
		introScale = playedIntro ? 0.45f : 1f;
		playedIntro = true;
	}

	@Override
	protected void init() {
		buttons.clear();
		float size = Math.max(26f, Math.min(42f, height * 0.072f));
		float gap = size * 0.42f;
		List<RoundButton> row = new ArrayList<>();
		row.add(new RoundButton(Icons.USER, () -> client.setScreen(new SelectWorldScreen(this)), false));
		row.add(new RoundButton(Icons.USERS, () -> client.setScreen(new MultiplayerScreen(this)), false));
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

		SkyPreset preset = module != null ? module.preset() : SkyPreset.MAGMA;
		float speed = module != null ? module.speed.f() : 1f;
		MenuSky.prepare(preset, width, height, px, py, speed);

		Render2D.setAlpha(1f);
		Render2D.menuBackground(ctx, width, height, 0xFFD2D2D2);
		renderParticles(ctx, dt, px, py);
		// Soft dark scrim behind the logo and the buttons keeps them readable on bright presets.
		float scrimW = Math.min(width * 0.62f, 560f);
		float scrimH = height * 0.5f;
		Render2D.shadow(ctx, width / 2f - scrimW / 2f, height * 0.24f, scrimW, scrimH, scrimH / 2f, scrimH * 0.45f, 0x8C000000);

		// Intro: the background rises out of black, the logo slides up and comes into focus,
		// the underline grows from the centre and the buttons fade in (they never move).
		float fade = Anim.easeInOutSine(Anim.progress(t, 0f, 1.1f));
		if (fade < 1f) Render2D.rect(ctx, 0, 0, width, height, ColorUtil.withAlpha(0x000000, Math.round((1f - fade) * 255)));

		float titleT = Anim.easeOutQuart(Anim.progress(t, 0.25f, 0.9f));
		float subT = Anim.easeOutCubic(Anim.progress(t, 0.55f, 0.6f));
		float lineT = Anim.easeInOutCubic(Anim.progress(t, 0.6f, 0.75f));
		renderTitle(ctx, titleT, subT, lineT, time, px, py);

		float buttonsAlpha = Anim.easeOutCubic(Anim.progress(t, 0.8f, 0.55f));
		Render2D.setAlpha(buttonsAlpha);
		int hoverX = overlay ? -10000 : mouseX;
		int hoverY = overlay ? -10000 : mouseY;
		for (RoundButton button : buttons) button.render(ctx, hoverX, hoverY);
		renderFooter(ctx);
		Render2D.setAlpha(1f);
	}

	private void renderTitle(DrawContext ctx, float titleT, float subT, float lineT, float time, float px, float py) {
		if (titleT <= 0f) return;
		SdfFont font = Fonts.title();
		String title = V1zir.NAME;
		float size = Math.max(24f, Math.min(52f, height * 0.095f));
		// Letters start slightly spread out and blurred, then settle into place.
		float spacing = size * (0.035f + 0.09f * (1f - titleT));
		float cx = width / 2f - px * 3f;
		float baseY = height * 0.33f - size * 0.75f + py * 2f;
		float y = baseY + (1f - titleT) * 12f;
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		// The glow breathes slowly once the logo is in place.
		int glowAlpha = Math.round(150f * (0.82f + 0.18f * (float) Math.sin(time * 1.3f)));

		Render2D.setAlpha(titleT);
		GLOW_STYLE.color = ColorUtil.withAlpha(accent, glowAlpha);
		Render2D.textCentered(ctx, font, title, cx, y, size,
			GLOW_STYLE.gradient(ColorUtil.withAlpha(accent2, glowAlpha)).soft(0.3f + 0.3f * (1f - titleT)).weight(0.16f).spacing(spacing));
		Render2D.textCentered(ctx, font, title, cx, y, size,
			TITLE_STYLE.gradient(ColorUtil.lerp(0xFFFFFFFF, accent2, 0.45f)).weight(0.03f).soft(0.45f * (1f - titleT)).spacing(spacing).shadow(0x70000000));

		SdfFont small = Fonts.medium();
		float subSize = Math.max(6f, size * 0.17f);
		float subY = baseY + font.lineHeight(size) * 0.98f;
		if (subT > 0f) {
			Render2D.setAlpha(subT);
			Render2D.textCentered(ctx, small, "VISUALS  ·  MINECRAFT 1.21.11", cx, subY + (1f - subT) * 5f, subSize,
				SUBTITLE_STYLE.spacing(subSize * 0.38f));
		}
		if (lineT > 0f) {
			float lineW = size * 2.2f * lineT;
			float lineY = subY + small.lineHeight(subSize) + 6f;
			Render2D.setAlpha(Math.min(1f, lineT * 1.6f));
			Render2D.glow(ctx, cx - lineW / 4f, lineY, lineW / 2f, 1.2f, 0.6f, 6f, ColorUtil.withAlpha(accent, 80), ColorUtil.withAlpha(accent2, 80));
			Render2D.gradientH(ctx, cx - lineW / 2f, lineY, lineW / 2f, 1.2f, 0.6f, ColorUtil.withAlpha(accent, 0), accent);
			Render2D.gradientH(ctx, cx, lineY, lineW / 2f, 1.2f, 0.6f, accent2, ColorUtil.withAlpha(accent2, 0));
		}
		Render2D.setAlpha(1f);
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
		if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (RoundButton button : buttons) {
				if (button.hovered(click.x(), click.y())) {
					button.action.run();
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static final class RoundButton {
		final int icon;
		final Runnable action;
		final boolean danger;
		float cx;
		float cy;
		float radius;

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

		void render(DrawContext ctx, int mouseX, int mouseY) {
			boolean hover = hovered(mouseX, mouseY);
			int accent = danger ? DANGER : Theme.accent();
			float d = radius * 2f;
			float x = cx - radius;
			float y = cy - radius;
			if (hover) {
				Render2D.shadow(ctx, x, y, d, d, radius, radius * 0.9f, ColorUtil.withAlpha(accent, 120));
				Render2D.circle(ctx, cx, cy, radius, ColorUtil.withAlpha(ColorUtil.darken(accent, 0.55f), 175));
				Render2D.ring(ctx, cx, cy, radius, 1.3f, accent);
				Render2D.icon(ctx, icon, cx, cy, radius * 0.92f, 0xFFFFFFFF);
			} else {
				Render2D.shadow(ctx, x, y + 1.5f, d, d, radius, radius * 0.7f, 0x55000000);
				Render2D.circle(ctx, cx, cy, radius, 0x66000000);
				Render2D.ring(ctx, cx, cy, radius, 1f, 0x33FFFFFF);
				Render2D.icon(ctx, icon, cx, cy, radius * 0.92f, 0xC8FFFFFF);
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
