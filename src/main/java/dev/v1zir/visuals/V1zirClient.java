package dev.v1zir.visuals;

import dev.v1zir.visuals.config.ConfigManager;
import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.gui.hud.HudRenderer;
import dev.v1zir.visuals.gui.menu.V1zirMainMenu;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
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
		HudRenderer.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Safety net for any code path that shows the vanilla title screen without setScreen.
			if (client.currentScreen instanceof TitleScreen && MainMenuModule.active()) {
				client.setScreen(new V1zirMainMenu());
			}
			ModuleManager.tick();
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
