package dev.v1zir.visuals.gui.messages;

import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Heads;
import dev.v1zir.visuals.render.ui.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;

import java.util.Locale;

/**
 * A contact's picture: their skin's face while they are on the same server (from the player list), otherwise their
 * initial on a gradient of a hue of their own, as messengers do.
 */
final class Avatar {
	private Avatar() {
	}

	static void draw(net.minecraft.client.gui.DrawContext ctx, String name, float x, float y, float size, float radius) {
		SkinTextures skin = skin(name);
		if (skin != null) {
			Heads.skin(ctx, skin, x, y, size, radius, 0xFFFFFFFF);
			return;
		}
		float hue = (name.toLowerCase(Locale.ROOT).hashCode() & 0xFFFF) / 65536f;
		Render2D.round(ctx, x, y, size, size, radius, hsv(hue, 0.55f, 0.95f), hsv(hue + 0.07f, 0.6f, 0.9f), hsv(hue + 0.1f, 0.75f, 0.7f),
			hsv(hue + 0.03f, 0.7f, 0.78f));
		SdfFont font = Fonts.semibold();
		float textSize = size * 0.46f;
		String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
		Render2D.textCentered(ctx, font, initial, x + size / 2f, Render2D.middle(font, y + size / 2f, textSize), textSize, 0xF2FFFFFF);
	}

	private static SkinTextures skin(String name) {
		ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();
		if (network == null) return null;
		PlayerListEntry entry = network.getPlayerListEntry(name);
		return entry != null ? entry.getSkinTextures() : null;
	}

	private static int hsv(float h, float s, float v) {
		h = (h % 1f + 1f) % 1f * 6f;
		int i = (int) h;
		float f = h - i;
		float p = v * (1f - s);
		float q = v * (1f - s * f);
		float t = v * (1f - s * (1f - f));
		float r, g, b;
		switch (i) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return 0xFF000000 | Math.round(r * 255f) << 16 | Math.round(g * 255f) << 8 | Math.round(b * 255f);
	}
}
