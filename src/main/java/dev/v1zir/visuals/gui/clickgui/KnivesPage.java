package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.knives.KnivesModule;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.knife.KnifeAnimator;
import dev.v1zir.visuals.render.knife.KnifeFinish;
import dev.v1zir.visuals.render.knife.KnifePreview;
import dev.v1zir.visuals.render.knife.KnifeType;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.KeyNames;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * The Knives tab, a knife library: a large live preview of the selected knife on the left (it sways gently, turns
 * when dragged and plays the knife's inspect on request) with its finish swatches under it, a grid of knife cards on
 * the right (each a small 3D render of that knife in the chosen finish, with its name; the selected one wears the
 * accent), and the Knives module's switch and settings in the header. Previews render only while the tab is on
 * screen, and a card only renders again when its picture changes (finish, size, or while it is hovered).
 */
final class KnivesPage {
	private static final KnifeType[] TYPES = KnifeType.values();
	private static final KnifeFinish[] FINISHES = KnifeFinish.values();
	private static final String SUBTITLE = TYPES.length + " knives, " + FINISHES.length + " finishes";
	private static final float GAP = 10f;
	private static final float PAD = 12f;
	private static final float CARD_H = 66f;
	private static final float CARD_GAP = 8f;
	private static final float SWATCH = 12f;
	private static final float SWATCH_GAP = 5f;
	private static final float BUTTON_H = 18f;
	private static final float GEAR = 18f;
	/** Previews render at this times the pixels they cover, so the GUI's filtering smooths their edges. */
	private static final float SUPERSAMPLE = 2f;
	/** Resting view of the knives: a little turned and tilted towards the viewer. */
	private static final float YAW = -16f;
	private static final float PITCH = 10f;

	// Shared by every screen (a new one is made each time the GUI opens), so the GPU targets are made once.
	private static final KnifePreview BIG = new KnifePreview("V1zir knife preview");
	private static final KnifePreview[] CARDS = new KnifePreview[TYPES.length];
	private static final TextureSetup[] CARD_IMAGES = new TextureSetup[TYPES.length];
	/** What each card's image shows: the finish, pixel size and turn it was rendered with. */
	private static final int[] CARD_FINISH = new int[TYPES.length];
	private static final int[] CARD_PW = new int[TYPES.length];
	private static final int[] CARD_PH = new int[TYPES.length];
	private static final float[] CARD_TURN = new float[TYPES.length];
	private static final KnifeAnimator INSPECT = new KnifeAnimator();
	private static final Matrix4f REST = new Matrix4f();
	private static final Matrix4f MOTION = new Matrix4f();

	static {
		for (int i = 0; i < TYPES.length; i++) {
			CARDS[i] = new KnifePreview("V1zir knife card " + TYPES[i].title());
			CARD_FINISH[i] = -1;
		}
	}

	private final Consumer<Module> openSettings;
	private final Scroller scroller = new Scroller();
	private final Anim[] cardHover = new Anim[TYPES.length];
	private final Anim[] cardSelect = new Anim[TYPES.length];
	private final Anim[] swatchHover = new Anim[FINISHES.length];
	private final Anim power = new Anim(0f, 18f);
	private final Anim powerHover = new Anim(0f, 22f);
	private final Anim gearHover = new Anim(0f, 22f);
	private final Anim playHover = new Anim(0f, 22f);
	/** 1 while the preview sways on its own; eases out while it is dragged or plays the inspect. */
	private final Anim idle = new Anim(1f, 4f);
	private final Label stateWidth = new Label();
	private final Label keyWidth = new Label();
	private final Label playWidth = new Label();
	// Layout of the last frame.
	private float viewX;
	private float viewY;
	private float viewW;
	private float viewH;
	private float gridX;
	private float gridY;
	private float gridW;
	private float gridH;
	private float cardW;
	private int cols = 2;
	private float scroll;
	private float contentHeight;
	private float previewX;
	private float previewY;
	private float previewW;
	private float previewH;
	private float swatchX;
	private float swatchY;
	private int swatchCols = 1;
	private float playX;
	private float playY;
	private float playW;
	private float powerX;
	private float powerY;
	private float powerW;
	private float gearX;
	private float gearY;
	// Turning the preview.
	private boolean dragging;
	private double dragX;
	private double dragY;
	private float userYaw;
	private float userPitch;
	private float clock;
	private long lastFrame;
	private boolean inspecting;
	private KnifeType inspected;

	KnivesPage(Consumer<Module> openSettings) {
		this.openSettings = openSettings;
		for (int i = 0; i < TYPES.length; i++) {
			cardHover[i] = new Anim(0f, 20f);
			cardSelect[i] = new Anim(0f, 14f);
		}
		for (int i = 0; i < FINISHES.length; i++) swatchHover[i] = new Anim(0f, 22f);
		KnivesModule module = ModuleManager.get(KnivesModule.class);
		if (module != null) {
			power.snap(module.isEnabled() ? 1f : 0f);
			for (int i = 0; i < TYPES.length; i++) cardSelect[i].snap(module.type() == TYPES[i] ? 1f : 0f);
		}
	}

	void reset() {
		scroller.reset();
	}

	// ------------------------------------------------------------------ header

	/** The subtitle next to the tab title, and on the right the module's switch and its settings button. */
	void renderHeader(DrawContext ctx, float subtitleX, float right, float mid, double mx, double my) {
		Render2D.textMiddle(ctx, Fonts.medium(), SUBTITLE, subtitleX, mid + 1f, Ui.TINY, Theme.textFaint());
		KnivesModule module = ModuleManager.get(KnivesModule.class);
		if (module == null) return;
		gearX = right - GEAR;
		gearY = mid - GEAR / 2f;
		float gh = gearHover.target(Ui.inside(mx, my, gearX, gearY, GEAR, GEAR) ? 1f : 0f).get();
		Ui.iconButton(ctx, gearX, gearY, GEAR, Icons.SETTINGS, gh, 0f);
		boolean on = module.isEnabled();
		String state = on ? "Enabled" : "Disabled";
		float textW = stateWidth.width(Fonts.medium(), state, Ui.SMALL);
		powerW = textW + 8f + Ui.SWITCH_W + 16f;
		powerX = gearX - 8f - powerW;
		powerY = mid - 10f;
		float ph = powerHover.target(Ui.inside(mx, my, powerX, powerY, powerW, 20f) ? 1f : 0f).get();
		float t = power.target(on ? 1f : 0f).get();
		Render2D.matte(ctx, powerX, powerY, powerW, 20f, Ui.CELL_RADIUS + 2f, ColorUtil.withAlpha(0xFFFFFF, Math.round(10 + 10 * ph)), Ui.LIT);
		Render2D.rim(ctx, powerX, powerY, powerW, 20f, Ui.CELL_RADIUS + 2f, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(20 + 16 * ph)),
			ColorUtil.withAlpha(Theme.accent(), 150), t), Ui.LIT);
		Render2D.textMiddle(ctx, Fonts.medium(), state, powerX + 8f, mid, Ui.SMALL, ColorUtil.lerp(Theme.textDim(), Theme.text(), t));
		Ui.toggle(ctx, powerX + powerW - 8f - Ui.SWITCH_W, mid - Ui.SWITCH_H / 2f, t, ph);
	}

	// ------------------------------------------------------------------ page

	void render(DrawContext ctx, float x, float y, float w, float h, double mx, double my, boolean mouseFree) {
		viewX = x;
		viewY = y;
		viewW = w;
		viewH = h;
		KnivesModule module = ModuleManager.get(KnivesModule.class);
		if (module == null) return;
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		clock += dt;
		double hx = mouseFree ? mx : -1000;
		double hy = mouseFree ? my : -1000;
		float pixels = pixelScale() * SUPERSAMPLE;
		float panelW = Math.max(170f, Math.min(270f, w * 0.42f));
		renderPreview(ctx, module, x, y, panelW, h, hx, hy, dt, pixels);
		gridX = x + panelW + GAP;
		gridY = y;
		gridW = w - panelW - GAP;
		gridH = h;
		renderGrid(ctx, module, hx, hy, pixels);
	}

	/** The selected knife large: name and finish, the live 3D view, Play inspect, and the finish swatches. */
	private void renderPreview(DrawContext ctx, KnivesModule module, float x, float y, float w, float h, double mx, double my, float dt,
							   float pixels) {
		KnifeType type = module.type();
		KnifeFinish finish = module.finish();
		Render2D.matte(ctx, x, y, w, h, Ui.PANEL_RADIUS, Theme.raised(), Ui.LIT);
		Render2D.rim(ctx, x, y, w, h, Ui.PANEL_RADIUS, Ui.HAIRLINE, Ui.LIT);
		Render2D.textMiddle(ctx, Fonts.bold(), type.title(), x + PAD, y + PAD + 4f, 10f, Theme.text());
		Render2D.gradientH(ctx, x + PAD, y + PAD + 13f, 22f, 2f, 1f, finish.light(), finish.dark());
		Render2D.textMiddle(ctx, Fonts.medium(), finish.title(), x + PAD + 27f, y + PAD + 14f, Ui.SMALL, Theme.textDim());

		// Swatches at the bottom, Play inspect above them, the view fills the rest.
		swatchCols = Math.max(1, (int) ((w - PAD * 2f + SWATCH_GAP) / (SWATCH + SWATCH_GAP)));
		int swatchRows = (FINISHES.length + swatchCols - 1) / swatchCols;
		float swatchesH = swatchRows * (SWATCH + SWATCH_GAP) - SWATCH_GAP;
		swatchX = x + PAD;
		swatchY = y + h - PAD - swatchesH;
		playY = swatchY - 10f - BUTTON_H;
		previewX = x + 4f;
		previewY = y + PAD + 24f;
		previewW = w - 8f;
		previewH = Math.max(20f, playY - 6f - previewY);

		// Turning: a gentle sway on its own, the user's drag on top, settled to the side view while inspecting.
		boolean wasInspecting = inspecting;
		if (inspecting && (inspected != type || !INSPECT.inspecting())) inspecting = false;
		float sway = idle.target(dragging || inspecting ? 0f : 1f).get();
		float yaw = YAW + userYaw + 22f * (float) Math.sin(clock * 0.55f) * sway;
		float pitch = PITCH + userPitch + 6f * (float) Math.sin(clock * 0.42f + 1f) * sway;
		Matrix4f motion = null;
		float[] parts = null;
		if (inspecting || wasInspecting) {
			INSPECT.update(dt, module.speed.f(), 0f, 0f, 0f, 0f, 0f, 0f);
			if (inspecting) {
				// The knife's motion relative to its rest pose in the hand, in the model's centimetres.
				MOTION.set(REST).mul(INSPECT.knife);
				float scale = INSPECT.scale();
				MOTION.m30(MOTION.m30() / scale).m31(MOTION.m31() / scale).m32(MOTION.m32() / scale);
				motion = MOTION;
				parts = INSPECT.parts;
			}
		}

		// A soft pool of the finish's light behind the knife, and its shadow below.
		float cx = previewX + previewW / 2f;
		float cy = previewY + previewH / 2f;
		Render2D.glow(ctx, cx - previewW * 0.32f, cy - previewH * 0.18f, previewW * 0.64f, previewH * 0.36f, previewH * 0.18f, previewH * 0.35f,
			ColorUtil.withAlpha(finish.light(), 34), ColorUtil.withAlpha(finish.dark(), 30));
		Render2D.shadow(ctx, cx - previewW * 0.26f, previewY + previewH * 0.84f, previewW * 0.52f, 3f, 1.5f, 10f, 0x55000000);
		TextureSetup image = BIG.render(type, finish, Math.round(previewW * pixels), Math.round(previewH * pixels), 0.8f, yaw, pitch, motion, parts);
		if (image != null) Render2D.image(ctx, image, previewX, previewY, previewW, previewH, 0f, 0xFFFFFFFF, 0f, 1f, 1f, 0f, 0);
		else Render2D.icon(ctx, Icons.SWORD, cx, cy, 22f, Theme.textFaint());
		if (Ui.inside(mx, my, previewX, previewY, previewW, previewH) && !dragging) {
			Render2D.icon(ctx, Icons.ROTATE_CCW, previewX + previewW - 10f, previewY + 8f, 7f, ColorUtil.withAlpha(Theme.textDim(), 160));
		}

		// Play inspect, with the in-game key beside it.
		String label = inspecting ? "Inspecting" : "Play inspect";
		float labelW = playWidth.width(Fonts.semibold(), label, Ui.SMALL);
		playW = labelW + 30f;
		playX = x + PAD;
		float hv = playHover.target(Ui.inside(mx, my, playX, playY, playW, BUTTON_H) ? 1f : 0f).get();
		Render2D.matte(ctx, playX, playY, playW, BUTTON_H, Ui.CELL_RADIUS + 2f, ColorUtil.withAlpha(0xFFFFFF, Math.round(12 + 10 * hv)), Ui.LIT);
		Render2D.rim(ctx, playX, playY, playW, BUTTON_H, Ui.CELL_RADIUS + 2f,
			inspecting ? ColorUtil.withAlpha(Theme.accent(), 170) : ColorUtil.withAlpha(0xFFFFFF, Math.round(22 + 16 * hv)), Ui.LIT);
		Render2D.icon(ctx, Icons.PLAY, playX + 11f, playY + BUTTON_H / 2f, 6.5f, inspecting ? Theme.accent() : Theme.text());
		Render2D.textMiddle(ctx, Fonts.semibold(), label, playX + 20f, playY + BUTTON_H / 2f, Ui.SMALL, Theme.text());
		if (module.inspectKey.isSet()) {
			Ui.keyChip(ctx, keyWidth, KeyNames.name(module.inspectKey.key()), playX + playW + 6f, playY + BUTTON_H / 2f, Theme.textDim());
		}

		// Finish swatches: the finish's own two colours; the chosen one ringed with the accent.
		for (int i = 0; i < FINISHES.length; i++) {
			KnifeFinish f = FINISHES[i];
			float sx = swatchX + (i % swatchCols) * (SWATCH + SWATCH_GAP);
			float sy = swatchY + (i / swatchCols) * (SWATCH + SWATCH_GAP);
			boolean chosen = f == finish;
			float sh = swatchHover[i].target(Ui.inside(mx, my, sx, sy, SWATCH, SWATCH) ? 1f : 0f).get();
			float grow = sh * 1f;
			Render2D.gradientV(ctx, sx - grow, sy - grow, SWATCH + grow * 2f, SWATCH + grow * 2f, SWATCH / 2f + grow, f.light(), f.dark());
			if (chosen) Render2D.ring(ctx, sx + SWATCH / 2f, sy + SWATCH / 2f, SWATCH / 2f + 2.5f, 1.2f, Theme.accent());
			else Render2D.ring(ctx, sx + SWATCH / 2f, sy + SWATCH / 2f, SWATCH / 2f + grow, 1f, ColorUtil.withAlpha(0xFFFFFF, Math.round(30 + 50 * sh)));
			if (sh > 0.5f && !chosen) {
				// The hovered finish's name where the selected one's is.
				Render2D.textMiddle(ctx, Fonts.medium(), f.title(), x + PAD + 27f, y + PAD + 24f, Ui.TINY, ColorUtil.withAlpha(Theme.textDim(), Math.round(255 * sh)));
			}
		}
	}

	/** The library: a card per knife, scrolling when they do not fit. */
	private void renderGrid(DrawContext ctx, KnivesModule module, double mx, double my, float pixels) {
		cols = gridW >= 420f ? 4 : gridW >= 300f ? 3 : 2;
		cardW = (gridW - CARD_GAP * (cols - 1)) / cols;
		int rows = (TYPES.length + cols - 1) / cols;
		contentHeight = rows * (CARD_H + CARD_GAP) - CARD_GAP;
		scroll = scroller.update(contentHeight, gridH);
		boolean inView = Ui.inside(mx, my, gridX, gridY, gridW, gridH);
		double cmx = inView ? mx : -1000;
		double cmy = inView ? my : -1000;
		KnifeType selected = module.type();
		KnifeFinish finish = module.finish();
		ctx.enableScissor((int) Math.floor(gridX - 4f), (int) Math.floor(gridY), (int) Math.ceil(gridX + gridW + 4f), (int) Math.ceil(gridY + gridH));
		float alpha = Render2D.alpha();
		for (int i = 0; i < TYPES.length; i++) {
			float cx = gridX + (i % cols) * (cardW + CARD_GAP);
			float cy = gridY + (i / cols) * (CARD_H + CARD_GAP) - scroll;
			boolean hovered = Ui.inside(cmx, cmy, cx, cy, cardW, CARD_H);
			float hv = cardHover[i].target(hovered ? 1f : 0f).get();
			float sel = cardSelect[i].target(TYPES[i] == selected ? 1f : 0f).get();
			if (cy + CARD_H < gridY || cy > gridY + gridH) continue;
			Render2D.setAlpha(alpha * Ui.edgeFade(cy, cy + CARD_H, gridY, gridY + gridH));
			float top = cy - hv * 1.5f;
			if (sel > 0.01f) Render2D.shadow(ctx, cx, top, cardW, CARD_H, Ui.CARD_RADIUS, 10f, ColorUtil.withAlpha(Theme.accent(), Math.round(40 * sel)));
			Render2D.matte(ctx, cx, top, cardW, CARD_H, Ui.CARD_RADIUS, ColorUtil.lerp(Theme.raised(), Theme.raisedLit(), Math.max(hv, sel * 0.6f)), Ui.LIT);
			Render2D.rim(ctx, cx, top, cardW, CARD_H, Ui.CARD_RADIUS, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(18 + 22 * hv)),
				ColorUtil.withAlpha(Theme.accent(), 210), sel), Ui.LIT);
			float iw = cardW - 8f;
			float ih = CARD_H - 22f;
			TextureSetup image = cardImage(i, finish, iw, ih, hv, pixels);
			if (image != null) Render2D.image(ctx, image, cx + 4f, top + 4f, iw, ih, 0f, 0xFFFFFFFF, 0f, 1f, 1f, 0f, 0);
			else Render2D.icon(ctx, Icons.SWORD, cx + cardW / 2f, top + 4f + ih / 2f, 12f, Theme.textFaint());
			Render2D.textMiddle(ctx, Fonts.semibold(), TYPES[i].title(), cx + 8f, top + CARD_H - 10f, Ui.SMALL,
				ColorUtil.lerp(Theme.textDim(), Theme.text(), Math.max(hv, sel)));
			if (sel > 0.01f) Render2D.icon(ctx, Icons.CHECK, cx + cardW - 10f, top + CARD_H - 10f, 6.5f, ColorUtil.fade(Theme.accent(), sel));
		}
		Render2D.setAlpha(alpha);
		ctx.disableScissor();
		scroller.drawBar(ctx, gridX + gridW + 3f, gridY, gridH, contentHeight, inView);
	}

	/** A card's image: rendered again only when the finish or size changed, or while the card is hovered (it turns). */
	private TextureSetup cardImage(int i, KnifeFinish finish, float w, float h, float hover, float pixels) {
		int pw = Math.round(w * pixels);
		int ph = Math.round(h * pixels);
		float turn = hover > 0.01f ? 20f * (float) Math.sin(clock * 1.3f) * hover : 0f;
		boolean stale = CARD_IMAGES[i] == null || CARD_FINISH[i] != finish.ordinal() || CARD_PW[i] != pw || CARD_PH[i] != ph
			|| Math.abs(CARD_TURN[i] - turn) > 0.05f;
		if (stale) {
			TextureSetup image = CARDS[i].render(TYPES[i], finish, pw, ph, 0.86f, YAW + turn, PITCH, null, null);
			CARD_IMAGES[i] = image;
			CARD_FINISH[i] = finish.ordinal();
			CARD_PW[i] = pw;
			CARD_PH[i] = ph;
			CARD_TURN[i] = turn;
		}
		return CARD_IMAGES[i];
	}

	/** Plays the selected knife's inspect in the large preview, from its rest pose. */
	private void playInspect(KnifeType type) {
		INSPECT.hold(type);
		INSPECT.update(0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f);
		REST.set(INSPECT.knife).invert();
		INSPECT.inspect();
		inspected = type;
		inspecting = true;
		userYaw = 0f;
		userPitch = 0f;
	}

	private static float pixelScale() {
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		float scale = gui != null ? gui.scale.f() : 1f;
		return scale * (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
	}

	// ------------------------------------------------------------------ input

	boolean mouseClicked(double mx, double my, int button) {
		KnivesModule module = ModuleManager.get(KnivesModule.class);
		if (module == null) return false;
		if (Ui.inside(mx, my, powerX, powerY, powerW, 20f)) {
			module.toggle();
			return true;
		}
		if (Ui.inside(mx, my, gearX, gearY, GEAR, GEAR)) {
			openSettings.accept(module);
			return true;
		}
		if (!Ui.inside(mx, my, viewX, viewY, viewW, viewH)) return false;
		if (Ui.inside(mx, my, playX, playY, playW, BUTTON_H)) {
			playInspect(module.type());
			return true;
		}
		for (int i = 0; i < FINISHES.length; i++) {
			float sx = swatchX + (i % swatchCols) * (SWATCH + SWATCH_GAP);
			float sy = swatchY + (i / swatchCols) * (SWATCH + SWATCH_GAP);
			if (Ui.inside(mx, my, sx - 1f, sy - 1f, SWATCH + 2f, SWATCH + 2f)) {
				module.finish.set(FINISHES[i].title());
				return true;
			}
		}
		if (Ui.inside(mx, my, previewX, previewY, previewW, previewH)) {
			dragging = true;
			dragX = mx;
			dragY = my;
			return true;
		}
		if (Ui.inside(mx, my, gridX, gridY, gridW, gridH)) {
			for (int i = 0; i < TYPES.length; i++) {
				float cx = gridX + (i % cols) * (cardW + CARD_GAP);
				float cy = gridY + (i / cols) * (CARD_H + CARD_GAP) - scroll;
				if (!Ui.inside(mx, my, cx, cy, cardW, CARD_H)) continue;
				module.knife.set(TYPES[i].title());
				if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) openSettings.accept(module);
				return true;
			}
		}
		return false;
	}

	void mouseDragged(double mx, double my) {
		if (!dragging) return;
		userYaw += (float) (mx - dragX) * 1.4f;
		userPitch = Math.max(-75f, Math.min(75f, userPitch + (float) (my - dragY) * 1.1f));
		dragX = mx;
		dragY = my;
	}

	void mouseReleased() {
		dragging = false;
	}

	void scroll(double amount) {
		scroller.scroll(amount);
	}
}
