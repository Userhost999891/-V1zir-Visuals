package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.chat.Emojis;
import dev.v1zir.visuals.gui.chat.EmojiSuggestions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Emoji in chat: :shortcodes: and typed glyphs go out as real emoji; the suggestion list over the chat box. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Shadow
	protected TextFieldWidget chatField;

	@ModifyVariable(method = "sendMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String v1zir$emoji(String text) {
		return Emojis.outgoing(text);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void v1zir$emojiSuggestions(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		EmojiSuggestions.render(ctx, chatField, MinecraftClient.getInstance().textRenderer, mouseX, mouseY);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void v1zir$emojiKeys(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
		if (EmojiSuggestions.keyPressed(input.key(), chatField)) cir.setReturnValue(true);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void v1zir$emojiClick(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (EmojiSuggestions.mouseClicked(click.x(), click.y(), chatField)) cir.setReturnValue(true);
	}
}
