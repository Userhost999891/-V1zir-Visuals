package dev.v1zir.visuals.render.cosmetic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static dev.v1zir.visuals.render.cosmetic.CosmeticMesh.*;

/** The cosmetics library: every item with its slot, model and the way it moves. All original designs. */
public enum Cosmetic {
	TOP_HAT(Slot.HAT, "Top Hat", CosmeticModels::topHat, Motion.NONE),
	CROWN(Slot.HAT, "Crown", CosmeticModels::crown, Motion.NONE),
	HALO(Slot.HAT, "Halo", CosmeticModels::halo, Motion.HALO),
	CAT_EARS(Slot.HAT, "Cat Ears", CosmeticModels::catEars, Motion.EARS),
	WITCH_HAT(Slot.HAT, "Witch Hat", CosmeticModels::witchHat, Motion.NONE),
	BEANIE(Slot.HAT, "Beanie", CosmeticModels::beanie, Motion.NONE),
	HORNS(Slot.HAT, "Horns", CosmeticModels::horns, Motion.NONE),
	FLOWER_CROWN(Slot.HAT, "Flower Crown", CosmeticModels::flowerCrown, Motion.NONE),
	HEADPHONES(Slot.HAT, "Headphones", CosmeticModels::headphones, Motion.NONE),
	COWBOY_HAT(Slot.HAT, "Cowboy Hat", CosmeticModels::cowboyHat, Motion.NONE),
	ANTLERS(Slot.HAT, "Antlers", CosmeticModels::antlers, Motion.NONE),
	PROPELLER_CAP(Slot.HAT, "Propeller Cap", CosmeticModels::propellerCap, Motion.PROPELLER),
	SANTA_HAT(Slot.HAT, "Santa Hat", CosmeticModels::santaHat, Motion.NONE),
	ANGEL_WINGS(Slot.WINGS, "Angel Wings", CosmeticModels::angelWings, Motion.WINGS),
	DEMON_WINGS(Slot.WINGS, "Demon Wings", CosmeticModels::demonWings, Motion.WINGS),
	BUTTERFLY_WINGS(Slot.WINGS, "Butterfly Wings", CosmeticModels::butterflyWings, Motion.FLUTTER),
	ENERGY_WINGS(Slot.WINGS, "Energy Wings", CosmeticModels::energyWings, Motion.WINGS),
	PHOENIX_WINGS(Slot.WINGS, "Phoenix Wings", CosmeticModels::phoenixWings, Motion.WINGS),
	MECHA_WINGS(Slot.WINGS, "Mecha Wings", CosmeticModels::mechaWings, Motion.WINGS),
	CRYSTAL_WINGS(Slot.WINGS, "Crystal Wings", CosmeticModels::crystalWings, Motion.WINGS),
	TWILIGHT_CAPE(Slot.CAPE, "Twilight Cape",
		() -> CosmeticModels.cape(0xFF3B1E8C, 0xFF1EC8D8, SILK, 0xFF1B1446, SATIN, 0xFFFFFFFF, GOLD, 0xFFFFFFFF, 0xFF7FE8FF), Motion.CAPE),
	GALAXY_CAPE(Slot.CAPE, "Galaxy Cape",
		() -> CosmeticModels.cape(0xFF4A38A8, 0xFF1A1450, GALAXY, 0xFF231A4A, SATIN, 0xFFD8DCE8, SILVER, 0xFFD8DCE8, 0xFFB48CFF), Motion.CAPE),
	AURORA_CAPE(Slot.CAPE, "Aurora Cape",
		() -> CosmeticModels.cape(0xFF22D3A6, 0xFF2A64E0, SWIRL, 0xFF0F2A33, SATIN, 0xFF16202A, SATIN, 0xFFD8DCE8, 0xFF3FF2C4), Motion.CAPE),
	ROYAL_CAPE(Slot.CAPE, "Royal Cape", CosmeticModels::royalCape, Motion.CAPE),
	EMBER_CAPE(Slot.CAPE, "Ember Cape", CosmeticModels::emberCape, Motion.CAPE),
	SAKURA_CAPE(Slot.CAPE, "Sakura Cape", CosmeticModels::sakuraCape, Motion.CAPE);

	public static final String NONE = "None";
	private static final Cosmetic[] VALUES = values();

	public final Slot slot;
	private final String title;
	private final Supplier<CosmeticMesh> model;
	final Motion motion;
	private CosmeticMesh mesh;

	Cosmetic(Slot slot, String title, Supplier<CosmeticMesh> model, Motion motion) {
		this.slot = slot;
		this.title = title;
		this.model = model;
		this.motion = motion;
	}

	public String title() {
		return title;
	}

	/** The model, built the first time it is needed. */
	public CosmeticMesh mesh() {
		if (mesh == null) mesh = model.get();
		return mesh;
	}

	/** Every item of a slot. */
	public static List<Cosmetic> of(Slot slot) {
		List<Cosmetic> out = new ArrayList<>();
		for (Cosmetic c : VALUES) if (c.slot == slot) out.add(c);
		return out;
	}

	/** "None" and the titles of a slot's items, for its setting. */
	public static String[] titles(Slot slot) {
		List<String> out = new ArrayList<>();
		out.add(NONE);
		for (Cosmetic c : VALUES) if (c.slot == slot) out.add(c.title);
		return out.toArray(new String[0]);
	}

	/** The item of this slot with this title, or null for "None" or an unknown one. */
	public static Cosmetic byTitle(Slot slot, String title) {
		for (Cosmetic c : VALUES) if (c.slot == slot && c.title.equals(title)) return c;
		return null;
	}

	/** Where an item is worn; one of each at a time. */
	public enum Slot {
		HAT("Hats"),
		WINGS("Wings"),
		CAPE("Capes");

		public final String title;

		Slot(String title) {
			this.title = title;
		}
	}

	/** How an item moves (CosmeticRenderer). */
	enum Motion {
		NONE,
		/** Floats and slowly turns. */
		HALO,
		/** Twitches now and then. */
		EARS,
		/** Feathered or bony wings: a slow beat, spread wide in the air, folded when sneaking. */
		WINGS,
		/** A butterfly's quick flutter. */
		FLUTTER,
		/** Cloth, simulated (CapeCloth). */
		CAPE,
		/** Part 1 spins about its pivot, faster on the move. */
		PROPELLER
	}
}
