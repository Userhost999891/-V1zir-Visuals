package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.gui.menu.V1zirMainMenu;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every time vanilla opens the title screen, ours is opened instead. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Shadow
	public ClientWorld world;
	@Shadow
	@Final
	public GameOptions options;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen v1zir$replaceTitleScreen(Screen screen) {
		if (!MainMenuModule.active()) return screen;
		// On first launch vanilla shows its accessibility onboarding screen (panorama, logo and
		// vanilla buttons) before the title screen; mark it as seen and go straight to ours.
		if (screen instanceof AccessibilityOnboardingScreen) {
			options.setAccessibilityOnboarded();
			return new V1zirMainMenu();
		}
		// setScreen(null) outside a world makes vanilla build a new TitleScreen further down in
		// this method, after this hook, so that case is replaced here as well.
		if (screen instanceof TitleScreen || screen == null && world == null) return new V1zirMainMenu();
		return screen;
	}
}
