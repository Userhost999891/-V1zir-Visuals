package dev.v1zir.visuals.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where an inventory's panel is on the screen: its slots' places are relative to it (a controller's D-pad goes to them). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
	@Accessor("x")
	int v1zir$x();

	@Accessor("y")
	int v1zir$y();
}
