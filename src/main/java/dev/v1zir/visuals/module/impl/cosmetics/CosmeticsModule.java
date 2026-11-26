package dev.v1zir.visuals.module.impl.cosmetics;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ModeSetting;
import dev.v1zir.visuals.render.cosmetic.Cosmetic;

/**
 * Cosmetics: a hat, wings and a cape on your player, drawn client side (others see nothing; you can also show
 * your choice on other players). The Cosmetics tab of the click GUI is this module's page.
 */
public final class CosmeticsModule extends Module {
	public final ModeSetting hat = add(new ModeSetting("Hat", Cosmetic.NONE, Cosmetic.titles(Cosmetic.Slot.HAT)));
	public final ModeSetting wings = add(new ModeSetting("Wings", Cosmetic.NONE, Cosmetic.titles(Cosmetic.Slot.WINGS)));
	public final ModeSetting cape = add(new ModeSetting("Cape", Cosmetic.NONE, Cosmetic.titles(Cosmetic.Slot.CAPE)));
	public final BoolSetting others = add(new BoolSetting("Other players", false));
	public final BoolSetting hideUnderHelmet = add(new BoolSetting("Hide hat under helmet", true));

	// The selection as items, resolved again only when a setting's text changes.
	private final String[] resolvedFor = new String[Cosmetic.Slot.values().length];
	private final Cosmetic[] resolved = new Cosmetic[Cosmetic.Slot.values().length];

	public CosmeticsModule() {
		super("Cosmetics", "Hats, wings and capes on your player", Category.COSMETICS);
	}

	public ModeSetting setting(Cosmetic.Slot slot) {
		return switch (slot) {
			case HAT -> hat;
			case WINGS -> wings;
			case CAPE -> cape;
		};
	}

	/** The item worn in a slot, or null. */
	public Cosmetic item(Cosmetic.Slot slot) {
		String title = setting(slot).get();
		int i = slot.ordinal();
		if (!title.equals(resolvedFor[i])) {
			resolvedFor[i] = title;
			resolved[i] = Cosmetic.byTitle(slot, title);
		}
		return resolved[i];
	}

	/** Puts an item on, or takes it off if it is already worn. */
	public void toggle(Cosmetic item) {
		ModeSetting setting = setting(item.slot);
		setting.set(setting.is(item.title()) ? Cosmetic.NONE : item.title());
	}

	@Override
	public String suffix() {
		int worn = 0;
		for (Cosmetic.Slot slot : Cosmetic.Slot.values()) if (item(slot) != null) worn++;
		return worn == 0 ? null : worn + " on";
	}
}
