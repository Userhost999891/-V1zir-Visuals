package dev.v1zir.visuals.gui.custom;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;

/**
 * Custom UI hotbar: a Theme-style bar with rounded slots and an accent selection that glides
 * between them. It keeps vanilla's footprint and item positions, so the health, food and
 * experience bars above it stay where they always are.
 */
public final class CustomHotbar {
	private static final Anim SELECTION = new Anim(0f, 22f);
	private static final Anim COOLDOWN = new Anim(0f, 16f);
	private static boolean placed;

	private CustomHotbar() {
	}

	public static void render(DrawContext ctx, RenderTickCounter tickCounter, PlayerEntity player) {
		ThemeModule theme = ModuleManager.get(ThemeModule.class);
		if (theme != null) theme.apply();
		MinecraftClient client = MinecraftClient.getInstance();
		int cx = ctx.getScaledWindowWidth() / 2;
		int h = ctx.getScaledWindowHeight();
		float alpha = Render2D.alpha();
		Render2D.setAlpha(1f);

		int selected = player.getInventory().getSelectedSlot();
		if (!placed) {
			SELECTION.snap(selected);
			placed = true;
		}
		float sel = SELECTION.target(selected).get();

		// Bar and slots.
		float barX = cx - 91f;
		float barY = h - 23f;
		Render2D.shadow(ctx, barX, barY + 1.5f, 182f, 22f, 7f, 10f, 0x50000000);
		Render2D.panel(ctx, barX, barY, 182f, 22f, 7f);
		for (int i = 0; i < 9; i++) slot(ctx, cx - 89 + i * 20, h - 21);
		selection(ctx, cx - 89 + sel * 20f, h - 21);

		// Off hand, on the side vanilla puts it.
		ItemStack offHand = player.getOffHandStack();
		Arm offSide = player.getMainArm().getOpposite();
		if (!offHand.isEmpty()) {
			float ox = offSide == Arm.LEFT ? cx - 120f : cx + 98f;
			Render2D.shadow(ctx, ox, barY + 1.5f, 22f, 22f, 7f, 10f, 0x50000000);
			Render2D.panel(ctx, ox, barY, 22f, 22f, 7f);
			slot(ctx, ox + 2f, h - 21);
		}

		// Items exactly where vanilla draws them.
		for (int i = 0; i < 9; i++) item(ctx, client, tickCounter, player, player.getInventory().getStack(i), cx - 88 + i * 20, h - 19, i + 1);
		if (!offHand.isEmpty()) item(ctx, client, tickCounter, player, offHand, offSide == Arm.LEFT ? cx - 117 : cx + 101, h - 19, 10);

		// Attack cooldown, when vanilla's indicator is set to the hotbar: a slim pill that fills up.
		if (client.options.getAttackIndicator().getValue() == AttackIndicator.HOTBAR) {
			float progress = player.getAttackCooldownProgress(0f);
			float shown = COOLDOWN.target(progress < 1f ? 1f : 0f).get();
			if (shown > 0.01f) {
				// Opposite the off hand slot, as in vanilla.
				float ix = offSide == Arm.RIGHT ? cx - 91f - 12f : cx + 91f + 6f;
				Render2D.setAlpha(shown);
				Render2D.round(ctx, ix, h - 21f, 6f, 18f, 3f, ColorUtil.withAlpha(0x000000, 110));
				float fill = 18f * Math.max(0f, Math.min(1f, progress));
				if (fill > 0.5f) {
					Render2D.gradientV(ctx, ix, h - 21f + 18f - fill, 6f, fill, 3f, Theme.accent2(), Theme.accent());
				}
				Render2D.outline(ctx, ix, h - 21f, 6f, 18f, 3f, 1f, ColorUtil.withAlpha(0xFFFFFF, 30));
				Render2D.setAlpha(1f);
			}
		}
		Render2D.setAlpha(alpha);
	}

	private static void slot(DrawContext ctx, float x, float y) {
		Render2D.round(ctx, x, y, 18f, 18f, 5f, ColorUtil.withAlpha(0x000000, 70));
		Render2D.outline(ctx, x, y, 18f, 18f, 5f, 1f, ColorUtil.withAlpha(0xFFFFFF, 20), ColorUtil.withAlpha(0xFFFFFF, 20),
			ColorUtil.withAlpha(0xFFFFFF, 8), ColorUtil.withAlpha(0xFFFFFF, 8));
	}

	private static void selection(DrawContext ctx, float x, float y) {
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		Render2D.glow(ctx, x, y, 18f, 18f, 5f, 7f, ColorUtil.withAlpha(accent, 90), ColorUtil.withAlpha(accent2, 90));
		Render2D.round(ctx, x, y, 18f, 18f, 5f, ColorUtil.withAlpha(accent, 70), ColorUtil.withAlpha(accent2, 50),
			ColorUtil.withAlpha(accent2, 30), ColorUtil.withAlpha(accent, 45));
		Render2D.outline(ctx, x, y, 18f, 18f, 5f, 1f, ColorUtil.withAlpha(accent, 240), ColorUtil.withAlpha(accent2, 230),
			ColorUtil.withAlpha(accent2, 150), ColorUtil.withAlpha(accent, 170));
	}

	/** Vanilla's renderHotbarItem, including the little squash of a freshly picked up stack. */
	private static void item(DrawContext ctx, MinecraftClient client, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack,
							 int x, int y, int seed) {
		if (stack.isEmpty()) return;
		float bob = stack.getBobbingAnimationTime() - tickCounter.getTickProgress(false);
		if (bob > 0f) {
			float s = 1f + bob / 5f;
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(x + 8f, y + 12f);
			ctx.getMatrices().scale(1f / s, (s + 1f) / 2f);
			ctx.getMatrices().translate(-(x + 8f), -(y + 12f));
		}
		ctx.drawItem(player, stack, x, y, seed);
		if (bob > 0f) ctx.getMatrices().popMatrix();
		ctx.drawStackOverlay(client.textRenderer, stack, x, y);
	}
}
