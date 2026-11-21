package dev.v1zir.visuals.test;

import dev.v1zir.visuals.account.Accounts;
import dev.v1zir.visuals.audio.AudioFiles;
import dev.v1zir.visuals.audio.Pcm;
import dev.v1zir.visuals.chat.ChatService;
import dev.v1zir.visuals.chat.Conversation;
import dev.v1zir.visuals.chat.LoopbackTransport;
import dev.v1zir.visuals.chat.Message;
import dev.v1zir.visuals.chat.Messages;
import dev.v1zir.visuals.chat.MqttTransport;
import dev.v1zir.visuals.compat.voicechat.MusicFeed;
import dev.v1zir.visuals.controller.Controller;
import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.gui.menu.ConsoleMenu;
import dev.v1zir.visuals.gui.menu.LockScreen;
import dev.v1zir.visuals.gui.menu.MainMenus;
import dev.v1zir.visuals.gui.menu.MenuShots;
import dev.v1zir.visuals.gui.menu.V1zirMainMenu;
import dev.v1zir.visuals.gui.messages.MessagesScreen;
import dev.v1zir.visuals.gui.messages.MessagesUi;
import dev.v1zir.visuals.media.Media;
import dev.v1zir.visuals.media.NowPlaying;
import dev.v1zir.visuals.media.Track;
import dev.v1zir.visuals.mixin.HandledScreenAccessor;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.module.impl.cosmetics.CosmeticsModule;
import dev.v1zir.visuals.module.impl.effects.ChamsModule;
import dev.v1zir.visuals.module.impl.effects.ChinaHatModule;
import dev.v1zir.visuals.module.impl.effects.CrosshairModule;
import dev.v1zir.visuals.module.impl.effects.EspModule;
import dev.v1zir.visuals.module.impl.effects.HealthIndicatorModule;
import dev.v1zir.visuals.module.impl.effects.HitSoundModule;
import dev.v1zir.visuals.module.impl.effects.JumpCirclesModule;
import dev.v1zir.visuals.module.impl.effects.KillEffectsModule;
import dev.v1zir.visuals.module.impl.effects.TargetEspModule;
import dev.v1zir.visuals.module.impl.hud.ArmorModule;
import dev.v1zir.visuals.module.impl.hud.ArrayListModule;
import dev.v1zir.visuals.module.impl.hud.ClockModule;
import dev.v1zir.visuals.module.impl.hud.CompassModule;
import dev.v1zir.visuals.module.impl.hud.CoordinatesModule;
import dev.v1zir.visuals.module.impl.hud.CustomIndicatorsModule;
import dev.v1zir.visuals.module.impl.hud.InventoryModule;
import dev.v1zir.visuals.module.impl.hud.ItemCounterModule;
import dev.v1zir.visuals.module.impl.hud.KeybindsModule;
import dev.v1zir.visuals.module.impl.hud.KeystrokesModule;
import dev.v1zir.visuals.module.impl.hud.NotificationsModule;
import dev.v1zir.visuals.module.impl.hud.PerformanceModule;
import dev.v1zir.visuals.module.impl.hud.PotionsModule;
import dev.v1zir.visuals.module.impl.hud.ServerInfoModule;
import dev.v1zir.visuals.module.impl.hud.SpeedModule;
import dev.v1zir.visuals.module.impl.hud.SpotifyModule;
import dev.v1zir.visuals.module.impl.hud.TargetHudModule;
import dev.v1zir.visuals.module.impl.hud.WatermarkModule;
import dev.v1zir.visuals.module.impl.knives.KnivesModule;
import dev.v1zir.visuals.module.impl.visuals.BlockReflectionModule;
import dev.v1zir.visuals.module.impl.visuals.CinematicModule;
import dev.v1zir.visuals.module.impl.visuals.ColorGradingModule;
import dev.v1zir.visuals.module.impl.visuals.FreeLookModule;
import dev.v1zir.visuals.module.impl.visuals.GodRaysModule;
import dev.v1zir.visuals.module.impl.visuals.HandChamsModule;
import dev.v1zir.visuals.module.impl.visuals.MistModule;
import dev.v1zir.visuals.module.impl.visuals.MotionBlurModule;
import dev.v1zir.visuals.module.impl.visuals.NametagsModule;
import dev.v1zir.visuals.module.impl.visuals.RainEffectsModule;
import dev.v1zir.visuals.module.impl.visuals.ShaderSkyModule;
import dev.v1zir.visuals.module.impl.visuals.ShootingStarsModule;
import dev.v1zir.visuals.module.impl.visuals.VibeModule;
import dev.v1zir.visuals.module.impl.visuals.WetWorldModule;
import dev.v1zir.visuals.module.impl.visuals.WorldParticlesModule;
import dev.v1zir.visuals.module.impl.visuals.ZoomModule;
import dev.v1zir.visuals.optimize.GpuOption;
import dev.v1zir.visuals.pack.PackMixer;
import dev.v1zir.visuals.render.knife.KnifeViewModel;
import dev.v1zir.visuals.render.sky.SkyPreset;
import dev.v1zir.visuals.render.world.Effects;
import dev.v1zir.visuals.util.Theme;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Uuids;
import net.minecraft.util.profiler.log.MultiValueDebugSampleLog;
import org.lwjgl.glfw.GLFW;

