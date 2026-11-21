package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;

/**
 * Hides or tones down vanilla overlays and clutter. Every option only changes what your client
 * draws: the hooks sit in the overlay, HUD, weather, particle and entity render mixins.
 */
public final class NoRenderModule extends Module {
	/** Fire quads sit at z = -0.5 in view space, where the screen is about 0.7 units tall. */
	private static final float FIRE_TRAVEL = 0.4f;

	public final ModeSetting fire = add(new ModeSetting("Fire", "Lower", "Vanilla", "Lower", "Hide"));
	public final NumberSetting fireOffset = add(new NumberSetting("Fire offset", 60, 10, 100, 5).suffix("%").visibleIf(() -> fire.is("Lower")));
	public final BoolSetting pumpkin = add(new BoolSetting("Pumpkin", true));
	public final BoolSetting portal = add(new BoolSetting("Portal", true));
	public final BoolSetting nausea = add(new BoolSetting("Nausea", true));
	public final BoolSetting underwater = add(new BoolSetting("Underwater", true));
	public final BoolSetting inWall = add(new BoolSetting("In wall", true));
	public final BoolSetting vignette = add(new BoolSetting("Vignette", false));
	public final BoolSetting spyglass = add(new BoolSetting("Spyglass", false));
	public final BoolSetting bossBars = add(new BoolSetting("Boss bars", false));
	public final BoolSetting scoreboard = add(new BoolSetting("Scoreboard", false));
	public final BoolSetting weather = add(new BoolSetting("Weather", false));
	public final BoolSetting explosions = add(new BoolSetting("Explosions", false));
	public final BoolSetting armorStands = add(new BoolSetting("Far armor stands", true));
	public final NumberSetting armorStandRange = add(new NumberSetting("Armor stand range", 24, 4, 64, 1).suffix(" m").visibleIf(armorStands::on));

	public NoRenderModule() {
		super("No Render", "Hides vanilla overlays and clutter", Category.VISUALS);
		setSubCategory("Render");
	}

	/** The module while it is enabled, else null. */
	public static NoRenderModule active() {
		NoRenderModule module = ModuleManager.get(NoRenderModule.class);
		return module != null && module.isEnabled() ? module : null;
	}

	/** How far the fire overlay moves down, in view-space units. */
	public float fireDrop() {
		return fireOffset.f() / 100f * FIRE_TRAVEL;
	}

	public boolean hidesArmorStand(double squaredDistance) {
		double range = armorStandRange.get();
		return armorStands.on() && squaredDistance > range * range;
	}
}
