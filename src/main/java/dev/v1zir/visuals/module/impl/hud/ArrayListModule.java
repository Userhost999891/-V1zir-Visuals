package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Enabled modules, sorted by width, sliding in and out. */
public final class ArrayListModule extends HudModule {
	public final BoolSetting suffixes = add(new BoolSetting("Suffixes", true));
	public final BoolSetting background = add(new BoolSetting("Background", true));
	public final BoolSetting bar = add(new BoolSetting("Accent bar", true));

	private static final Comparator<Row> WIDEST_FIRST = (a, b) -> Float.compare(b.width, a.width);

	/** One row per listable module, built once; shown holds the visible ones in drawing order. */
	private Row[] rows;
	private final List<Row> shown = new ArrayList<>();

	public ArrayListModule() {
		super("Array List", "Lists enabled modules", 0.995f, 0.012f);
		setSubCategory("Info");
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		SdfFont font = Fonts.medium();
		float size = 7.5f;
		float rowH = 12.5f;
		if (rows == null) {
			List<Row> list = new ArrayList<>();
			for (Module module : ModuleManager.all()) if (module.showInList()) list.add(new Row(module));
			rows = list.toArray(new Row[0]);
		}
		boolean withSuffix = suffixes.on();
		shown.clear();
		for (Row row : rows) {
			row.anim.target(row.module.isEnabled() ? 1f : 0f);
			if (row.anim.get() > 0.01f) {
				row.measure(font, size, withSuffix);
				shown.add(row);
			}
		}
		shown.sort(WIDEST_FIRST);
		float maxW = 40f;
		float total = 0f;
		for (int i = 0; i < shown.size(); i++) {
			Row row = shown.get(i);
			maxW = Math.max(maxW, row.width + 12f);
			total += rowH * row.anim.value();
		}
		width = maxW;
		height = Math.max(rowH, total);

		boolean right = anchoredRight();
		float cy = y;
		for (int index = 0; index < shown.size(); index++) {
			Row row = shown.get(index);
			float t = row.anim.value();
			float w = row.width + 12f;
			float rx = right ? x + width - w : x;
			float slide = (1f - t) * (w + 10f) * (right ? 1f : -1f);
			Render2D.setAlpha(t);
			int accent = Theme.accentWave(index * 0.08f);
			if (background.on()) {
				Render2D.rect(ctx, rx + slide, cy, w, rowH * t, Theme.panel());
			}
			if (bar.on()) {
				float bx = right ? rx + w - 1.5f : rx;
				Render2D.glow(ctx, bx + slide, cy, 1.5f, rowH * t, 0.75f, 4f, ColorUtil.withAlpha(accent, 140), ColorUtil.withAlpha(accent, 140));
				Render2D.rect(ctx, bx + slide, cy, 1.5f, rowH * t, accent);
			}
			float tx = rx + slide + (right ? 5f : 6.5f);
			float advance = Render2D.textMiddle(ctx, font, row.module.name(), tx, cy + rowH * t / 2f, size, ColorUtil.lerp(Theme.text(), accent, 0.25f));
			if (row.suffix != null) Render2D.textMiddle(ctx, font, row.suffix, tx + advance + 4f, cy + rowH * t / 2f, size, Theme.textDim());
			Render2D.setAlpha(1f);
			cy += rowH * t;
		}
	}

	/** A listed module with its slide animation and label width, measured again only when its suffix or the font changes. */
	private static final class Row {
		final Module module;
		final Anim anim = new Anim(0f, 12f);
		String suffix;
		float width = -1f;
		private SdfFont font;
		private float size;

		Row(Module module) {
			this.module = module;
		}

		void measure(SdfFont font, float size, boolean withSuffix) {
			String current = withSuffix ? module.suffix() : null;
			if (width >= 0f && font == this.font && size == this.size && Objects.equals(current, suffix)) return;
			this.font = font;
			this.size = size;
			suffix = current;
			width = font.width(module.name(), size) + (current != null ? font.width(current, size) + 4f : 0f);
		}
	}
}
