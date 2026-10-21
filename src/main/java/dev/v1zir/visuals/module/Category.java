package dev.v1zir.visuals.module;

import dev.v1zir.visuals.render.font.Icons;

public enum Category {
	VISUALS("Visuals", Icons.EYE),
	EFFECTS("Effects", Icons.SPARKLES),
	WORLD("World", Icons.EARTH),
	HUD("Hud", Icons.LAYOUT_DASHBOARD),
	CLIENT("Client", Icons.SETTINGS);

	private final String title;
	private final int icon;

	Category(String title, int icon) {
		this.title = title;
		this.icon = icon;
	}

	public String title() {
		return title;
	}

	public int icon() {
		return icon;
	}
}
