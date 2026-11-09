package dev.v1zir.visuals.gui.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.v1zir.visuals.V1zir;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;

/**
 * The picture of the world or server played last ({@link LastPlayed#icon}) as a texture for the lock screen's widget
 * and the menu's Continue pill: read when the menu opens, uploaded again only when it changed. Render thread only.
 */
final class LastPlayedIcon {
	@Nullable
	private static NativeImageBackedTexture texture;
	@Nullable
	private static TextureSetup setup;
	@Nullable
	private static byte[] shown;

	private LastPlayedIcon() {
	}

	/** Reads the icon again (the menu opened or the last world or server changed). */
	static void refresh(MinecraftClient client) {
		byte[] bytes = LastPlayed.icon(client);
		if (Arrays.equals(bytes, shown)) return;
		release();
		shown = bytes;
		if (bytes == null) return;
		NativeImage image = null;
		try {
			image = NativeImage.read(bytes);
			texture = new NativeImageBackedTexture(() -> "V1zir last played icon", image);
			setup = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
		} catch (IOException | RuntimeException e) {
			if (texture == null && image != null) image.close();
			V1zir.LOGGER.debug("Could not read the icon of the world or server played last", e);
			release();
			shown = bytes;
		}
	}

	/** The icon, or null when there is none (the widgets draw their own symbol then). */
	@Nullable
	static TextureSetup get() {
		return setup;
	}

	private static void release() {
		if (texture != null) texture.close();
		texture = null;
		setup = null;
		shown = null;
	}
}
