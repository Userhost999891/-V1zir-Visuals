package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.util.Anim;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Quaternionf;

/**
 * First-person swing styles. Vanilla's swing (swingArm and applySwingOffset) is skipped for held
 * items and the style is applied right before the item is drawn, where View Model applies its
 * offsets; vanilla's equip offset stays in place. Item size is View Model's Scale, so the two
 * modules share one item transform instead of stacking two.
 * <p>
 * Every style is a translation followed by rotations inside the frame vanilla swings in (turned
 * 45 degrees about Y), so p = 0 and p = 1 are the resting pose and swings start and end cleanly.
 * The sword block pose comes last, like 1.8 applied it after the swing, so hits swing the blocking
 * sword ("block hitting").
 */
public final class SwingAnimationsModule extends Module {
	private static final float PI = (float) Math.PI;
	private static final float DEG = PI / 180f;

	public final ModeSetting style = add(new ModeSetting("Style", "Smooth", "Vanilla", "1.7", "Smooth", "Slide", "Spin", "Stab", "Swipe", "Exhibition"));
	public final NumberSetting speed = add(new NumberSetting("Swing speed", 1.0, 0.4, 2.5, 0.05).suffix("x"));
	public final BoolSetting swordBlock = add(new BoolSetting("Sword block", true).visibleIf(() -> style.is("1.7") || style.is("Exhibition")));
	public final BoolSetting noCooldownDip = add(new BoolSetting("No cooldown dip", true));

	private final Anim block = new Anim(0f, 14f);
	private final Quaternionf rotation = new Quaternionf();
	private boolean custom;
	private boolean pending;
	private boolean mainHand;
	private float side;
	private float progress;

	public SwingAnimationsModule() {
		super("Swing Animations", "Custom first-person swings", Category.VISUALS);
	}

	@Override
	protected void onDisable() {
		custom = false;
		pending = false;
	}

	@Override
	public String suffix() {
		return style.get();
	}

	/** Start of one hand's first-person render. Maps keep their own vanilla animation. */
	public void begin(PlayerEntity player, Hand hand, float swingProgress, ItemStack item) {
		pending = false;
		custom = !style.is("Vanilla") && !item.isEmpty() && !item.contains(DataComponentTypes.MAP_ID);
		mainHand = hand == Hand.MAIN_HAND;
		Arm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
		side = arm == Arm.RIGHT ? 1f : -1f;
		progress = swingProgress;
	}

	/** Vanilla is about to apply a part of its swing: returns true to skip it and use the style. */
	public boolean takeOver() {
		if (custom) pending = true;
		return custom;
	}

	public int swingDuration(int ticks) {
		return Math.max(1, Math.round(ticks / speed.f()));
	}

	public boolean noCooldownDip() {
		return noCooldownDip.on();
	}

	/** Right before the item is drawn: applies the style if vanilla's swing was skipped for it. */
	public void apply(MatrixStack m, PlayerEntity player) {
		if (!pending) return;
		pending = false;
		float p = progress;
		float s = side;
		float sq = (float) Math.sin(Math.sqrt(p) * PI);
		float s1 = (float) Math.sin(p * PI);
		float s2 = (float) Math.sin(p * p * PI);
		float b = mainHand ? block.target(blocking(player) ? 1f : 0f).get() : 0f;
		switch (style.get()) {
			case "1.7" -> {
				// Vanilla's swing (identical to 1.7) whose bob gives way to the block pose.
				float k = 1f - b;
				m.translate(s * -0.4f * sq * k, 0.2f * (float) Math.sin(Math.sqrt(p) * 2.0 * PI) * k, -0.2f * s1 * k);
				rotate(m, 45f * s, 0f, 1f, 0f);
				rotate(m, -20f * s2 * s, 0f, 1f, 0f);
				rotate(m, -20f * sq * s, 0f, 0f, 1f);
				rotate(m, -80f * sq, 1f, 0f, 0f);
			}
			case "Smooth" -> {
				m.translate(s * -0.12f * sq, 0.06f * sq, -0.1f * s1);
				rotate(m, 45f * s, 0f, 1f, 0f);
				rotate(m, -25f * sq * s, 0f, 1f, 0f);
				rotate(m, -12f * sq * s, 0f, 0f, 1f);
				rotate(m, -55f * sq, 1f, 0f, 0f);
			}
			case "Slide" -> {
				m.translate(s * -0.32f * sq, 0.04f * sq, -0.3f * s1);
				rotate(m, 45f * s, 0f, 1f, 0f);
				rotate(m, -8f * sq * s, 0f, 0f, 1f);
				rotate(m, -22f * sq, 1f, 0f, 0f);
			}
			case "Spin" -> {
				m.translate(0f, 0.04f * sq, -0.06f * sq);
				rotate(m, 45f * s, 0f, 1f, 0f);
				rotate(m, -360f * Anim.easeInOutCubic(p), 1f, 0f, 0f);
			}
			case "Stab" -> {
				m.translate(s * -0.06f * sq, 0.05f * sq, -0.42f * sq);
				rotate(m, 45f * s, 0f, 1f, 0f);
				rotate(m, -30f * sq, 1f, 0f, 0f);
			}
			case "Swipe" -> {
				m.translate(s * -0.1f * sq, 0.05f * sq, -0.1f * sq);
				rotate(m, 45f * s, 0f, 1f, 0f);
				rotate(m, -70f * sq * s, 0f, 0f, 1f);
				rotate(m, -25f * sq, 1f, 0f, 0f);
			}
			default -> {
				// Exhibition: a diagonal wobble that dips part way into the block pose on every hit.
				b = Math.max(b, 0.4f * sq);
				rotate(m, 45f * s, 0f, 1f, 0f);
				rotate(m, -20f * sq, sq / 2f, s, 4f * s);
				rotate(m, -30f * sq, 1f, sq / 3f * s, 0f);
			}
		}
		rotate(m, -45f * s, 0f, 1f, 0f);
		if (b > 0.001f) {
			// Vanilla's own pose for blocking with a non-shield item, faded in by b.
			m.translate(s * -0.14142136f * b, 0.08f * b, 0.14142136f * b);
			rotate(m, -102.25f * b, 1f, 0f, 0f);
			rotate(m, 13.365f * b * s, 0f, 1f, 0f);
			rotate(m, 78.05f * b * s, 0f, 0f, 1f);
		}
	}

	/** Visual only: holding use with a sword shows the old block pose; nothing is sent or used. */
	private boolean blocking(PlayerEntity player) {
		return swordBlock.on() && (style.is("1.7") || style.is("Exhibition")) && mc.currentScreen == null
			&& mc.options.useKey.isPressed() && !player.isUsingItem() && player.getMainHandStack().isIn(ItemTags.SWORDS);
	}

	private void rotate(MatrixStack m, float degrees, float x, float y, float z) {
		m.multiply(rotation.rotationAxis(degrees * DEG, x, y, z));
	}
}
