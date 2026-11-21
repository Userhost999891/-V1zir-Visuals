package dev.v1zir.visuals.gui.messages;

import dev.v1zir.visuals.account.Accounts;
import dev.v1zir.visuals.chat.Contact;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.ui.Heads;
import dev.v1zir.visuals.render.ui.ImageElement;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.entity.player.SkinTextures;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A picture of someone in Messages: the one they chose for their profile, else their head as the game draws it (their
 * skin's face and hat, from the player list on the same server or fetched by name, {@link Faces}), the default head
 * for names without a skin of their own, a quiet placeholder while it is on its way.
 */
public final class Avatar {
	@Nullable
	private static Supplier<SkinTextures> own;
	private static int ownRevision = -1;

	private Avatar() {
	}

	/** A contact's picture, in a rounded square (radius; half the size is a circle). */
	public static void draw(DrawContext ctx, Contact contact, float x, float y, float size, float radius) {
		draw(ctx, contact.name(), contact.uuid(), contact.avatar(), x, y, size, radius);
	}

	/** The picture of a name: the PNG they chose (null for none), else their head. */
	public static void draw(DrawContext ctx, String name, @Nullable UUID uuid, @Nullable byte[] picture, float x, float y, float size, float radius) {
		if (picture != null) {
			TextureSetup chosen = Faces.picture(picture);
			if (chosen != null) {
				Render2D.image(ctx, chosen, x, y, size, size, radius, 0xFFFFFFFF);
				return;
			}
		}
		SkinTextures listed = listed(name);
		if (listed != null) {
			Heads.skin(ctx, listed, x, y, size, radius, 0xFFFFFFFF);
			return;
		}
		TextureSetup face = Faces.face(name, uuid);
		if (face != null) {
			Render2D.image(ctx, face, x, y, size, size, radius, 0xFFFFFFFF, 0f, 0f, 1f, 1f, ImageElement.PIXEL_ART);
			return;
		}
		SkinTextures fallback = Faces.loading(name) ? null : Faces.fallback(name);
		if (fallback != null) Heads.skin(ctx, fallback, x, y, size, radius, 0xFFFFFFFF);
		else placeholder(ctx, x, y, size, radius);
	}

	/** This account's own picture: the one it chose for Messages (null for none), else its own head. */
	public static void drawOwn(DrawContext ctx, @Nullable byte[] picture, float x, float y, float size, float radius) {
		if (picture != null) {
			TextureSetup chosen = Faces.picture(picture);
			if (chosen != null) {
				Render2D.image(ctx, chosen, x, y, size, size, radius, 0xFFFFFFFF);
				return;
			}
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (own == null || ownRevision != Accounts.revision()) {
			ownRevision = Accounts.revision();
			own = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);
		}
		SkinTextures skin = own.get();
		if (skin != null) Heads.skin(ctx, skin, x, y, size, radius, 0xFFFFFFFF);
		else placeholder(ctx, x, y, size, radius);
	}

	private static void placeholder(DrawContext ctx, float x, float y, float size, float radius) {
		Render2D.round(ctx, x, y, size, size, radius, ColorUtil.withAlpha(0xFFFFFF, 28));
		Render2D.icon(ctx, Icons.USER, x + size / 2f, y + size / 2f, size * 0.5f, 0x80FFFFFF);
	}

	/** Their skin from the player list, while they are on the same server. */
	@Nullable
	private static SkinTextures listed(String name) {
		ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();
		if (network == null) return null;
		PlayerListEntry entry = network.getPlayerListEntry(name);
		return entry != null ? entry.getSkinTextures() : null;
	}
}
