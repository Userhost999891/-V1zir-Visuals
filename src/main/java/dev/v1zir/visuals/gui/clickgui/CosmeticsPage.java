package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.cosmetics.CosmeticsModule;
import dev.v1zir.visuals.render.cosmetic.Cosmetic;
import dev.v1zir.visuals.render.cosmetic.CosmeticMotion;
import dev.v1zir.visuals.render.cosmetic.CosmeticPreview;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Cosmetics tab: your own player on the left wearing the current selection (drag to turn it round, it sways
 * gently on its own), and on the right the slot chips (hats, wings, capes) with a card per item, each a small 3D
 * preview of it moving as it would when worn; clicking a card puts the item on or takes it off. The module's switch
 * and settings sit in the header.
 */
final class CosmeticsPage {
	private static final Cosmetic.Slot[] SLOTS = Cosmetic.Slot.values();
	private static final String SUBTITLE = Cosmetic.values().length + " items, all client side";
	private static final float DEG = (float) (Math.PI / 180.0);
	private static final float GAP = 10f;
	private static final float PAD = 12f;
	private static final float CHIP_H = 18f;
	private static final float CARD_H = 74f;
	private static final float CARD_GAP = 8f;
	private static final float GEAR = 18f;
	private static final float SUPERSAMPLE = 2f;

	// Shared by every screen instance, so GPU targets are made once.
	private static final CosmeticPreview[] CARDS = new CosmeticPreview[Cosmetic.values().length];

	static {
		for (Cosmetic c : Cosmetic.values()) CARDS[c.ordinal()] = new CosmeticPreview("V1zir cosmetic card " + c.title());
	}

	private static final Quaternionf ROTATION = new Quaternionf();
	private static final Quaternionf CAMERA = new Quaternionf();
	private static final Vector3f TRANSLATION = new Vector3f();
	private static final Vector2f CORNER_A = new Vector2f();
	private static final Vector2f CORNER_B = new Vector2f();

	private final Consumer<Module> openSettings;
	private final Scroller scroller = new Scroller();
	private final Anim[] chipHover = new Anim[SLOTS.length];
	private final Anim[] chipActive = new Anim[SLOTS.length];
	private final Anim[] cardHover = new Anim[Cosmetic.values().length];
	private final Anim[] cardOn = new Anim[Cosmetic.values().length];
	private final Anim power = new Anim(0f, 18f);
	private final Anim powerHover = new Anim(0f, 22f);
	private final Anim gearHover = new Anim(0f, 22f);
	private final Anim idle = new Anim(1f, 4f);
	private final Label stateWidth = new Label();
	private final Label[] chipWidth = new Label[SLOTS.length];
	private Cosmetic.Slot slot = Cosmetic.Slot.HAT;
	// Layout of the last frame.
	private float viewX;
	private float viewY;
	private float viewW;
	private float viewH;
	private float previewX;
	private float previewY;
	private float previewW;
	private float previewH;
	private float gridX;
	private float gridY;
	private float gridW;
	private float gridH;
	private float cardW;
	private int cols = 2;
	private float scroll;
	private final float[] chipX = new float[SLOTS.length];
	private final float[] chipW = new float[SLOTS.length];
	private float chipY;
	private float powerX;
	private float powerY;
	private float powerW;
	private float gearX;
	private float gearY;
	private boolean dragging;
	private double dragX;
	private double dragY;
	private float userYaw = 150f;
	private float userPitch = 6f;
	private float clock;
	private long lastFrame;

	CosmeticsPage(Consumer<Module> openSettings) {
		this.openSettings = openSettings;
		for (int i = 0; i < SLOTS.length; i++) {
			chipHover[i] = new Anim(0f, 22f);
			chipActive[i] = new Anim(i == 0 ? 1f : 0f, 16f);
			chipWidth[i] = new Label();
		}
		for (int i = 0; i < cardHover.length; i++) {
			cardHover[i] = new Anim(0f, 20f);
			cardOn[i] = new Anim(0f, 14f);
		}
		CosmeticsModule module = ModuleManager.get(CosmeticsModule.class);
		if (module != null) power.snap(module.isEnabled() ? 1f : 0f);
	}

