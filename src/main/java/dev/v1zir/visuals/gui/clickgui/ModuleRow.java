package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.Setting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.KeyNames;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** One module inside a category panel, with its settings folding out underneath. */
public final class ModuleRow {
	private static final float KEY_SIZE = 5.5f;
	/** Cell of a disabled module: near black with a faint rim, as in the reference design. */
	private static final int OFF = 0xB80C0F13;
	private static final int OFF_HOVER = 0xC41A1F26;
	private static final int OFF_TEXT = 0xFFCDD2DB;
	/** Cell of an enabled module: a lighter grey with a light rim. */
	private static final int ON = 0xDC48505A;

	final Module module;
	private final List<SettingComponent<?>> components = new ArrayList<>();
	private final Anim expand = new Anim(0f, 13f);
	private final Anim hover = new Anim(0f, 24f);
	private final Label nameWidth = new Label();
	private final Label keyWidth = new Label();
	private boolean expanded;
	private boolean binding;
	private boolean hovered;
	private float settingsHeight;
	private float x;
	private float y;
	private float width;
	/** Name of the bound key, rebuilt only when the bind changes. */
	private int shownKey = GLFW.GLFW_KEY_UNKNOWN;
	private String keyName = "";

	ModuleRow(Module module) {
		this.module = module;
		for (Setting<?> setting : module.settings()) components.add(SettingComponent.of(setting));
	}

	boolean isBinding() {
		return binding;
	}

	/** Whether the mouse was over the cell in the last frame. */
	boolean isHovered() {
		return hovered;
	}

	boolean hasSettings() {
		for (SettingComponent<?> c : components) if (c.visible()) return true;
		return false;
	}

	/** Height this row will take next frame (used for layout before drawing). */
	float height() {
		return Ui.ROW_HEIGHT + settingsHeight * expand.target(expanded ? 1f : 0f).value();
	}

