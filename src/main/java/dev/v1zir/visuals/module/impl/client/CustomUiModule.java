package dev.v1zir.visuals.module.impl.client;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;

/** Restyles vanilla buttons, sliders, text fields, tooltips and the inventory in the Theme style. */
public final class CustomUiModule extends Module {
	public final BoolSetting buttons = add(new BoolSetting("Buttons", true));
	public final BoolSetting sliders = add(new BoolSetting("Sliders", true));
	public final BoolSetting textFields = add(new BoolSetting("Text fields", true));
	public final BoolSetting inventory = add(new BoolSetting("Inventory", true));
	public final BoolSetting hotbar = add(new BoolSetting("Hotbar", true));
	public final BoolSetting tooltips = add(new BoolSetting("Tooltips", true));

	public CustomUiModule() {
		super("Custom UI", "Premium buttons, sliders, inventory, hotbar and tooltips", Category.CLIENT);
		setEnabled(true, false);
	}

	@Override
	public boolean showInList() {
		return false;
	}
}