	void reset() {
		scroller.reset();
	}

	// ------------------------------------------------------------------ header

	void renderHeader(DrawContext ctx, float subtitleX, float right, float mid, double mx, double my) {
		Render2D.textMiddle(ctx, Fonts.medium(), SUBTITLE, subtitleX, mid + 1f, Ui.TINY, Theme.textFaint());
		CosmeticsModule module = ModuleManager.get(CosmeticsModule.class);
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
		CosmeticsModule module = ModuleManager.get(CosmeticsModule.class);
		if (module == null) return;
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		clock += dt;
		double hx = mouseFree ? mx : -1000;
		double hy = mouseFree ? my : -1000;
		float panelW = Math.max(150f, Math.min(230f, w * 0.36f));
		renderPlayer(ctx, module, x, y, panelW, h, hx, hy);
		float rx = x + panelW + GAP;
		float rw = w - panelW - GAP;
		renderChips(ctx, module, rx, y, hx, hy);
		gridX = rx;
		gridY = y + CHIP_H + 10f;
		gridW = rw;
		gridH = h - CHIP_H - 10f;
		renderGrid(ctx, module, hx, hy, pixelScale() * SUPERSAMPLE);
	}

	/** Your player wearing the selection, on a soft pedestal of light; what is worn listed under it. */
	private void renderPlayer(DrawContext ctx, CosmeticsModule module, float x, float y, float w, float h, double mx, double my) {
		Render2D.matte(ctx, x, y, w, h, Ui.PANEL_RADIUS, Theme.raised(), Ui.LIT);
		Render2D.rim(ctx, x, y, w, h, Ui.PANEL_RADIUS, Ui.HAIRLINE, Ui.LIT);
		previewX = x + 6f;
		previewY = y + 8f;
		previewW = w - 12f;
		previewH = h - 52f;
		float cx = previewX + previewW / 2f;
		Render2D.glow(ctx, cx - previewW * 0.3f, previewY + previewH * 0.82f, previewW * 0.6f, 8f, 4f, 14f,
			ColorUtil.withAlpha(Theme.accent(), 60), ColorUtil.withAlpha(Theme.accent2(), 40));
		float sway = idle.target(dragging ? 0f : 1f).get();
		drawPlayer(ctx, previewX, previewY, previewW, previewH, userYaw + 14f * (float) Math.sin(clock * 0.5f) * sway, userPitch);
		if (Ui.inside(mx, my, previewX, previewY, previewW, previewH) && !dragging) {
			Render2D.icon(ctx, Icons.ROTATE_CCW, previewX + previewW - 10f, previewY + 8f, 7f, ColorUtil.withAlpha(Theme.textDim(), 160));
		}
		// What is worn, slot by slot.
		float ly = y + h - 38f;
		for (int i = 0; i < SLOTS.length; i++) {
			Cosmetic worn = module.item(SLOTS[i]);
			float rowY = ly + i * 11f;
			Render2D.textMiddle(ctx, Fonts.medium(), SLOTS[i].title, x + PAD, rowY, Ui.TINY, Theme.textFaint());
			Render2D.textMiddle(ctx, Fonts.semibold(), worn == null ? "None" : worn.title(), x + PAD + 42f, rowY, Ui.TINY,
				worn == null ? Theme.textDim() : Theme.text());
		}
	}

