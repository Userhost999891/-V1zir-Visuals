package dev.v1zir.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public class ModeSetting extends Setting<String> {
	private List<String> modes;

	public ModeSetting(String name, String defaultValue, String... modes) {
		super(name, defaultValue);
		this.modes = List.of(modes);
		if (!this.modes.contains(defaultValue)) throw new IllegalArgumentException("Unknown default mode " + defaultValue);
	}

	public List<String> modes() {
		return modes;
	}

	/** Replaces the choices (when they change, as the files of a folder do); the value stays if it is still one of them. */
	public void setModes(List<String> next) {
		if (next.isEmpty()) return;
		modes = List.copyOf(next);
		if (!modes.contains(value)) value = modes.contains(defaultValue()) ? defaultValue() : modes.get(0);
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
