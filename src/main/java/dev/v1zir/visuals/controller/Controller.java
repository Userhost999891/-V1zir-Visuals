package dev.v1zir.visuals.controller;

import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.gui.hud.Notifications;
import dev.v1zir.visuals.mixin.MouseInvoker;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ControllerModule;
import dev.v1zir.visuals.render.font.Icons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * A PlayStation controller (DualShock 4 or DualSense, or any other the system knows as a gamepad) playing the game,
 * through GLFW's gamepad API and its mappings. In the world: the left stick walks (as far as it is tilted), the right
 * stick looks, Cross jumps, Circle sneaks, L3 sprints (until you stop), R2 attacks and breaks, L2 uses and places,
 * L1 / R1 go along the hotbar, Triangle opens the inventory, Square drops, R3 changes the view, the D-pad swaps
 * hands (up), shows the player list (down, held), opens chat (left) and picks a block (right), Options pauses and
 * Create (Share) opens V1zir's menu. In screens: the left stick moves the pointer, Cross clicks (or, after the D-pad,
 * takes what is in focus), Square takes a stack to the other side, L2 right-clicks, Circle and Options go back,
 * Triangle closes an inventory, the D-pad moves the focus as the arrow keys do, L1 / R1 go through the focus (or the
 * tabs of the PS5 menu) and the right stick scrolls.
 * <p>
 * It stands down when a controller mod is installed (Controlify, MidnightControls, Controllable). Render thread.
 */
public final class Controller {
	/** What the left stick and the buttons ask of the player's movement this tick (KeyboardInputMixin). */
	public record Movement(float x, float y, boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak,
						   boolean sprint, boolean moving) {
	}

	/** GLFW's buttons, then the two triggers as buttons. */
	private static final int L2 = 15;
	private static final int R2 = 16;
	private static final int BUTTONS = 17;
	private static final float TRIGGER = -0.2f;
	private static final boolean OTHER_MOD = loaded("controlify") || loaded("midnightcontrols") || loaded("controllable");
	/** Degrees a second the view turns with the right stick all the way over, across and up and down. */
	private static final float TURN = 300f;
	private static final float PITCH = 200f;
	private static final double REPEAT_FIRST = 0.35;
	private static final double REPEAT_NEXT = 0.09;

	private static final GLFWGamepadState STATE = GLFWGamepadState.create();
	private static int joystick = -1;
	private static String name = "";
	private static final boolean[] down = new boolean[BUTTONS];
	private static final boolean[] before = new boolean[BUTTONS];
	private static final float[] axes = new float[6];
	private static long lastFrame;
	private static int scanFrames;
	private static boolean sprinting;
	private static boolean ownSprintKey;
	private static boolean ownListKey;
	private static boolean ownAttack;
	private static boolean ownUse;
	/** In screens: the pointer where the controller put it, whether the D-pad was used last, and the D-pad's repeat. */
	private static double cursorX = Double.NaN;
	private static double cursorY = Double.NaN;
	private static boolean focusMode;
	private static int heldArrow = -1;
	private static double heldFor;
	private static double nextRepeat;
	private static double scroll;
	private static boolean clicking;
	private static boolean rightClicking;

	private Controller() {
	}

	private static boolean loaded(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}

	@Nullable
	private static ControllerModule module() {
		ControllerModule module = ModuleManager.get(ControllerModule.class);
		return module != null && module.isEnabled() && !OTHER_MOD ? module : null;
	}

	/** The controller's name, empty when none is connected. */
	public static String name() {
		return joystick >= 0 ? name : "";
	}

