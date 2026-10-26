package dev.v1zir.visuals.test;

import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.gui.menu.V1zirMainMenu;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.module.impl.effects.CrosshairModule;
import dev.v1zir.visuals.module.impl.effects.JumpCirclesModule;
import dev.v1zir.visuals.module.impl.hud.ArmorModule;
import dev.v1zir.visuals.module.impl.hud.ArrayListModule;
import dev.v1zir.visuals.module.impl.hud.ClockModule;
import dev.v1zir.visuals.module.impl.hud.CompassModule;
import dev.v1zir.visuals.module.impl.hud.CoordinatesModule;
import dev.v1zir.visuals.module.impl.hud.InventoryModule;
import dev.v1zir.visuals.module.impl.hud.ItemCounterModule;
import dev.v1zir.visuals.module.impl.hud.KeybindsModule;
import dev.v1zir.visuals.module.impl.hud.KeystrokesModule;
import dev.v1zir.visuals.module.impl.hud.NotificationsModule;
import dev.v1zir.visuals.module.impl.hud.PerformanceModule;
import dev.v1zir.visuals.module.impl.hud.PotionsModule;
import dev.v1zir.visuals.module.impl.hud.ServerInfoModule;
import dev.v1zir.visuals.module.impl.hud.SpeedModule;
import dev.v1zir.visuals.module.impl.hud.WatermarkModule;
import dev.v1zir.visuals.module.impl.visuals.BlockReflectionModule;
import dev.v1zir.visuals.module.impl.visuals.RainEffectsModule;
import dev.v1zir.visuals.module.impl.visuals.ShaderSkyModule;
import dev.v1zir.visuals.module.impl.visuals.WorldParticlesModule;
import dev.v1zir.visuals.render.sky.SkyPreset;
import dev.v1zir.visuals.util.Theme;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.profiler.log.MultiValueDebugSampleLog;
import org.lwjgl.glfw.GLFW;

import jdk.jfr.Recording;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Starts the real client and walks through everything the mod draws: the main menu, every sky
 * preset, the HUD, the 3D effects and the Click GUI tabs. Each step is saved as a screenshot to
 * build/run/clientGameTest/screenshots.
 */
public class V1zirClientGameTest implements FabricClientGameTest {
	private static final int BENCH_TICKS = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		resize(context, 1280, 720);
		mainMenu(context);
		vanillaMenuNeverShows(context);

		// Software rendering in CI is slow: load the world in a small window so the integrated
		// server gets enough CPU, then go back to a large window for the screenshots.
		resize(context, 854, 480);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			resize(context, 1280, 720);
			singleplayer.getServer().runCommand("effect give @a minecraft:speed 9999 1 true");
			singleplayer.getServer().runCommand("effect give @a minecraft:night_vision 9999 0 true");

			sky(context, singleplayer);
			walk(context, singleplayer);
			hud(context, singleplayer);
			hudElements(context, singleplayer);
			reflections(context, singleplayer);
			clickGui(context);

