package dev.v1zir.visuals.module;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.hud.Notifications;
import dev.v1zir.visuals.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A toggleable feature with settings, shown in its category panel of the click GUI. */
public abstract class Module {
	/** Sub-category of modules that do not pick one. */
	public static final String GENERAL = "General";
	protected static final MinecraftClient mc = MinecraftClient.getInstance();

	private final String name;
	private final String description;
	private final Category category;
	private String subCategory = GENERAL;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;
	private int key = GLFW.GLFW_KEY_UNKNOWN;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	/** Tab inside the category panel; a panel whose modules share one hides its tabs. */
	public String subCategory() {
		return subCategory;
	}

	protected void setSubCategory(String subCategory) {
		this.subCategory = subCategory;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public boolean isEnabled() {
		return enabled;
	}

	/** Modules like the theme are always on and cannot be toggled from the GUI. */
	public boolean canToggle() {
		return true;
	}

	/** Whether the module shows up in the HUD module list. */
	public boolean showInList() {
		return true;
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void setEnabled(boolean enabled) {
		setEnabled(enabled, true);
	}

	public void setEnabled(boolean enabled, boolean notify) {
		if (this.enabled == enabled || !canToggle() && !enabled) return;
		this.enabled = enabled;
		ModuleManager.changed();
		try {
			if (enabled) onEnable();
			else onDisable();
		} catch (RuntimeException e) {
			V1zir.LOGGER.error("Error while toggling {}", name, e);
		}
		if (notify && showInList()) Notifications.moduleToggled(this);
	}

	public int key() {
		return key;
	}

	public void setKey(int key) {
		this.key = key;
		ModuleManager.changed();
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	public void onTick() {
	}

	/** A key was pressed in game (no screen open); enabled modules with action keys react here. */
	public void onKeyPressed(int key) {
	}

	/** Short state shown next to the name in the module list (e.g. the selected preset). */
	public String suffix() {
		return null;
	}
}
