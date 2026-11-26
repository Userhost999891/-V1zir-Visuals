package dev.v1zir.visuals.browser;

import dev.v1zir.visuals.V1zir;
import net.minecraft.client.input.KeyInput;
import org.cef.browser.CefBrowser;
import org.lwjgl.glfw.GLFW;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;

/**
 * The game's keys for Chromium, as JCEF reads them. On Windows JCEF takes a key's code from the AWT event's private
 * scan code field, which the events MCEF makes from the game's keys leave at 0, so Backspace, Enter, the arrows and
 * every shortcut (Ctrl+A, C, V, X, Z) reached the page as no key at all (letters come as characters and worked).
 * The events are made here with a field of that name holding the key's real scan code (JNI finds a subclass's field
 * before its parent's), and sent by JCEF's own method; Enter also brings its character, which pages need to send a
 * form or start a new line.
 */
final class BrowserKeys {
	@SuppressWarnings({"unused", "serial"})
	private static final class ScanKeyEvent extends KeyEvent {
		/** Read by JCEF's native code on Windows, which gets the virtual key from it. */
		private final long scancode;

		ScanKeyEvent(Component source, int id, int modifiers, int keyCode, char keyChar, long scancode) {
			super(source, id, System.currentTimeMillis(), modifiers, keyCode, keyChar);
			this.scancode = scancode;
		}
	}

	private static Method send;
	private static boolean failed;

	private BrowserKeys() {
	}

	/** Whether keys go this way (false once JCEF's method could not be used). */
	static boolean works() {
		return !failed;
	}

	/** A key pressed: false when it could not be sent this way (MCEF's own events are used then). */
	static boolean pressed(CefBrowser browser, KeyInput input) {
		int vk = awtKey(input.key());
		if (vk == KeyEvent.VK_UNDEFINED) return false;
		Component source = browser.getUIComponent();
		int modifiers = awtModifiers(input.modifiers());
		long scan = scanCode(input.key(), input.scancode());
		if (!send(browser, new ScanKeyEvent(source, KeyEvent.KEY_PRESSED, modifiers, vk, KeyEvent.CHAR_UNDEFINED, scan))) return false;
		// The character Enter types: forms are sent and lines broken on it, not on the key.
		if (vk == KeyEvent.VK_ENTER) send(browser, new ScanKeyEvent(source, KeyEvent.KEY_TYPED, modifiers, KeyEvent.VK_UNDEFINED, '\r', scan));
		return true;
	}

	static boolean released(CefBrowser browser, KeyInput input) {
		int vk = awtKey(input.key());
		if (vk == KeyEvent.VK_UNDEFINED) return false;
		long scan = scanCode(input.key(), input.scancode());
		return send(browser, new ScanKeyEvent(browser.getUIComponent(), KeyEvent.KEY_RELEASED, awtModifiers(input.modifiers()), vk,
			KeyEvent.CHAR_UNDEFINED, scan));
	}

	/** JCEF's own (protected) way of sending a key event to the page, found up the browser's classes. */
	private static boolean send(CefBrowser browser, KeyEvent event) {
		if (failed) return false;
		try {
			if (send == null) {
				for (Class<?> type = browser.getClass(); type != null && send == null; type = type.getSuperclass()) {
					try {
						Method method = type.getDeclaredMethod("sendKeyEvent", KeyEvent.class);
						method.setAccessible(true);
						send = method;
					} catch (NoSuchMethodException ignored) {
						// Further up.
					}
				}
				if (send == null) throw new NoSuchMethodException("sendKeyEvent");
			}
			send.invoke(browser, event);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			failed = true;
			V1zir.LOGGER.warn("Browser keys go MCEF's way (shortcuts may not work on Windows)", e);
			return false;
		}
	}

	/**
	 * The scan code JCEF wants: the key's own from GLFW (on Windows the hardware scan code, 0x100 marking the
	 * extended keys, given to JCEF with the 0xE0 prefix Windows uses for them); a key without one gets the scan code
	 * of its place on a US keyboard.
	 */
	private static long scanCode(int key, int scancode) {
		int code = scancode > 0 ? scancode : fallbackScanCode(key);
		if (code <= 0) return 0;
		return (code & 0x100) != 0 ? 0xE000 | code & 0xFF : code & 0xFF;
	}

