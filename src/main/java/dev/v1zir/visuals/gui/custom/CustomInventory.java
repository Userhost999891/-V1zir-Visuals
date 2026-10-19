package dev.v1zir.visuals.gui.custom;

import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HopperScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * Custom UI inventory: the vanilla container texture is replaced by a Theme-style panel with
 * rounded slot frames. Only screens whose background is nothing but the texture (plus the player
 * model in the survival inventory) are restyled, so no vanilla element is lost.
 */
public final class CustomInventory {
	/** Vanilla's dark grey label colour; on our dark panels it is swapped for a light one. */
	public static final int VANILLA_LABEL = 0xFF404040;

	private CustomInventory() {
	}

	public static boolean handles(Screen screen) {
		return CustomUi.inventory() && (screen instanceof InventoryScreen || screen instanceof GenericContainerScreen
			|| screen instanceof ShulkerBoxScreen || screen instanceof Generic3x3ContainerScreen || screen instanceof HopperScreen);
	}

	public static int labelColor() {
		return ColorUtil.withAlpha(Theme.text(), 230);
	}

	/** Replaces drawBackground: panel, slot frames and, in the survival inventory, the player model. */
	public static void drawBackground(DrawContext ctx, Screen screen, ScreenHandler handler, int x, int y, int width, int height,
									  int mouseX, int mouseY) {
		CustomUi.containerPanel(ctx, x, y, width, height);
		for (Slot slot : handler.slots) {
			if (slot.isEnabled()) CustomUi.slot(ctx, x + slot.x, y + slot.y);
		}
		if (screen instanceof InventoryScreen) {
			// Player preview box and the crafting arrow, as in the vanilla layout.
			CustomUi.frame(ctx, x + 26, y + 8, 49, 70);
			Render2D.icon(ctx, Icons.CHEVRON_RIGHT, x + 144.5f, y + 36.5f, 11f, ColorUtil.withAlpha(Theme.text(), 170));
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				InventoryScreen.drawEntity(ctx, x + 26, y + 8, x + 75, y + 78, 30, 0.0625f, mouseX, mouseY, client.player);
			}
		}
	}
}