			// The test runner expects to end on the vanilla title screen.
			context.runOnClient(client -> MainMenuModule.suspended = true);
		}
	}

	private static void resize(ClientGameTestContext context, int width, int height) {
		context.getInput().resizeWindow(width, height);
		context.runOnClient(client -> {
			client.options.getGuiScale().setValue(2);
			client.onResolutionChanged();
		});
		context.waitTick();
	}

	private static void mainMenu(ClientGameTestContext context) {
		context.waitForScreen(V1zirMainMenu.class);
		// Intro: fade in, logo, then the buttons.
		for (int frame = 0; frame < 4; frame++) {
			context.waitTicks(frame == 0 ? 2 : 6);
			context.takeScreenshot("main-menu-intro-" + frame);
		}
		context.waitTicks(40);
		context.takeScreenshot("main-menu");
		bench(context, "main-menu");

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
		context.getInput().setCursorPos(640, 360);
	}

	/** Every way back to the title screen must land on our menu, never on the vanilla one. */
	private static void vanillaMenuNeverShows(ClientGameTestContext context) {
		context.setScreen(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			return new OptionsScreen(client.currentScreen, client.options);
		});
		context.waitTicks(10);
		context.takeScreenshot("options-from-menu");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		assertMainMenu(context, "closing Options with Escape");

		context.setScreen(() -> null);
		context.waitTicks(2);
		assertMainMenu(context, "setScreen(null) outside a world");

		context.setScreen(TitleScreen::new);
		context.waitTicks(2);
		assertMainMenu(context, "opening the vanilla TitleScreen");

		// First launch: vanilla opens the accessibility onboarding screen before the title screen.
		context.setScreen(() -> new AccessibilityOnboardingScreen(MinecraftClient.getInstance().options, () -> {}));
		context.waitTicks(2);
		assertMainMenu(context, "the first-launch accessibility onboarding screen");
	}

	private static void assertMainMenu(ClientGameTestContext context, String action) {
		String screen = context.computeOnClient(client -> client.currentScreen == null ? "null" : client.currentScreen.getClass().getName());
		if (!screen.equals(V1zirMainMenu.class.getName())) {
			context.takeScreenshot("unexpected-screen");
			throw new AssertionError("After " + action + " the screen is " + screen + " instead of the V1zir main menu");
		}
	}

	private static void sky(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		look(singleplayer, 0, -25);
		singleplayer.getServer().runCommand("time set 6000");
		// Let chunk meshing finish so the first measurement is not inflated.
		context.waitTicks(100);
		bench(context, "world-vanilla-sky");
		context.runOnClient(client -> ModuleManager.get(ShaderSkyModule.class).setEnabled(true, false));

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
		context.runOnClient(client -> ModuleManager.get(ShaderSkyModule.class).setEnabled(false, false));
		bench(context, "world-vanilla-sky-end");
		context.runOnClient(client -> ModuleManager.get(ShaderSkyModule.class).setEnabled(true, false));
	}

	private static void skyShot(ClientGameTestContext context, TestSingleplayerContext singleplayer, SkyPreset preset, int time, String name) {
		singleplayer.getServer().runCommand("time set " + time);
		context.runOnClient(client -> ModuleManager.get(ShaderSkyModule.class).preset.set(preset.title()));
		context.waitTicks(20);
		context.takeScreenshot(name);
		bench(context, name);
	}

	/** Frame time of a scene the test does not touch, for the CI benchmark table. */
	private static void bench(ClientGameTestContext context, String scene) {
		context.waitTicks(5);
		measure(context, scene, BENCH_TICKS, tick -> {
		});
	}

	/**
	 * Walking benchmark: sprints through fresh terrain while turning, once with every module off
	 * (the vanilla baseline) and once with the default modules and the Realistic sky. Both runs are
	 * profiled with Java Flight Recorder; CI prints the hottest methods of each.
	 */
	private static void walk(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("time set 6000");
		singleplayer.getServer().runCommand("effect give @a minecraft:speed 9999 4 true");
		look(singleplayer, 0, 0);
		Map<Module, Boolean> defaults = context.computeOnClient(client -> {
			Map<Module, Boolean> states = new HashMap<>();
			for (Module module : ModuleManager.all()) {
				states.put(module, module.isEnabled());
				if (module.canToggle()) module.setEnabled(false, false);
			}
			return states;
		});
		profiled("walk-vanilla", () -> walkFor(context, "walk-vanilla"));
		context.runOnClient(client -> {
			defaults.forEach((module, on) -> {
				if (module.canToggle()) module.setEnabled(on, false);
			});
			ModuleManager.get(ShaderSkyModule.class).preset.set(SkyPreset.REALISTIC.title());
		});
		profiled("walk-default", () -> walkFor(context, "walk-default"));
		moduleCosts(context, defaults);
		singleplayer.getServer().runCommand("effect give @a minecraft:speed 9999 1 true");
	}

	/**
	 * What each module costs: walks with exactly one module on (each sky preset separately), then
	 * with none again, and prints the frame time of every run. Runs alternate between walking
	 * forwards and backwards so they stay on terrain that is already generated.
	 */
	private static void moduleCosts(ClientGameTestContext context, Map<Module, Boolean> defaults) {
		List<Module> modules = context.computeOnClient(client -> {
			List<Module> list = new ArrayList<>();
			for (Module module : ModuleManager.all()) {
				if (module.canToggle() && module.category() != Category.SETTINGS) list.add(module);
			}
			return list;
		});
		List<Runnable> setups = new ArrayList<>();
		List<String> names = new ArrayList<>();
		setups.add(() -> {
		});
		names.add("cost-none");
		for (Module module : modules) {
			if (module instanceof ShaderSkyModule sky) {
				for (SkyPreset preset : SkyPreset.values()) {
					setups.add(() -> {
						sky.setEnabled(true, false);
						sky.preset.set(preset.title());
					});
					names.add("cost-sky-" + preset.name().toLowerCase(Locale.ROOT));
				}
			} else {
				setups.add(() -> module.setEnabled(true, false));
				names.add("cost-" + module.name().toLowerCase(Locale.ROOT).replace(' ', '-'));
			}
		}
		setups.add(() -> {
		});
		names.add("cost-none-again");
		for (int i = 0; i < setups.size(); i++) {
			Runnable setup = setups.get(i);
			context.runOnClient(client -> {
				for (Module module : ModuleManager.all()) {
					if (module.canToggle()) module.setEnabled(false, false);
				}
				setup.run();
			});
			context.waitTicks(10);
			walkFor(context, names.get(i), 60, i % 2 == 0);
		}
		context.runOnClient(client -> defaults.forEach((module, on) -> {
			if (module.canToggle()) module.setEnabled(on, false);
		}));
	}

	/** Sprints forward while slowly turning, then reports the frame time of the walk. */
	private static void walkFor(ClientGameTestContext context, String scene) {
		walkFor(context, scene, 120, true);
	}

	private static void walkFor(ClientGameTestContext context, String scene, int ticks, boolean forward) {
		Function<GameOptions, KeyBinding> key = forward ? options -> options.forwardKey : options -> options.backKey;
		context.getInput().holdKey(key);
		context.getInput().holdKey(options -> options.sprintKey);
		measure(context, scene, ticks, tick -> context.getInput().moveCursor(6.0, tick % 40 < 20 ? 1.5 : -1.5));
		context.getInput().releaseKey(key);
		context.getInput().releaseKey(options -> options.sprintKey);
	}

	/**
	 * Runs the given number of client ticks and prints the mean frame time, the matching FPS and
	 * the 99th percentile frame time. The client renders freely between game test ticks, so every
	 * frame is read from its own frame log (the one behind the F3 frame chart) after each tick;
	 * timing the ticks themselves could never resolve anything below 50 ms.
	 */
	private static void measure(ClientGameTestContext context, String scene, int ticks, IntConsumer step) {
		context.runOnClient(client -> frameLog(client).clear());
		List<Long> frames = new ArrayList<>();
		for (int i = 0; i < ticks; i++) {
			step.accept(i);
			context.waitTick();
			context.runOnClient(client -> {
				MultiValueDebugSampleLog log = frameLog(client);
				for (int f = 0; f < log.getLength(); f++) frames.add(log.get(f));
				log.clear();
			});
		}
		frames.sort(null);
		long total = 0L;
		for (long nanos : frames) total += nanos;
		double ms = frames.isEmpty() ? 0.0 : total / 1.0e6 / frames.size();
		double p99 = frames.isEmpty() ? 0.0 : frames.get(Math.min(frames.size() - 1, (int) (frames.size() * 0.99))) / 1.0e6;
		int fps = ms > 0.0 ? (int) Math.round(1000.0 / ms) : 0;
		System.out.printf(Locale.ROOT, "V1ZIR-BENCH %-26s %8.2f ms/frame  %4d fps  %8.2f ms p99  %5d frames%n", scene, ms, fps, p99, frames.size());
	}

	/** Durations of the frames since the last clear, oldest first, in nanoseconds. */
	private static MultiValueDebugSampleLog frameLog(MinecraftClient client) {
		return (MultiValueDebugSampleLog) field(client.getDebugHud(), DebugHud.class, "frameNanosLog");
	}

	/** Runs the body under a Java Flight Recorder recording saved as v1zir-<name>.jfr in the run directory. */
	private static void profiled(String name, Runnable body) {
		try (Recording recording = new Recording()) {
			recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(4));
			recording.enable("jdk.ObjectAllocationSample");
			recording.enable("jdk.GarbageCollection");
			recording.start();
			body.run();
			recording.stop();
			recording.dump(Path.of("v1zir-" + name + ".jfr"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
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
		bench(context, "world-hud-effects");

		// Custom UI: the restyled survival inventory, in every Theme style.
		context.setScreen(() -> new InventoryScreen(MinecraftClient.getInstance().player));
		for (Theme.Style style : new Theme.Style[]{Theme.Style.BLUR, Theme.Style.NORMAL}) {
			context.runOnClient(client -> ModuleManager.get(ThemeModule.class).style.set(style.title()));
			context.waitTicks(10);
			context.takeScreenshot("custom-inventory-" + style.name().toLowerCase());
		}
		context.runOnClient(client -> ModuleManager.get(ThemeModule.class).style.set(Theme.Style.BLUR.title()));
		context.setScreen(() -> null);
	}

	/** The info and widget HUD elements with live data (items, armour, walking), then their HUD editor previews. */
	private static void hudElements(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// The sword first, so it takes the selected hotbar slot; the rest spills into the main inventory.
		for (String command : List.of(
			"item replace entity @a weapon.mainhand with minecraft:netherite_sword[damage=600]",
			"item replace entity @a weapon.offhand with minecraft:shield",
			"item replace entity @a armor.head with minecraft:diamond_helmet",
			"item replace entity @a armor.chest with minecraft:diamond_chestplate[damage=420]",
			"item replace entity @a armor.legs with minecraft:diamond_leggings[damage=150]",
			"item replace entity @a armor.feet with minecraft:diamond_boots[damage=410]",
			"give @a minecraft:totem_of_undying 3", "give @a minecraft:ender_pearl 16", "give @a minecraft:golden_apple 12",
			"give @a minecraft:end_crystal 48", "give @a minecraft:arrow 64", "give @a minecraft:experience_bottle 20",
			"give @a minecraft:cobblestone 320", "give @a minecraft:oak_log 100", "give @a minecraft:torch 40")) {
			singleplayer.getServer().runCommand(command);
		}
		List<Class<? extends Module>> elements = List.of(CompassModule.class, ClockModule.class, SpeedModule.class,
			ServerInfoModule.class, PerformanceModule.class, KeystrokesModule.class, InventoryModule.class, ArmorModule.class,
			ItemCounterModule.class);
		context.runOnClient(client -> {
			for (Class<? extends Module> type : elements) ModuleManager.get(type).setEnabled(true, false);
		});
		look(singleplayer, 0, 20);
		context.getInput().holdKey(options -> options.forwardKey);
		context.waitTicks(40);
		context.takeScreenshot("hud-elements");
		context.getInput().releaseKey(options -> options.forwardKey);
		bench(context, "world-hud-elements");

		context.setScreen(ClickGuiScreen::hudEditor);
		context.waitTicks(30);
		context.takeScreenshot("hud-elements-editor");
		context.runOnClient(client -> {
			for (Class<? extends Module> type : elements) ModuleManager.get(type).setEnabled(false, false);
		});
		context.setScreen(() -> null);
		singleplayer.getServer().runCommand("clear @a");
	}

	/**
	 * Block Reflection in both modes over a pool with pillars of quartz, iron, gold, glass and a brick
	 * wall built in front of the player, then Rain Effects with soaked ground and splashes underfoot.
	 */
	private static void reflections(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("time set 3500");
		for (String command : List.of(
			"execute as @a at @s run fill ~-5 ~-1 ~4 ~5 ~-1 ~13 minecraft:water",
			"execute as @a at @s run fill ~-7 ~ ~15 ~-4 ~3 ~15 minecraft:quartz_block",
			"execute as @a at @s run fill ~4 ~ ~15 ~7 ~2 ~15 minecraft:iron_block",
			"execute as @a at @s run fill ~-1 ~ ~16 ~1 ~ ~16 minecraft:gold_block",
			"execute as @a at @s run fill ~-2 ~1 ~17 ~2 ~2 ~17 minecraft:glass",
			"execute as @a at @s run fill ~-12 ~ ~21 ~12 ~5 ~21 minecraft:bricks")) {
			singleplayer.getServer().runCommand(command);
		}
		look(singleplayer, 0, 22);
		context.waitTicks(20);
		bench(context, "reflection-off");
		// The block textures around the camera are complete after 16 ticks.
		context.runOnClient(client -> {
			BlockReflectionModule reflection = ModuleManager.get(BlockReflectionModule.class);
			reflection.mode.set(BlockReflectionModule.NORMAL);
			reflection.setEnabled(true, false);
		});
		context.waitTicks(30);
		context.takeScreenshot("reflection-normal");
		bench(context, "reflection-normal");
		context.runOnClient(client -> ModuleManager.get(BlockReflectionModule.class).mode.set(BlockReflectionModule.METAL));
		context.waitTicks(5);
		context.takeScreenshot("reflection-metal");
		bench(context, "reflection-metal");

		// Rain: the server ramps the rain up over 100 ticks, then the ground is soaked at once
		// instead of over the minute it takes in play.
		boolean rainWasOn = context.computeOnClient(client -> {
			ModuleManager.get(BlockReflectionModule.class).setEnabled(false, false);
			RainEffectsModule rain = ModuleManager.get(RainEffectsModule.class);
			boolean on = rain.isEnabled();
			rain.setEnabled(true, false);
			return on;
		});
		singleplayer.getServer().runCommand("weather rain");
		context.waitTicks(110);
		context.runOnClient(client -> {
			RainEffectsModule rain = ModuleManager.get(RainEffectsModule.class);
			for (String name : List.of("wet", "lastWet")) setFloat(rain, RainEffectsModule.class, name, 1f);
		});
		look(singleplayer, 0, 35);
		context.waitTicks(10);
		context.takeScreenshot("rain-wet");
		bench(context, "rain-wet");
		// Splashes underfoot: looking down, one frame just after the jump and one just after the landing.
		look(singleplayer, 0, 65);
		context.waitTicks(5);
		context.getInput().holdKeyFor(options -> options.jumpKey, 2);
		context.takeScreenshot("rain-splash-jump");
		context.waitTicks(11);
		context.takeScreenshot("rain-splash-landing");
		context.runOnClient(client -> {
			BlockReflectionModule reflection = ModuleManager.get(BlockReflectionModule.class);
			reflection.mode.set(BlockReflectionModule.NORMAL);
			reflection.setEnabled(true, false);
		});
		look(singleplayer, 0, 22);
		context.waitTicks(10);
		context.takeScreenshot("rain-reflection-normal");
		bench(context, "rain-reflection-normal");

		// Dry again before the next steps: the module is off while the rain fades out.
		context.runOnClient(client -> {
			ModuleManager.get(BlockReflectionModule.class).setEnabled(false, false);
			ModuleManager.get(RainEffectsModule.class).setEnabled(false, false);
		});
		singleplayer.getServer().runCommand("weather clear");
		context.waitTicks(110);
		context.runOnClient(client -> ModuleManager.get(RainEffectsModule.class).setEnabled(rainWasOn, false));
		singleplayer.getServer().runCommand("time set 6000");
	}

	private static void clickGui(ClientGameTestContext context) {
		context.runOnClient(client -> setStatic(ClickGuiScreen.class, "tab", Category.VISUALS.ordinal()));
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitTicks(5);
		boolean opened = context.computeOnClient(client -> client.currentScreen instanceof ClickGuiScreen);
		if (!opened) {
			System.err.println("[v1zir-gametest] Right Shift did not open the Click GUI, opening it directly");
			context.setScreen(ClickGuiScreen::new);
		}
		context.waitTicks(30);
		context.takeScreenshot("clickgui-modules");
		bench(context, "clickgui");

		context.getInput().typeChars("sky");
		context.waitTicks(10);
		context.takeScreenshot("clickgui-search");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

		// Every panel style of the Theme module.
		for (Theme.Style style : new Theme.Style[]{Theme.Style.NORMAL, Theme.Style.BLUR}) {
			context.runOnClient(client -> ModuleManager.get(ThemeModule.class).style.set(style.title()));
			context.waitTicks(8);
			if (style != Theme.Style.BLUR) context.takeScreenshot("clickgui-style-" + style.name().toLowerCase());
		}

		// One screenshot per tab; Settings holds the theme editor.
		for (Category category : new Category[]{Category.COMBAT, Category.SHADERS, Category.HUD, Category.SETTINGS}) {
			context.setScreen(() -> null);
			context.runOnClient(client -> setStatic(ClickGuiScreen.class, "tab", category.ordinal()));
			context.setScreen(ClickGuiScreen::new);
			context.waitTicks(30);
			context.takeScreenshot(category == Category.SETTINGS ? "clickgui-themes" : "clickgui-tab-" + category.name().toLowerCase(Locale.ROOT));
		}

		// A module's settings column, on the Shaders tab.
		context.setScreen(() -> null);
		context.runOnClient(client -> setStatic(ClickGuiScreen.class, "tab", Category.SHADERS.ordinal()));
		context.setScreen(ClickGuiScreen::new);
		context.waitTicks(10);
		context.runOnClient(client -> {
			if (client.currentScreen instanceof ClickGuiScreen screen) screen.openSettings(ModuleManager.get(ShaderSkyModule.class));
		});
		context.waitTicks(20);
		context.takeScreenshot("clickgui-module-settings");

		context.setScreen(() -> null);
		context.setScreen(ClickGuiScreen::hudEditor);
		context.waitTicks(30);
		context.takeScreenshot("clickgui-hud-editor");

		context.runOnClient(client -> setStatic(ClickGuiScreen.class, "tab", Category.VISUALS.ordinal()));
		context.setScreen(() -> null);
		animations(context);
	}

	/** Frames in the middle of the open and close animations, played at half speed so a frame lands inside them. */
	private static void animations(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
			gui.openAnimation.set(ClickGuiModule.OPEN_3D);
			gui.closeAnimation.set(ClickGuiModule.CLOSE_SHATTER);
			gui.animationSpeed.set(0.5);
		});
		context.setScreen(ClickGuiScreen::new);
		context.waitTicks(2);
		context.takeScreenshot("clickgui-open-3d");
		context.waitTicks(30);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(6);
		context.takeScreenshot("clickgui-close-3d-frost");
		context.waitTicks(50);
		context.runOnClient(client -> ModuleManager.get(ClickGuiModule.class).animationSpeed.set(1.0));
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

	private static void setFloat(Object target, Class<?> owner, String name, float value) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			field.setFloat(target, value);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Cannot write " + owner.getSimpleName() + "." + name, e);
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