	private static int fallbackScanCode(int key) {
		if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) return 0x02 + key - GLFW.GLFW_KEY_1;
		if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F10) return 0x3B + key - GLFW.GLFW_KEY_F1;
		if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
			int[] letters = {0x1E, 0x30, 0x2E, 0x20, 0x12, 0x21, 0x22, 0x23, 0x17, 0x24, 0x25, 0x26, 0x32, 0x31, 0x18, 0x19, 0x10, 0x13,
				0x1F, 0x14, 0x16, 0x2F, 0x11, 0x2D, 0x15, 0x2C};
			return letters[key - GLFW.GLFW_KEY_A];
		}
		return switch (key) {
			case GLFW.GLFW_KEY_0 -> 0x0B;
			case GLFW.GLFW_KEY_ESCAPE -> 0x01;
			case GLFW.GLFW_KEY_BACKSPACE -> 0x0E;
			case GLFW.GLFW_KEY_TAB -> 0x0F;
			case GLFW.GLFW_KEY_ENTER -> 0x1C;
			case GLFW.GLFW_KEY_SPACE -> 0x39;
			case GLFW.GLFW_KEY_LEFT_SHIFT -> 0x2A;
			case GLFW.GLFW_KEY_RIGHT_SHIFT -> 0x36;
			case GLFW.GLFW_KEY_LEFT_CONTROL -> 0x1D;
			case GLFW.GLFW_KEY_LEFT_ALT -> 0x38;
			case GLFW.GLFW_KEY_F11 -> 0x57;
			case GLFW.GLFW_KEY_F12 -> 0x58;
			case GLFW.GLFW_KEY_KP_ENTER -> 0x11C;
			case GLFW.GLFW_KEY_RIGHT_CONTROL -> 0x11D;
			case GLFW.GLFW_KEY_RIGHT_ALT -> 0x138;
			case GLFW.GLFW_KEY_HOME -> 0x147;
			case GLFW.GLFW_KEY_UP -> 0x148;
			case GLFW.GLFW_KEY_PAGE_UP -> 0x149;
			case GLFW.GLFW_KEY_LEFT -> 0x14B;
			case GLFW.GLFW_KEY_RIGHT -> 0x14D;
			case GLFW.GLFW_KEY_END -> 0x14F;
			case GLFW.GLFW_KEY_DOWN -> 0x150;
			case GLFW.GLFW_KEY_PAGE_DOWN -> 0x151;
			case GLFW.GLFW_KEY_INSERT -> 0x152;
			case GLFW.GLFW_KEY_DELETE -> 0x153;
			default -> 0;
		};
	}

	private static int awtModifiers(int glfw) {
		int mods = 0;
		if ((glfw & GLFW.GLFW_MOD_SHIFT) != 0) mods |= InputEvent.SHIFT_DOWN_MASK;
		if ((glfw & GLFW.GLFW_MOD_CONTROL) != 0) mods |= InputEvent.CTRL_DOWN_MASK;
		if ((glfw & GLFW.GLFW_MOD_ALT) != 0) mods |= InputEvent.ALT_DOWN_MASK;
		if ((glfw & GLFW.GLFW_MOD_SUPER) != 0) mods |= InputEvent.META_DOWN_MASK;
		return mods;
	}

	/** The AWT key code of a GLFW key, or VK_UNDEFINED. */
	private static int awtKey(int key) {
		if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return KeyEvent.VK_A + key - GLFW.GLFW_KEY_A;
		if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return KeyEvent.VK_0 + key - GLFW.GLFW_KEY_0;
		if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) return KeyEvent.VK_F1 + key - GLFW.GLFW_KEY_F1;
		if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) return KeyEvent.VK_NUMPAD0 + key - GLFW.GLFW_KEY_KP_0;
		return switch (key) {
			case GLFW.GLFW_KEY_SPACE -> KeyEvent.VK_SPACE;
			case GLFW.GLFW_KEY_APOSTROPHE -> KeyEvent.VK_QUOTE;
			case GLFW.GLFW_KEY_COMMA -> KeyEvent.VK_COMMA;
			case GLFW.GLFW_KEY_MINUS -> KeyEvent.VK_MINUS;
			case GLFW.GLFW_KEY_PERIOD -> KeyEvent.VK_PERIOD;
			case GLFW.GLFW_KEY_SLASH -> KeyEvent.VK_SLASH;
			case GLFW.GLFW_KEY_SEMICOLON -> KeyEvent.VK_SEMICOLON;
			case GLFW.GLFW_KEY_EQUAL -> KeyEvent.VK_EQUALS;
			case GLFW.GLFW_KEY_LEFT_BRACKET -> KeyEvent.VK_OPEN_BRACKET;
			case GLFW.GLFW_KEY_BACKSLASH -> KeyEvent.VK_BACK_SLASH;
			case GLFW.GLFW_KEY_RIGHT_BRACKET -> KeyEvent.VK_CLOSE_BRACKET;
			case GLFW.GLFW_KEY_GRAVE_ACCENT -> KeyEvent.VK_BACK_QUOTE;
			case GLFW.GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> KeyEvent.VK_ENTER;
			case GLFW.GLFW_KEY_TAB -> KeyEvent.VK_TAB;
			case GLFW.GLFW_KEY_BACKSPACE -> KeyEvent.VK_BACK_SPACE;
			case GLFW.GLFW_KEY_INSERT -> KeyEvent.VK_INSERT;
			case GLFW.GLFW_KEY_DELETE -> KeyEvent.VK_DELETE;
			case GLFW.GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT;
			case GLFW.GLFW_KEY_LEFT -> KeyEvent.VK_LEFT;
			case GLFW.GLFW_KEY_DOWN -> KeyEvent.VK_DOWN;
			case GLFW.GLFW_KEY_UP -> KeyEvent.VK_UP;
			case GLFW.GLFW_KEY_PAGE_UP -> KeyEvent.VK_PAGE_UP;
			case GLFW.GLFW_KEY_PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN;
			case GLFW.GLFW_KEY_HOME -> KeyEvent.VK_HOME;
			case GLFW.GLFW_KEY_END -> KeyEvent.VK_END;
			case GLFW.GLFW_KEY_CAPS_LOCK -> KeyEvent.VK_CAPS_LOCK;
			case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> KeyEvent.VK_SHIFT;
			case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> KeyEvent.VK_CONTROL;
			case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> KeyEvent.VK_ALT;
			case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> KeyEvent.VK_META;
			case GLFW.GLFW_KEY_KP_DECIMAL -> KeyEvent.VK_DECIMAL;
			case GLFW.GLFW_KEY_KP_DIVIDE -> KeyEvent.VK_DIVIDE;
			case GLFW.GLFW_KEY_KP_MULTIPLY -> KeyEvent.VK_MULTIPLY;
			case GLFW.GLFW_KEY_KP_SUBTRACT -> KeyEvent.VK_SUBTRACT;
			case GLFW.GLFW_KEY_KP_ADD -> KeyEvent.VK_ADD;
			default -> KeyEvent.VK_UNDEFINED;
		};
	}
}
