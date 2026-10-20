package dev.v1zir.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.v1zir.visuals.gui.custom.CustomInventory;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom UI inventory: Theme-style panel, recessed slots, a spring hover glow on slots and light labels. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
	@Shadow
	protected int x;
	@Shadow
	protected int y;
	@Shadow
	protected int backgroundWidth;
	@Shadow
	protected int backgroundHeight;
	@Shadow
	@Final
	protected ScreenHandler handler;
	@Shadow
	@Nullable
	protected Slot focusedSlot;

	protected HandledScreenMixin(Text title) {
		super(title);
	}

	// drawBackground is called from renderBackground; renderMain is covered too in case it moves.
	@WrapOperation(method = {"renderBackground", "renderMain"}, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V"))
	private void v1zir$drawBackground(HandledScreen<?> screen, DrawContext context, float deltaTicks, int mouseX, int mouseY,
									   Operation<Void> original) {
		if (CustomInventory.handles(screen)) {
			CustomInventory.drawBackground(context, screen, handler, x, y, backgroundWidth, backgroundHeight, mouseX, mouseY);
		} else {
			original.call(screen, context, deltaTicks, mouseX, mouseY);
		}
	}

	@Unique
	private CustomInventory.SlotGlow v1zir$slotGlow;

	// Called every frame from renderMain (translated to the container), right after focusedSlot is updated.
	@Inject(method = "drawSlotHighlightBack", at = @At("HEAD"), cancellable = true)
	private void v1zir$slotHighlightBack(DrawContext context, CallbackInfo ci) {
		if (!CustomInventory.handles(this)) return;
		if (v1zir$slotGlow == null) v1zir$slotGlow = new CustomInventory.SlotGlow();
		v1zir$slotGlow.render(context, focusedSlot);
		ci.cancel();
	}

	@Inject(method = "drawSlotHighlightFront", at = @At("HEAD"), cancellable = true)
	private void v1zir$slotHighlightFront(DrawContext context, CallbackInfo ci) {
		if (CustomInventory.handles(this)) ci.cancel();
	}

	/** The dark grey vanilla labels are unreadable on the dark panel. */
	@ModifyArg(method = "drawForeground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"), index = 4)
	private int v1zir$labelColor(int color) {
		return color == CustomInventory.VANILLA_LABEL && CustomInventory.handles(this) ? CustomInventory.labelColor() : color;
	}
}
