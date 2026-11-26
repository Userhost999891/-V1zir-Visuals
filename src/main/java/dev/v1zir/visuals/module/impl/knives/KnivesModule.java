package dev.v1zir.visuals.module.impl.knives;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.KeySetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.render.knife.KnifeFinish;
import dev.v1zir.visuals.render.knife.KnifeType;
import dev.v1zir.visuals.render.knife.KnifeViewModel;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import org.lwjgl.glfw.GLFW;

/**
 * Knives: the held sword (and optionally axe) becomes a knife from the library, with its own model, finish and
 * first-person animations (draw, inspect, stabs) and the player's arm holding it. Purely visual: attacks, their
 * timing and cooldown stay vanilla. The Knives tab of the click GUI is this module's page.
 */
public final class KnivesModule extends Module {
	public static final String SWORDS = "Swords";
	public static final String SWORDS_AND_AXES = "Swords and axes";

	public final ModeSetting knife = add(new ModeSetting("Knife", KnifeType.KARAMBIT.title(), KnifeType.titles()));
	public final ModeSetting finish = add(new ModeSetting("Finish", KnifeFinish.STEEL.title(), KnifeFinish.titles()));
	public final ModeSetting replaces = add(new ModeSetting("Replaces", SWORDS, SWORDS, SWORDS_AND_AXES));
	public final BoolSetting others = add(new BoolSetting("Other players", true));
	public final KeySetting inspectKey = add(new KeySetting("Inspect key", GLFW.GLFW_KEY_G));
	public final NumberSetting speed = add(new NumberSetting("Animation speed", 1.0, 0.5, 2.0, 0.05).suffix("x"));
	public final NumberSetting offsetX = add(new NumberSetting("Offset X", 0.0, -0.3, 0.3, 0.01));
	public final NumberSetting offsetY = add(new NumberSetting("Offset Y", 0.0, -0.3, 0.3, 0.01));
	public final NumberSetting offsetZ = add(new NumberSetting("Offset Z", 0.0, -0.3, 0.3, 0.01));
	public final NumberSetting sway = add(new NumberSetting("Bob & sway", 1.0, 0.0, 2.0, 0.05).suffix("x"));

	// The selection as types, resolved again only when the setting's text changes.
	private String typeFor;
	private KnifeType type = KnifeType.KARAMBIT;
	private String finishFor;
	private KnifeFinish knifeFinish = KnifeFinish.STEEL;

	public KnivesModule() {
		super("Knives", "Your sword becomes a knife from the library", Category.KNIVES);
	}

	public KnifeType type() {
		String title = knife.get();
		if (!title.equals(typeFor)) {
			typeFor = title;
			type = KnifeType.byTitle(title);
		}
		return type;
	}

	public KnifeFinish finish() {
		String title = finish.get();
		if (!title.equals(finishFor)) {
			finishFor = title;
			knifeFinish = KnifeFinish.byTitle(title);
		}
		return knifeFinish;
	}

	/** Whether this item is shown as the knife. */
	public boolean replaces(ItemStack stack) {
		if (stack.isEmpty()) return false;
		return stack.isIn(ItemTags.SWORDS) || replaces.is(SWORDS_AND_AXES) && stack.isIn(ItemTags.AXES);
	}

	@Override
	protected void onEnable() {
		// A knife already in hand is drawn, not popped into place.
		KnifeViewModel.reset();
	}

	@Override
	public void onKeyPressed(int key) {
		if (key == inspectKey.key()) KnifeViewModel.inspect();
	}

	@Override
	public String suffix() {
		return knife.get();
	}
}
