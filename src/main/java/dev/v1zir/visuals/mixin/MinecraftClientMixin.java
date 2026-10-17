package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.gui.menu.V1zirMainMenu;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every time vanilla opens the title screen, ours is opened instead. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen v1zir$replaceTitleScreen(Screen screen) {
		if (screen instanceof TitleScreen && ModuleManager.isEnabled(MainMenuModule.class)) return new V1zirMainMenu();
		return screen;
	}
}