import jdk.jfr.Recording;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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
		// CI's second client run has Sodium in it, for its Video Settings only.
		if (System.getenv("V1ZIR_SODIUM") != null) {
			sodium(context);
			return;
		}
		// Pictures for the PS5 style menu are taken when the test asks, not at whatever moment comes.
		context.runOnClient(client -> MenuShots.auto = false);
		resize(context, 1280, 720);
		mainMenu(context);
		vanillaMenuNeverShows(context);
		loadingAndPs5(context);
		audio(context);

		// Software rendering in CI is slow: load the world in a small window so the integrated
		// server gets enough CPU, then go back to a large window for the screenshots.
		resize(context, 854, 480);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			resize(context, 1280, 720);
			singleplayer.getServer().runCommand("effect give @a minecraft:speed 9999 1 true");
			singleplayer.getServer().runCommand("effect give @a minecraft:night_vision 9999 0 true");
			// The weather cycle runs: rain that starts by chance halfway through darkens every later shot.
			singleplayer.getServer().runCommand("weather clear 1000000");

			sky(context, singleplayer);
			walk(context, singleplayer);
			hud(context, singleplayer);
			dynamicIsland(context);
			hudElements(context, singleplayer);
			spotify(context);
			targetHud(context, singleplayer);
			targetEsp(context, singleplayer);
			customIndicators(context, singleplayer);
			healthIndicator(context, singleplayer);
			chamsAndEsp(context, singleplayer);
			killEffects(context, singleplayer);
			shootingStars(context, singleplayer);
			motionBlur(context, singleplayer);
			atmosphere(context, singleplayer);
			vibe(context, singleplayer);
			nametags(context);
			ps5InGame(context);
			wetWorld(context, singleplayer);
			handChams(context, singleplayer);
			knives(context, singleplayer);
			cosmetics(context, singleplayer);
			packs(context, singleplayer);
			reflections(context, singleplayer);
			farWater(context, singleplayer);
			clickGui(context);
			clickGuiFavourites(context);
			messages(context);
			controllerGui(context, singleplayer);

			// The test runner expects to end on the vanilla title screen.
			context.runOnClient(client -> MainMenuModule.suspended = true);
		}
		lastPlayed(context);
	}

	/**
	 * Back from the world, the lock screen's widget and the menu's Continue pill offer it again, with its picture and
	 * its name; the test then ends on the vanilla title screen, as the runner expects.
	 */
	private static void lastPlayed(ClientGameTestContext context) {
		context.runOnClient(client -> {
			MainMenuModule.suspended = false;
			LockScreen.lock();
			client.setScreen(new V1zirMainMenu());
		});
		context.waitTicks(40);
		context.takeScreenshot("lock-screen-last-played");
		context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
		context.waitTicks(40);
		context.takeScreenshot("main-menu-continue");
		// A message while the menu is open: its banner, and the count on the Messages button in the corner.
		context.runOnClient(client -> {
			if (bot != null) bot.send(bot.open(client.getSession().getUsername()), "Jestem na serwerze, wbijaj!");
		});
		context.waitTicks(20);
		context.takeScreenshot("main-menu-messages");
		// The PS5 style menu now that a world was played: its tile comes after home, and home offers to continue it.
		context.runOnClient(client -> {
			ModuleManager.get(ThemeModule.class).styleTheme.set("PS5");
			client.setScreen(MainMenus.create());
		});
		context.waitTicks(20);
		// The menu remembers the tab shown last (Multiplayer, from the start of the test): back to the worlds.
		if (context.computeOnClient(client -> client.currentScreen instanceof ConsoleMenu menu && menu.tab() == 1)) {
			context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		}
		context.waitTicks(20);
		context.takeScreenshot("main-menu-ps5-worlds");
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		// Its picture, taken in the world, read and faded in behind it.
		context.waitTicks(40);
		context.takeScreenshot("main-menu-ps5-world");
		context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		context.waitTicks(10);
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(15);
		context.takeScreenshot("main-menu-ps5-popup");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(10);
		ps5TopBar(context);
		context.runOnClient(client -> ModuleManager.get(ThemeModule.class).styleTheme.reset());
		context.runOnClient(client -> {
			if (bot != null) bot.stop();
			if (kuba != null) kuba.stop();
			Messages.useTransport(null);
		});
		context.runOnClient(client -> {
			MainMenuModule.suspended = true;
			client.setScreen(new TitleScreen());
		});
		context.waitForScreen(TitleScreen.class);
	}

	/** A small picture for a profile: a ring on a gradient, as a PNG. */
	private static byte[] testPicture() {
		try (NativeImage image = new NativeImage(NativeImage.Format.RGBA, 64, 64, false)) {
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					double d = Math.hypot(x - 31.5, y - 31.5);
					int argb = Math.abs(d - 17.0) < 4.0 ? 0xFFFFFFFF : 0xFF000000 | (90 + x * 2) << 16 | (50 + y * 2) << 8 | 230 - x;
					image.setColorArgb(x, y, argb);
				}
			}
			Path temp = Files.createTempFile("v1zir-picture", ".png");
			image.writeTo(temp);
			byte[] bytes = Files.readAllBytes(temp);
			Files.delete(temp);
			return bytes;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The in-game relay of the Messages steps and the two players on it besides this one. */
	private static final LoopbackTransport.Bus BUS = new LoopbackTransport.Bus();
	private static ChatService bot;
	private static ChatService kuba;

	/**
	 * Messages over an in-game relay, with two more players on it: Kuba_PL's message and V1zirBot's come as banners in
	 * game, then over the pause menu with its Messages button; a click on V1zirBot's banner opens that conversation at the
	 * message; a reply goes out and comes back delivered, then read; V1zirBot types; the field for a new conversation.
	 * Then the real relay is tried once (the result is logged, it does not fail the test).
	 */
	private static void messages(ClientGameTestContext context) {
		Path dir = context.computeOnClient(client -> client.runDirectory.toPath().resolve("v1zir-test-messages"));
		context.runOnClient(client -> Messages.useTransport(() -> new LoopbackTransport(BUS)));
		context.waitTicks(5);
		String me = context.computeOnClient(client -> client.getSession().getUsername());
		context.runOnClient(client -> {
			bot = Messages.peer("V1zirBot", new LoopbackTransport(BUS), dir, (conversation, message) -> {});
			bot.start(true);
			kuba = Messages.peer("Kuba_PL", new LoopbackTransport(BUS), dir, (conversation, message) -> {});
			kuba.start(true);
		});
		context.waitTicks(10);
		// Kuba_PL has a name and a picture of his own for Messages: they go with his message, sealed like it.
		byte[] picture = context.computeOnClient(client -> testPicture());
		context.runOnClient(client -> kuba.setProfile("Kuba", picture));
		context.runOnClient(client -> kuba.send(kuba.open(me), "gg wp, rewanż jutro o 18?"));
		context.waitTicks(10);
		context.runOnClient(client -> bot.send(bot.open(me), "Hej! Wbijasz dziś na serwer? Mam nowy preset nieba"));
		context.waitTicks(20);
		context.takeScreenshot("messages-banner");

		// The pause menu: the Messages button on the left with the unread count, the banners over it.
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(12);
		context.takeScreenshot("messages-pause");

		// A click on the newest banner (top right) opens its conversation at that message.
		context.getInput().setCursorPos(1060, 56);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(15);
		context.takeScreenshot("messages-from-banner");
		String opened = context.computeOnClient(client -> client.currentScreen instanceof MessagesScreen screen && screen.selected() != null
			? screen.selected().contact().name() : String.valueOf(client.currentScreen));
		if (!"V1zirBot".equals(opened)) throw new AssertionError("The banner opened " + opened + " instead of V1zirBot's conversation");

		context.runOnClient(client -> ((MessagesScreen) client.currentScreen).sendText("Jasne, za 5 minut jestem. Który serwer?"));
		context.waitTicks(10);
		context.runOnClient(client -> bot.viewing(bot.find(me)));
		context.waitTicks(10);
		context.runOnClient(client -> bot.typing(bot.find(me)));
		context.waitTicks(6);
		context.takeScreenshot("messages-conversation");
		int state = context.computeOnClient(client -> {
			Conversation conversation = Messages.service().find("V1zirBot");
			Message last = conversation == null ? null : conversation.last();
			return last == null ? -1 : last.state();
		});
		if (state != Message.READ) throw new AssertionError("The reply should show as read, its state is " + state);
		String kubaShown = context.computeOnClient(client -> {
			Conversation conversation = Messages.service().find("Kuba_PL");
			return conversation == null ? "none" : conversation.contact().displayName() + (conversation.contact().avatar() != null ? " +picture" : "");
		});
		if (!"Kuba +picture".equals(kubaShown)) throw new AssertionError("Kuba_PL's name and picture should have come, they are " + kubaShown);

		// This account's own profile: the editor, a name typed and saved; Kuba_PL's game gets it.
		context.runOnClient(client -> ((MessagesScreen) client.currentScreen).editProfile());
		context.waitTicks(4);
		context.getInput().typeChars("Tester 01");
		context.waitTicks(10);
		context.takeScreenshot("messages-profile");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(10);
		String mine = context.computeOnClient(client -> {
			Conversation conversation = kuba.find(me);
			return conversation == null ? "none" : conversation.contact().displayName();
		});
		if (!"Tester 01".equals(mine)) throw new AssertionError("Kuba_PL's game should show this account as Tester 01, it shows " + mine);

		context.runOnClient(client -> bot.send(bot.find(me), "Play.v1zir.pl, czekam przy spawnie"));
		context.waitTicks(10);
		context.runOnClient(client -> ((MessagesScreen) client.currentScreen).newConversation());
		context.getInput().typeChars("Steve_");
		context.waitTicks(6);
		context.takeScreenshot("messages-new");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(3);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(5);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(10);
		relayCheck(context);
	}

	/**
	 * Two throwaway players on the real relay (EMQX, over TLS): one writes, the other must get it. Logged as
	 * V1ZIR-RELAY-CHECK; the network of a CI runner is not the mod's to fail the test over.
	 */
	private static void relayCheck(ClientGameTestContext context) {
		Path dir = context.computeOnClient(client -> client.runDirectory.toPath().resolve("v1zir-relay-check"));
		String tag = Long.toHexString(System.nanoTime() & 0xFFFFFFFL);
		List<Message> received = new java.util.concurrent.CopyOnWriteArrayList<>();
		ChatService[] pair = context.computeOnClient(client -> new ChatService[]{
			Messages.peer("cia" + tag, new MqttTransport("broker.emqx.io", 8883, true), dir, (conversation, message) -> {}),
			Messages.peer("cib" + tag, new MqttTransport("broker.emqx.io", 8883, true), dir, (conversation, message) -> received.add(message))});
		context.runOnClient(client -> {
			pair[0].start(false);
			pair[1].start(false);
		});
		long started = System.currentTimeMillis();
		int ticks = 0;
		while (ticks < 400 && !context.computeOnClient(client -> pair[0].status() == ChatService.Status.ONLINE && pair[1].status() == ChatService.Status.ONLINE)) {
			context.waitTick();
			ticks++;
		}
		String result;
		if (ticks >= 400) {
			result = "failed: no connection (" + context.computeOnClient(client -> pair[0].status() + " " + pair[0].error()) + ")";
		} else {
			long connected = System.currentTimeMillis() - started;
			context.waitTicks(10);
			context.runOnClient(client -> pair[0].send(pair[0].open("cib" + tag), "relay check " + tag));
			ticks = 0;
			while (ticks < 400 && received.isEmpty()) {
				context.waitTick();
				ticks++;
			}
			result = received.isEmpty() ? "failed: nothing arrived" : "ok, connected in " + connected + " ms, message in " + ticks * 50 + " ms";
		}
		System.out.println("V1ZIR-RELAY-CHECK " + result);
		context.runOnClient(client -> {
			pair[0].stop();
			pair[1].stop();
		});
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
		// The menu opens on the lock screen. Drag it part of the way up and hold it there, let go below the
		// threshold so it springs back, then open it with a key.
		context.waitTicks(40);
		context.takeScreenshot("lock-screen");
		context.getInput().setCursorPos(640, 600);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		for (int step = 0; step < 4; step++) {
			context.getInput().moveCursor(0, -30);
			context.waitTick();
		}
		context.takeScreenshot("lock-screen-drag");
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(20);

		// Holding the mouse button still on an empty spot opens the lock screen's wallpaper picker; the right arrow
		// moves to the next card (the test input has no mouse wheel) and Escape cancels.
		openWallpaperPicker(context, 200, 200, "wallpaper-edit-lock");
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		context.waitTicks(15);
		context.takeScreenshot("wallpaper-edit-lock-next");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(20);
		context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);

		// Intro: the lock screen slides away and the menu comes up from below.
		for (int frame = 0; frame < 4; frame++) {
			context.waitTicks(frame == 0 ? 1 : 3);
			context.takeScreenshot("main-menu-intro-" + frame);
		}
		context.waitTicks(40);
		context.takeScreenshot("main-menu");
		bench(context, "main-menu");

		// Hover the first round button: its frosted fill lights up.
		double[] cursor = context.computeOnClient(client -> {
			List<?> buttons = (List<?>) field(client.currentScreen, V1zirMainMenu.class, "buttons");
			Object first = buttons.getFirst();
			double scale = client.getWindow().getScaleFactor();
			return new double[]{(float) field(first, first.getClass(), "cx") * scale, (float) field(first, first.getClass(), "cy") * scale};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(10);
		context.takeScreenshot("main-menu-hover");

		// The same on the home screen, below the buttons.
		openWallpaperPicker(context, 200, 560, "wallpaper-edit-home");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(20);

		// A swipe up from an empty spot brings the lock screen back: it rises from the bottom with the cursor.
		context.getInput().setCursorPos(200, 660);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		for (int step = 0; step < 4; step++) {
			context.getInput().moveCursor(0, -45);
			context.waitTick();
		}
		context.takeScreenshot("main-menu-swipe-up");
		for (int step = 0; step < 4; step++) {
			context.getInput().moveCursor(0, -45);
			context.waitTick();
		}
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(30);
		context.takeScreenshot("main-menu-relocked");

		// Locked again: hovering the multiplayer quick action shows its name, and a click unlocks straight into
		// the multiplayer screen, which goes back to the home screen.
		context.runOnClient(client -> {
			LockScreen.lock();
			client.setScreen(new V1zirMainMenu());
		});
		context.waitTicks(40);
		double[] multiplayer = context.computeOnClient(client -> {
			Object lock = field(client.currentScreen, V1zirMainMenu.class, "lock");
			Object widgets = field(lock, LockScreen.class, "widgets");
			float[] box = (float[]) field(widgets, widgets.getClass(), "box");
			int i = (int) field(null, widgets.getClass(), "MULTI") * 4;
			double scale = client.getWindow().getScaleFactor();
			return new double[]{(box[i] + box[i + 2] / 2f) * scale, (box[i + 1] + box[i + 3] / 2f) * scale};
		});
		context.getInput().setCursorPos(multiplayer[0], multiplayer[1]);
		context.waitTicks(10);
		context.takeScreenshot("lock-screen-action");
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitForScreen(MultiplayerScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("lock-screen-multiplayer-entering");
		context.waitTicks(25);
		context.takeScreenshot("lock-screen-multiplayer");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(V1zirMainMenu.class);
		context.waitTicks(10);
		context.getInput().setCursorPos(640, 360);
		accounts(context);
	}

	/**
	 * The name pill opens the accounts panel. A nickname typed into it becomes an offline account the game plays as (its
	 * name, the UUID offline servers give it, no chat keys), and the cross on its row forgets it and goes back to the
	 * launcher's account.
	 */
	private static void accounts(ClientGameTestContext context) {
		String launcher = context.computeOnClient(client -> client.getSession().getUsername());
		double[] pill = context.computeOnClient(client -> {
			double scale = client.getWindow().getScaleFactor();
			float x = (float) field(client.currentScreen, V1zirMainMenu.class, "pillX");
			float y = (float) field(client.currentScreen, V1zirMainMenu.class, "pillY");
			float w = (float) field(client.currentScreen, V1zirMainMenu.class, "pillW");
			float h = (float) field(client.currentScreen, V1zirMainMenu.class, "pillH");
			return new double[]{(x + w / 2f) * scale, (y + h / 2f) * scale};
		});
		context.getInput().setCursorPos(pill[0], pill[1]);
		context.waitTicks(8);
		context.takeScreenshot("main-menu-pill-hover");
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(15);
		context.takeScreenshot("main-menu-accounts");

		context.getInput().typeChars("V1zirTester");
		context.waitTicks(5);
		context.takeScreenshot("main-menu-accounts-typing");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(15);
		context.runOnClient(client -> {
			String name = client.getSession().getUsername();
			if (!"V1zirTester".equals(name)) throw new AssertionError("Playing as " + name + " after adding V1zirTester");
			if (!Uuids.getOfflinePlayerUuid("V1zirTester").equals(client.getSession().getUuidOrNull())) {
				throw new AssertionError("The offline account does not have its offline UUID");
			}
			if (client.getProfileKeys() != ProfileKeys.MISSING) throw new AssertionError("The offline account kept the launcher's chat keys");
			if (client.getGameProfile() == null) throw new AssertionError("No game profile for the offline account");
		});
		context.takeScreenshot("main-menu-accounts-added");

		// Its row is the second: hovered, the cross shows at its right end, and a click there forgets it.
		double[] cross = context.computeOnClient(client -> {
			Object panel = field(client.currentScreen, V1zirMainMenu.class, "accounts");
			Class<?> type = panel.getClass();
			float x = (float) field(panel, type, "x");
			float listY = (float) field(panel, type, "listY");
			float width = (float) field(null, type, "WIDTH");
			float row = (float) field(null, type, "ROW");
			double scale = client.getWindow().getScaleFactor();
			return new double[]{(x + width - 15f) * scale, (listY + row + (row - 2f) / 2f) * scale};
		});
		context.getInput().setCursorPos(cross[0], cross[1]);
		context.waitTicks(8);
		context.takeScreenshot("main-menu-accounts-remove");
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		context.runOnClient(client -> {
			String name = client.getSession().getUsername();
			if (!launcher.equals(name)) throw new AssertionError("Playing as " + name + " after forgetting V1zirTester, not as " + launcher);
			if (Accounts.all().size() != 1) throw new AssertionError("Accounts left after forgetting V1zirTester: " + Accounts.all());
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(10);
		context.getInput().setCursorPos(640, 360);
	}

	/** Holds the left mouse button still at (x, y) in window pixels past the three seconds and shoots the picker. */
	private static void openWallpaperPicker(ClientGameTestContext context, double x, double y, String name) {
		context.getInput().setCursorPos(x, y);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		// Ticks follow real time, at most one per frame, so this is at least 3.3 seconds.
		context.waitTicks(66);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(20);
		context.takeScreenshot(name);
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

	/**
	 * The loading screen while the resource packs reload, in the client's style and then in the PS5 style, and the PS5
	 * style menu: home picked, then the next tile, then the Multiplayer tab with two servers in the list.
	 */
	private static void loadingAndPs5(ClientGameTestContext context) {
		reloadShot(context, "loading-screen");
		context.runOnClient(client -> {
			ServerList list = new ServerList(client);
			list.loadFile();
			// A real server first: its icon comes with its ping, and the menu shows it enlarged.
			list.add(new ServerInfo("CraftCube", "craftcube.pl", ServerInfo.ServerType.OTHER), false);
			list.add(new ServerInfo("V1zir Test", "play.example.net", ServerInfo.ServerType.OTHER), false);
			list.add(new ServerInfo("Lobby", "lobby.example.net:25566", ServerInfo.ServerType.OTHER), false);
			list.saveFile();
			ModuleManager.get(ThemeModule.class).styleTheme.set("PS5");
		});
		context.setScreen(MainMenus::create);
		context.waitTicks(40);
		String screen = context.computeOnClient(client -> client.currentScreen == null ? "null" : client.currentScreen.getClass().getName());
		if (!screen.equals(ConsoleMenu.class.getName())) throw new AssertionError("With the PS5 style theme the menu is " + screen);
		context.takeScreenshot("main-menu-ps5");
		bench(context, "main-menu-ps5");
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		context.waitTicks(15);
		context.takeScreenshot("main-menu-ps5-next");
		context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		context.waitTicks(10);
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		context.waitTicks(15);
		context.takeScreenshot("main-menu-ps5-multiplayer");
		serverIcon(context);
		reloadShot(context, "loading-screen-ps5");
		context.runOnClient(client -> {
			ServerList list = new ServerList(client);
			list.loadFile();
			while (list.size() > 0) list.remove(list.get(0));
			list.saveFile();
			ModuleManager.get(ThemeModule.class).styleTheme.reset();
		});
		context.setScreen(MainMenus::create);
		context.waitTicks(10);
		assertMainMenu(context, "going back to the Normal style theme");
	}

	/**
	 * The first server's icon as its ping brings it: the menu saves it to the list (so this waits for it there, half a
	 * minute at most, as the server may be slow or away), the log gets the icon itself (V1ZIR-FAVICON, Base64 PNG) and a
	 * picture shows its tile and the large icon of the picked tile, enlarged. Not a failure when the server is away.
	 */
	private static void serverIcon(ClientGameTestContext context) {
		boolean came = false;
		for (int i = 0; i < 30 && !came; i++) {
			context.waitTicks(20);
			came = context.computeOnClient(client -> {
				ServerList list = new ServerList(client);
				list.loadFile();
				return list.size() > 0 && list.get(0).getFavicon() != null;
			});
		}
		String favicon = context.computeOnClient(client -> {
			ServerList list = new ServerList(client);
			list.loadFile();
			byte[] bytes = list.size() > 0 ? list.get(0).getFavicon() : null;
			return bytes == null ? "none" : java.util.Base64.getEncoder().encodeToString(bytes);
		});
		System.out.println("V1ZIR-FAVICON " + favicon);
		// Enlarged off the render thread, then faded in.
		context.waitTicks(30);
		context.takeScreenshot("main-menu-ps5-server-icon");
		bench(context, "main-menu-ps5-server");
	}

	/**
	 * The audio of Hit Sound and Voice Chat Music decodes: a built-in sample whole and streamed as voice chat audio
	 * (48 kHz mono), then the same as a WAV file; and a hit sound plays without an error, sound device or not.
	 */
	private static void audio(ClientGameTestContext context) {
		String result = context.computeOnClient(client -> {
			try (InputStream in = V1zirClientGameTest.class.getResourceAsStream("/assets/v1zir/sounds/hit/bubble.ogg")) {
				byte[] bytes = in.readAllBytes();
				Pcm pcm = AudioFiles.decode(bytes, "bubble.ogg");
				Path ogg = Files.createTempFile("v1zir-audio", ".ogg");
				Files.write(ogg, bytes);
				int streamed = streamed(ogg);
				// The same sound as a WAV file, through Java's own audio.
				Path wav = Files.createTempFile("v1zir-audio", ".wav");
				byte[] raw = new byte[pcm.samples().length * 2];
				for (int i = 0; i < pcm.samples().length; i++) {
					raw[i * 2] = (byte) pcm.samples()[i];
					raw[i * 2 + 1] = (byte) (pcm.samples()[i] >> 8);
				}
				javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(pcm.rate(), 16, pcm.channels(), true, false);
				try (javax.sound.sampled.AudioInputStream stream = new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(raw), format,
					pcm.samples().length / pcm.channels())) {
					javax.sound.sampled.AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, wav.toFile());
				}
				Pcm fromWav = AudioFiles.decode(wav);
				int streamedWav = streamed(wav);
				Files.deleteIfExists(ogg);
				Files.deleteIfExists(wav);
				// An MP3 file (a short tone made for the test): whole, and streamed.
				Path mp3 = Files.createTempFile("v1zir-audio", ".mp3");
				try (InputStream tone = V1zirClientGameTest.class.getResourceAsStream("/v1zir-test-tone.mp3")) {
					Files.write(mp3, tone.readAllBytes());
				}
				Pcm fromMp3 = AudioFiles.decode(mp3);
				int streamedMp3 = streamed(mp3);
				Files.deleteIfExists(mp3);
				return pcm.samples().length + " " + pcm.rate() + " " + pcm.channels() + " " + streamed + " " + fromWav.samples().length + " " + streamedWav
					+ " " + fromMp3.samples().length + " " + fromMp3.rate() + " " + fromMp3.channels() + " " + streamedMp3;
			} catch (IOException | RuntimeException e) {
				return "failed " + e;
			}
		});
		System.out.println("V1ZIR-AUDIO samples, rate, channels, streamed at 48 kHz, WAV samples, WAV streamed, MP3 samples, rate, channels, streamed: " + result);
		if (result.startsWith("failed")) throw new AssertionError("The audio did not decode: " + result);
		String[] parts = result.split(" ");
		int samples = Integer.parseInt(parts[0]);
		int rate = Integer.parseInt(parts[1]);
		int streamed = Integer.parseInt(parts[3]);
		int expected = Math.round(samples / (float) Integer.parseInt(parts[2]) * MusicFeed.RATE / rate);
		if (samples < 5000 || Math.abs(streamed - expected) > 64 || Integer.parseInt(parts[4]) != samples || Math.abs(Integer.parseInt(parts[5]) - expected) > 64) {
			throw new AssertionError("The audio decoded wrong: " + result + " (streamed should be about " + expected + ")");
		}
		// The tone is half a second at 44.1 kHz, mono; the encoder adds a little silence at the ends.
		int mp3 = Integer.parseInt(parts[6]);
		int mp3Streamed = Integer.parseInt(parts[9]);
		if (Integer.parseInt(parts[7]) != 44100 || Integer.parseInt(parts[8]) != 1 || mp3 < 21000 || mp3 > 28000
			|| Math.abs(mp3Streamed - Math.round(mp3 * (float) MusicFeed.RATE / 44100)) > 64) {
			throw new AssertionError("The MP3 decoded wrong: " + result);
		}
		context.runOnClient(client -> {
			HitSoundModule hit = ModuleManager.get(HitSoundModule.class);
			boolean was = hit.isEnabled();
			hit.setEnabled(true, false);
			hit.onHit();
			hit.sound.set("Custom");
			hit.onHit();
			hit.sound.reset();
			hit.setEnabled(was, false);
		});
	}

	private static int streamed(Path file) throws IOException {
		int total = 0;
		short[] buffer = new short[MusicFeed.FRAME];
		try (AudioFiles.MonoStream stream = AudioFiles.open(file, MusicFeed.RATE)) {
			for (int n; (n = stream.read(buffer, 0, buffer.length)) >= 0; ) total += n;
		}
		return total;
	}

	/** Reloads the resource packs, shoots the loading screen once it has come in, then waits for the load to end. */
	private static void reloadShot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> client.reloadResources());
		for (int i = 0; i < 200 && context.computeOnClient(client -> client.getOverlay() == null); i++) context.waitTick();
		context.waitTicks(14);
		boolean loading = context.computeOnClient(client -> client.getOverlay() != null);
		context.takeScreenshot(name);
		System.out.println("V1ZIR-LOADING " + name + " overlay shown: " + loading);
		for (int i = 0; i < 3000 && context.computeOnClient(client -> client.getOverlay() != null); i++) context.waitTick();
		context.waitTicks(5);
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
			SkyPreset.INK, SkyPreset.SYNTHWAVE, SkyPreset.BLOOD_MOON, SkyPreset.PLASMA, SkyPreset.LAGOON)) {
			skyShot(context, singleplayer, preset, 18000, "sky-" + preset.name().toLowerCase().replace('_', '-'));
		}
		// Recoloured: Ink in two picked colours and as a rainbow, Magma and the Blood Moon in colours of their own.
		skyColors(context, "Custom", 0xFFFF3D9A, 0xFF3DA5FF);
		skyShot(context, singleplayer, SkyPreset.INK, 18000, "sky-ink-custom");
		skyShot(context, singleplayer, SkyPreset.NEBULA, 18000, "sky-nebula-custom");
		skyColors(context, "Custom", 0xFF22D3EE, 0xFFA855F7);
		skyShot(context, singleplayer, SkyPreset.MAGMA, 18000, "sky-magma-custom");
		skyShot(context, singleplayer, SkyPreset.BLOOD_MOON, 18000, "sky-blood-moon-custom");
		skyShot(context, singleplayer, SkyPreset.PLASMA, 18000, "sky-plasma-custom");
		skyColors(context, "Rainbow", 0, 0);
		skyShot(context, singleplayer, SkyPreset.INK, 18000, "sky-ink-rainbow");
		context.runOnClient(client -> {
			ShaderSkyModule sky = ModuleManager.get(ShaderSkyModule.class);
			sky.colors.reset();
			sky.colorA.reset();
			sky.colorB.reset();
			sky.preset.set(SkyPreset.REALISTIC.title());
		});
		context.runOnClient(client -> ModuleManager.get(ShaderSkyModule.class).setEnabled(false, false));
		bench(context, "world-vanilla-sky-end");
		context.runOnClient(client -> ModuleManager.get(ShaderSkyModule.class).setEnabled(true, false));
	}

	/**
	 * A preset at a time of day. The shot is taken without the sky cache (which takes two refreshes to show a new
	 * preset, long at the frame rate of CI), the measurement after it with the cache, as the sky is drawn in play.
	 */
	private static void skyShot(ClientGameTestContext context, TestSingleplayerContext singleplayer, SkyPreset preset, int time, String name) {
		singleplayer.getServer().runCommand("time set " + time);
		context.runOnClient(client -> {
			ShaderSkyModule sky = ModuleManager.get(ShaderSkyModule.class);
			sky.preset.set(preset.title());
			sky.cache.set(false);
			sky.renderScale.set(1.0);
		});
		context.waitTicks(6);
		context.takeScreenshot(name);
		context.runOnClient(client -> {
			ShaderSkyModule sky = ModuleManager.get(ShaderSkyModule.class);
			sky.cache.reset();
			sky.renderScale.reset();
		});
		bench(context, name);
	}

	/** Where the sky's colours come from, and the two picked ones (ignored unless Custom). */
	private static void skyColors(ClientGameTestContext context, String source, int primary, int secondary) {
		context.runOnClient(client -> {
			ShaderSkyModule sky = ModuleManager.get(ShaderSkyModule.class);
			sky.colors.set(source);
			if (primary != 0) sky.colorA.set(primary);
			if (secondary != 0) sky.colorB.set(secondary);
		});
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

		// Jump Circles in the Water style, looking down at the ripples while they spread and settle.
		context.runOnClient(client -> ModuleManager.get(JumpCirclesModule.class).style.set("Water"));
		look(singleplayer, 0, 50);
		context.waitTicks(10);
		context.getInput().holdKeyFor(options -> options.jumpKey, 2);
		context.waitTicks(8);
		context.takeScreenshot("jump-circles-water");
		context.waitTicks(12);
		context.takeScreenshot("jump-circles-water-late");
		context.runOnClient(client -> ModuleManager.get(JumpCirclesModule.class).style.set("Glow"));
		look(singleplayer, 0, 20);
		context.waitTicks(5);

		// Color Grading looks over the same view.
		boolean gradingWasOn = context.computeOnClient(client -> {
			ColorGradingModule grading = ModuleManager.get(ColorGradingModule.class);
			boolean on = grading.isEnabled();
			grading.look.set("Amethyst");
			grading.setEnabled(true, false);
			return on;
		});
		context.waitTicks(3);
		context.takeScreenshot("color-grading-amethyst");
		context.runOnClient(client -> ModuleManager.get(ColorGradingModule.class).look.set("Frost"));
		context.waitTicks(3);
		context.takeScreenshot("color-grading-frost");
		context.runOnClient(client -> {
			ColorGradingModule grading = ModuleManager.get(ColorGradingModule.class);
			grading.look.reset();
			grading.setEnabled(gradingWasOn, false);
		});

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

	/** The Spotify card and its compact style over a sample song whose cover is generated here. */
	private static void spotify(ClientGameTestContext context) {
		Path cover = Path.of("v1zir-test-cover.png").toAbsolutePath();
		writeCover(cover);
		context.runOnClient(client -> {
			Media.preview(new NowPlaying(new Track("Midnight Drive", "Neon Coast", "Afterglow", 214_000L), cover.toString(), 83_000L, true,
				System.nanoTime()));
			ModuleManager.get(SpotifyModule.class).setEnabled(true, false);
		});
		context.waitTicks(30);
		context.takeScreenshot("spotify-card");
		context.runOnClient(client -> ModuleManager.get(SpotifyModule.class).style.set("Compact"));
		context.waitTicks(5);
		context.takeScreenshot("spotify-compact");
		// The debug lines under the card (what the player's backend sees; little on Linux).
		context.runOnClient(client -> {
			SpotifyModule spotify = ModuleManager.get(SpotifyModule.class);
			spotify.style.reset();
			spotify.debugInfo.set(true);
		});
		context.waitTicks(5);
		context.takeScreenshot("spotify-debug");
		context.runOnClient(client -> {
			SpotifyModule spotify = ModuleManager.get(SpotifyModule.class);
			spotify.debugInfo.set(false);
			spotify.setEnabled(false, false);
			Media.preview(null);
		});
	}

	/** A synthwave cover: a dusk gradient, a striped sun and a grid, so the card's colours have something to follow. */
	private static void writeCover(Path path) {
		BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setPaint(new GradientPaint(0, 0, new Color(0x1B0B3A), 0, 300, new Color(0xE0457B)));
		g.fillRect(0, 0, 300, 300);
		g.setPaint(new GradientPaint(0, 70, new Color(0xFFD86B), 0, 190, new Color(0xFF4F9A)));
		g.fillOval(75, 60, 150, 150);
		g.setColor(new Color(0x2A0F4F));
		for (int i = 0; i < 5; i++) g.fillRect(70, 150 + i * 12, 160, 4 + i);
		g.setColor(new Color(0x2A0F4F));
		g.fillRect(0, 205, 300, 95);
		g.setColor(new Color(0x7DF9FF));
		for (int i = 0; i <= 10; i++) g.drawLine(150 + (i - 5) * 12, 205, 150 + (i - 5) * 70, 300);
		for (int i = 0; i < 6; i++) g.drawLine(0, 210 + i * i * 3, 300, 210 + i * i * 3);
		g.dispose();
		try {
			ImageIO.write(image, "png", path.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A module toggled with the HUD showing: the Dynamic Island at the top centre shows its tab icon, name and ON. */
	private static void dynamicIsland(ClientGameTestContext context) {
		context.runOnClient(client -> {
			NotificationsModule notifications = ModuleManager.get(NotificationsModule.class);
			notifications.setEnabled(true, false);
			notifications.style.set("Island");
			ModuleManager.get(ChinaHatModule.class).setEnabled(true);
		});
		context.waitTicks(10);
		context.takeScreenshot("dynamic-island");
		context.runOnClient(client -> ModuleManager.get(ChinaHatModule.class).setEnabled(false, false));
		// Back to the idle pill before the next screenshots.
		context.waitTicks(40);
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

	/** The Target HUD on a pig under the crosshair (how it picks a target without a fight): the face cut from the pig's own texture. */
	private static void targetHud(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		boolean wasOn = context.computeOnClient(client -> ModuleManager.get(TargetHudModule.class).isEnabled());
		context.runOnClient(client -> ModuleManager.get(TargetHudModule.class).setEnabled(true, false));
		singleplayer.getServer().runCommand("execute as @a at @s anchored eyes run summon minecraft:pig ^ ^-0.5 ^2.2 "
			+ "{NoAI:1b,Silent:1b,Invulnerable:1b,Tags:[\"v1zir_target\"],DeathLootTable:\"minecraft:empty\"}");
		singleplayer.getServer().runCommand("execute as @a at @s anchored eyes run tp @s ~ ~ ~ facing entity @e[tag=v1zir_target,limit=1] eyes");
		context.waitTicks(30);
		context.takeScreenshot("target-hud-mob");
		context.runOnClient(client -> ModuleManager.get(TargetHudModule.class).setEnabled(wasOn, false));
		singleplayer.getServer().runCommand("kill @e[tag=v1zir_target]");
		context.waitTicks(25);
	}

	/**
	 * Target ESP around a husk the player "hit": every mode in turn, then Ghosts again right after a hit (swollen,
	 * flashing red), then Target with the husk fourteen blocks away.
	 */
	private static void targetEsp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		look(singleplayer, 0, 4);
		plainFov(context, true);
		singleplayer.getServer().runCommand("execute as @a at @s rotated ~ 0 run summon minecraft:husk ^ ^ ^3.2 "
			+ "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"v1zir_esp\"],DeathLootTable:\"minecraft:empty\"}");
		context.waitTicks(15);
		boolean wasOn = context.computeOnClient(client -> {
			TargetEspModule esp = ModuleManager.get(TargetEspModule.class);
			boolean on = esp.isEnabled();
			esp.setEnabled(true, false);
			return on;
		});
		// The Target HUD's card would sit right over the husk.
		boolean hudWasOn = context.computeOnClient(client -> {
			TargetHudModule hud = ModuleManager.get(TargetHudModule.class);
			boolean on = hud.isEnabled();
			hud.setEnabled(false, false);
			return on;
		});
		for (String mode : List.of("Target", "Brackets", "Ghosts", "Ring", "Marker", "Helix", "Orbit", "Crystals")) {
			context.runOnClient(client -> {
				ModuleManager.get(TargetEspModule.class).mode.set(mode);
				// Hit again: a fight ends after eight seconds without one.
				Effects.onAttack(nearestMob(client));
			});
			context.waitTicks(12);
			context.takeScreenshot("target-esp-" + mode.toLowerCase(Locale.ROOT));
		}
		context.runOnClient(client -> {
			ModuleManager.get(TargetEspModule.class).mode.set("Ghosts");
			LivingEntity mob = nearestMob(client);
			Effects.onAttack(mob);
			mob.hurtTime = 10;
			mob.maxHurtTime = 10;
		});
		context.waitTicks(1);
		context.takeScreenshot("target-esp-hit");
		// Target fourteen blocks away: the marker keeps about its size on screen while the husk gets small.
		singleplayer.getServer().runCommand("execute as @a at @s rotated ~ 0 run tp @e[tag=v1zir_esp] ^ ^ ^14");
		context.runOnClient(client -> {
			ModuleManager.get(TargetEspModule.class).mode.set("Target");
			Effects.onAttack(nearestMob(client));
		});
		context.waitTicks(15);
		context.takeScreenshot("target-esp-target-far");
		context.runOnClient(client -> {
			TargetEspModule esp = ModuleManager.get(TargetEspModule.class);
			esp.mode.reset();
			esp.setEnabled(wasOn, false);
			ModuleManager.get(TargetHudModule.class).setEnabled(hudWasOn, false);
		});
		plainFov(context, false);
		singleplayer.getServer().runCommand("kill @e[tag=v1zir_esp]");
		context.waitTicks(25);
	}

	/**
	 * The speed effect the test player has widens the field of view, which makes everything a few blocks away small:
	 * close-ups switch that off for their shots.
	 */
	private static void plainFov(ClientGameTestContext context, boolean plain) {
		context.runOnClient(client -> {
			if (plain) client.options.getFovEffectScale().setValue(0.0);
			else client.options.getFovEffectScale().setValue(1.0);
		});
		context.waitTicks(12);
	}

	/** The living entity nearest to the player (the mob a test summoned in front of them). */
	private static LivingEntity nearestMob(MinecraftClient client) {
		LivingEntity nearest = null;
		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof LivingEntity living) || entity == client.player) continue;
			if (nearest == null || living.squaredDistanceTo(client.player) < nearest.squaredDistanceTo(client.player)) nearest = living;
		}
		if (nearest == null) throw new AssertionError("No mob near the player");
		return nearest;
	}

	/** Custom Indicators over the hotbar with the player hurt, a little hungry, absorbing and a few levels up. */
	private static void customIndicators(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> {
			CustomIndicatorsModule bars = ModuleManager.get(CustomIndicatorsModule.class);
			bars.experience.set(true);
			bars.setEnabled(true, false);
		});
		for (String command : List.of("damage @a 7 minecraft:generic", "effect give @a minecraft:hunger 2 255 true",
			"effect give @a minecraft:absorption 30 0 true", "xp add @a 7 levels", "xp add @a 30 points")) {
			singleplayer.getServer().runCommand(command);
		}
		context.waitTicks(60);
		context.takeScreenshot("custom-indicators");
		context.runOnClient(client -> {
			CustomIndicatorsModule bars = ModuleManager.get(CustomIndicatorsModule.class);
			bars.setEnabled(false, false);
			bars.experience.reset();
		});
		// Full health and food and no experience again for the screenshots after this one.
		for (String command : List.of("effect clear @a minecraft:absorption", "effect give @a minecraft:instant_health 1 5 true",
			"effect give @a minecraft:saturation 1 10 true", "xp set @a 0 levels", "xp set @a 0 points")) {
			singleplayer.getServer().runCommand(command);
		}
		context.waitTicks(10);
	}

	/**
	 * Health Indicator over mobs (no other player can join the test world): halves and empty hearts on a husk with
	 * the number on, and one row standing for "x5" on an iron golem.
	 */
	private static void healthIndicator(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> {
			HealthIndicatorModule hearts = ModuleManager.get(HealthIndicatorModule.class);
			hearts.mobs.set(true);
			hearts.number.set(true);
			hearts.setEnabled(true, false);
		});
		singleplayer.getServer().runCommand("execute as @a at @s rotated ~ 0 run summon minecraft:husk ^0.8 ^ ^4 "
			+ "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:13f,Tags:[\"v1zir_hearts\"],DeathLootTable:\"minecraft:empty\"}");
		singleplayer.getServer().runCommand("execute as @a at @s rotated ~ 0 run summon minecraft:iron_golem ^-1.8 ^ ^5.5 "
			+ "{NoAI:1b,Silent:1b,Health:73f,Tags:[\"v1zir_hearts\"],DeathLootTable:\"minecraft:empty\"}");
		singleplayer.getServer().runCommand("execute as @a at @s anchored eyes run tp @s ~ ~ ~ facing entity @e[tag=v1zir_hearts,sort=nearest,limit=1] eyes");
		context.waitTicks(30);
		context.takeScreenshot("health-indicator");
		context.runOnClient(client -> {
			HealthIndicatorModule hearts = ModuleManager.get(HealthIndicatorModule.class);
			hearts.setEnabled(false, false);
			hearts.mobs.reset();
			hearts.number.reset();
		});
		singleplayer.getServer().runCommand("kill @e[tag=v1zir_hearts]");
		context.waitTicks(25);
	}

	/**
	 * Chams and ESP (mobs on for the test) on a husk in the open and a pig behind a stone wall, which
	 * both show through it: Glass with Box 2D, Glow with Corners, Flat with Box 3D.
	 */
	private static void chamsAndEsp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("time set 6000");
		look(singleplayer, 0, 8);
		for (String command : List.of(
			"execute as @a at @s rotated ~ 0 run summon minecraft:husk ^-0.6 ^ ^4 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"v1zir_chams\"],DeathLootTable:\"minecraft:empty\"}",
			"execute as @a at @s rotated ~ 0 run summon minecraft:pig ^1.6 ^ ^6.5 {NoAI:1b,Silent:1b,Tags:[\"v1zir_chams\"],DeathLootTable:\"minecraft:empty\"}",
			"execute as @a at @s rotated ~ 0 run fill ^1 ^ ^5 ^2 ^1 ^5 minecraft:stone")) {
			singleplayer.getServer().runCommand(command);
		}
		context.runOnClient(client -> {
			ChamsModule chams = ModuleManager.get(ChamsModule.class);
			chams.mobs.set(true);
			chams.setEnabled(true, false);
			EspModule esp = ModuleManager.get(EspModule.class);
			esp.mobs.set(true);
			esp.setEnabled(true, false);
		});
		context.waitTicks(20);
		for (String[] look : new String[][]{{"Glass", "Box 2D"}, {"Crystal", "Corners"}, {"Glow", "Corners"}, {"Flat", "Box 3D"}}) {
			context.runOnClient(client -> {
				ModuleManager.get(ChamsModule.class).style.set(look[0]);
				ModuleManager.get(EspModule.class).mode.set(look[1]);
			});
			context.waitTicks(5);
			context.takeScreenshot("chams-" + look[0].toLowerCase(Locale.ROOT) + "-esp-" + look[1].toLowerCase(Locale.ROOT).replace(' ', '-'));
		}
		// Crystal up close, two blocks from the husk, without ESP.
		context.runOnClient(client -> {
			ModuleManager.get(ChamsModule.class).style.set("Crystal");
			ModuleManager.get(EspModule.class).setEnabled(false, false);
		});
		singleplayer.getServer().runCommand("execute as @a at @s run tp @s ^ ^ ^2");
		context.waitTicks(5);
		context.takeScreenshot("chams-crystal-close");
		for (String style : List.of("Hologram", "Galaxy", "Wireframe", "Rainbow")) {
			context.runOnClient(client -> ModuleManager.get(ChamsModule.class).style.set(style));
			context.waitTicks(5);
			context.takeScreenshot("chams-" + style.toLowerCase(Locale.ROOT) + "-close");
		}
		singleplayer.getServer().runCommand("execute as @a at @s run tp @s ^ ^ ^-2");
		context.runOnClient(client -> {
			ModuleManager.get(ChamsModule.class).style.set("Flat");
			ModuleManager.get(EspModule.class).setEnabled(true, false);
		});
		context.waitTicks(5);
		bench(context, "chams-esp");
		context.runOnClient(client -> {
			ChamsModule chams = ModuleManager.get(ChamsModule.class);
			chams.setEnabled(false, false);
			chams.mobs.reset();
			chams.style.reset();
			EspModule esp = ModuleManager.get(EspModule.class);
			esp.setEnabled(false, false);
			esp.mobs.reset();
			esp.mode.reset();
		});
		singleplayer.getServer().runCommand("kill @e[tag=v1zir_chams]");
		singleplayer.getServer().runCommand("execute as @a at @s rotated ~ 0 run fill ^1 ^ ^5 ^2 ^1 ^5 minecraft:air");
		context.waitTicks(25);
	}

	/**
	 * Kill Effects on husks killed with a command (every death nearby counts here): Light Pillar, Lightning, Shatter and
	 * Firework each just after the blow and a little later (Lightning also once the game has taken the body away, where
	 * motes rise instead of its puff of smoke), then the Ghost of a mannequin.
	 */
	private static void killEffects(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		look(singleplayer, 0, 2);
		boolean wasOn = context.computeOnClient(client -> {
			KillEffectsModule kills = ModuleManager.get(KillEffectsModule.class);
			boolean on = kills.isEnabled();
			kills.trigger.set("All deaths");
			kills.setEnabled(true, false);
			return on;
		});
		for (String mode : List.of("Light Pillar", "Lightning", "Shatter", "Firework")) {
			context.runOnClient(client -> ModuleManager.get(KillEffectsModule.class).mode.set(mode));
			singleplayer.getServer().runCommand("execute as @a at @s rotated ~ 0 run summon minecraft:husk ^ ^ ^5 "
				+ "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"v1zir_kill\"],DeathLootTable:\"minecraft:empty\"}");
			context.waitTicks(15);
			singleplayer.getServer().runCommand("kill @e[tag=v1zir_kill]");
			String name = "kill-" + mode.toLowerCase(Locale.ROOT).replace(' ', '-');
			context.waitTicks(3);
			context.takeScreenshot(name);
			context.waitTicks(9);
			context.takeScreenshot(name + "-later");
			if (mode.equals("Lightning")) {
				context.waitTicks(12);
				context.takeScreenshot(name + "-taken");
			}
			context.waitTicks(40);
		}
		// Ghost, on a mannequin (a player's model and skin): just after the death, lifted and whole, then falling apart.
		// (Slowed down: the test's ticks run at the frame rate, which is low in CI.)
		context.runOnClient(client -> {
			KillEffectsModule kills = ModuleManager.get(KillEffectsModule.class);
			kills.mode.set("Ghost");
			kills.duration.set(1.5);
		});
		look(singleplayer, 0, -8);
		plainFov(context, true);
		singleplayer.getServer().runCommand("execute as @a at @s run summon minecraft:mannequin ~ ~ ~3.4 {Silent:1b,Tags:[\"v1zir_kill\"]}");
		singleplayer.getServer().runCommand("execute as @a at @s run tp @e[tag=v1zir_kill] ~ ~ ~3.4 facing entity @s");
		context.waitTicks(20);
		context.takeScreenshot("kill-ghost-before");
		singleplayer.getServer().runCommand("kill @e[tag=v1zir_kill]");
		context.waitTicks(6);
		context.takeScreenshot("kill-ghost");
		context.waitTicks(24);
		context.takeScreenshot("kill-ghost-rising");
		context.waitTicks(20);
		context.takeScreenshot("kill-ghost-dissolving");
		context.waitTicks(16);
		context.takeScreenshot("kill-ghost-embers");
		context.waitTicks(60);
		plainFov(context, false);
		look(singleplayer, 0, 2);
		context.runOnClient(client -> {
			KillEffectsModule kills = ModuleManager.get(KillEffectsModule.class);
			kills.setEnabled(wasOn, false);
			kills.trigger.reset();
			kills.mode.reset();
			kills.duration.reset();
		});
	}

	/** Shooting Stars at night, looking up: a few meteors launched at once, as a high rate would over a while. */
	private static void shootingStars(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("time set 18000");
		look(singleplayer, 0, -55);
		context.runOnClient(client -> ModuleManager.get(ShootingStarsModule.class).setEnabled(true, false));
		context.waitTicks(20);
		context.runOnClient(client -> {
			ShootingStarsModule stars = ModuleManager.get(ShootingStarsModule.class);
			for (int i = 0; i < 8; i++) invoke(stars, ShootingStarsModule.class, "spawn");
		});
		context.waitTicks(2);
		context.takeScreenshot("shooting-stars");
		bench(context, "shooting-stars");
		context.runOnClient(client -> ModuleManager.get(ShootingStarsModule.class).setEnabled(false, false));
		singleplayer.getServer().runCommand("time set 6000");
	}

	/**
	 * Motion Blur while turning. The screenshot renders a frame of its own while the game waits, so the view
	 * turns right before it: that frame sees the whole turn since the last one.
	 */
	private static void motionBlur(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		look(singleplayer, 0, 10);
		context.runOnClient(client -> {
			MotionBlurModule blur = ModuleManager.get(MotionBlurModule.class);
			blur.strength.set(1.0);
			blur.setEnabled(true, false);
		});
		context.waitTicks(10);
		context.runOnClient(client -> client.player.setYaw(client.player.getYaw() + 25f));
		context.takeScreenshot("motion-blur-turning");
		context.runOnClient(client -> {
			MotionBlurModule blur = ModuleManager.get(MotionBlurModule.class);
			blur.setEnabled(false, false);
			blur.strength.reset();
		});
		look(singleplayer, 0, 0);
	}

	/**
	 * The atmosphere and the lens: God Rays through a row of trunks under a canopy with gaps at sunset, the Mist over
	 * the land in the morning and together with the rays, and the Cinematic lens with letterbox bars.
	 */
	private static void atmosphere(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// The sun sets in the west (yaw 90): trunks and leaves between it and the camera cut its glow into shafts.
		String[] build = {
			"fill ~-14 ~ ~-10 ~-14 ~6 ~-10 minecraft:oak_log", "fill ~-14 ~ ~-6 ~-14 ~6 ~-6 minecraft:oak_log",
			"fill ~-14 ~ ~-3 ~-14 ~6 ~-3 minecraft:oak_log", "fill ~-14 ~ ~1 ~-14 ~6 ~1 minecraft:oak_log",
			"fill ~-14 ~ ~4 ~-14 ~6 ~4 minecraft:oak_log", "fill ~-14 ~ ~8 ~-14 ~6 ~8 minecraft:oak_log",
			"fill ~-17 ~6 ~-12 ~-12 ~8 ~11 minecraft:oak_leaves[persistent=true]",
			"fill ~-17 ~6 ~-5 ~-12 ~8 ~-4 minecraft:air", "fill ~-17 ~6 ~2 ~-12 ~8 ~3 minecraft:air",
		};
		for (String command : build) singleplayer.getServer().runCommand("execute as @a at @s run " + command);
		look(singleplayer, 90, -10);
		singleplayer.getServer().runCommand("time set 12300");
		context.waitTicks(20);
		context.takeScreenshot("god-rays-off");
		context.runOnClient(client -> ModuleManager.get(GodRaysModule.class).setEnabled(true, false));
		context.waitTicks(5);
		context.takeScreenshot("god-rays-sunset");
		bench(context, "god-rays");
		context.runOnClient(client -> ModuleManager.get(MistModule.class).setEnabled(true, false));
		context.waitTicks(5);
		context.takeScreenshot("god-rays-mist-sunset");
		context.runOnClient(client -> ModuleManager.get(GodRaysModule.class).setEnabled(false, false));

		// The Mist alone in the morning, over open land and then looking down on it.
		singleplayer.getServer().runCommand("time set 23600");
		look(singleplayer, -90, 0);
		context.waitTicks(10);
		context.takeScreenshot("mist-dawn");
		bench(context, "mist");
		singleplayer.getServer().runCommand("time set 4000");
		look(singleplayer, -60, 12);
		context.waitTicks(10);
		context.takeScreenshot("mist-day");
		context.runOnClient(client -> {
			MistModule mist = ModuleManager.get(MistModule.class);
			mist.colors.set("Theme");
			mist.density.set(2.0);
		});
		context.waitTicks(5);
		context.takeScreenshot("mist-theme");
		context.runOnClient(client -> {
			MistModule mist = ModuleManager.get(MistModule.class);
			mist.setEnabled(false, false);
			mist.colors.reset();
			mist.density.reset();
		});

		context.runOnClient(client -> {
			CinematicModule cinematic = ModuleManager.get(CinematicModule.class);
			cinematic.bars.set(true);
			cinematic.fringes.set(0.8);
			cinematic.setEnabled(true, false);
		});
		look(singleplayer, 90, -10);
		singleplayer.getServer().runCommand("time set 12300");
		context.waitTicks(10);
		context.takeScreenshot("cinematic");
		context.runOnClient(client -> {
			CinematicModule cinematic = ModuleManager.get(CinematicModule.class);
			cinematic.setEnabled(false, false);
			cinematic.bars.reset();
			cinematic.fringes.reset();
		});
		singleplayer.getServer().runCommand("execute as @a at @s run fill ~-17 ~ ~-12 ~-12 ~8 ~11 minecraft:air");
		singleplayer.getServer().runCommand("time set 6000");
		look(singleplayer, 0, 0);
	}

	/**
	 * VIBE in every theme: by day Sakura, Autumn, Winter, Ocean and Dreamy, at night Fireflies, Neon and Stardust, each
	 * with its particles, haze and grade, looking out over the field.
	 */
	private static void vibe(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		look(singleplayer, 30, -6);
		for (String theme : List.of("Sakura", "Autumn", "Winter", "Ocean", "Dreamy", "Fireflies", "Neon", "Stardust")) {
			boolean night = theme.equals("Fireflies") || theme.equals("Neon") || theme.equals("Stardust");
			setTime(context, singleplayer, night ? 18000 : 6000);
			context.runOnClient(client -> {
				VibeModule vibe = ModuleManager.get(VibeModule.class);
				vibe.theme.set(theme);
				vibe.setEnabled(true, false);
			});
			context.waitTicks(25);
			context.takeScreenshot("vibe-" + theme.toLowerCase(Locale.ROOT));
		}
		bench(context, "vibe");
		context.runOnClient(client -> {
			VibeModule vibe = ModuleManager.get(VibeModule.class);
			vibe.setEnabled(false, false);
			vibe.theme.reset();
		});
		singleplayer.getServer().runCommand("time set 6000");
		look(singleplayer, 0, 0);
	}

	/** The PS5 style theme in a world: the pause menu as a control center, Options, the inventory and the hotbar. */
	private static void ps5InGame(ClientGameTestContext context) {
		context.runOnClient(client -> ModuleManager.get(ThemeModule.class).styleTheme.set("PS5"));
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(15);
		context.takeScreenshot("pause-ps5");
		context.setScreen(() -> new OptionsScreen(MinecraftClient.getInstance().currentScreen, MinecraftClient.getInstance().options));
		context.waitTicks(12);
		context.takeScreenshot("options-ps5");
		context.setScreen(() -> new InventoryScreen(MinecraftClient.getInstance().player));
		context.waitTicks(12);
		context.takeScreenshot("inventory-ps5");
		context.setScreen(() -> null);
		context.waitTicks(12);
		context.takeScreenshot("hotbar-ps5");
		// The PS5 menu's picture of this world: taken as the next frame is drawn, without the hand or the HUD.
		int before = context.computeOnClient(client -> MenuShots.saved());
		boolean asked = context.computeOnClient(MenuShots::request);
		for (int i = 0; i < 100 && context.computeOnClient(client -> MenuShots.saved()) == before; i++) context.waitTick();
		int after = context.computeOnClient(client -> MenuShots.saved());
		System.out.println("V1ZIR-PICTURE asked: " + asked + ", saved: " + (after - before));
		if (!asked || after == before) throw new AssertionError("The menu picture of the world was not saved");
		context.runOnClient(client -> ModuleManager.get(ThemeModule.class).styleTheme.reset());
		context.waitTicks(2);
	}

	/** Nametags on your own player in third person (the only player in the test world), as others see it over you. */
	private static void nametags(ClientGameTestContext context) {
		context.runOnClient(client -> {
			NametagsModule tags = ModuleManager.get(NametagsModule.class);
			tags.self.set(true);
			tags.setEnabled(true, false);
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		});
		context.waitTicks(10);
		context.takeScreenshot("nametags-self");
		context.runOnClient(client -> {
			NametagsModule tags = ModuleManager.get(NametagsModule.class);
			tags.setEnabled(false, false);
			tags.self.reset();
			client.options.setPerspective(Perspective.FIRST_PERSON);
		});
	}

	/**
	 * Wet World without rain, in the morning: the field soaked, with puddles mirroring the sky, then looking down at the
	 * puddles near by, then with Drizzle ringing on them.
	 */
	private static void wetWorld(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		look(singleplayer, 20, 22);
		setTime(context, singleplayer, 3000);
		context.runOnClient(client -> ModuleManager.get(WetWorldModule.class).setEnabled(true, false));
		context.waitTicks(40);
		System.out.println("V1ZIR-TIME wet-world " + context.computeOnClient(client -> client.world.getTimeOfDay() % 24000L));
		context.takeScreenshot("wet-world");
		bench(context, "wet-world");
		look(singleplayer, 200, 48);
		context.waitTicks(20);
		context.takeScreenshot("wet-world-near");
		look(singleplayer, 20, 22);
		context.runOnClient(client -> ModuleManager.get(WetWorldModule.class).drizzle.set(true));
		context.waitTicks(10);
		context.takeScreenshot("wet-world-drizzle");
		context.runOnClient(client -> {
			WetWorldModule wet = ModuleManager.get(WetWorldModule.class);
			wet.setEnabled(false, false);
			wet.drizzle.reset();
		});
		singleplayer.getServer().runCommand("time set 6000");
		look(singleplayer, 0, 0);
	}

	/**
	 * Hand Chams with the arm holding the item: a diamond sword in every look (Outline round its true silhouette, Sky
	 * as Nebula, Ink in two picked colours, Plasma as a rainbow and Aurora, Crystal, Tint), the Sky without the arm, the
	 * arm holding a block and an apple, and the Sky on the empty hand. The sword is raised fully before each shot: a
	 * newly held sword dips while the attack recharges.
	 */
	private static void handChams(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		look(singleplayer, 0, 0);
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
		boolean wasOn = context.computeOnClient(client -> ModuleManager.get(HandChamsModule.class).isEnabled());
		context.runOnClient(client -> ModuleManager.get(HandChamsModule.class).setEnabled(false, false));
		context.waitTicks(30);
		context.takeScreenshot("hand-vanilla-sword");
		handLook(context, "Outline", null, null, "hand-chams-outline");
		handLook(context, "Sky", "Nebula", "Preset", "hand-chams-sky-nebula");
		context.runOnClient(client -> {
			HandChamsModule chams = ModuleManager.get(HandChamsModule.class);
			chams.skyColorA.set(0xFFFF3D9A);
			chams.skyColorB.set(0xFF3DA5FF);
		});
		handLook(context, "Sky", "Ink", "Custom", "hand-chams-sky-ink-custom");
		handLook(context, "Sky", "Plasma", "Rainbow", "hand-chams-sky-plasma-rainbow");
		handLook(context, "Sky", "Aurora", "Preset", "hand-chams-sky-aurora");
		handLook(context, "Crystal", null, null, "hand-chams-crystal");
		handLook(context, "Tint", null, null, "hand-chams-tint");
		context.runOnClient(client -> ModuleManager.get(HandChamsModule.class).armItems.set(false));
		handLook(context, "Sky", "Nebula", "Preset", "hand-chams-sky-no-arm");
		context.runOnClient(client -> ModuleManager.get(HandChamsModule.class).armItems.set(true));
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:grass_block");
		context.waitTicks(30);
		handLook(context, "Outline", null, null, "hand-chams-arm-block");
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:apple");
		context.waitTicks(30);
		handLook(context, "Outline", null, null, "hand-chams-arm-apple");
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
		context.waitTicks(15);
		handLook(context, "Sky", "Nebula", "Preset", "hand-chams-sky-empty");
		context.runOnClient(client -> {
			HandChamsModule chams = ModuleManager.get(HandChamsModule.class);
			chams.setEnabled(wasOn, false);
			chams.style.reset();
			chams.sky.reset();
			chams.skyColors.reset();
			chams.skyColorA.reset();
			chams.skyColorB.reset();
			chams.armItems.reset();
		});
		singleplayer.getServer().runCommand("clear @a");
	}

	/** One Hand Chams look (sky preset and colours for Sky), shot a few frames later. */
	private static void handLook(ClientGameTestContext context, String style, String sky, String colors, String name) {
		context.runOnClient(client -> {
			HandChamsModule chams = ModuleManager.get(HandChamsModule.class);
			chams.style.set(style);
			if (sky != null) chams.sky.set(sky);
			if (colors != null) chams.skyColors.set(colors);
			chams.setEnabled(true, false);
		});
		context.waitTicks(4);
		context.takeScreenshot(name);
	}

	/**
	 * Knives: a diamond sword in hand becomes the selected knife held by the player's own arm: at rest after its draw,
	 * in the middle of its inspect and of a stab, another knife and finish, in third person (F5), and the library on
	 * the Knives tab of the Click GUI.
	 */
	private static void knives(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
		look(singleplayer, 30f, 8f);
		boolean wasOn = context.computeOnClient(client -> {
			KnivesModule knives = ModuleManager.get(KnivesModule.class);
			boolean on = knives.isEnabled();
			knives.knife.set("Karambit");
			knives.finish.set("Sunset Fade");
			knives.setEnabled(true, false);
			return on;
		});
		// The draw plays first; the knife then rests in hand.
		context.waitTicks(40);
		context.takeScreenshot("knife-first-person");
		context.runOnClient(client -> KnifeViewModel.inspect());
		context.waitTicks(22);
		context.takeScreenshot("knife-inspect");
		context.waitTicks(100);
		context.runOnClient(client -> client.player.swingHand(Hand.MAIN_HAND));
		context.waitTicks(3);
		context.takeScreenshot("knife-attack");
		context.waitTicks(20);
		context.runOnClient(client -> {
			KnivesModule knives = ModuleManager.get(KnivesModule.class);
			knives.knife.set("Butterfly");
			knives.finish.set("Damascus");
		});
		context.waitTicks(40);
		context.runOnClient(client -> KnifeViewModel.inspect());
		context.waitTicks(30);
		context.takeScreenshot("knife-butterfly-inspect");
		context.waitTicks(100);
		context.runOnClient(client -> {
			KnivesModule knives = ModuleManager.get(KnivesModule.class);
			knives.knife.set("Bayonet");
			knives.finish.set("Sapphire");
			client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
		});
		context.waitTicks(15);
		context.takeScreenshot("knife-third-person");
		context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
		context.waitTicks(40);
		context.takeScreenshot("knife-bayonet");

		context.runOnClient(client -> setStatic(ClickGuiScreen.class, "tab", Category.KNIVES.ordinal()));
		context.setScreen(ClickGuiScreen::new);
		context.waitTicks(30);
		context.takeScreenshot("clickgui-tab-knives");
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			KnivesModule knives = ModuleManager.get(KnivesModule.class);
			knives.setEnabled(wasOn, false);
			knives.knife.reset();
			knives.finish.reset();
		});
		singleplayer.getServer().runCommand("clear @a");
	}

	/**
	 * Cosmetics: a top hat, angel wings and the galaxy cape on the player, seen from the front and from behind in third
	 * person, then the Cosmetics tab with the player preview.
	 */
	/** The Packs tab: every texture of the loaded packs as tiles with their previews. */
	/**
	 * The Pack Mixer with two packs in the folder that are not enabled: a current one with a magenta diamond sword and
	 * a 1.8 one with a blue sword where packs of that time keep it (textures/items). Both must show as versions of the
	 * sword; the 1.8 one is picked and applied, and the sword in hand must be blue. Then everything goes back.
	 */
	private static void packs(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> {
			Path folder = client.runDirectory.toPath().resolve("resourcepacks");
			testPack(folder.resolve("v1zir-test-modern"), 46, "item/diamond_sword", new Color(235, 40, 200));
			testPack(folder.resolve("v1zir-test-legacy"), 1, "items/diamond_sword", new Color(40, 110, 255));
		});
		context.runOnClient(client -> setStatic(ClickGuiScreen.class, "tab", Category.PACKS.ordinal()));
		context.setScreen(ClickGuiScreen::new);
		context.waitTicks(40);
		context.takeScreenshot("clickgui-tab-packs");
		// The Swords tab with its first texture selected.
		context.runOnClient(client -> {
			Object page = field(client.currentScreen, ClickGuiScreen.class, "packsPage");
			Class<?> type = page.getClass();
			try {
				Field filter = type.getDeclaredField("filter");
				filter.setAccessible(true);
				filter.setInt(page, 3 + Arrays.asList(PackMixer.CATEGORIES).indexOf("Swords"));
				for (PackMixer.Entry e : PackMixer.entries()) {
					if (!e.group().equals("Swords")) continue;
					Field selected = type.getDeclaredField("selected");
					selected.setAccessible(true);
					selected.set(page, e);
					break;
				}
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
		context.waitTicks(20);
		context.takeScreenshot("clickgui-tab-packs-swords");
		String packs = context.computeOnClient(client -> {
			PackMixer.Entry sword = PackMixer.find("item/diamond_sword");
			StringBuilder ids = new StringBuilder();
			if (sword != null) for (PackMixer.Version v : sword.versions()) ids.append(v.packId()).append(", ");
			return ids.toString();
		});
		System.out.println("V1ZIR-PACKS diamond_sword versions: " + packs);
		if (!packs.contains("file/v1zir-test-modern") || !packs.contains("file/v1zir-test-legacy")) {
			throw new AssertionError("The Pack Mixer should offer the sword of both test packs (not enabled, one from 1.8), it has: " + packs);
		}
		context.runOnClient(client -> {
			PackMixer.choose(PackMixer.find("item/diamond_sword"), "file/v1zir-test-legacy");
			PackMixer.apply();
		});
		waitForReload(context);
		context.setScreen(() -> null);
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
		look(singleplayer, 0, 0);
		context.waitTicks(30);
		context.takeScreenshot("pack-mixer-applied");
		context.runOnClient(client -> {
			PackMixer.resetAll();
			PackMixer.apply();
		});
		waitForReload(context);
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
	}

	private static void waitForReload(ClientGameTestContext context) {
		context.waitTicks(5);
		for (int i = 0; i < 1200 && context.computeOnClient(client -> PackMixer.reloading()); i++) context.waitTick();
		context.waitTicks(10);
	}

	/** A folder pack of that pack format with one 16x16 sword of that colour at textures/{path}.png. */
	private static void testPack(Path dir, int format, String path, Color color) {
		try {
			java.nio.file.Files.createDirectories(dir.resolve("assets/minecraft/textures").resolve(path).getParent());
			java.nio.file.Files.writeString(dir.resolve("pack.mcmeta"),
				"{\"pack\":{\"pack_format\":" + format + ",\"description\":\"V1zir test pack\"}}");
			BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			int rgb = color.getRGB();
			int dark = color.darker().getRGB();
			for (int i = 3; i <= 14; i++) {
				// The blade along the diagonal, two pixels wide, the handle at the bottom left.
				image.setRGB(i, 15 - i, rgb);
				if (i < 14) image.setRGB(i + 1, 15 - i, dark);
			}
			for (int i = 0; i < 3; i++) image.setRGB(1 + i, 14 - i, 0xFF6B4A2B);
			image.setRGB(2, 11, rgb);
			image.setRGB(4, 13, rgb);
			ImageIO.write(image, "png", dir.resolve("assets/minecraft/textures").resolve(path + ".png").toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void cosmetics(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		look(singleplayer, 30f, 8f);
		boolean wasOn = context.computeOnClient(client -> {
			CosmeticsModule cosmetics = ModuleManager.get(CosmeticsModule.class);
			boolean on = cosmetics.isEnabled();
			cosmetics.hat.set("Top Hat");
			cosmetics.wings.set("Angel Wings");
			cosmetics.cape.set("Galaxy Cape");
			cosmetics.setEnabled(true, false);
			client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
			return on;
		});
		context.waitTicks(20);
		context.takeScreenshot("cosmetics-third-person");
		context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
		context.waitTicks(15);
		context.takeScreenshot("cosmetics-back");
		context.runOnClient(client -> {
			CosmeticsModule cosmetics = ModuleManager.get(CosmeticsModule.class);
			cosmetics.hat.set("Halo");
			cosmetics.wings.set("Demon Wings");
			cosmetics.cape.set("Aurora Cape");
		});
		context.waitTicks(15);
		context.takeScreenshot("cosmetics-back-2");
		context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
		context.runOnClient(client -> setStatic(ClickGuiScreen.class, "tab", Category.COSMETICS.ordinal()));
		context.setScreen(ClickGuiScreen::new);
		context.waitTicks(30);
		context.takeScreenshot("clickgui-tab-cosmetics");
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			CosmeticsModule cosmetics = ModuleManager.get(CosmeticsModule.class);
			cosmetics.setEnabled(wasOn, false);
			cosmetics.hat.reset();
			cosmetics.wings.reset();
			cosmetics.cape.reset();
		});
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
		// Puddles over the open ground behind the player, far and near.
		look(singleplayer, 180, 18);
		context.waitTicks(5);
		context.takeScreenshot("rain-puddles");
		look(singleplayer, 180, 50);
		context.waitTicks(3);
		context.takeScreenshot("rain-puddles-near");
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
		singleplayer.getServer().runCommand("weather clear 1000000");
		context.waitTicks(110);
		context.runOnClient(client -> ModuleManager.get(RainEffectsModule.class).setEnabled(rainWasOn, false));
		singleplayer.getServer().runCommand("time set 6000");
	}

	/**
	 * A lake reaching far out behind the player, seen from just above it: the water reflects out to its far shore
	 * (it used to stop sixty blocks out, the rest plain water with its tiled texture), and stays calm far away and
	 * through a 14x zoom instead of shimmering.
	 */
	private static void farWater(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Kept loaded while the fill runs (chunks load over the next ticks, not within the command).
		singleplayer.getServer().runCommand("execute as @a at @s run forceload add ~-48 ~-150 ~48 ~-16");
		context.waitTicks(20);
		singleplayer.getServer().runCommand("execute as @a at @s run fill ~-40 ~-1 ~-140 ~40 ~-1 ~-20 minecraft:water");
		singleplayer.getServer().runCommand("execute as @a at @s run forceload remove ~-48 ~-150 ~48 ~-16");
		look(singleplayer, 180, 4);
		boolean zoomWasOn = context.computeOnClient(client -> {
			BlockReflectionModule reflection = ModuleManager.get(BlockReflectionModule.class);
			reflection.mode.set(BlockReflectionModule.NORMAL);
			reflection.setEnabled(true, false);
			return ModuleManager.isEnabled(ZoomModule.class);
		});
		// The water's chunks rebuild, and the far block columns fill (in under a second).
		context.waitTicks(100);
		context.takeScreenshot("reflection-far-water");
		bench(context, "reflection-far-water");
		look(singleplayer, 180, 0.6f);
		context.runOnClient(client -> {
			ZoomModule zoom = ModuleManager.get(ZoomModule.class);
			zoom.setEnabled(true, false);
			zoom.factor.set(14.0);
		});
		context.getInput().holdKey(GLFW.GLFW_KEY_C);
		context.waitTicks(30);
		context.takeScreenshot("reflection-far-water-zoom");
		context.getInput().releaseKey(GLFW.GLFW_KEY_C);
		context.waitTicks(20);
		context.runOnClient(client -> {
			ModuleManager.get(ZoomModule.class).setEnabled(zoomWasOn, false);
			ModuleManager.get(BlockReflectionModule.class).setEnabled(false, false);
		});
	}

	/**
	 * With Sodium (CI's second client run): Sodium's Video Settings, which take the place of the game's, have V1zir
	 * Visuals' GPU page (SodiumGpuOption), registered without a fault; it is opened on that page (V1ZIR-SODIUM,
	 * sodium-video-settings-gpu). Sodium's own classes are reached by reflection: the test is built without them.
	 */
	private static void sodium(ClientGameTestContext context) {
		context.waitTicks(20);
		String found = context.computeOnClient(client -> {
			try {
				Object config = Class.forName("net.caffeinemc.mods.sodium.client.config.ConfigManager").getField("CONFIG").get(null);
				Object option = config.getClass().getMethod("getOption", Identifier.class).invoke(config, Identifier.of("v1zir", "gpu"));
				Object value = config.getClass().getMethod("readEnumOption", Identifier.class, Class.class)
					.invoke(config, Identifier.of("v1zir", "gpu"), GpuOption.Gpu.class);
				return "GPU option registered: " + (option != null) + ", value " + value;
			} catch (ReflectiveOperationException | RuntimeException e) {
				return "GPU option not found: " + e;
			}
		});
		System.out.println("V1ZIR-SODIUM " + found);
		context.setScreen(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			try {
				Object config = Class.forName("net.caffeinemc.mods.sodium.client.config.ConfigManager").getField("CONFIG").get(null);
				Object page = null;
				for (Object mod : (List<?>) config.getClass().getMethod("getModOptions").invoke(config)) {
					if ("v1zir".equals(mod.getClass().getMethod("configId").invoke(mod))) page = ((List<?>) mod.getClass().getMethod("pages").invoke(mod)).get(0);
				}
				Class<?> screen = Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen");
				Class<?> pageType = Class.forName("net.caffeinemc.mods.sodium.client.config.structure.OptionPage");
				return (Screen) screen.getMethod("createScreen", Screen.class, pageType).invoke(null, client.currentScreen, page);
			} catch (ReflectiveOperationException | RuntimeException e) {
				System.out.println("V1ZIR-SODIUM could not open its Video Settings: " + e);
				return client.currentScreen;
			}
		});
		context.waitTicks(20);
		System.out.println("V1ZIR-SODIUM screen: " + context.computeOnClient(client -> String.valueOf(client.currentScreen)));
		context.takeScreenshot("sodium-video-settings-gpu");
		context.runOnClient(client -> {
			MainMenuModule.suspended = true;
			client.setScreen(new TitleScreen());
		});
		context.waitForScreen(TitleScreen.class);
	}

	/**
	 * The PS5 style menu's top bar from the keys (a controller's D-pad): up from the buttons to the row, up again to the
	 * tab shown, right along the bar to the settings icon (V1ZIR-PS5-TOP), whose Enter opens the game's options.
	 */
	private static void ps5TopBar(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_UP);
		context.getInput().pressKey(GLFW.GLFW_KEY_UP);
		context.waitTicks(3);
		int top = context.computeOnClient(client -> client.currentScreen instanceof ConsoleMenu menu
			&& (int) field(menu, ConsoleMenu.class, "focus") == -1 ? (int) field(menu, ConsoleMenu.class, "top") : -100);
		// Left to right: the two tabs, then the power, settings and Messages icons (no Mod Menu here), then the account.
		for (int i = top; i >= 0 && i < 3; i++) context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		context.waitTicks(8);
		int at = context.computeOnClient(client -> client.currentScreen instanceof ConsoleMenu menu ? (int) field(menu, ConsoleMenu.class, "top") : -100);
		context.takeScreenshot("main-menu-ps5-top-bar");
		boolean options = false;
		if (top >= 0 && at == 3) {
			context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
			context.waitTicks(10);
			options = context.computeOnClient(client -> client.currentScreen instanceof OptionsScreen);
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(10);
		}
		System.out.println("V1ZIR-PS5-TOP up to the bar on " + top + ", along to " + at + ", settings opened: " + options);
	}

	/**
	 * A controller's D-pad on the game's screens: on the inventory it goes from slot to slot, on the creative inventory
	 * up to the tabs, on the click GUI from button to button; Video Settings has GPU as its first row. Each says where
	 * it went (V1ZIR-DPAD, V1ZIR-GPU-OPTION); the inventory's steps, the plainest case, must land on the next slot.
	 */
	private static void controllerGui(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("gamemode survival @a");
		context.waitTicks(10);
		context.setScreen(() -> new InventoryScreen(MinecraftClient.getInstance().player));
		context.waitTicks(10);
		String inventory = context.computeOnClient(client -> {
			if (!(client.currentScreen instanceof InventoryScreen screen)) return "no inventory: " + client.currentScreen;
			HandledScreenAccessor panel = (HandledScreenAccessor) screen;
			List<Slot> slots = screen.getScreenHandler().slots;
			// The hotbar's first slot; right is its second, up the first of the inventory's row above.
			Slot from = slots.get(36), right = slots.get(37), up = slots.get(27);
			double x = panel.v1zir$x() + from.x + 8, y = panel.v1zir$y() + from.y + 8;
			float[] r = Controller.dpad(client, screen, x, y, GLFW.GLFW_KEY_RIGHT);
			float[] u = Controller.dpad(client, screen, x, y, GLFW.GLFW_KEY_UP);
			boolean ok = r != null && r[0] == panel.v1zir$x() + right.x + 8 && r[1] == panel.v1zir$y() + right.y + 8
				&& u != null && u[0] == panel.v1zir$x() + up.x + 8 && u[1] == panel.v1zir$y() + up.y + 8;
			return (ok ? "ok" : "WRONG") + " right " + Arrays.toString(r) + " up " + Arrays.toString(u) + " from " + x + ", " + y;
		});
		System.out.println("V1ZIR-DPAD inventory " + inventory);
		if (!inventory.startsWith("ok")) throw new AssertionError("The D-pad did not go from slot to slot: " + inventory);

		singleplayer.getServer().runCommand("gamemode creative @a");
		context.setScreen(() -> null);
		context.waitTicks(10);
		context.getInput().pressKey(GLFW.GLFW_KEY_E);
		context.waitTicks(10);
		String creative = context.computeOnClient(client -> {
			if (!(client.currentScreen instanceof CreativeInventoryScreen)) return "no creative inventory: " + client.currentScreen;
			HandledScreen<?> screen = (HandledScreen<?>) client.currentScreen;
			HandledScreenAccessor panel = (HandledScreenAccessor) screen;
			// From the top left slot up: one of the tabs over the panel.
			Slot top = null;
			for (Slot slot : screen.getScreenHandler().slots) {
				if (slot.isEnabled() && (top == null || slot.y < top.y || slot.y == top.y && slot.x < top.x)) top = slot;
			}
			if (top == null) return "no slots";
			double x = panel.v1zir$x() + top.x + 8, y = panel.v1zir$y() + top.y + 8;
			float[] u = Controller.dpad(client, screen, x, y, GLFW.GLFW_KEY_UP);
			return (u != null && u[1] < panel.v1zir$y() ? "ok" : "WRONG") + " up " + Arrays.toString(u) + " from " + x + ", " + y
				+ " (panel top " + panel.v1zir$y() + ")";
		});
		System.out.println("V1ZIR-DPAD creative " + creative);
		singleplayer.getServer().runCommand("gamemode survival @a");

		// The pause menu: left of its buttons, the round Messages button takes the focus.
		context.setScreen(() -> null);
		context.waitTicks(3);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(10);
		context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		context.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
		context.waitTicks(5);
		String pause = context.computeOnClient(client -> client.currentScreen == null ? "no pause menu"
			: client.currentScreen.getClass().getSimpleName() + ", Messages in focus: " + MessagesUi.isPauseKey(client.currentScreen.getFocused()));
		System.out.println("V1ZIR-DPAD pause " + pause);
		context.takeScreenshot("pause-messages-focus");

		context.setScreen(() -> null);
		context.setScreen(ClickGuiScreen::new);
		context.waitTicks(30);
		String gui = context.computeOnClient(client -> {
			Screen screen = client.currentScreen;
			if (screen == null) return "no screen";
			StringBuilder out = new StringBuilder();
			double x = screen.width / 2.0, y = screen.height / 2.0;
			int reached = 0;
			for (int arrow : new int[]{GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP}) {
				float[] to = Controller.dpad(client, screen, x, y, arrow);
				out.append(' ').append(Arrays.toString(to));
				if (to != null) {
					x = to[0];
					y = to[1];
					reached++;
				}
			}
			return reached + " of 5 steps:" + out;
		});
		System.out.println("V1ZIR-DPAD clickgui " + gui);

		context.setScreen(() -> null);
		context.setScreen(() -> new VideoOptionsScreen(null, MinecraftClient.getInstance(), MinecraftClient.getInstance().options));
		context.waitTicks(10);
		boolean listed = context.computeOnClient(client -> client.currentScreen != null && GpuOption.listedOn(client.currentScreen));
		System.out.println("V1ZIR-GPU-OPTION first row of Video Settings: " + listed);
		context.takeScreenshot("video-settings-gpu");
		context.setScreen(() -> null);
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

	/** Three Visuals modules starred, the Visuals tab on its favourites chip and the mouse resting on the first card (its star and tooltip). */
	@SuppressWarnings("unchecked")
	private static void clickGuiFavourites(ClientGameTestContext context) {
		List<Class<? extends Module>> starred = List.of(ZoomModule.class, FreeLookModule.class, ChinaHatModule.class);
		context.runOnClient(client -> {
			for (Class<? extends Module> type : starred) ModuleManager.get(type).setFavourite(true);
			((Map<Category, String>) field(null, ClickGuiScreen.class, "SUBS")).put(Category.VISUALS, "Favourites");
			setStatic(ClickGuiScreen.class, "tab", Category.VISUALS.ordinal());
		});
		context.setScreen(ClickGuiScreen::new);
		context.waitTicks(30);
		double[] cursor = context.computeOnClient(client -> {
			Object grid = ((Object[]) field(client.currentScreen, ClickGuiScreen.class, "grids"))[Category.VISUALS.ordinal()];
			Object card = ((List<?>) field(grid, grid.getClass(), "visible")).getFirst();
			double scale = client.getWindow().getScaleFactor();
			float x = (float) field(card, card.getClass(), "x");
			float y = (float) field(card, card.getClass(), "y");
			float w = (float) field(card, card.getClass(), "w");
			return new double[]{(x + w * 0.4f) * scale, (y + 20f) * scale};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(25);
		context.takeScreenshot("clickgui-favourites");
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			for (Class<? extends Module> type : starred) ModuleManager.get(type).setFavourite(false);
			((Map<Category, String>) field(null, ClickGuiScreen.class, "SUBS")).put(Category.VISUALS, "All");
		});
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

	/**
	 * Sets the time of day and waits (up to five seconds) until the client's world has it: the server sends the time
	 * on its own schedule, so a shot right after the command can still show the old one.
	 */
	private static void setTime(ClientGameTestContext context, TestSingleplayerContext singleplayer, int time) {
		singleplayer.getServer().runCommand("time set " + time);
		for (int i = 0; i < 100; i++) {
			boolean there = context.computeOnClient(client -> {
				long now = Math.floorMod(client.world.getTimeOfDay(), 24000L);
				long diff = Math.abs(now - time);
				return Math.min(diff, 24000L - diff) < 400L;
			});
			if (there) return;
			context.waitTicks(1);
		}
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

	private static void invoke(Object target, Class<?> owner, String name) {
		try {
			Method method = owner.getDeclaredMethod(name);
			method.setAccessible(true);
			method.invoke(target);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Cannot call " + owner.getSimpleName() + "." + name, e);
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
