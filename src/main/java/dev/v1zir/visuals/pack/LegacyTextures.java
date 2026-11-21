package dev.v1zir.visuals.pack;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where packs made before 1.13 keep their textures, which the game has not looked for since: textures/items and
 * textures/blocks (now item and block), many under older names (wood_sword, apple_golden, wool_colored_red...), and
 * armour in textures/models/armor, which 1.21.2 moved to textures/entity/equipment. Most PvP packs are 1.8 packs:
 * with this their swords, armour, wool and the rest can be picked in the Pack Mixer like those of any other pack.
 */
final class LegacyTextures {
	/** Old path to new, under textures/ and without .png; the rest of items/ and blocks/ only lost the plural. */
	private static final Map<String, String> RENAMED = new HashMap<>();
	private static final Map<String, List<String>> OLD_NAMES = new HashMap<>();
	private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "silver", "cyan",
		"purple", "blue", "brown", "green", "red", "black"};
	private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "big_oak"};

	static {
		for (String tool : new String[]{"sword", "pickaxe", "axe", "shovel", "hoe"}) {
			item("wood_" + tool, "wooden_" + tool);
			item("gold_" + tool, "golden_" + tool);
		}
		for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) item("gold_" + piece, "golden_" + piece);
		item("gold_horse_armor", "golden_horse_armor");
		item("bow_standby", "bow");
		item("fishing_rod_uncast", "fishing_rod");
		item("apple_golden", "golden_apple");
		item("carrot_golden", "golden_carrot");
		item("melon_speckled", "glistering_melon_slice");
		item("melon", "melon_slice");
		for (String meat : new String[]{"beef", "porkchop", "chicken", "mutton", "rabbit"}) {
			item(meat + "_raw", meat);
			item(meat + "_cooked", "cooked_" + meat);
		}
		item("fish_cod_raw", "cod");
		item("fish_cod_cooked", "cooked_cod");
		item("fish_salmon_raw", "salmon");
		item("fish_salmon_cooked", "cooked_salmon");
		item("fish_clownfish_raw", "tropical_fish");
		item("fish_pufferfish_raw", "pufferfish");
		item("potato_baked", "baked_potato");
		item("potato_poisonous", "poisonous_potato");
		item("spider_eye_fermented", "fermented_spider_eye");
		item("potion_bottle_drinkable", "potion");
		item("potion_bottle_splash", "splash_potion");
		item("potion_bottle_lingering", "lingering_potion");
		item("potion_bottle_empty", "glass_bottle");
		item("bucket_empty", "bucket");
		item("bucket_water", "water_bucket");
		item("bucket_lava", "lava_bucket");
		item("bucket_milk", "milk_bucket");
		item("book_normal", "book");
		item("book_enchanted", "enchanted_book");
		item("book_writable", "writable_book");
		item("book_written", "written_book");
		item("slimeball", "slime_ball");
		item("fireball", "fire_charge");
		item("netherbrick", "nether_brick");
		item("redstone_dust", "redstone");
		item("seeds_wheat", "wheat_seeds");
		item("reeds", "sugar_cane");
		item("totem", "totem_of_undying");
		item("chorus_fruit_popped", "popped_chorus_fruit");
		item("map_empty", "map");
		item("map_filled", "filled_map");
		item("minecart_normal", "minecart");
		item("boat", "oak_boat");

		for (String color : COLORS) {
			String now = color.equals("silver") ? "light_gray" : color;
			block("wool_colored_" + color, now + "_wool");
			block("glass_" + color, now + "_stained_glass");
			block("glass_pane_top_" + color, now + "_stained_glass_pane_top");
			block("hardened_clay_stained_" + color, now + "_terracotta");
		}
		block("hardened_clay", "terracotta");
		for (String wood : WOODS) {
			String now = wood.equals("big_oak") ? "dark_oak" : wood;
			block("planks_" + wood, now + "_planks");
			block("log_" + wood, now + "_log");
			block("log_" + wood + "_top", now + "_log_top");
			block("leaves_" + wood, now + "_leaves");
		}
		block("grass_top", "grass_block_top");
		block("grass_side", "grass_block_side");
		block("grass_side_overlay", "grass_block_side_overlay");
		block("grass_side_snowed", "grass_block_snow");
		block("stonebrick", "stone_bricks");
		block("stonebrick_mossy", "mossy_stone_bricks");
		block("stonebrick_cracked", "cracked_stone_bricks");
		block("stonebrick_carved", "chiseled_stone_bricks");
		block("brick", "bricks");
		block("cobblestone_mossy", "mossy_cobblestone");
		block("sandstone_normal", "sandstone");
		block("sandstone_carved", "chiseled_sandstone");
		block("sandstone_smooth", "cut_sandstone");
		block("red_sandstone_normal", "red_sandstone");
		block("quartz_ore", "nether_quartz_ore");
		block("stone_andesite", "andesite");
		block("stone_andesite_smooth", "polished_andesite");
		block("stone_diorite", "diorite");
		block("stone_diorite_smooth", "polished_diorite");
		block("stone_granite", "granite");
		block("stone_granite_smooth", "polished_granite");
		block("web", "cobweb");
		block("ice_packed", "packed_ice");
		block("furnace_front_off", "furnace_front");
		block("fire_layer_0", "fire_0");
		block("fire_layer_1", "fire_1");
		block("portal", "nether_portal");

		// Armour worn on the body, layer 1 on the head, chest and feet, layer 2 on the legs.
		for (String material : new String[]{"leather", "chainmail", "iron", "gold", "diamond", "netherite", "turtle"}) {
			String now = material.equals("turtle") ? "turtle_scute" : material;
			put("models/armor/" + material + "_layer_1", "entity/equipment/humanoid/" + now);
			put("models/armor/" + material + "_layer_2", "entity/equipment/humanoid_leggings/" + now);
		}
		put("models/armor/leather_layer_1_overlay", "entity/equipment/humanoid/leather_overlay");
		put("models/armor/leather_layer_2_overlay", "entity/equipment/humanoid_leggings/leather_overlay");
	}

	private LegacyTextures() {
	}

	private static void item(String old, String now) {
		put("items/" + old, "item/" + now);
	}

	private static void block(String old, String now) {
		put("blocks/" + old, "block/" + now);
	}

	private static void put(String old, String now) {
		RENAMED.put(old, now);
		OLD_NAMES.computeIfAbsent(now, k -> new ArrayList<>()).add(old);
	}

	/** The texture the game uses now for one at an old pack's path, or null when the path is not an old one. */
	static Identifier modern(Identifier old) {
		if (!old.getNamespace().equals("minecraft")) return null;
		String path = old.getPath();
		if (!path.startsWith("textures/") || !path.endsWith(".png")) return null;
		String inner = path.substring("textures/".length(), path.length() - ".png".length());
		String now = RENAMED.get(inner);
		if (now == null) {
			if (inner.startsWith("items/")) now = "item/" + inner.substring("items/".length());
			else if (inner.startsWith("blocks/")) now = "block/" + inner.substring("blocks/".length());
			else return null;
		}
		return Identifier.of("minecraft", "textures/" + now + ".png");
	}

	/** Where an old pack keeps this texture: its old name, or just the old folder. */
	static List<Identifier> aliases(Identifier modern) {
		if (!modern.getNamespace().equals("minecraft")) return List.of();
		String path = modern.getPath();
		if (!path.startsWith("textures/") || !path.endsWith(".png")) return List.of();
		String inner = path.substring("textures/".length(), path.length() - ".png".length());
		List<Identifier> out = new ArrayList<>(2);
		for (String old : OLD_NAMES.getOrDefault(inner, List.of())) out.add(Identifier.of("minecraft", "textures/" + old + ".png"));
		if (inner.startsWith("item/")) out.add(Identifier.of("minecraft", "textures/items/" + inner.substring("item/".length()) + ".png"));
		else if (inner.startsWith("block/")) out.add(Identifier.of("minecraft", "textures/blocks/" + inner.substring("block/".length()) + ".png"));
		return out;
	}
}
