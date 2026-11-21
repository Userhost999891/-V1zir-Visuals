package dev.v1zir.visuals.compat.voicechat;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

/**
 * Makes the plugin entrypoint Simple Voice Chat asks for (fabric.mod.json, "voicechat"): a proxy of whatever plugin
 * interface it asks with, so the mod builds and runs without voice chat's API. Only used when voice chat is installed.
 */
public final class VoiceChatAdapter implements LanguageAdapter {
	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		if (!type.isInterface()) throw new LanguageAdapterException(type.getName() + " is not an interface");
		return type.cast(VoiceChatBridge.plugin(type));
	}
}
