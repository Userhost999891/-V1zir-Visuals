package dev.v1zir.visuals.gui.clickgui;

import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.module.setting.Setting;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Base of the inline setting widgets shown under an expanded module. */
public abstract class SettingComponent<S extends Setting<?>> {
	protected final S setting;
	/** Hover glow of the whole component; hovering only lights a widget up, nothing moves. */
	protected final Anim hover = new Anim(0f, 20f);
	protected float x;
	protected float y;
	protected float width;
	/** Height returned by the last draw, or -1 before the first one. */
	private float drawn = -1f;

	protected SettingComponent(S setting) {
		this.setting = setting;
	}

	public static SettingComponent<?> of(Setting<?> setting) {
		if (setting instanceof BoolSetting b) return new BoolComponent(b);
		if (setting instanceof NumberSetting n) return new SliderComponent(n);
		if (setting instanceof ModeSetting m) return new ModeComponent(m);
		if (setting instanceof ColorSetting c) return new ColorComponent(c);
		if (setting instanceof KeySetting k) return new KeyComponent(k);
		throw new IllegalArgumentException("Unsupported setting " + setting.getClass());
	}

	public boolean visible() {
		return setting.isVisible();
	}

	/** Height before the component has been laid out, so the first expansion animates to the right size. */
	public abstract float estimateHeight();

	/** Height for layout: the last drawn height, or the estimate before the component was drawn. */
	public float height() {
		return drawn >= 0f ? drawn : estimateHeight();
	}

	/** Lays out and draws the component; returns its height. */
	public float render(DrawContext ctx, float x, float y, float width, double mouseX, double mouseY) {
		this.x = x;
		this.y = y;
		this.width = width;
		drawn = draw(ctx, mouseX, mouseY);
		return drawn;
	}

	protected abstract float draw(DrawContext ctx, double mouseX, double mouseY);

	/** Label colour: dim at rest, full text colour while hovered or active. */
	protected int labelColor(float light) {
		return ColorUtil.lerp(Theme.textDim(), Theme.text(), light);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	public void mouseDragged(double mouseX, double mouseY) {
	}

	public void mouseReleased() {
	}

	/** Returns true when the component consumed the key (a key setting waiting for input). */
	public boolean keyPressed(int key) {
		return false;
	}
}
