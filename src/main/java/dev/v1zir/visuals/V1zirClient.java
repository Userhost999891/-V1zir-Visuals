package dev.v1zir.visuals;

import dev.v1zir.visuals.render.cosmetic.CosmeticsFeature;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;
import dev.v1zir.visuals.config.ConfigManager;
import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.chat.Messages;
import dev.v1zir.visuals.gui.hud.HudRenderer;
import dev.v1zir.visuals.gui.messages.MessagesUi;
import dev.v1zir.visuals.gui.menu.MainMenus;
import dev.v1zir.visuals.gui.menu.MenuShots;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.pack.PackMixer;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.module.impl.hud.VoiceChatMusicModule;
import dev.v1zir.visuals.optimize.GpuCheck;
import dev.v1zir.visuals.optimize.GpuOption;
import dev.v1zir.visuals.optimize.HitchMonitor;
import dev.v1zir.visuals.optimize.Optimizations;
import dev.v1zir.visuals.optimize.ThreadTuning;
import dev.v1zir.visuals.render.Reflections;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.world.CobwebModel;
import dev.v1zir.visuals.render.world.Effects;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.lwjgl.glfw.GLFW;

public final class V1zirClient implements ClientModInitializer {
	private static int threadTicks;
	@Override
	public void onInitializeClient() {
		V1zirPipelines.register();
		ModuleManager.init();
		ConfigManager.load();
		// Before the first resource load, so the textures picked in the Packs tab show from the start.
		PackMixer.load();
		Optimizations.logCompatibility();
		HudRenderer.register();
		MessagesUi.register();
		// The cobweb's model, wrapped so Cobwebs can draw it translucent (it stays vanilla's while the module is off).
		ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register((model, context) ->
			context.state().isOf(Blocks.COBWEB) ? new CobwebModel(model) : model));
		// Cosmetics ride on the player model as one more feature of the player renderers.
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (entityType == EntityType.PLAYER && renderer instanceof PlayerEntityRenderer<?> players) helper.register(new CosmeticsFeature(players));
		});

		HitchMonitor.start();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			long begun = HitchMonitor.begin();
			// Safety net for any code path that shows the vanilla title screen without setScreen.
			if (client.currentScreen instanceof TitleScreen && MainMenuModule.active()) {
				client.setScreen(MainMenus.create());
			}
			ModuleManager.tick();
			// New background threads (pools start them as needed) get their priority every ten seconds.
			if (++threadTicks % 200 == 0) ThreadTuning.apply();
			Reflections.tick(client);
			Effects.tick(client);
			HudRenderer.tick(client);
			Messages.tick(client);
			MenuShots.tick(client);
			VoiceChatMusicModule.idleTick();
			HitchMonitor.end(HitchMonitor.TICK, begun);
		});
		// Once the game has started (its options are loaded): Unlock frame rate, a single time.
		ClientLifecycleEvents.CLIENT_STARTED.register(Optimizations::unlockFrameRate);
		// The render thread first: raised once, background workers lowered a step.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> ThreadTuning.apply());
		// Laptops: a dedicated graphics card the game does not use is worth saying at once.
		ClientLifecycleEvents.CLIENT_STARTED.register(GpuCheck::check);
		GpuOption.register();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ConfigManager.save();
			// Contacts see this account go offline now, not when the relay notices the dropped connection.
			Messages.stop();
		});
		V1zir.LOGGER.info("{} {} loaded", V1zir.NAME, V1zir.version());
	}

	/** Global key handler (KeyboardMixin). Returns true to swallow the key. */
	public static boolean onKey(int key, int action) {
		if (action != GLFW.GLFW_PRESS || key == GLFW.GLFW_KEY_UNKNOWN) return false;
		MinecraftClient mc = V1zir.mc();
		if (mc.currentScreen != null || mc.world == null) return false;
		ClickGuiModule gui = ModuleManager.get(ClickGuiModule.class);
		if (gui != null && key == gui.key()) {
			mc.setScreen(new ClickGuiScreen());
			return true;
		}
		ModuleManager.onKey(key);
		return false;
	}
}
