package dev.v1zir.visuals.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class KeyNames {
	private KeyNames() {
	}

	/** Names are built once per key: HUD elements ask for them every frame. */
	private static final Int2ObjectOpenHashMap<String> CACHE = new Int2ObjectOpenHashMap<>();

	public static String name(int key) {
		if (key == GLFW.GLFW_KEY_UNKNOWN || key < 0) return "None";
		String name = CACHE.get(key);
		if (name == null) CACHE.put(key, name = build(key));
		return name;
	}

	private static String build(int key) {
		return switch (key) {
			case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
			case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
			case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
			case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
			case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
			case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
			case GLFW.GLFW_KEY_SPACE -> "Space";
			case GLFW.GLFW_KEY_TAB -> "Tab";
			case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
			case GLFW.GLFW_KEY_ENTER -> "Enter";
			case GLFW.GLFW_KEY_BACKSPACE -> "Back";
			case GLFW.GLFW_KEY_INSERT -> "Insert";
			case GLFW.GLFW_KEY_DELETE -> "Delete";
			case GLFW.GLFW_KEY_HOME -> "Home";
			case GLFW.GLFW_KEY_END -> "End";
			case GLFW.GLFW_KEY_PAGE_UP -> "PgUp";
			case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
			case GLFW.GLFW_KEY_UP -> "Up";
			case GLFW.GLFW_KEY_DOWN -> "Down";
			case GLFW.GLFW_KEY_LEFT -> "Left";
			case GLFW.GLFW_KEY_RIGHT -> "Right";
			case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
			default -> {
				if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) yield "F" + (key - GLFW.GLFW_KEY_F1 + 1);
				if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) yield "Num" + (key - GLFW.GLFW_KEY_KP_0);
				if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z || key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) yield String.valueOf((char) key);
				String name = GLFW.glfwGetKeyName(key, 0);
				yield name != null ? name.toUpperCase(Locale.ROOT) : "Key " + key;
			}
		};
	}
}
