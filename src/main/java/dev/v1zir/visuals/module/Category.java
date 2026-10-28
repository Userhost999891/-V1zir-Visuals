package dev.v1zir.visuals.module;

import dev.v1zir.visuals.render.font.Icons;

import java.util.List;

/** Tabs of the click GUI sidebar; every module belongs to exactly one. */
public enum Category {
	VISUALS("Visuals", Icons.EYE, "Camera", "Render", "World"),
	COMBAT("Combat", Icons.SWORDS, "Target", "Hits", "Info"),
	SHADERS("Shaders", Icons.CLOUD_SUN, "Sky", "World"),
	HUD("Hud", Icons.LAYOUT_DASHBOARD, "Info", "Widgets", "Media"),
	SETTINGS("Settings", Icons.SETTINGS);

	private final String title;
	private final int icon;
	private final List<String> subCategories;

	Category(String title, int icon, String... subCategories) {
		this.title = title;
		this.icon = icon;
		this.subCategories = List.of(subCategories);
	}

	/** Sub-categories in the order the tab shows them (its filter chips). */
	public List<String> subCategories() {
		return subCategories;
	}

	public String title() {
		return title;
	}

	public int icon() {
		return icon;
	}
}
