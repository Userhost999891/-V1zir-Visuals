package dev.v1zir.visuals.mixin;

import dev.v1zir.visuals.chat.Emojis;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Emoji in chat lines show in colour: their sequences become the emoji font's glyphs (Emojis). */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
	@ModifyVariable(
		method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Text v1zir$emoji(Text message) {
		return Emojis.toGlyphs(message);
	}
}
