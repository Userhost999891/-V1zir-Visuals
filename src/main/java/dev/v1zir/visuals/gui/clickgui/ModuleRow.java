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
	final Module module;
	private final List<SettingComponent<?>> components = new ArrayList<>();
	private final Anim expand = new Anim(0f, 13f);
	private final Anim hover = new Anim(0f, 20f);
	private final Anim active;
	private boolean expanded;
	private boolean binding;
	private float settingsHeight;
	private float x;
	private float y;
	private float width;

	ModuleRow(Module module) {
		this.module = module;
		for (Setting<?> setting : module.settings()) components.add(SettingComponent.of(setting));
		active = new Anim(module.isEnabled() ? 1f : 0f, 14f);
	}

	boolean isBinding() {
		return binding;
	}

	boolean hasSettings() {
		for (SettingComponent<?> c : components) if (c.visible()) return true;
		return false;
	}

	/** Height this row will take next frame (used for layout before drawing). */
	float height() {
		return Ui.ROW_HEIGHT + settingsHeight * Anim.easeOutCubic(expand.value());
	}

	float render(DrawContext ctx, float x, float y, float width, double mouseX, double mouseY) {
		this.x = x;
		this.y = y;
		this.width = width;
		SdfFont font = Fonts.medium();
		boolean hovered = Ui.inside(mouseX, mouseY, x, y, width, Ui.ROW_HEIGHT);
		float h = hover.target(hovered ? 1f : 0f).get();
		float on = active.target(module.isEnabled() ? 1f : 0f).get();
		int accent = Theme.accent();

		if (h > 0.01f) Render2D.round(ctx, x + 3f, y + 1f, width - 6f, Ui.ROW_HEIGHT - 2f, 4f, ColorUtil.withAlpha(0xFFFFFF, Math.round(14 * h)));
		if (on > 0.01f) {
			Render2D.round(ctx, x + 3f, y + 1f, width - 6f, Ui.ROW_HEIGHT - 2f, 4f,
				ColorUtil.withAlpha(accent, Math.round(52 * on)), ColorUtil.withAlpha(Theme.accent2(), Math.round(10 * on)),
				ColorUtil.withAlpha(Theme.accent2(), Math.round(10 * on)), ColorUtil.withAlpha(accent, Math.round(52 * on)));
			float barH = (Ui.ROW_HEIGHT - 8f) * on;
			Render2D.glow(ctx, x + 4f, y + Ui.ROW_HEIGHT / 2f - barH / 2f, 2f, barH, 1f, 4f, ColorUtil.withAlpha(accent, 150), ColorUtil.withAlpha(accent, 150));
			Render2D.round(ctx, x + 4f, y + Ui.ROW_HEIGHT / 2f - barH / 2f, 2f, barH, 1f, accent);
		}
		String label = binding ? "Press a key..." : module.name();
		int textColor = binding ? accent : ColorUtil.lerp(Theme.textDim(), Theme.text(), Math.max(on, h * 0.6f));
		Render2D.textMiddle(ctx, font, label, x + 10f, y + Ui.ROW_HEIGHT / 2f, Ui.TEXT, textColor);

		float right = x + width - 8f;
		if (hasSettings()) {
			Render2D.icon(ctx, expanded ? Icons.CHEVRON_UP : Icons.ELLIPSIS, right - 3f, y + Ui.ROW_HEIGHT / 2f, 8f,
				ColorUtil.withAlpha(Theme.textDim(), hovered || expanded ? 255 : 150));
			right -= 12f;
		}
		if (module.key() != GLFW.GLFW_KEY_UNKNOWN && !binding) {
			String key = KeyNames.name(module.key());
			SdfFont small = Fonts.semibold();
			float kw = small.width(key, 5.5f) + 6f;
			Render2D.round(ctx, right - kw, y + 4.5f, kw, Ui.ROW_HEIGHT - 9f, 2.5f, ColorUtil.withAlpha(0xFFFFFF, 16));
			Render2D.textMiddle(ctx, small, key, right - kw + 3f, y + Ui.ROW_HEIGHT / 2f, 5.5f, Theme.textDim());
		}

		float t = Anim.easeOutCubic(expand.target(expanded ? 1f : 0f).get());
		float total = Ui.ROW_HEIGHT;
		if (t > 0.005f) {
			float sy = y + Ui.ROW_HEIGHT;
			float visibleH = settingsHeight * t;
			ctx.enableScissor((int) Math.floor(x), (int) Math.floor(sy), (int) Math.ceil(x + width), (int) Math.ceil(sy + visibleH));
			Render2D.round(ctx, x + 3f, sy, width - 6f, Math.max(settingsHeight - 2f, 1f), 4f, ColorUtil.withAlpha(0x000000, 50));
			float cy = sy + 4f;
			float alpha = Render2D.alpha();
			Render2D.setAlpha(alpha * t);
			for (SettingComponent<?> component : components) {
				if (!component.visible()) continue;
				cy += component.render(ctx, x + Ui.PAD + 2f, cy, width - (Ui.PAD + 2f) * 2f, mouseX, mouseY) + 3f;
			}
			Render2D.setAlpha(alpha);
			ctx.disableScissor();
			settingsHeight = cy - sy + 3f;
			total += visibleH;
		} else {
			// Measure the settings so the first expansion animates to the right height.
			float measured = 4f;
			for (SettingComponent<?> component : components) if (component.visible()) measured += estimate(component) + 3f;
			settingsHeight = measured + 3f;
		}
		return total;
	}

	private static float estimate(SettingComponent<?> component) {
		if (component instanceof SliderComponent) return 21f;
		if (component instanceof ModeComponent) return 26f;
		return 15f;
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
