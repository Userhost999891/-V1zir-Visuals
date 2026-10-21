package dev.v1zir.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** A keyboard key that a module polls while it is held (zoom, free look), unlike the module's toggle bind. */
public class KeySetting extends Setting<Integer> {
	public KeySetting(String name, int defaultKey) {
		super(name, defaultKey);
	}

	public int key() {
		return value;
	}

	public boolean isSet() {
		return value != GLFW.GLFW_KEY_UNKNOWN;
	}

	/** True while the key is down in game; any open screen counts as released. */
	public boolean held() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return isSet() && mc.currentScreen == null && InputUtil.isKeyPressed(mc.getWindow(), value);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) set(element.getAsInt());
	}
}
