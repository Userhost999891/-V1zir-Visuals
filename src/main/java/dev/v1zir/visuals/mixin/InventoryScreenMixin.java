package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.gui.custom.CustomInventory;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The survival inventory draws its own "Crafting" label in vanilla's dark grey. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	@ModifyArg(method = "drawForeground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"), index = 4)
	private int v1zir$labelColor(int color) {
		return color == CustomInventory.VANILLA_LABEL && CustomInventory.handles((InventoryScreen) (Object) this) ? CustomInventory.labelColor() : color;
	}
}
