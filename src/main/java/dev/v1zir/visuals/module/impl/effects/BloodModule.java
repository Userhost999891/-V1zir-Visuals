package dev.v1zir.visuals.module.impl.effects;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.world.Effects;
import dev.v1zir.visuals.render.world.Splatter;
import dev.v1zir.visuals.render.world.WorldEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;

/**
 * Blood on every hit: droplets spray away from you, fall with gravity and leave small stains on the
 * ground that dry darker and fade (render/world/Splatter). Only living things bleed, not armour stands.
 */
public final class BloodModule extends Module implements WorldEffect {
	public final NumberSetting amount = add(new NumberSetting("Amount", 16, 4, 40, 1));
	public final NumberSetting dropSize = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05).suffix("x"));
	public final BoolSetting stains = add(new BoolSetting("Stains", true));
	public final NumberSetting stainTime = add(new NumberSetting("Stain time", 6, 2, 20, 1).suffix("s").visibleIf(stains::on));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFA30A16, false));

	private final Splatter splatter = new Splatter();
	private long lastFrame = System.nanoTime();

	public BloodModule() {
		super("Blood", "Blood droplets and stains on hits", Category.COMBAT);
		setSubCategory("Hits");
	}

	@Override
	protected void onDisable() {
		splatter.clear();
	}

	/** You hit the entity (Effects). */
	public void spawn(Entity entity) {
		if (!(entity instanceof LivingEntity) || entity instanceof ArmorStandEntity || mc.player == null) return;
		double dx = entity.getX() - mc.player.getX();
		double dz = entity.getZ() - mc.player.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		float pushX = length > 1e-3 ? (float) (dx / length) : 0f;
		float pushZ = length > 1e-3 ? (float) (dz / length) : 0f;
		double ground = Effects.groundBelow(entity.getX(), entity.getY() + 0.1, entity.getZ());
		if (Double.isNaN(ground)) ground = entity.getY();
		splatter.spawn(entity.getX(), entity.getY() + entity.getHeight() * 0.6, entity.getZ(), ground, amount.i(), 2.4f, dropSize.f(), pushX, pushZ);
	}

	@Override
	public void render3D(float tickDelta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1.0e9f, 0.05f);
		lastFrame = now;
		splatter.render(dt, color.argb(), stains.on() ? stainTime.f() : 0f);
	}
}
