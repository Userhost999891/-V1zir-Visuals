package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.gui.custom.CustomUi;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Arrays;

/**
 * The 27 main inventory slots in a compact grid. A slot whose stack changes flashes in the accent
 * colour. Every pass (wells, flashes, items, counts) is drawn in one go, so the GUI renderer can
 * batch each pass into a single layer.
 */
public final class InventoryModule extends HudModule {
	private static final int SLOTS = 27;
	private static final float CELL = 18f;
	private static final float GAP = 2f;
	private static final float PAD = 6f;
	private static final float W = PAD * 2f + CELL * 9f + GAP * 8f;
	/** Header strip, three rows of cells, bottom padding. */
	private static final float H = 18f + CELL * 3f + GAP * 2f + PAD - 1f;
	private static final Render2D.TextStyle COUNT = Render2D.TextStyle.of(0xFFFFFFFF).shadow();

	private final Item[] items = new Item[SLOTS];
	private final int[] counts = new int[SLOTS];
	private final Anim[] flash = new Anim[SLOTS];
	private final String[] countText = new String[SLOTS];
	private final float[] countWidth = new float[SLOTS];
	private SdfFont countFont;
	private boolean primed;

	public InventoryModule() {
		super("Inventory", "Your inventory at a glance", 0.995f, 0.66f);
		setSubCategory("Widgets");
		width = W;
		height = H;
		for (int i = 0; i < SLOTS; i++) flash[i] = new Anim(0f, 5f);
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void onEnable() {
		primed = false;
	}

	@Override
	public boolean hasContent() {
		for (Item item : items) if (item != null) return true;
		return false;
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		PlayerInventory inventory = mc.player.getInventory();
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stack = inventory.getStack(9 + i);
			Item item = stack.isEmpty() ? null : stack.getItem();
			int count = stack.isEmpty() ? 0 : stack.getCount();
			if (item == items[i] && count == counts[i]) continue;
			if (primed) {
				flash[i].snap(1f);
				flash[i].target(0f);
			}
			items[i] = item;
			counts[i] = count;
			countText[i] = count > 1 ? NumberText.of(count) : null;
			countWidth[i] = -1f;
		}
		primed = true;
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		if (mc.player == null) return;
		float top = headerPanel(ctx, x, y, W, H, Icons.BOX, "Inventory");
		float left = x + PAD;

		for (int i = 0; i < SLOTS; i++) CustomUi.well(ctx, cellX(left, i), cellY(top, i), CELL, CELL, 4f);
		int accent = Theme.accent();
		for (int i = 0; i < SLOTS; i++) {
			float f = flash[i].get();
			if (f > 0.01f) Render2D.round(ctx, cellX(left, i), cellY(top, i), CELL, CELL, 4f, ColorUtil.withAlpha(accent, Math.round(110 * f)));
		}
		PlayerInventory inventory = mc.player.getInventory();
		for (int i = 0; i < SLOTS; i++) {
			if (items[i] != null) item(ctx, inventory.getStack(9 + i), cellX(left, i) + 1f, cellY(top, i) + 1f, 1f);
		}
		// Durability bars and counts go above the icons.
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stack = inventory.getStack(9 + i);
			if (stack.isEmpty() || !stack.isItemBarVisible()) continue;
			float bx = cellX(left, i) + 2.5f;
			float by = cellY(top, i) + CELL - 3.5f;
			Render2D.round(ctx, bx, by, 13f, 1.5f, 0.75f, 0xB0000000);
			Render2D.round(ctx, bx, by, stack.getItemBarStep(), 1.5f, 0.75f, ColorUtil.rgb(stack.getItemBarColor()));
		}
		SdfFont bold = Fonts.semibold();
		if (bold != countFont) {
			countFont = bold;
			Arrays.fill(countWidth, -1f);
		}
		for (int i = 0; i < SLOTS; i++) {
			String text = countText[i];
			if (text == null) continue;
			if (countWidth[i] < 0f) countWidth[i] = bold.width(text, 6.5f);
			Render2D.textMiddle(ctx, bold, text, cellX(left, i) + CELL - 1.5f - countWidth[i], cellY(top, i) + CELL - 4.5f, 6.5f, COUNT);
		}
	}

	private static float cellX(float left, int slot) {
		return left + slot % 9 * (CELL + GAP);
	}

	private static float cellY(float top, int slot) {
		return top + slot / 9 * (CELL + GAP);
	}
}