	float render(DrawContext ctx, float x, float y, float width, double mouseX, double mouseY) {
		this.x = x;
		this.y = y;
		this.width = width;
		hovered = Ui.inside(mouseX, mouseY, x, y, width, Ui.ROW_HEIGHT);
		float h = hover.target(hovered ? 1f : 0f).get();
		float t = expand.target(expanded ? 1f : 0f).get();
		boolean on = module.isEnabled();
		int accent = Theme.accent();
		float cy = y + Ui.ROW_HEIGHT / 2f;

		// Every module is a rounded cell; an enabled one simply lights up (no enable animation),
		// hovering brightens it a little.
		float cellX = x + Ui.CELL_INSET;
		float cellY = y + 1f;
		float cellW = width - Ui.CELL_INSET * 2f;
		float cellH = Ui.ROW_HEIGHT - 2f;
		float r = Ui.CELL_RADIUS;
		int fill = on ? ColorUtil.brighten(ColorUtil.lerp(ON, ColorUtil.withAlpha(accent, ColorUtil.alpha(ON)), 0.12f), 0.05f * h)
			: ColorUtil.lerp(OFF, OFF_HOVER, h);
		Render2D.gradientV(ctx, cellX, cellY, cellW, cellH, r, ColorUtil.brighten(fill, 0.03f), fill);
		int rim = binding ? ColorUtil.withAlpha(accent, 220) : ColorUtil.withAlpha(0xFFFFFF, on ? Math.round(88 + 34 * h) : Math.round(18 + 20 * h));
		Render2D.outline(ctx, cellX, cellY, cellW, cellH, r, 1f, rim);

		// Settings chevron and key name sit quietly on the right; the name stays centred unless it would touch them.
		float right = cellX + cellW - 5f;
		if (hasSettings()) {
			// The chevron turns from pointing right to pointing down as the settings fold out.
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(right - 2.5f, cy);
			ctx.getMatrices().rotate((t - 1f) * (float) Math.PI * 0.5f);
			int base = on ? 0xFFFFFFFF : Theme.textDim();
			Render2D.icon(ctx, Icons.CHEVRON_DOWN, 0f, 0f, 6.5f, ColorUtil.withAlpha(base, Math.round(70 + 150 * Math.max(h, t))));
			ctx.getMatrices().popMatrix();
			right -= 9f;
		}
		if (module.key() != GLFW.GLFW_KEY_UNKNOWN && !binding) {
			if (module.key() != shownKey) {
				shownKey = module.key();
				keyName = KeyNames.name(shownKey);
			}
			SdfFont small = Fonts.semibold();
			float kw = keyWidth.width(small, keyName, KEY_SIZE);
			Render2D.textMiddle(ctx, small, keyName, right - kw, cy, KEY_SIZE, on ? 0x9CFFFFFF : ColorUtil.withAlpha(Theme.textDim(), 170));
			right -= kw + 4f;
		}

		SdfFont font = Fonts.medium();
		String label = binding ? "Press a key..." : module.name();
		int textColor = binding ? ColorUtil.lerp(accent, 0xFFFFFFFF, 0.3f) : on ? 0xFFFFFFFF : ColorUtil.lerp(OFF_TEXT, 0xFFFFFFFF, h);
		float lw = nameWidth.width(font, label, Ui.TEXT);
		float nx = Math.max(cellX + 5f, Math.min(cellX + (cellW - lw) / 2f, right - 2f - lw));
		Render2D.text(ctx, font, label, nx, Render2D.middle(font, cy, Ui.TEXT), Ui.TEXT, textColor);

		float total = Ui.ROW_HEIGHT;
		if (t > 0.005f) {
			float sy = y + Ui.ROW_HEIGHT;
			float visibleH = settingsHeight * t;
			ctx.enableScissor((int) Math.floor(x), (int) Math.floor(sy), (int) Math.ceil(x + width), (int) Math.ceil(sy + visibleH));
			float wellH = Math.max(settingsHeight - 2f, 1f);
			Render2D.round(ctx, cellX, sy, cellW, wellH, r, 0x38000000);
			Render2D.outline(ctx, cellX, sy, cellW, wellH, r, 1f, 0x10FFFFFF);
			// A slim accent guide ties the settings to their module.
			Render2D.gradientV(ctx, cellX + 2.5f, sy + 5f, 1.5f, Math.max(wellH - 10f, 1f), 0.75f,
				ColorUtil.withAlpha(accent, 200), ColorUtil.withAlpha(Theme.accent2(), 40));
			float sx = cellX + 8f;
			float sw = cellW - 13f;
			float py = sy + 4f;
			float alpha = Render2D.alpha();
			Render2D.setAlpha(alpha * t);
			for (SettingComponent<?> component : components) {
				if (!component.visible()) continue;
				py += component.render(ctx, sx, py, sw, mouseX, mouseY) + 3f;
			}
			Render2D.setAlpha(alpha);
			ctx.disableScissor();
			settingsHeight = py - sy + 3f;
			total += visibleH;
		} else {
			// Measure the settings so the first expansion animates to the right height.
			float measured = 4f;
			for (SettingComponent<?> component : components) if (component.visible()) measured += component.estimateHeight() + 3f;
			settingsHeight = measured + 3f;
		}
		return total;
	}

	boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (Ui.inside(mouseX, mouseY, x, y, width, Ui.ROW_HEIGHT)) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				if (module.canToggle()) module.toggle();
				else expanded = !expanded;
			} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				if (hasSettings()) expanded = !expanded;
			} else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
				binding = !binding;
			}
			return true;
		}
		if (expanded && expand.value() > 0.9f) {
			for (SettingComponent<?> component : components) {
				if (component.visible() && component.mouseClicked(mouseX, mouseY, button)) return true;
			}
		}
		return false;
	}

	void mouseDragged(double mouseX, double mouseY) {
		if (!expanded) return;
		for (SettingComponent<?> component : components) component.mouseDragged(mouseX, mouseY);
	}

	void mouseReleased() {
		for (SettingComponent<?> component : components) component.mouseReleased();
	}

	/** Returns true when the key was consumed by bind mode. */
	boolean keyPressed(int key) {
		if (!binding) return false;
		if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) module.setKey(GLFW.GLFW_KEY_UNKNOWN);
		else module.setKey(key);
		binding = false;
		return true;
	}
}
