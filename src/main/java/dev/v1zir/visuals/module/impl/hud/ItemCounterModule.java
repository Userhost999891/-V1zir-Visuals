package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * How many totems, pearls, golden apples, crystals, arrows and experience bottles you carry. Only
 * the ones you have show up; they grow in and out, and a count that changes gives a little pop.
 */
public final class ItemCounterModule extends HudModule {
	private static final float ROW = 18f;
	private static final float LIST_W = 96f;

	public final ModeSetting layout = add(new ModeSetting("Layout", "Horizontal", "Horizontal", "Vertical"));
	private final Entry[] entries = {
		new Entry(add(new BoolSetting("Totems", true)), Items.TOTEM_OF_UNDYING),
		new Entry(add(new BoolSetting("Ender pearls", true)), Items.ENDER_PEARL),
		new Entry(add(new BoolSetting("Golden apples", true)), Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE),
		new Entry(add(new BoolSetting("End crystals", true)), Items.END_CRYSTAL),
		new Entry(add(new BoolSetting("Arrows", true)), Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW),
		new Entry(add(new BoolSetting("XP bottles", true)), Items.EXPERIENCE_BOTTLE)
	};

	public ItemCounterModule() {
		super("Item Counter", "Counts totems, pearls, gapples and more", 0.5f, 0.72f);
		setSubCategory("Widgets");
		width = 120f;
		height = 22f;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	/** The row grows to both sides as counters come and go, so it stays centred over the hotbar. */
	@Override
	protected boolean centered() {
		return true;
	}

	@Override
	public boolean hasContent() {
		for (Entry e : entries) if (e.count > 0 && e.setting.on() || e.shown.get() > 0.01f) return true;
		return false;
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		for (Entry e : entries) e.counting = 0;
		PlayerInventory inventory = mc.player.getInventory();
		for (int i = 0; i < 36; i++) count(inventory.getStack(i));
		count(mc.player.getOffHandStack());
		for (Entry e : entries) {
			if (e.counting != e.count && e.count > 0 && e.counting > 0) {
				e.pop.snap(1f);
				e.pop.target(0f);
			}
			e.count = e.counting;
		}
	}

	private void count(ItemStack stack) {
		if (stack.isEmpty()) return;
		Item item = stack.getItem();
		for (Entry e : entries) {
			if (e.matches(item)) {
				e.counting += stack.getCount();
				return;
			}
		}
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		boolean preview = editing();
		SdfFont bold = Fonts.semibold();
		for (Entry e : entries) {
			e.shown.target(e.setting.on() && (e.count > 0 || preview) ? 1f : 0f);
			e.text.set(e.count);
			if (e.icon == null) e.icon = new ItemStack(e.items[0]);
		}
		if (layout.is("Vertical")) vertical(ctx, bold, x, y);
		else horizontal(ctx, bold, x, y);
	}

	private void horizontal(DrawContext ctx, SdfFont bold, float x, float y) {
		float h = 22f;
		float w = 3f;
		float visible = 0f;
		for (Entry e : entries) {
			float s = e.shown.get();
			w += slot(e, bold) * s;
			visible = Math.max(visible, s);
		}
		width = Math.max(h, w);
		height = h;
		// The pill fades with its last counter instead of lingering empty for a moment.
		Render2D.setAlpha(visible);
		panel(ctx, x, y, width, h, 7f);
		Render2D.setAlpha(1f);
		float cx = x + 1.5f;
		for (Entry e : entries) {
			float s = e.shown.get();
			if (s <= 0.01f) continue;
			icon(ctx, e, cx + 3.5f, y + 3f, s);
			count(ctx, bold, e, cx + 3.5f + 16f + 2.5f, y + h / 2f, s);
			cx += slot(e, bold) * s;
		}
	}

	private void vertical(DrawContext ctx, SdfFont bold, float x, float y) {
		float h = 6f;
		float visible = 0f;
		for (Entry e : entries) {
			float s = e.shown.get();
			h += ROW * s;
			visible = Math.max(visible, s);
		}
		width = LIST_W;
		height = h;
		Render2D.setAlpha(visible);
		panel(ctx, x, y, LIST_W, h, 7f);
		Render2D.setAlpha(1f);
		SdfFont font = Fonts.medium();
		float cy = y + 3f;
		for (Entry e : entries) {
			float s = e.shown.get();
			if (s <= 0.01f) continue;
			float mid = cy + ROW * s / 2f;
			icon(ctx, e, x + 5f, mid - 8f, s);
			Render2D.setAlpha(s);
			Render2D.textMiddle(ctx, font, e.setting.name(), x + 25f, mid, 7f, Theme.textDim());
			Render2D.setAlpha(1f);
			count(ctx, bold, e, x + LIST_W - 7f - e.text.width(bold, 8f), mid, s);
			cy += ROW * s;
		}
	}

	/** Width an entry takes in the horizontal row: padding, icon, gap, count, padding. */
	private static float slot(Entry e, SdfFont bold) {
		return 3.5f + 16f + 2.5f + e.text.width(bold, 8f) + 3.5f;
	}

	/** The item icon; s grows it in and out, a changed count makes it pop with a glow. */
	private static void icon(DrawContext ctx, Entry e, float x, float y, float s) {
		float pop = e.pop.get();
		if (pop > 0.01f) {
			Render2D.glow(ctx, x + 2f, y + 2f, 12f, 12f, 6f, 6f, ColorUtil.withAlpha(Theme.accent(), Math.round(120 * pop)),
				ColorUtil.withAlpha(Theme.accent2(), Math.round(120 * pop)));
		}
		item(ctx, e.icon, x, y, s * (1f + 0.12f * pop));
	}

	private static void count(DrawContext ctx, SdfFont bold, Entry e, float x, float centerY, float s) {
		float pop = e.pop.get();
		Render2D.setAlpha(s);
		int color = e.count == 0 ? Theme.textFaint() : ColorUtil.lerp(Theme.text(), Theme.accent(), pop);
		Render2D.textMiddle(ctx, bold, e.text.text(), x, centerY, 8f * (1f + 0.2f * pop), color);
		Render2D.setAlpha(1f);
	}

	private static final class Entry {
		final BoolSetting setting;
		final Item[] items;
		final Anim shown = new Anim(0f, 10f);
		final Anim pop = new Anim(0f, 9f);
		final NumberText text = new NumberText(0, "");
		ItemStack icon;
		int count;
		int counting;

		Entry(BoolSetting setting, Item... items) {
			this.setting = setting;
			this.items = items;
		}

		boolean matches(Item item) {
			for (Item i : items) if (i == item) return true;
			return false;
		}
	}
}
