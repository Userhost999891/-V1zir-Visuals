package dev.v1zir.visuals.module.impl.client;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/** Holds the click GUI keybind (Right Shift by default) and its options, including the open and close animations. */
public final class ClickGuiModule extends Module {
	public static final String OPEN_NORMAL = "Normal";
	public static final String OPEN_3D = "3D";
	public static final String CLOSE_FADE = "Fade out";
	public static final String CLOSE_SHATTER = "3D Frost";

	public final BoolSetting animations = add(new BoolSetting("Animations", true));
	public final ModeSetting openAnimation = add(new ModeSetting("Open animation", OPEN_3D, OPEN_NORMAL, OPEN_3D).visibleIf(animations::on));
	/** A saved value that no longer exists (the former "Frost") is ignored, so it falls back to 3D Frost. */
	public final ModeSetting closeAnimation = add(new ModeSetting("Close animation", CLOSE_SHATTER, CLOSE_FADE, CLOSE_SHATTER)
		.visibleIf(animations::on));
	public final NumberSetting animationSpeed = add(new NumberSetting("Animation speed", 1.0, 0.5, 2.0, 0.05).suffix("x").visibleIf(animations::on));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.7, 1.4, 0.05).suffix("x"));
	public final BoolSetting dimBackground = add(new BoolSetting("Dim background", true));

	public ClickGuiModule() {
		super("Click GUI", "Opens this menu", Category.SETTINGS);
		setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		setEnabled(true, false);
	}

	@Override
	public boolean canToggle() {
		return false;
	}

	@Override
	public boolean showInList() {
		return false;
	}

	/** Animation speed multiplier, 1 when animations are off (callers skip them then anyway). */
	public float speed() {
		return animations.on() ? animationSpeed.f() : 1f;
	}
}
