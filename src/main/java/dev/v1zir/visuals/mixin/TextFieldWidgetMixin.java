package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.v1zir.visuals.gui.custom.CustomUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Custom UI: text fields sit in a rounded inset box that lights up in the accent colour when focused. */
@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetMixin {
	// The only sprite renderWidget draws is the background, and only when drawsBackground() is set;
	// the text, selection and cursor that follow stay vanilla.
	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"))
	private void v1zir$background(DrawContext context, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height,
								  Operation<Void> original) {
		if (!CustomUi.textFields()) {
			original.call(context, pipeline, sprite, x, y, width, height);
			return;
		}
		ClickableWidget widget = (ClickableWidget) (Object) this;
		CustomUi.textField(context, x, y, width, height, widget.isFocused(), widget.isHovered(), widget.isInteractable());
	}
}