	/** Every frame (render thread): finds a controller now and then, reads it and does what its sticks and buttons ask. */
	public static void frame(MinecraftClient client) {
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0f : Math.min((now - lastFrame) / 1.0e9f, 0.1f);
		lastFrame = now;
		ControllerModule module = module();
		if (module == null) {
			if (joystick >= 0) release(client);
			joystick = -1;
			return;
		}
		if (!connected(client)) return;
		if (!GLFW.glfwGetGamepadState(joystick, STATE)) return;
		for (int i = 0; i < 15; i++) down[i] = STATE.buttons(i) == GLFW.GLFW_PRESS;
		for (int i = 0; i < 6; i++) axes[i] = STATE.axes(i);
		down[L2] = axes[GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER] > TRIGGER;
		down[R2] = axes[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER] > TRIGGER;
		if (client.isWindowFocused()) {
			if (client.currentScreen == null) game(client, module, dt);
			else screen(client, client.currentScreen, module, dt);
		}
		System.arraycopy(down, 0, before, 0, BUTTONS);
	}

	/** Whether a controller is connected, looked for every second while none is; says so when one comes or goes. */
	private static boolean connected(MinecraftClient client) {
		if (joystick >= 0 && !GLFW.glfwJoystickIsGamepad(joystick)) {
			Notifications.push(Icons.GAMEPAD_2, "Controller", name + " disconnected", false);
			release(client);
			joystick = -1;
		}
		if (joystick < 0) {
			if (++scanFrames % 60 != 1) return false;
			for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
				if (!GLFW.glfwJoystickPresent(jid) || !GLFW.glfwJoystickIsGamepad(jid)) continue;
				joystick = jid;
				String gamepad = GLFW.glfwGetGamepadName(jid);
				name = gamepad != null ? gamepad : "Controller";
				for (int i = 0; i < BUTTONS; i++) before[i] = down[i] = false;
				Notifications.push(Icons.GAMEPAD_2, "Controller", name + " connected", true);
				break;
			}
		}
		return joystick >= 0;
	}

	private static boolean pressed(int button) {
		return down[button] && !before[button];
	}

	private static boolean released(int button) {
		return !down[button] && before[button];
	}

	/** An axis past the deadzone, rescaled to 0..1 beyond it, squared for fine aim near the middle (sign kept). */
	private static float axis(int index, float deadzone) {
		float v = axes[index];
		float a = Math.abs(v);
		if (a <= deadzone) return 0f;
		float t = Math.min(1f, (a - deadzone) / (1f - deadzone));
		return Math.signum(v) * t * t;
	}

	/** The left stick as a walk, past the deadzone, at most 1 long (linear: walking speed follows the tilt). */
	private static float[] stick(int xAxis, int yAxis, float deadzone) {
		float x = axes[xAxis], y = axes[yAxis];
		float length = (float) Math.sqrt(x * x + y * y);
		if (length <= deadzone) return new float[]{0f, 0f};
		float scale = Math.min(1f, (length - deadzone) / (1f - deadzone)) / length;
		return new float[]{x * scale, y * scale};
	}

	// ------------------------------------------------------------------ in the world

	/** This tick's movement from the controller, null when it asks for none (KeyboardInputMixin). */
	@Nullable
	public static Movement movement() {
		ControllerModule module = module();
		MinecraftClient client = MinecraftClient.getInstance();
		if (module == null || joystick < 0 || client.currentScreen != null) return null;
		float[] walk = stick(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, module.deadzone.f());
		// GLFW: right and down are positive. The game: left and forward are.
		float x = -walk[0], y = -walk[1];
		boolean moving = x != 0f || y != 0f;
		if (!moving || down[GLFW.GLFW_GAMEPAD_BUTTON_B]) sprinting = false;
		return new Movement(x, y, y > 0.3f, y < -0.3f, x > 0.3f, x < -0.3f, down[GLFW.GLFW_GAMEPAD_BUTTON_A], down[GLFW.GLFW_GAMEPAD_BUTTON_B],
			sprinting, moving);
	}

	private static void game(MinecraftClient client, ControllerModule module, float dt) {
		ClientPlayerEntity player = client.player;
		GameOptions options = client.options;
		if (player != null) {
			// The game turns 0.15 degrees for each unit it is given.
			float speed = module.lookSpeed.f();
			float lookX = axis(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X, module.deadzone.f());
			float lookY = axis(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y, module.deadzone.f()) * (module.invertY.on() ? -1f : 1f);
			if (lookX != 0f || lookY != 0f) player.changeLookDirection(lookX * TURN * speed * dt / 0.15, lookY * PITCH * speed * dt / 0.15);
		}
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB)) sprinting = true;
		hold(options.sprintKey, sprinting, ownSprintKey);
		ownSprintKey = sprinting;
		boolean list = down[GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN];
		hold(options.playerListKey, list, ownListKey);
		ownListKey = list;
		ownAttack = trigger(options.attackKey, R2, ownAttack);
		ownUse = trigger(options.useKey, L2, ownUse);
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_Y)) press(options.inventoryKey);
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_X)) press(options.dropKey);
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB)) press(options.togglePerspectiveKey);
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP)) press(options.swapHandsKey);
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT)) press(options.chatKey);
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) press(options.pickItemKey);
		Window window = client.getWindow();
		MouseInvoker mouse = (MouseInvoker) client.mouse;
		// The bumpers go along the hotbar as the wheel does (up: the slot before).
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER)) mouse.v1zir$scroll(window.getHandle(), 0.0, 1.0);
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)) mouse.v1zir$scroll(window.getHandle(), 0.0, -1.0);
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_START)) {
			release(client);
			client.openGameMenu(false);
		} else if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_BACK)) {
			release(client);
			client.setScreen(new ClickGuiScreen());
		}
		cursorX = Double.NaN;
		// A D-pad button held as a screen opens (chat on the left) does not also act in it.
		heldArrow = arrow();
		heldFor = 0.0;
		nextRepeat = REPEAT_FIRST;
	}

	/** A binding pressed once, as a tap of its key. */
	private static void press(KeyBinding binding) {
		if (binding.isUnbound()) return;
		KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey()));
	}

	/** A binding held while the controller holds it; let go only if the controller was the one holding it. */
	private static void hold(KeyBinding binding, boolean held, boolean wasOurs) {
		if (held) binding.setPressed(true);
		else if (wasOurs) binding.setPressed(false);
	}

	/** A trigger as its binding: tapped as it goes down (one attack, one use), held while down; returns whether held. */
	private static boolean trigger(KeyBinding binding, int button, boolean wasOurs) {
		if (binding.isUnbound()) return false;
		InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
		if (pressed(button)) {
			KeyBinding.onKeyPressed(key);
			KeyBinding.setKeyPressed(key, true);
			return true;
		}
		if (released(button) && wasOurs) {
			KeyBinding.setKeyPressed(key, false);
			return false;
		}
		return wasOurs && down[button];
	}

	/** Lets go of everything the controller holds (a screen opens, the controller goes, the module turns off). */
	private static void release(MinecraftClient client) {
		GameOptions options = client.options;
		if (ownSprintKey) options.sprintKey.setPressed(false);
		if (ownListKey) options.playerListKey.setPressed(false);
		if (ownAttack && !options.attackKey.isUnbound()) KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(options.attackKey.getBoundKeyTranslationKey()), false);
		if (ownUse && !options.useKey.isUnbound()) KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(options.useKey.getBoundKeyTranslationKey()), false);
		ownSprintKey = ownListKey = ownAttack = ownUse = false;
		sprinting = false;
		if (clicking || rightClicking) {
			MouseInvoker mouse = (MouseInvoker) client.mouse;
			long handle = client.getWindow().getHandle();
			if (clicking) mouse.v1zir$mouseButton(handle, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0), GLFW.GLFW_RELEASE);
			if (rightClicking) mouse.v1zir$mouseButton(handle, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0), GLFW.GLFW_RELEASE);
			clicking = rightClicking = false;
		}
	}

	// ------------------------------------------------------------------ in screens

	private static void screen(MinecraftClient client, Screen screen, ControllerModule module, float dt) {
		// Whatever was held in the world is let go as a screen opens.
		if (ownSprintKey || ownListKey || ownAttack || ownUse) release(client);
		Window window = client.getWindow();
		long handle = window.getHandle();
		MouseInvoker mouse = (MouseInvoker) client.mouse;
		// The pointer follows the real mouse until the stick moves it.
		if (Double.isNaN(cursorX) || Math.abs(client.mouse.getX() - cursorX) > 2.0 || Math.abs(client.mouse.getY() - cursorY) > 2.0) {
			cursorX = client.mouse.getX();
			cursorY = client.mouse.getY();
		}
		float[] move = stick(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, module.deadzone.f());
		if (move[0] != 0f || move[1] != 0f) {
			// Faster the further it is pushed (squared), the same across the screen at any window size.
			double length = Math.sqrt(move[0] * move[0] + move[1] * move[1]);
			double pace = 1100.0 * module.cursorSpeed.f() * length * window.getHeight() / 1080.0 * dt;
			cursorX = Math.max(0.0, Math.min(window.getWidth() - 1, cursorX + move[0] * pace));
			cursorY = Math.max(0.0, Math.min(window.getHeight() - 1, cursorY + move[1] * pace));
			GLFW.glfwSetCursorPos(handle, cursorX, cursorY);
			mouse.v1zir$cursorPos(handle, cursorX, cursorY);
			focusMode = false;
		}
		// Cross: a click where the pointer is, or, after the D-pad, what is in focus.
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_A)) {
			if (focusMode) {
				key(screen, GLFW.GLFW_KEY_ENTER, 0);
			} else {
				mouse.v1zir$mouseButton(handle, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0), GLFW.GLFW_PRESS);
				clicking = true;
			}
		}
		if (released(GLFW.GLFW_GAMEPAD_BUTTON_A) && clicking) {
			mouse.v1zir$mouseButton(handle, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0), GLFW.GLFW_RELEASE);
			clicking = false;
		}
		// Square: a click with Shift (a stack to the other side of an inventory).
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_X)) {
			mouse.v1zir$mouseButton(handle, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT), GLFW.GLFW_PRESS);
			mouse.v1zir$mouseButton(handle, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT), GLFW.GLFW_RELEASE);
		}
		// L2: a right click.
		if (pressed(L2)) {
			mouse.v1zir$mouseButton(handle, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0), GLFW.GLFW_PRESS);
			rightClicking = true;
		}
		if (released(L2) && rightClicking) {
			mouse.v1zir$mouseButton(handle, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0), GLFW.GLFW_RELEASE);
			rightClicking = false;
		}
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_B) || pressed(GLFW.GLFW_GAMEPAD_BUTTON_START)) {
			key(screen, GLFW.GLFW_KEY_ESCAPE, 0);
			return;
		}
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_Y) && screen instanceof HandledScreen<?>) {
			screen.close();
			return;
		}
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER)) key(screen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)) key(screen, GLFW.GLFW_KEY_TAB, 0);
		arrows(screen, dt);
		// The right stick scrolls, faster the further it is pushed.
		float wheel = axis(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y, module.deadzone.f());
		scroll += -wheel * 10.0 * dt;
		while (Math.abs(scroll) >= 1.0) {
			double step = Math.signum(scroll);
			mouse.v1zir$scroll(handle, 0.0, step);
			scroll -= step;
		}
		if (wheel == 0f) scroll = 0.0;
	}

	/** The arrow key of the D-pad button held, -1 for none. */
	private static int arrow() {
		return down[GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP] ? GLFW.GLFW_KEY_UP : down[GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN] ? GLFW.GLFW_KEY_DOWN
			: down[GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT] ? GLFW.GLFW_KEY_LEFT : down[GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT] ? GLFW.GLFW_KEY_RIGHT : -1;
	}

	/** The D-pad as the arrow keys, repeating while held. */
	private static void arrows(Screen screen, float dt) {
		int arrow = arrow();
		if (arrow != heldArrow) {
			heldArrow = arrow;
			heldFor = 0.0;
			nextRepeat = REPEAT_FIRST;
			if (arrow >= 0) {
				focusMode = true;
				key(screen, arrow, 0);
			}
			return;
		}
		if (arrow < 0) return;
		heldFor += dt;
		if (heldFor >= nextRepeat) {
			nextRepeat += REPEAT_NEXT;
			key(screen, arrow, 0);
		}
	}

	private static void key(Screen screen, int key, int modifiers) {
		screen.keyPressed(new KeyInput(key, 0, modifiers));
	}
}
