package dev.v1zir.visuals.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.v1zir.visuals.controller.Targets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What the game's inventories draw as buttons without being widgets (the creative tabs, the enchantments on offer,
 * the stonecutter's recipes, the loom's patterns) is a place for the D-pad, where it is drawn.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
	@Inject(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V", at = @At("HEAD"))
	private void v1zir$target(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, CallbackInfo ci) {
		if (Targets.collecting() && pressable(sprite.getPath())) Targets.addDrawn((DrawContext) (Object) this, x, y, width, height);
	}

	private static boolean pressable(String path) {
		if (!path.startsWith("container/")) return false;
		return path.startsWith("container/creative_inventory/tab_")
			|| path.startsWith("container/enchanting_table/enchantment_slot") && !path.endsWith("disabled")
			|| path.startsWith("container/stonecutter/recipe")
			|| path.startsWith("container/loom/pattern");
	}
}
