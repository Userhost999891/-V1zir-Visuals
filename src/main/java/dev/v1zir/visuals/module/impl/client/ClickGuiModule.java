package dev.v1zir.visuals.module.impl.client;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/** Holds the click GUI keybind (Right Shift by default) and its options. */
public final class ClickGuiModule extends Module {
	public final BoolSetting animations = add(new BoolSetting("Animations", true));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.7, 1.4, 0.05).suffix("x"));
	public final BoolSetting dimBackground = add(new BoolSetting("Dim background", true));

	public ClickGuiModule() {
		super("Click GUI", "Opens this menu", Category.CLIENT);
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
}
