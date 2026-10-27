package dev.v1zir.visuals.gui.hud;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.gui.custom.CustomHotbar;
import dev.v1zir.visuals.gui.custom.CustomUi;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.module.impl.effects.CrosshairModule;
import dev.v1zir.visuals.module.impl.effects.HitMarkerModule;
import dev.v1zir.visuals.module.impl.hud.CustomIndicatorsModule;
import dev.v1zir.visuals.module.impl.hud.HudModule;
import dev.v1zir.visuals.module.impl.visuals.DamageTintModule;
import dev.v1zir.visuals.render.ui.Render2D;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/** Draws every enabled HUD module through Fabric's HUD element registry. */
public final class HudRenderer {
	private HudRenderer() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, V1zir.id("hud"), HudRenderer::render);
		// Screen tints belong with vanilla's vignette and overlays, under the hotbar and the rest of the HUD.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, V1zir.id("damage_tint"), (ctx, tickCounter) -> {
			DamageTintModule tint = ModuleManager.get(DamageTintModule.class);
			if (tint != null && tint.isEnabled() && !V1zir.mc().options.hudHidden) tint.render(ctx);
		});
		// With the chat open, HUD elements can be clicked (the Spotify buttons).
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen) {
				ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !click(click.x(), click.y(), click.button()));
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, vanilla -> (ctx, tickCounter) -> {
			if (CustomUi.hotbar() && V1zir.mc().getCameraEntity() instanceof PlayerEntity player && !player.isSpectator()) {
				CustomHotbar.render(ctx, tickCounter, player);
			} else {
				vanilla.render(ctx, tickCounter);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (ctx, tickCounter) -> {
			CrosshairModule crosshair = ModuleManager.get(CrosshairModule.class);
			if (crosshair != null && crosshair.isEnabled()) crosshair.render(ctx, tickCounter.getTickProgress(false));
			else vanilla.render(ctx, tickCounter);
			HitMarkerModule marker = ModuleManager.get(HitMarkerModule.class);
			if (marker != null && marker.isEnabled()) marker.render(ctx);
		});
		// Custom Indicators draws these rows itself while it is on.
		for (Identifier row : CustomIndicatorsModule.ROWS) {
			HudElementRegistry.replaceElement(row, vanilla -> (ctx, tickCounter) -> {
				CustomIndicatorsModule indicators = ModuleManager.get(CustomIndicatorsModule.class);
				if (indicators == null || !indicators.replaces(row)) vanilla.render(ctx, tickCounter);
			});
		}
	}

	/** Offers a click to the visible HUD elements; true if one used it. */
	public static boolean click(double x, double y, int button) {
		for (HudModule hud : ModuleManager.hud()) {
			if (hud.isEnabled() && hud.hasContent() && hud.mouseClicked(x, y, button)) return true;
		}
		return false;
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
		// In the HUD editor every element is drawn, so empty ones can still be placed.
		boolean editing = mc.currentScreen instanceof ClickGuiScreen screen && screen.isHudEditor();
		for (HudModule hud : ModuleManager.hud()) {
			if (!hud.isEnabled() || !(editing || hud.hasContent())) continue;
			try {
				hud.renderAt(ctx, tickDelta, w, h);
			} catch (RuntimeException e) {
				V1zir.LOGGER.error("HUD element {} failed", hud.name(), e);
				hud.setEnabled(false, false);
			}
		}
		CameraIndicator.render(ctx, w, h);
		Notifications.render(ctx, w, h);
		Render2D.setAlpha(1f);
	}
}
