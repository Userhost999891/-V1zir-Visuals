package dev.v1zir.visuals.module.impl.hud;

import dev.v1zir.visuals.gui.clickgui.ClickGuiScreen;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.Icons;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.ui.Render2D;
import dev.v1zir.visuals.render.world.Effects;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Locale;

/** Panel with the head, name, health and distance of the current target. */
public final class TargetHudModule extends HudModule {
	private final Anim health = new Anim(1f, 8f);
	private final Anim healthLag = new Anim(1f, 2.5f);
	private final Anim appear = new Anim(0f, 10f);
	private LivingEntity last;

	public TargetHudModule() {
		super("Target Hud", "Information about your opponent", 0.56f, 0.6f);
	}

	@Override
	public boolean showInList() {
		return false;
	}

	@Override
	public boolean hasContent() {
		LivingEntity target = Effects.target();
		appear.target(target != null || editing() ? 1f : 0f);
		return appear.get() > 0.01f;
	}

	private boolean editing() {
		return mc.currentScreen instanceof ClickGuiScreen screen && screen.isHudTab();
	}

	@Override
	protected void render(DrawContext ctx, float tickDelta, float x, float y) {
		LivingEntity target = Effects.target();
		if (target == null && editing()) target = mc.player;
		if (target != null) last = target;
		if (last == null) return;
		LivingEntity e = last;

		SdfFont bold = Fonts.semibold();
		SdfFont font = Fonts.medium();
		float w = 128f;
		float h = 38f;
		width = w;
		height = h;
		float a = Anim.easeOutCubic(appear.value());
		Render2D.setAlpha(a);

		float max = Math.max(1f, e.getMaxHealth());
		float hp = Math.max(0f, e.getHealth());
		float absorption = e.getAbsorptionAmount();
		health.target(Math.min(1f, hp / max));
		healthLag.target(Math.min(1f, hp / max));
		float hpFrac = health.get();
		float lagFrac = healthLag.get();

		panel(ctx, x, y, w, h, 7f);
		// Hurt flash.
		if (e.hurtTime > 0) {
			Render2D.round(ctx, x, y, w, h, 7f, ColorUtil.withAlpha(0xFF3B4F, Math.round(e.hurtTime * 7f)));
		}
		float avatar = 26f;
		float ax = x + 6f;
		float ay = y + 6f;
		Render2D.shadow(ctx, ax, ay, avatar, avatar, 5f, 6f, ColorUtil.withAlpha(Theme.accent(), 90));
		boolean drewHead = false;
		if (e instanceof PlayerEntity player && mc.getNetworkHandler() != null) {
			PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
			if (entry != null) {
				float hurtScale = 1f - Math.min(e.hurtTime, 10) * 0.012f;
				ctx.getMatrices().pushMatrix();
				ctx.getMatrices().translate(ax + avatar / 2f, ay + avatar / 2f);
				ctx.getMatrices().scale(hurtScale, hurtScale);
				PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), -(int) (avatar / 2f), -(int) (avatar / 2f), (int) avatar);
				ctx.getMatrices().popMatrix();
				drewHead = true;
			}
		}
		if (!drewHead) {
			Render2D.round(ctx, ax, ay, avatar, avatar, 5f, ColorUtil.withAlpha(Theme.accent(), 45));
			Render2D.icon(ctx, e instanceof PlayerEntity ? Icons.USER : Icons.SKULL, ax + avatar / 2f, ay + avatar / 2f, 14f, Theme.accent());
		}
		Render2D.outline(ctx, ax, ay, avatar, avatar, 5f, 1f, ColorUtil.withAlpha(0xFFFFFF, 30));

		float tx = ax + avatar + 6f;
		float tw = x + w - 6f - tx;
		String name = bold.trim(e.getName().getString(), 8f, tw);
		Render2D.text(ctx, bold, name, tx, y + 5f, 8f, Theme.text());
		String info = String.format(Locale.ROOT, "%.1f HP", hp + absorption);
		String dist = mc.player != null ? String.format(Locale.ROOT, "%.1fm", e.distanceTo(mc.player)) : "";
		Render2D.text(ctx, font, info, tx, y + 15.5f, 6.5f, Theme.textDim());
		float dw = font.width(dist, 6.5f);
		Render2D.text(ctx, font, dist, x + w - 6f - dw, y + 15.5f, 6.5f, Theme.textFaint());

		// Health bar: trailing "damage" part, then the animated gradient fill.
		float bx = tx;
		float by = y + h - 10f;
		float bw = tw;
		float bh = 4f;
		Render2D.round(ctx, bx, by, bw, bh, 2f, ColorUtil.withAlpha(0xFFFFFF, 20));
		if (lagFrac > hpFrac) Render2D.round(ctx, bx, by, bw * lagFrac, bh, 2f, ColorUtil.withAlpha(0xFFFFFF, 70));
		int accent = Theme.accent();
		int accent2 = Theme.accent2();
		Render2D.glow(ctx, bx, by, bw * hpFrac, bh, 2f, 5f, ColorUtil.withAlpha(accent, 90), ColorUtil.withAlpha(accent2, 90));
		Render2D.gradientH(ctx, bx, by, bw * hpFrac, bh, 2f, accent, accent2);
		if (absorption > 0) {
			float af = Math.min(1f, absorption / max);
			Render2D.round(ctx, bx, by - 2.5f, bw * af, 1.5f, 0.75f, 0xFFFFD34D);
		}
		Render2D.setAlpha(1f);
	}
}
