package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.gui.custom.CustomUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipBackgroundRenderer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom UI: tooltips sit on a Theme-style panel with an accent edge. */
@Mixin(TooltipBackgroundRenderer.class)
public abstract class TooltipBackgroundRendererMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private static void v1zir$render(DrawContext context, int x, int y, int width, int height, @Nullable Identifier texture, CallbackInfo ci) {
		if (!CustomUi.tooltips()) return;
		CustomUi.tooltip(context, x, y, width, height);
		ci.cancel();
	}
}
