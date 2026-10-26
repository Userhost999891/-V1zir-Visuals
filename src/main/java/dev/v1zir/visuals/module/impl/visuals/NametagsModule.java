package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.font.Fonts;
import dev.v1zir.visuals.render.font.SdfFont;
import dev.v1zir.visuals.render.world.WorldEffect;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.render.world.WorldText;
import dev.v1zir.visuals.util.Anim;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Player nametags built with WorldText: a rounded plate with a soft shadow carrying the name and
 * health, a smoothly animated health bar that leaves a trail when health drops, and a compact
 * line with one pip per armour piece (material colour, filled by durability), the held item in
 * its rarity colour and the distance. Far tags grow so they never shrink below a minimum size on
 * screen. The vanilla label of players is hidden while this is on (PlayerEntityRendererMixin).
 * Strings and their widths are cached per player and only rebuilt when they change.
 */
public final class NametagsModule extends Module implements WorldEffect {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Rarity colours, by ordinal: common, uncommon, rare, epic. */
	private static final int[] RARITY = {0xFFE6E8EE, 0xFFFFE066, 0xFF66E6FF, 0xFFFF7AF2};
	private static final int LOW = 0xFFFF4D5E;
	private static final int MID = 0xFFFFD34D;
	private static final int HIGH = 0xFF5CFF8A;
	private static final int ABSORPTION = 0xFFFFC83D;
	private static final int MAX_ITEM_NAME = 22;
	// Layout in blocks at scale 1 (the name is a little smaller than vanilla's label).
	private static final float NAME = 0.22f;
	private static final float SMALL = 0.165f;
	private static final float PAD_X = 0.11f;
	private static final float PAD_Y = 0.085f;
	private static final float GAP = 0.06f;
	private static final float BAR = 0.05f;
	private static final float PIP = 0.1f;
	private static final float PIP_GAP = 0.03f;
	private static final float RADIUS = 0.1f;
	private static final float MIN_CONTENT = 0.9f;
	/** Distance up to which tags keep their world size; past it Min size takes over. */
	private static final float NEAR = 6f;

