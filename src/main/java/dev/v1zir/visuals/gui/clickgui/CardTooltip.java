package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.KeyNames;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A small card that fades in by a module card once the mouse rests on it: the module's name, its whole
 * description, its key bind and what the other mouse buttons do. It glides after the cursor on springs,
 * flips to stay inside the window, and moving on to the next card while it shows swaps its contents at
 * once. Texts are laid out once per module and key.
 */
final class CardTooltip {
	private static final float DELAY = 0.45f;
	private static final float MAX_W = 170f;
	private static final float PAD = 8f;
	private static final float TITLE = 7.5f;
	private static final float LINE = 8.5f;

	private final Anim shown = new Anim(0f, 14f);
	private final Anim px = new Anim(0f, 20f);
	private final Anim py = new Anim(0f, 20f);
	private final Label keyWidth = new Label();
	@Nullable
	private ModuleCard hovering;
	private long hoverSince;
	/** What it describes; kept while it fades out. */
	@Nullable
	private Module module;
	private int laidKey = Integer.MIN_VALUE;
	private String[] lines = new String[0];
	private String keyText = "";
	private String hint = "";
	private float width;
	private float height;
	private boolean placed;

	/** Draws it for the card under the mouse (or fades it out without one), inside the rectangle left, top, right, bottom. */
	void render(DrawContext ctx, @Nullable ModuleCard card, double mx, double my, float left, float top, float right, float bottom) {
		long now = System.nanoTime();
		if (card != hovering) {
			hovering = card;
			// Already showing: the next card's tooltip follows straight away.
			if (card == null || shown.target() < 0.5f) hoverSince = now;
		}
		boolean show = card != null && (now - hoverSince) / 1.0e9f >= DELAY;
		if (show && card.module != module) {
			module = card.module;
			laidKey = Integer.MIN_VALUE;
		}
		float s = shown.target(show ? 1f : 0f).get();
		if (s < 0.01f || module == null) {
			placed = false;
			return;
		}
		if (module.key() != laidKey) layout(card != null ? card.hasSettings() : !module.settings().isEmpty());

		// Below and right of the cursor, flipped where it would leave the window.
		float tx = (float) mx + 12f;
		float ty = (float) my + 14f;
		if (tx + width > right - 4f) tx = (float) mx - 12f - width;
		if (ty + height > bottom - 4f) ty = (float) my - 8f - height;
		tx = Math.max(left + 4f, Math.min(right - 4f - width, tx));
		ty = Math.max(top + 4f, Math.min(bottom - 4f - height, ty));
		if (!placed) {
			px.snap(tx);
			py.snap(ty);
			placed = true;
		}
		float x = px.target(tx).get();
		float y = py.target(ty).get() + (1f - s) * 4f;

		float alpha = Render2D.alpha();
		Render2D.setAlpha(alpha * s);
		float r = Ui.CELL_RADIUS + 2f;
		Render2D.shadow(ctx, x, y + 2f, width, height, r, 10f, 0x66000000);
		Render2D.matte(ctx, x, y, width, height, r, ColorUtil.withAlpha(ColorUtil.brighten(Theme.preset().base(), 0.09f), 248), 0);
		Render2D.rim(ctx, x, y, width, height, r, ColorUtil.withAlpha(0xFFFFFF, 26), 0);
		float cy = y + PAD + 3.5f;
		Render2D.textMiddle(ctx, Fonts.semibold(), module.name(), x + PAD, cy, TITLE, Theme.text());
		SdfFont regular = Fonts.regular();
		for (int i = 0; i < lines.length; i++) {
			Render2D.textMiddle(ctx, regular, lines[i], x + PAD, cy + 10f + i * LINE, Ui.SMALL, Theme.textDim());
		}
		float fy = cy + 10f + lines.length * LINE + 4f;
		Render2D.rect(ctx, x + PAD, fy - 4.5f, width - PAD * 2f, 1f, Ui.HAIRLINE);
		float hx = x + PAD;
		if (!keyText.isEmpty()) hx += Ui.keyChip(ctx, keyWidth, keyText, hx, fy + 2f, Theme.text()) + 5f;
		if (!hint.isEmpty()) Render2D.textMiddle(ctx, regular, hint, hx, fy + 2f, Ui.TINY, Theme.textFaint());
		Render2D.setAlpha(alpha);
	}

	/** Wraps the description and builds the footer for the module's current key. */
	private void layout(boolean settings) {
		laidKey = module.key();
		boolean bound = laidKey != GLFW.GLFW_KEY_UNKNOWN;
		keyText = bound ? KeyNames.name(laidKey) : "";
		hint = bound ? settings ? "Right click: settings" : "" : settings ? "Middle click: bind  ·  Right click: settings" : "Middle click: bind";
		SdfFont regular = Fonts.regular();
		List<String> wrapped = new ArrayList<>(3);
		String line = "";
		for (String word : module.description().split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && regular.width(next, Ui.SMALL) > MAX_W - PAD * 2f) {
				wrapped.add(line);
				line = word;
			} else {
				line = next;
			}
		}
		if (!line.isEmpty()) wrapped.add(line);
		lines = wrapped.toArray(new String[0]);
		float w = Fonts.semibold().width(module.name(), TITLE);
		for (String l : lines) w = Math.max(w, regular.width(l, Ui.SMALL));
		float footer = (bound ? Fonts.semibold().width(keyText, 5.5f) + 13f : 0f) + (hint.isEmpty() ? 0f : regular.width(hint, Ui.TINY));
		width = Math.max(w, footer) + PAD * 2f;
		height = PAD * 2f + 22f + lines.length * LINE;
	}
}
