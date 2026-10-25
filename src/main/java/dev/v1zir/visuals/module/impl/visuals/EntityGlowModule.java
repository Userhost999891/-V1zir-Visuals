package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Vanilla's glowing outline in custom colours for players, mobs, dropped items and end crystals.
 * The chosen entities count as glowing (MinecraftClientMixin), so vanilla draws them into its
 * outline buffer, and their render state takes the colour (EntityRenderManagerMixin). There is no
 * pass of its own: the cost is vanilla's outline of the outlined entities, kept small by the range.
 */
public final class EntityGlowModule extends Module {
	/** Team colour of an entity without a coloured team. */
	private static final int NO_TEAM = 0xFFFFFF;

	public final BoolSetting players = add(new BoolSetting("Players", true));
	public final ColorSetting playerColor = add(new ColorSetting("Player color", 0xFF9D7CFF, false).visibleIf(players::on));
	public final BoolSetting teamColor = add(new BoolSetting("Team color", true).visibleIf(players::on));
	public final BoolSetting mobs = add(new BoolSetting("Mobs", false));
	public final ColorSetting hostileColor = add(new ColorSetting("Hostile color", 0xFFFF4D5E, false).visibleIf(mobs::on));
	public final ColorSetting passiveColor = add(new ColorSetting("Passive color", 0xFF5CFF8A, false).visibleIf(mobs::on));
	public final BoolSetting items = add(new BoolSetting("Items", false));
	public final ColorSetting itemColor = add(new ColorSetting("Item color", 0xFFFFD34D, false).visibleIf(items::on));
	public final BoolSetting crystals = add(new BoolSetting("Crystals", true));
	public final ColorSetting crystalColor = add(new ColorSetting("Crystal color", 0xFFFF6BEF, false).visibleIf(crystals::on));
	public final NumberSetting range = add(new NumberSetting("Range", 64, 8, 256, 8).suffix(" m"));

	// Opaque colours, refreshed every tick so rainbow colours move without per entity work.
	private int player;
	private int hostile;
	private int passive;
	private int item;
	private int crystal;

	public EntityGlowModule() {
		super("Entity Glow", "Coloured outlines around entities", Category.VISUALS);
		setSubCategory("Render");
		onTick();
	}

	/** The module while it is enabled, else null. */
	public static EntityGlowModule active() {
		EntityGlowModule module = ModuleManager.get(EntityGlowModule.class);
		return module != null && module.isEnabled() ? module : null;
	}

	@Override
	public void onTick() {
		player = 0xFF000000 | playerColor.argb();
		hostile = 0xFF000000 | hostileColor.argb();
		passive = 0xFF000000 | passiveColor.argb();
		item = 0xFF000000 | itemColor.argb();
		crystal = 0xFF000000 | crystalColor.argb();
	}

	/** Outline colour of the entity, or 0 to leave it to vanilla. You never outline yourself. */
	public int color(Entity entity) {
		int color = typeColor(entity);
		if (color == 0) return 0;
		double max = range.get();
		return mc.getEntityRenderDispatcher().getSquaredDistanceToCamera(entity) <= max * max ? color : 0;
	}

	private int typeColor(Entity entity) {
		if (entity instanceof PlayerEntity) {
			if (!players.on() || entity == mc.player) return 0;
			int team = entity.getTeamColorValue();
			return teamColor.on() && team != NO_TEAM ? 0xFF000000 | team : player;
		}
		if (entity instanceof EndCrystalEntity) return crystals.on() ? crystal : 0;
		if (entity instanceof ItemEntity) return items.on() ? item : 0;
		if (entity instanceof MobEntity) return mobs.on() ? entity instanceof Monster ? hostile : passive : 0;
		return 0;
	}
}