	/** The player model through the GUI's entity renderer, which draws the player's features, so the cosmetics too. */
	private static void drawPlayer(DrawContext ctx, float x, float y, float w, float h, float yaw, float pitch) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			Render2D.icon(ctx, Icons.SHIRT, x + w / 2f, y + h / 2f, 22f, Theme.textFaint());
			return;
		}
		EntityRenderState state = InventoryScreen.drawEntity(mc.player);
		if (state instanceof LivingEntityRenderState living) {
			living.bodyYaw = 180f + yaw;
			living.relativeHeadYaw = 0f;
			living.pitch = 0f;
			living.width = living.width / living.baseScale;
			living.height = living.height / living.baseScale;
			living.baseScale = 1f;
		}
		// Entity previews are placed in screen space: take the rectangle through the GUI's own transform.
		Matrix3x2f m = ctx.getMatrices();
		m.transformPosition(x, y, CORNER_A);
		m.transformPosition(x + w, y + h, CORNER_B);
		float scale = (float) Math.sqrt(m.m00 * m.m00 + m.m01 * m.m01);
		float size = h * 0.4f * scale;
		ROTATION.rotationZ((float) Math.PI);
		CAMERA.rotationX(pitch * DEG);
		ROTATION.mul(CAMERA);
		TRANSLATION.set(0f, state.height / 2f + 0.05f, 0f);
		ctx.addEntity(state, size, TRANSLATION, ROTATION, CAMERA, (int) CORNER_A.x, (int) CORNER_A.y, (int) Math.ceil(CORNER_B.x),
			(int) Math.ceil(CORNER_B.y));
	}

	/** Chips for the slots, with how many items each has. */
	private void renderChips(DrawContext ctx, CosmeticsModule module, float x, float y, double mx, double my) {
		chipY = y;
		float cx = x;
		for (int i = 0; i < SLOTS.length; i++) {
			Cosmetic.Slot s = SLOTS[i];
			String label = s.title;
			float tw = chipWidth[i].width(Fonts.semibold(), label, Ui.SMALL);
			boolean worn = module.item(s) != null;
			chipW[i] = tw + (worn ? 30f : 22f);
			chipX[i] = cx;
			float hv = chipHover[i].target(Ui.inside(mx, my, cx, y, chipW[i], CHIP_H) ? 1f : 0f).get();
			float on = chipActive[i].target(s == slot ? 1f : 0f).get();
			Render2D.matte(ctx, cx, y, chipW[i], CHIP_H, CHIP_H / 2f, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(10 + 10 * hv)),
				ColorUtil.withAlpha(Theme.accent(), 60), on), Ui.LIT);
			Render2D.rim(ctx, cx, y, chipW[i], CHIP_H, CHIP_H / 2f, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(20 + 16 * hv)),
				ColorUtil.withAlpha(Theme.accent(), 180), on), Ui.LIT);
			Render2D.textMiddle(ctx, Fonts.semibold(), label, cx + 11f, y + CHIP_H / 2f, Ui.SMALL, ColorUtil.lerp(Theme.textDim(), Theme.text(), Math.max(on, hv)));
			// A dot when something of this slot is worn.
			if (worn) Render2D.circle(ctx, cx + chipW[i] - 11f, y + CHIP_H / 2f, 2.2f, Theme.accent());
			cx += chipW[i] + 6f;
		}
	}

	/** The selected slot's items: a card each, with a live preview, its name, and the accent when worn. */
	private void renderGrid(DrawContext ctx, CosmeticsModule module, double mx, double my, float pixels) {
		List<Cosmetic> items = Cosmetic.of(slot);
		cols = gridW >= 360f ? 4 : gridW >= 250f ? 3 : 2;
		cardW = (gridW - CARD_GAP * (cols - 1)) / cols;
		int rows = (items.size() + cols - 1) / cols;
		float content = rows * (CARD_H + CARD_GAP) - CARD_GAP;
		scroll = scroller.update(content, gridH);
		boolean inView = Ui.inside(mx, my, gridX, gridY, gridW, gridH);
		double cmx = inView ? mx : -1000;
		double cmy = inView ? my : -1000;
		CosmeticMotion motion = CosmeticMotion.preview();
		ctx.enableScissor((int) Math.floor(gridX - 4f), (int) Math.floor(gridY), (int) Math.ceil(gridX + gridW + 4f), (int) Math.ceil(gridY + gridH));
		float alpha = Render2D.alpha();
		for (int i = 0; i < items.size(); i++) {
			Cosmetic item = items.get(i);
			int id = item.ordinal();
			float cx = gridX + (i % cols) * (cardW + CARD_GAP);
			float cy = gridY + (i / cols) * (CARD_H + CARD_GAP) - scroll;
			boolean hovered = Ui.inside(cmx, cmy, cx, cy, cardW, CARD_H);
			float hv = cardHover[id].target(hovered ? 1f : 0f).get();
			float on = cardOn[id].target(module.item(slot) == item ? 1f : 0f).get();
			if (cy + CARD_H < gridY || cy > gridY + gridH) continue;
			Render2D.setAlpha(alpha * Ui.edgeFade(cy, cy + CARD_H, gridY, gridY + gridH));
			float top = cy - hv * 1.5f;
			if (on > 0.01f) Render2D.shadow(ctx, cx, top, cardW, CARD_H, Ui.CARD_RADIUS, 10f, ColorUtil.withAlpha(Theme.accent(), Math.round(40 * on)));
			Render2D.matte(ctx, cx, top, cardW, CARD_H, Ui.CARD_RADIUS, ColorUtil.lerp(Theme.raised(), Theme.raisedLit(), Math.max(hv, on * 0.6f)), Ui.LIT);
			Render2D.rim(ctx, cx, top, cardW, CARD_H, Ui.CARD_RADIUS, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFF, Math.round(18 + 22 * hv)),
				ColorUtil.withAlpha(Theme.accent(), 210), on), Ui.LIT);
			float iw = cardW - 8f;
			float ih = CARD_H - 22f;
			float turn = (float) Math.sin(clock * 0.8f + i) * (10f + 14f * hv);
			float yaw = switch (slot) {
				case HAT -> 205f;
				case WINGS -> 12f;
				case CAPE -> 18f;
			} + turn;
			TextureSetup image = CARDS[id].render(item, Math.round(iw * pixels), Math.round(ih * pixels), 0.86f, yaw, 14f, motion);
			if (image != null) Render2D.image(ctx, image, cx + 4f, top + 4f, iw, ih, 0f, 0xFFFFFFFF, 0f, 1f, 1f, 0f, 0);
			else Render2D.icon(ctx, Icons.SHIRT, cx + cardW / 2f, top + 4f + ih / 2f, 12f, Theme.textFaint());
			Render2D.textMiddle(ctx, Fonts.semibold(), item.title(), cx + 8f, top + CARD_H - 10f, Ui.SMALL,
				ColorUtil.lerp(Theme.textDim(), Theme.text(), Math.max(hv, on)));
			if (on > 0.01f) Render2D.icon(ctx, Icons.CHECK, cx + cardW - 10f, top + CARD_H - 10f, 6.5f, ColorUtil.fade(Theme.accent(), on));
		}
		Render2D.setAlpha(alpha);
		ctx.disableScissor();
		scroller.drawBar(ctx, gridX + gridW + 3f, gridY, gridH, content, inView);
	}

	private static float pixelScale() {
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		float scale = gui != null ? gui.scale.f() : 1f;
		return scale * (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
	}

	// ------------------------------------------------------------------ input

	boolean mouseClicked(double mx, double my, int button) {
		CosmeticsModule module = ModuleManager.get(CosmeticsModule.class);
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
		for (int i = 0; i < SLOTS.length; i++) {
			if (Ui.inside(mx, my, chipX[i], chipY, chipW[i], CHIP_H)) {
				if (slot != SLOTS[i]) {
					slot = SLOTS[i];
					scroller.reset();
				}
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
			List<Cosmetic> items = Cosmetic.of(slot);
			for (int i = 0; i < items.size(); i++) {
				float cx = gridX + (i % cols) * (cardW + CARD_GAP);
				float cy = gridY + (i / cols) * (CARD_H + CARD_GAP) - scroll;
				if (!Ui.inside(mx, my, cx, cy, cardW, CARD_H)) continue;
				if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) openSettings.accept(module);
				else {
					module.toggle(items.get(i));
					// Putting something on with the module off turns it on, so the choice shows.
					if (!module.isEnabled() && module.item(slot) != null) module.toggle();
				}
				return true;
			}
		}
		return false;
	}

	void mouseDragged(double mx, double my) {
		if (!dragging) return;
		userYaw += (float) (mx - dragX) * 1.6f;
		userPitch = Math.max(-30f, Math.min(30f, userPitch + (float) (my - dragY) * 0.6f));
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
