package dev.v1zir.visuals.test;

import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.gui.menu.V1zirMainMenu;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.module.impl.effects.CrosshairModule;
import dev.v1zir.visuals.module.impl.effects.JumpCirclesModule;
import dev.v1zir.visuals.module.impl.hud.ArrayListModule;
import dev.v1zir.visuals.module.impl.hud.CoordinatesModule;
import dev.v1zir.visuals.module.impl.hud.KeybindsModule;
import dev.v1zir.visuals.module.impl.hud.NotificationsModule;
import dev.v1zir.visuals.module.impl.hud.PotionsModule;
import dev.v1zir.visuals.module.impl.hud.WatermarkModule;
import dev.v1zir.visuals.module.impl.visuals.ShaderSkyModule;
import dev.v1zir.visuals.module.impl.visuals.WorldParticlesModule;
import dev.v1zir.visuals.render.sky.SkyPreset;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Starts the real client and walks through everything the mod draws: the main menu, every sky
 * preset, the HUD, the 3D effects and the Click GUI tabs. Each step is saved as a screenshot to
 * build/run/clientGameTest/screenshots.
 */
public class V1zirClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1600, 900);
		context.runOnClient(client -> {
			client.options.getGuiScale().setValue(2);
			client.onResolutionChanged();
		});

		mainMenu(context);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			singleplayer.getServer().runCommand("effect give @a minecraft:speed 9999 1 true");
			singleplayer.getServer().runCommand("effect give @a minecraft:night_vision 9999 0 true");

			sky(context, singleplayer);
			hud(context, singleplayer);
			clickGui(context);

			// The test runner expects to end on the vanilla title screen.
			context.runOnClient(client -> ModuleManager.get(MainMenuModule.class).setEnabled(false, false));
		}
	}

	private static void mainMenu(ClientGameTestContext context) {
		context.waitForScreen(V1zirMainMenu.class);
		context.waitTicks(60);
		context.takeScreenshot("main-menu");

		// Hover the first round button: it only highlights.
		double[] cursor = context.computeOnClient(client -> {
			List<?> buttons = (List<?>) field(client.currentScreen, V1zirMainMenu.class, "buttons");
			Object first = buttons.getFirst();
			double scale = client.getWindow().getScaleFactor();
			return new double[]{(float) field(first, first.getClass(), "cx") * scale, (float) field(first, first.getClass(), "cy") * scale};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(10);
		context.takeScreenshot("main-menu-hover");
		context.getInput().setCursorPos(800, 450);
	}

	private static void sky(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> ModuleManager.get(ShaderSkyModule.class).setEnabled(true, false));
		look(singleplayer, 0, -25);

		skyShot(context, singleplayer, SkyPreset.REALISTIC, 6000, "sky-realistic-noon");
		look(singleplayer, 90, -8);
		skyShot(context, singleplayer, SkyPreset.REALISTIC, 12300, "sky-realistic-sunset");
		look(singleplayer, 0, -40);
		skyShot(context, singleplayer, SkyPreset.REALISTIC, 18000, "sky-realistic-night");
		look(singleplayer, 0, -25);
		for (SkyPreset preset : List.of(SkyPreset.MAGMA, SkyPreset.AURORA, SkyPreset.NEBULA, SkyPreset.SUNSET,
			SkyPreset.INK, SkyPreset.SYNTHWAVE, SkyPreset.BLOOD_MOON)) {
			skyShot(context, singleplayer, preset, 18000, "sky-" + preset.name().toLowerCase().replace('_', '-'));
		}
	}

	private static void skyShot(ClientGameTestContext context, TestSingleplayerContext singleplayer, SkyPreset preset, int time, String name) {
		singleplayer.getServer().runCommand("time set " + time);
		context.runOnClient(client -> ModuleManager.get(ShaderSkyModule.class).preset.set(preset.title()));
		context.waitTicks(20);
		context.takeScreenshot(name);
	}

	private static void hud(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("time set 6000");
		context.runOnClient(client -> {
			ModuleManager.get(ShaderSkyModule.class).preset.set(SkyPreset.REALISTIC.title());
			for (Class<? extends Module> type : List.of(WatermarkModule.class, ArrayListModule.class, KeybindsModule.class,
				PotionsModule.class, CoordinatesModule.class, NotificationsModule.class, CrosshairModule.class,
				JumpCirclesModule.class, WorldParticlesModule.class)) {
				ModuleManager.get(type).setEnabled(true);
			}
		});
		look(singleplayer, 0, 20);
		context.waitTicks(20);
		context.getInput().holdKeyFor(options -> options.jumpKey, 2);
		context.waitTicks(14);
		context.takeScreenshot("hud-effects");
	}

	private static void clickGui(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitTicks(5);
		boolean opened = context.computeOnClient(client -> client.currentScreen instanceof ClickGuiScreen);
		if (!opened) {
			System.err.println("[v1zir-gametest] Right Shift did not open the Click GUI, opening it directly");
			context.setScreen(ClickGuiScreen::new);
		}
		context.waitTicks(30);
		context.takeScreenshot("clickgui-modules");

		context.getInput().typeChars("sky");
		context.waitTicks(10);
		context.takeScreenshot("clickgui-search");

		for (int tab : new int[]{2, 1}) {
			context.setScreen(() -> null);
			context.runOnClient(client -> setStatic(ClickGuiScreen.class, "tab", tab));
			context.setScreen(ClickGuiScreen::new);
			context.waitTicks(30);
			context.takeScreenshot(tab == 2 ? "clickgui-themes" : "clickgui-hud-editor");
		}

		context.runOnClient(client -> setStatic(ClickGuiScreen.class, "tab", 0));
		context.setScreen(() -> null);
	}

	private static void look(TestSingleplayerContext singleplayer, float yaw, float pitch) {
		singleplayer.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ " + yaw + " " + pitch);
	}

	private static Object field(Object target, Class<?> owner, String name) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Cannot read " + owner.getSimpleName() + "." + name, e);
		}
	}

	private static void setStatic(Class<?> owner, String name, int value) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			field.setInt(null, value);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Cannot write " + owner.getSimpleName() + "." + name, e);
		}
	}
}
