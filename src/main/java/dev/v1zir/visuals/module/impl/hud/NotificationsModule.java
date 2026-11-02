package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;

/**
 * Shows module toggles and other events, as a Dynamic Island at the top centre (gui/hud/DynamicIsland) or as
 * toasts in the corner (gui/hud/Notifications).
 */
public final class NotificationsModule extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Island", "Island", "Toasts"));
	public final ModeSetting idle = add(new ModeSetting("Idle", "Pill", "Pill", "Hidden").visibleIf(this::island));
	/** Song changes; only while a Spotify widget runs the media backend. */
	public final BoolSetting songs = add(new BoolSetting("Songs", true).visibleIf(this::island));
	public final BoolSetting menu = add(new BoolSetting("Menu", true).visibleIf(this::island));

	public NotificationsModule() {
		super("Notifications", "Dynamic Island or toasts for toggles and songs", Category.HUD);
		setSubCategory("Widgets");
	}

	@Override
	public boolean showInList() {
		return false;
	}

	public boolean island() {
		return style.is("Island");
	}
}
