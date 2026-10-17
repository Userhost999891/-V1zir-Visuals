package dev.v1zir.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String name, boolean defaultValue) {
		super(name, defaultValue);
	}

	public boolean on() {
		return value;
	}

	public void toggle() {
		set(!value);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) set(element.getAsBoolean());
	}
}
