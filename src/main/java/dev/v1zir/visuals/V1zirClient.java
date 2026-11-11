package dev.v1zir.visuals;

import dev.v1zir.visuals.render.cosmetic.CosmeticsFeature;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;
import dev.v1zir.visuals.config.ConfigManager;
import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.gui.hud.HudRenderer;
import dev.v1zir.visuals.gui.menu.V1zirMainMenu;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.pack.PackMixer;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.optimize.Optimizations;
import dev.v1zir.visuals.render.Reflections;
import dev.v1zir.visuals.render.V1zirPipelines;
import dev.v1zir.visuals.render.world.Effects;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.lwjgl.glfw.GLFW;

public final class V1zirClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		V1zirPipelines.register();
		ModuleManager.init();
		ConfigManager.load();
		// Before the first resource load, so the textures picked in the Packs tab show from the start.
		PackMixer.load();
		Optimizations.logCompatibility();
		HudRenderer.register();
		// Cosmetics ride on the player model as one more feature of the player renderers.
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (entityType == EntityType.PLAYER && renderer instanceof PlayerEntityRenderer<?> players) helper.register(new CosmeticsFeature(players));
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Safety net for any code path that shows the vanilla title screen without setScreen.
			if (client.currentScreen instanceof TitleScreen && MainMenuModule.active()) {
				client.setScreen(new V1zirMainMenu());
			}
			ModuleManager.tick();
			Reflections.tick(client);
			Effects.tick(client);
			HudRenderer.tick(client);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());
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
