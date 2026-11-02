package dev.v1zir.visuals.render.cosmetic;

import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.cosmetics.CosmeticsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;

/**
 * Cosmetics on players: the hat on the head, wings and the cape on the back, following the model's pose (looking
 * around, sneaking, swimming, flying) because they are drawn in the space of its head and body. Runs wherever the
 * player model is drawn, so also in the inventory and on the Cosmetics tab, and never in first person, where the
 * model is not drawn. Other players only when the module says so.
 */
public final class CosmeticsFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	public CosmeticsFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle,
					   float limbDistance) {
		CosmeticsModule module = ModuleManager.get(CosmeticsModule.class);
		if (module == null || !module.isEnabled() || state.invisible) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean self = mc.player != null && state.id == mc.player.getId();
		if (!self && !module.others.on()) return;
		Cosmetic hat = module.item(Cosmetic.Slot.HAT);
		Cosmetic wings = module.item(Cosmetic.Slot.WINGS);
		Cosmetic cape = module.item(Cosmetic.Slot.CAPE);
		if (hat != null && module.hideUnderHelmet.on() && !state.equippedHeadStack.isEmpty()) hat = null;
		// An elytra takes the back.
		if (state.equippedChestStack.isOf(Items.ELYTRA)) {
			wings = null;
			cape = null;
		}
		if (hat == null && wings == null && cape == null) return;
		CosmeticMotion motion = CosmeticMotion.of(state.id, state.x, state.y, state.z, state.bodyYaw, state.isInSneakingPose, state.isGliding,
			state.isSwimming);
		PlayerEntityModel model = getContextModel();
		// Pose the shared model for this player now: it is posed again when the queue draws it.
		model.setAngles(state);
		ModelPart root = model.getRootPart();
		if (hat != null) {
			matrices.push();
			root.applyTransform(matrices);
			model.head.applyTransform(matrices);
			CosmeticRenderer.submit(matrices, queue, hat, light, motion);
			matrices.pop();
		}
		if (wings != null || cape != null) {
			matrices.push();
			root.applyTransform(matrices);
			model.body.applyTransform(matrices);
			if (wings != null) CosmeticRenderer.submit(matrices, queue, wings, light, motion);
			if (cape != null) {
				// Over a chestplate the cape hangs a little further back.
				if (!state.equippedChestStack.isEmpty()) matrices.translate(0f, 0f, 1f / 16f);
				CosmeticRenderer.submit(matrices, queue, cape, light, motion);
			}
			matrices.pop();
		}
	}

	/** Whether this player wears a cosmetic cape, which then hides the vanilla one (CapeFeatureRendererMixin). */
	public static boolean wearsCape(PlayerEntityRenderState state) {
		CosmeticsModule module = ModuleManager.get(CosmeticsModule.class);
		if (module == null || !module.isEnabled() || module.item(Cosmetic.Slot.CAPE) == null) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean self = mc.player != null && state.id == mc.player.getId();
		return (self || module.others.on()) && !state.equippedChestStack.isOf(Items.ELYTRA);
	}
}
