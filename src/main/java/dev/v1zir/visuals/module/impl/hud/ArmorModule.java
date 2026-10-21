package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.gui.clickgui.Label;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/** Armour pieces and held items with durability bars and percentages, in a row or a column. */
public final class ArmorModule extends HudModule {
	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};
	/** Vanilla's empty slot silhouettes, shown faintly where nothing is worn. */
	private static final Identifier[] EMPTY = {
		Identifier.ofVanilla("container/slot/helmet"), Identifier.ofVanilla("container/slot/chestplate"),
		Identifier.ofVanilla("container/slot/leggings"), Identifier.ofVanilla("container/slot/boots"),
		Identifier.ofVanilla("container/slot/sword"), Identifier.ofVanilla("container/slot/shield")
	};
	private static final String[] PERCENT = new String[101];
	private static final float CELL = 22f;
	private static final float ROW = 20f;
	private static final float COLUMN_W = 72f;

	static {
		for (int i = 0; i <= 100; i++) PERCENT[i] = i + "%";
	}

	public final ModeSetting layout = add(new ModeSetting("Layout", "Horizontal", "Horizontal", "Vertical"));
	public final BoolSetting hands = add(new BoolSetting("Held items", true));
	public final BoolSetting percent = add(new BoolSetting("Percent", true));

	private final ItemStack[] stacks = new ItemStack[SLOTS.length];
	private final String[] texts = new String[SLOTS.length];
	private final int[] colors = new int[SLOTS.length];
	private final boolean[] bars = new boolean[SLOTS.length];
	private final Anim[] durability = new Anim[SLOTS.length];
	private final Label[] textWidths = new Label[SLOTS.length];

	public ArmorModule() {
		super("Armor", "Armour and held items with durability", 0.53f, 0.8f);
		setSubCategory("Widgets");
		for (int i = 0; i < SLOTS.length; i++) {
			durability[i] = new Anim(1f, 6f);
			textWidths[i] = new Label();
		}
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	public boolean hasContent() {
		if (mc.player == null) return false;
		int n = hands.on() ? SLOTS.length : 4;
		for (int i = 0; i < n; i++) if (!mc.player.getEquippedStack(SLOTS[i]).isEmpty()) return true;
		return false;
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		if (mc.player == null) return;
		int n = hands.on() ? SLOTS.length : 4;
		// Pieces close to breaking pulse.
		float pulse = 0.6f + 0.4f * (float) Math.sin(System.nanoTime() / 1.0e9 * 6.0);
		for (int i = 0; i < n; i++) {
			ItemStack stack = mc.player.getEquippedStack(SLOTS[i]);
			stacks[i] = stack;
			bars[i] = stack.isDamageable();
			if (bars[i]) {
				float left = 1f - (float) stack.getDamage() / Math.max(1, stack.getMaxDamage());
				durability[i].target(left);
				texts[i] = PERCENT[Math.round(Anim.clamp01(left) * 100f)];
				colors[i] = left < 0.15f ? ColorUtil.fade(quality(1f - left), pulse) : quality(1f - left);
			} else {
				texts[i] = !stack.isEmpty() && stack.getCount() > 1 ? NumberText.of(stack.getCount()) : null;
				colors[i] = Theme.text();
			}
		}
		if (layout.is("Vertical")) column(ctx, x, y, n);
		else row(ctx, x, y, n);
	}

	private void row(DrawContext ctx, float x, float y, int n) {
		float h = percent.on() ? 35f : 27f;
		width = 4f + n * CELL;
		height = h;
		panel(ctx, x, y, width, h, 7f);
		SdfFont font = Fonts.semibold();
		for (int i = 0; i < n; i++) {
			float cx = x + 2f + i * CELL;
			icon(ctx, i, cx + 3f, y + 4f);
			if (percent.on() && texts[i] != null) {
				float tw = textWidths[i].width(font, texts[i], 6f);
				Render2D.textMiddle(ctx, font, texts[i], cx + (CELL - tw) / 2f, y + 24.5f, 6f, colors[i]);
			}
			if (bars[i]) bar(ctx, i, cx + 4f, y + h - 5f, CELL - 8f);
		}
	}

	private void column(DrawContext ctx, float x, float y, int n) {
		width = COLUMN_W;
		height = 6f + n * ROW;
		panel(ctx, x, y, COLUMN_W, height, 7f);
		SdfFont font = Fonts.semibold();
		for (int i = 0; i < n; i++) {
			float ry = y + 3f + i * ROW;
			icon(ctx, i, x + 4f, ry + 2f);
			float tx = x + 25f;
			if (percent.on() && texts[i] != null) Render2D.textMiddle(ctx, font, texts[i], tx, ry + (bars[i] ? 7f : ROW / 2f), 7f, colors[i]);
			if (bars[i]) bar(ctx, i, tx, ry + 13f, x + COLUMN_W - 6f - tx);
		}
	}

	private void icon(DrawContext ctx, int i, float x, float y) {
		if (!stacks[i].isEmpty()) {
			item(ctx, stacks[i], x, y, 1f);
			return;
		}
		Matrix3x2fStack matrices = ctx.getMatrices();
		matrices.pushMatrix();
		matrices.translate(x, y);
		ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, EMPTY[i], 0, 0, 16, 16, 0.3f);
		matrices.popMatrix();
	}

	private void bar(DrawContext ctx, int i, float x, float y, float w) {
		float fill = Anim.clamp01(durability[i].get());
		Render2D.round(ctx, x, y, w, 2f, 1f, ColorUtil.withAlpha(0xFFFFFF, 25));
		if (fill > 0f) Render2D.round(ctx, x, y, w * fill, 2f, 1f, colors[i]);
	}
}
