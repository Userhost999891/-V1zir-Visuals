package dev.v1zir.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String name, String defaultValue, String... modes) {
		super(name, defaultValue);
		this.modes = List.of(modes);
		if (!this.modes.contains(defaultValue)) throw new IllegalArgumentException("Unknown default mode " + defaultValue);
	}

	public List<String> modes() {
		return modes;
	}

	public boolean is(String mode) {
		return value.equals(mode);
	}

	public int index() {
		return Math.max(0, modes.indexOf(value));
	}

	public void cycle(int direction) {
		int size = modes.size();
		set(modes.get(Math.floorMod(index() + direction, size)));
	}

	@Override
	public void set(String value) {
		if (modes.contains(value)) super.set(value);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element.isJsonPrimitive()) set(element.getAsString());
	}
}
