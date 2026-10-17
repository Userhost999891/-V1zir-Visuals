package dev.v1zir.visuals.module;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.hud.Notifications;
import dev.v1zir.visuals.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
	protected static final MinecraftClient mc = MinecraftClient.getInstance();

	private final String name;
	private final String description;
	private final Category category;
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
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	public void onTick() {
	}

	/** Short state shown next to the name in the module list (e.g. the selected preset). */
	public String suffix() {
		return null;
	}
}