	public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.05).suffix("x"));
	public final NumberSetting minSize = add(new NumberSetting("Min size", 70, 0, 100, 5).suffix("%"));
	public final BoolSetting healthBar = add(new BoolSetting("Health bar", true));
	public final ModeSetting barColor = add(new ModeSetting("Bar color", "Health", "Health", "Theme").visibleIf(healthBar::on));
	public final BoolSetting items = add(new BoolSetting("Items", true));
	public final BoolSetting distance = add(new BoolSetting("Distance", true));
	public final NumberSetting background = add(new NumberSetting("Background", 0.6, 0.0, 1.0, 0.05));
	public final BoolSetting throughWalls = add(new BoolSetting("Through walls", true));
	public final BoolSetting self = add(new BoolSetting("Self", false));
	public final NumberSetting range = add(new NumberSetting("Range", 64, 16, 128, 4).suffix(" m"));

	private final Int2ObjectOpenHashMap<Tag> byId = new Int2ObjectOpenHashMap<>();
	/** The same tags as a list, so drawing iterates without an iterator. */
	private final List<Tag> tags = new ArrayList<>();
	private final WorldText.Style plate = new WorldText.Style();
	private final WorldText.Style text = new WorldText.Style().weight(0.02f);
	/** Entity ids start over in a new world. */
	private ClientWorld world;
	// Fonts the cached widths were measured with; the font family can change at any time.
	private SdfFont nameFont;
	private SdfFont smallFont;

	public NametagsModule() {
		super("Nametags", "Premium nametags for players", Category.COMBAT);
		setSubCategory("Info");
	}

	@Override
	protected void onDisable() {
		byId.clear();
		tags.clear();
	}

	@Override
	public void onTick() {
		if (mc.world != world) {
			world = mc.world;
			onDisable();
		}
		for (int i = 0; i < tags.size(); i++) tags.get(i).seen = false;
		if (mc.world != null && mc.player != null) {
			double max = range.get() * range.get();
			boolean firstPerson = mc.options.getPerspective().isFirstPerson();
			for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
				if (player == mc.player ? !self.on() || firstPerson : player.isInvisibleTo(mc.player)) continue;
				if (player.isSpectator() || !player.isAlive() || player.squaredDistanceTo(mc.player) > max) continue;
				Tag tag = byId.get(player.getId());
				boolean fresh = tag == null;
				if (fresh) {
					tag = new Tag(player);
					byId.put(player.getId(), tag);
					tags.add(tag);
				}
				tag.player = player;
				tag.seen = true;
				tag.update();
				// A new tag starts at the real health instead of sliding down from full.
				if (fresh) {
					tag.healthFraction.snap(tag.healthFraction.target());
					tag.healthLag.snap(tag.healthLag.target());
				}
			}
		}
		// Tags of players that left keep fading out at their last position, then go.
		for (int i = tags.size() - 1; i >= 0; i--) {
			Tag tag = tags.get(i);
			if (!tag.seen && tag.appear.target() == 0f && tag.appear.settled()) {
				byId.remove(tag.player.getId());
				tags.remove(i);
			}
		}
	}

	@Override
	public void render3D(float tickDelta) {
		if (tags.isEmpty() || mc.options.hudHidden) return;
		SdfFont bold = Fonts.semibold();
		SdfFont medium = Fonts.medium();
		if (bold != nameFont || medium != smallFont) {
			nameFont = bold;
			smallFont = medium;
			for (int i = 0; i < tags.size(); i++) tags.get(i).invalidateWidths();
		}
		for (int i = 0; i < tags.size(); i++) {
			Tag tag = tags.get(i);
			float fade = tag.appear.target(tag.seen ? 1f : 0f).get();
			if (tag.seen) {
				AbstractClientPlayerEntity p = tag.player;
				tag.x = MathHelper.lerp(tickDelta, p.lastRenderX, p.getX());
				tag.y = MathHelper.lerp(tickDelta, p.lastRenderY, p.getY()) + p.getHeight() + 0.25;
				tag.z = MathHelper.lerp(tickDelta, p.lastRenderZ, p.getZ());
				tag.seeThrough = throughWalls.on() && !p.isSneaking();
			}
			if (fade > 0.01f) draw(tag, fade);
		}
	}

	private void draw(Tag tag, float fade) {
		double dx = tag.x - WorldEffectRenderer.camX;
		double dy = tag.y - WorldEffectRenderer.camY;
		double dz = tag.z - WorldEffectRenderer.camZ;
		float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		// At 100% the tag keeps the on-screen size it has at NEAR blocks however far it is.
		float k = scale.f() * (1f + minSize.f() / 100f * Math.max(0f, dist / NEAR - 1f));
		int meters = Math.round(dist);
		if (meters != tag.meters) {
			tag.meters = meters;
			tag.distance = meters + "m";
			tag.distanceWidth = -1f;
		}
		tag.measure(nameFont, smallFont);

		boolean bar = healthBar.on();
		boolean info = items.on() || distance.on();
		float name = NAME * k;
		float small = SMALL * k;
		float padX = PAD_X * k;
		float gap = GAP * k;
		float pip = PIP * k;
		float pipGap = PIP_GAP * k;
		float pips = items.on() ? 4f * pip + 3f * pipGap : 0f;
		float held = items.on() && tag.held != null ? pipGap * 2.5f + tag.heldWidth * small : 0f;
		float distW = distance.on() ? tag.distanceWidth * small : 0f;
		float row1 = tag.nameWidth * name + gap * 2f + tag.healthWidth * name;
		float row3 = pips + held + (distW > 0f ? gap * 2f + distW : 0f);
		float content = Math.max(MIN_CONTENT * k, Math.max(row1, row3));
		float width = content + padX * 2f;

		// Rows from the bottom; the anchor is the bottom centre of the plate, just above the head.
		float y = PAD_Y * k;
		float row3Y = y + small * 0.36f;
		if (info) y += small * 0.72f + gap;
		float barY = y + BAR * k * 0.5f;
		if (bar) y += BAR * k + gap;
		float row1Y = y + name * 0.36f;
		float height = y + name * 0.72f + PAD_Y * k;
		float left = -content * 0.5f;
		float right = content * 0.5f;
		double x = tag.x;
		double ay = tag.y;
		double z = tag.z;

		plate.alpha(fade).seeThrough(tag.seeThrough);
		text.alpha(fade).seeThrough(tag.seeThrough);
		float opacity = background.f();
		float radius = RADIUS * k;
		if (opacity > 0.01f) {
			WorldText.halo(x, ay, z, 0f, height * 0.5f - 0.02f * k, width, height, radius, 0.12f * k, ColorUtil.withAlpha(0, Math.round(110 * opacity)), false, plate);
			int base = ColorUtil.withAlpha(0x0E0F15, Math.round(255 * opacity));
			WorldText.plate(x, ay, z, 0f, height * 0.5f, width, height, radius, base, ColorUtil.brighten(base, 0.04f), plate);
		}

		float hp = tag.healthFraction.get();
		float lag = tag.healthLag.get();
		if (bar) {
			float barW = content;
			float barH = BAR * k;
			WorldText.plate(x, ay, z, 0f, barY, barW, barH, barH * 0.5f, ColorUtil.withAlpha(0xFFFFFF, 26), ColorUtil.withAlpha(0xFFFFFF, 26), plate);
			if (lag > hp) {
				int trail = ColorUtil.withAlpha(0xFFFFFF, 95);
				WorldText.plate(x, ay, z, left + barW * lag * 0.5f, barY, barW * lag, barH, barH * 0.5f, trail, trail, plate);
			}
			if (hp > 0.002f) {
				int from;
				int to;
				if (barColor.is("Theme")) {
					from = Theme.accent();
					to = Theme.accent2();
				} else {
					to = healthColor(hp);
					from = ColorUtil.darken(to, 0.25f);
				}
				float fill = barW * hp;
				WorldText.halo(x, ay, z, left + fill * 0.5f, barY, fill, barH, barH * 0.5f, barH * 1.4f, ColorUtil.withAlpha(to, 80), true, plate);
				WorldText.plate(x, ay, z, left + fill * 0.5f, barY, fill, barH, barH * 0.5f, from, to, plate);
			}
		}

		int nameColor = tag.teamColor != 0 ? tag.teamColor : Theme.text();
		WorldText.draw(tag.name, x, ay, z, left, row1Y, text.font(nameFont).size(name).color(nameColor).align(WorldText.Align.LEFT));
		int healthColor = tag.absorbing ? ABSORPTION : healthColor(hp);
		WorldText.draw(tag.health, x, ay, z, right, row1Y, text.color(healthColor).align(WorldText.Align.RIGHT));

		if (!info) return;
		if (items.on()) {
			float px = left + pip * 0.5f;
			float pipR = pip * 0.3f;
			for (int i = 0; i < ARMOR.length; i++, px += pip + pipGap) {
				int color = tag.armorColor[i];
				if (color == 0) {
					int empty = ColorUtil.withAlpha(0xFFFFFF, 22);
					WorldText.plate(x, ay, z, px, row3Y, pip, pip, pipR, empty, empty, plate);
					continue;
				}
				int dim = ColorUtil.withAlpha(color, 70);
				WorldText.plate(x, ay, z, px, row3Y, pip, pip, pipR, dim, dim, plate);
				float filled = pip * Math.max(0.2f, tag.durability[i]);
				WorldText.plate(x, ay, z, px - pip * 0.5f + filled * 0.5f, row3Y, filled, pip, pipR, color, color, plate);
			}
			if (tag.held != null) {
				WorldText.draw(tag.held, x, ay, z, left + pips + pipGap * 2.5f, row3Y, text.font(smallFont).size(small).color(tag.heldColor).align(WorldText.Align.LEFT));
			}
		}
		if (distW > 0f) {
			WorldText.draw(tag.distance, x, ay, z, right, row3Y, text.font(smallFont).size(small).color(Theme.textDim()).align(WorldText.Align.RIGHT));
		}
	}

	private static int healthColor(float fraction) {
		return fraction < 0.5f ? ColorUtil.lerp(LOW, MID, fraction * 2f) : ColorUtil.lerp(MID, HIGH, fraction * 2f - 1f);
	}

	/** Material colour of an armour piece, from its item id (dyes and trims are ignored). */
	private static int armorColor(ItemStack stack) {
		String id = Registries.ITEM.getId(stack.getItem()).getPath();
		if (id.startsWith("netherite")) return 0xFF7A6A86;
		if (id.startsWith("diamond")) return 0xFF4EE2EC;
		if (id.startsWith("iron")) return 0xFFD8DEE6;
		if (id.startsWith("golden")) return 0xFFFFD34D;
		if (id.startsWith("chainmail")) return 0xFF9AA3AD;
		if (id.startsWith("copper")) return 0xFFE0855A;
		if (id.startsWith("leather")) return 0xFFB07A4F;
		if (id.startsWith("turtle")) return 0xFF5CC85C;
		if (id.equals("elytra")) return 0xFFB9B4D6;
		return 0xFFBFC5CC;
	}

	/** Cached state and strings of one player's tag. */
	private static final class Tag {
		AbstractClientPlayerEntity player;
		boolean seen;
		final Anim appear = new Anim(0f, 8f);
		final Anim healthFraction = new Anim(1f, 9f);
		/** Follows the health slowly downwards: the trail of a hit. */
		final Anim healthLag = new Anim(1f, 2.2f);
		double x, y, z;
		boolean seeThrough;

		final String name;
		int teamColor;
		String health;
		int healthTenths = -1;
		boolean absorbing;
		String distance = "";
		int meters = -1;
		String held;
		ItemStack heldStack;
		int heldCount;
		int heldColor;
		final ItemStack[] armor = new ItemStack[4];
		final int[] armorColor = new int[4];
		final float[] durability = new float[4];
		// Widths at size 1; negative means not measured with the current font.
		float nameWidth = -1f;
		float healthWidth = -1f;
		float heldWidth = -1f;
		float distanceWidth = -1f;

		Tag(AbstractClientPlayerEntity player) {
			this.player = player;
			// A player's name does not change while they are in the world.
			this.name = player.getName().getString();
		}

		void update() {
			AbstractClientPlayerEntity p = player;
			int team = p.getTeamColorValue();
			teamColor = team == 0xFFFFFF ? 0 : 0xFF000000 | team;
			float max = Math.max(1f, p.getMaxHealth());
			float hp = Math.max(0f, p.getHealth());
			float absorption = p.getAbsorptionAmount();
			healthFraction.target(Math.min(1f, hp / max));
			healthLag.target(Math.min(1f, hp / max));
			absorbing = absorption > 0f;
			int tenths = Math.round((hp + absorption) * 10f);
			if (tenths != healthTenths) {
				healthTenths = tenths;
				health = tenths % 10 == 0 ? Integer.toString(tenths / 10) : tenths / 10 + "." + tenths % 10;
				healthWidth = -1f;
			}
			for (int i = 0; i < ARMOR.length; i++) {
				ItemStack stack = p.getEquippedStack(ARMOR[i]);
				if (stack != armor[i]) {
					armor[i] = stack;
					armorColor[i] = stack.isEmpty() ? 0 : armorColor(stack);
				}
				durability[i] = stack.isDamageable() ? 1f - stack.getDamage() / (float) Math.max(1, stack.getMaxDamage()) : 1f;
			}
			ItemStack stack = p.getMainHandStack();
			if (stack != heldStack || stack.getCount() != heldCount) {
				heldStack = stack;
				heldCount = stack.getCount();
				if (stack.isEmpty()) {
					held = null;
				} else {
					String item = stack.getName().getString();
					if (item.length() > MAX_ITEM_NAME) item = item.substring(0, MAX_ITEM_NAME - 1) + "…";
					held = heldCount > 1 ? item + " x" + heldCount : item;
					heldColor = RARITY[Math.min(stack.getRarity().ordinal(), RARITY.length - 1)];
				}
				heldWidth = -1f;
			}
		}

		void invalidateWidths() {
			nameWidth = healthWidth = heldWidth = distanceWidth = -1f;
		}

		void measure(SdfFont nameFont, SdfFont smallFont) {
			if (nameWidth < 0f) nameWidth = nameFont.width(name, 1f);
			if (healthWidth < 0f) healthWidth = nameFont.width(health, 1f);
			if (heldWidth < 0f) heldWidth = held != null ? smallFont.width(held, 1f) : 0f;
			if (distanceWidth < 0f) distanceWidth = smallFont.width(distance, 1f);
		}
	}
}
