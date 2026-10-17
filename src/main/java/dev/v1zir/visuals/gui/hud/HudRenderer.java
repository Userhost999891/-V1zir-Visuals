package dev.v1zir.visuals.gui.hud;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.module.impl.effects.CrosshairModule;
import dev.v1zir.visuals.module.impl.hud.HudModule;
import dev.v1zir.visuals.render.ui.Render2D;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Draws every enabled HUD module through Fabric's HUD element registry. */
public final class HudRenderer {
	private HudRenderer() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, V1zir.id("hud"), HudRenderer::render);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (ctx, tickCounter) -> {
			CrosshairModule crosshair = ModuleManager.get(CrosshairModule.class);
			if (crosshair != null && crosshair.isEnabled()) crosshair.render(ctx, tickCounter.getTickProgress(false));
			else vanilla.render(ctx, tickCounter);
		});
	}

	public static void tick(MinecraftClient client) {
		Notifications.tick();
	}

	private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		MinecraftClient mc = V1zir.mc();
		if (mc.options.hudHidden || mc.player == null) return;
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme != null) theme.apply();
		float tickDelta = tickCounter.getTickProgress(false);
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		Render2D.setAlpha(1f);
		for (Module module : ModuleManager.all()) {
			if (!(module instanceof HudModule hud) || !hud.isEnabled() || !hud.hasContent()) continue;
			try {
				hud.renderAt(ctx, tickDelta, w, h);
			} catch (RuntimeException e) {
				V1zir.LOGGER.error("HUD element {} failed", hud.name(), e);
				hud.setEnabled(false, false);
			}
		}
		Notifications.render(ctx, w, h);
		Render2D.setAlpha(1f);
	}
}
