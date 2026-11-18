package dev.v1zir.visuals.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.v1zir.visuals.V1zir;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadata;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Pack Mixer: every texture the game uses and, for each, the versions of it in every resource pack there is, the
 * enabled ones (vanilla included, the top one first: the version used without a choice) and those in the folder that
 * are not enabled, including packs made before 1.13 (1.8 PvP packs), whose textures lie at older paths and names
 * ({@link LegacyTextures}). A pack does not have to be enabled to give a texture: picked from a pack that is not, the
 * texture comes from that pack alone and the rest of it stays out. Choices are kept per texture in
 * config/v1zir/pack_mixer.json and applied while resources load (LifecycledResourceManagerImplMixin hands the chosen
 * pack's file to the atlases and textures), so a reload of the resources makes them show.
 * <p>
 * The choice lookups run on resource loading threads and read an immutable snapshot; everything else is render
 * thread only.
 */
public final class PackMixer {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** One pack's version of a texture. */
	public record Version(String packId, String packName, Resource resource) {
	}

	/**
	 * A texture: its id (assets path), short name, group, and the versions from the top pack (the one used without a
	 * choice) down.
	 */
	public record Entry(Identifier id, String name, String group, List<Version> versions) {
		public boolean conflict() {
			return versions.size() > 1;
		}
	}

	/** The tabs of the Packs page, in order; PvP packs mostly change the first few. */
	public static final String[] CATEGORIES = {"Swords", "Axes", "Pickaxes", "Tools", "Bows", "Armor", "Food", "Potions", "Items",
		"Blocks", "Ores", "Wool & Glass", "Mobs", "GUI", "Effects", "Particles", "Sky", "Other"};
	/** The texture that stands for each tab (its icon), or null where the page draws a glyph instead. */
	public static final String[] CATEGORY_TEXTURES = {"item/diamond_sword", "item/diamond_axe", "item/diamond_pickaxe", "item/diamond_shovel",
		"item/bow", "item/diamond_chestplate", "item/golden_apple", "item/splash_potion", "item/emerald", "block/grass_block_side",
		"block/diamond_ore", "block/light_blue_wool", null, null, "mob_effect/strength", "particle/heart", "environment/sun", null};
	private static final int SWORDS = 0;
	private static final int AXES = 1;
	private static final int PICKAXES = 2;
	private static final int TOOLS = 3;
	private static final int BOWS = 4;
	private static final int ARMOR = 5;
	private static final int FOOD = 6;
	private static final int POTIONS = 7;
	private static final int ITEMS = 8;
	private static final int BLOCKS = 9;
	private static final int ORES = 10;
	private static final int WOOL_GLASS = 11;
	private static final int MOBS = 12;
	private static final int GUI = 13;
	private static final int EFFECTS = 14;
	private static final int PARTICLES = 15;
	private static final int SKY = 16;
	private static final int OTHER = 17;
	private static final Set<String> FOODS = Set.of("apple", "golden_apple", "enchanted_golden_apple", "bread", "beef", "porkchop", "mutton",
		"chicken", "rabbit", "cod", "salmon", "tropical_fish", "pufferfish", "potato", "baked_potato", "poisonous_potato", "carrot", "golden_carrot",
		"beetroot", "melon_slice", "glistering_melon_slice", "cookie", "pumpkin_pie", "cake", "sweet_berries", "glow_berries", "chorus_fruit",
		"dried_kelp", "honey_bottle", "rotten_flesh", "spider_eye");
	private static final Set<String> POTION_ITEMS = Set.of("ender_pearl", "ender_eye", "experience_bottle", "totem_of_undying", "ominous_bottle",
		"dragon_breath", "glass_bottle", "wind_charge");

	private static volatile Map<Identifier, String> choices = Map.of();
	/** Packs that are not enabled, opened to read single textures from (also on the resource loading threads). */
	private static final Map<String, ResourcePack> OPENED = new ConcurrentHashMap<>();
	private static List<Entry> entries = List.of();
	private static int conflicts;
	private static boolean loaded;
	private static boolean dirty;
	private static boolean reloading;
	private static String scannedPacks = "";

	private PackMixer() {
	}

	// ------------------------------------------------------------------ choices (any thread)

	/** The pack chosen for a texture, or null to use the top pack's. */
	public static String choice(Identifier id) {
		if (!loaded) load();
		Map<Identifier, String> map = choices;
		return map.isEmpty() ? null : map.get(id);
	}

	public static Map<Identifier, String> choices() {
		if (!loaded) load();
		return choices;
	}

	/**
	 * The chosen pack's version of a texture: from the manager's packs when it is enabled, else from the pack opened
	 * here; at the texture's own path or where a pack from before 1.13 keeps it. Null when the pack does not have it.
	 */
	public static Resource pick(ResourceManager manager, Identifier id, String packId) {
		for (Resource resource : manager.getAllResources(id)) {
			if (resource.getPackId().equals(packId)) return resource;
		}
		List<Identifier> aliases = LegacyTextures.aliases(id);
		for (Identifier old : aliases) {
			for (Resource resource : manager.getAllResources(old)) {
				if (resource.getPackId().equals(packId)) return resource;
			}
		}
		ResourcePack pack = opened(packId);
		if (pack == null) return null;
		Resource resource = resource(pack, id);
		for (int i = 0; resource == null && i < aliases.size(); i++) resource = resource(pack, aliases.get(i));
		return resource;
	}

	/** A pack that is not enabled, opened once (a pack the game does not know of any more gives null). */
	private static ResourcePack opened(String packId) {
		ResourcePack pack = OPENED.get(packId);
		if (pack != null) return pack;
		synchronized (OPENED) {
			pack = OPENED.get(packId);
			if (pack != null) return pack;
			ResourcePackProfile profile = MinecraftClient.getInstance().getResourcePackManager().getProfile(packId);
			if (profile == null) return null;
			try {
				pack = profile.createResourcePack();
			} catch (RuntimeException e) {
				V1zir.LOGGER.warn("Could not open the resource pack {}", packId, e);
				return null;
			}
			OPENED.put(packId, pack);
			return pack;
		}
	}

	/** The file at id in the pack, with its .mcmeta (an animation), as the game reads resources; null when absent. */
	private static Resource resource(ResourcePack pack, Identifier id) {
		InputSupplier<InputStream> input = pack.open(ResourceType.CLIENT_RESOURCES, id);
		return input == null ? null : new Resource(pack, input, metadata(pack, id));
	}

	private static InputSupplier<ResourceMetadata> metadata(ResourcePack pack, Identifier id) {
		return () -> {
			InputSupplier<InputStream> meta = pack.open(ResourceType.CLIENT_RESOURCES, id.withSuffixedPath(".mcmeta"));
			if (meta == null) return ResourceMetadata.NONE;
			try (InputStream in = meta.get()) {
				return ResourceMetadata.create(in);
			}
		};
	}

	// ------------------------------------------------------------------ editing (render thread)

	/** Uses a pack's version of a texture; the top pack's (or null) clears the choice. Takes effect on Apply. */
	public static void choose(Entry entry, String packId) {
		Map<Identifier, String> next = new HashMap<>(choices());
		if (packId == null || entry.versions().isEmpty() || entry.versions().getFirst().packId().equals(packId)) next.remove(entry.id());
		else next.put(entry.id(), packId);
		if (next.equals(choices)) return;
		choices = Collections.unmodifiableMap(next);
		dirty = true;
		save();
	}

	public static void resetAll() {
		if (choices().isEmpty()) return;
		choices = Map.of();
		dirty = true;
		save();
	}

	/** The pack whose version of the texture is in use (or will be after Apply). */
	public static String active(Entry entry) {
		String chosen = choices().get(entry.id());
		if (chosen != null) {
			for (Version v : entry.versions()) if (v.packId().equals(chosen)) return chosen;
		}
		return entry.versions().isEmpty() ? null : entry.versions().getFirst().packId();
	}

	public static boolean changed(Entry entry) {
		return choices().containsKey(entry.id());
	}

	/** Choices changed since the textures were last loaded. */
	public static boolean dirty() {
		return dirty;
	}

	public static boolean reloading() {
		return reloading;
	}

	/** Reloads the resources so the choices show, then looks at the packs again. */
	public static void apply() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (reloading) return;
		reloading = true;
		dirty = false;
		mc.reloadResources().whenComplete((done, error) -> mc.execute(() -> {
			reloading = false;
			scannedPacks = "";
			scan();
		}));
	}

	// ------------------------------------------------------------------ the textures

	/** Looks at the resource pack folder again (as the game's own pack screen does on opening), then lists anew. */
	public static void refresh() {
		if (reloading) return;
		try {
			MinecraftClient.getInstance().getResourcePackManager().scanPacks();
		} catch (RuntimeException e) {
			V1zir.LOGGER.warn("Could not look at the resource packs again", e);
		}
		scannedPacks = "";
	}

	public static List<Entry> entries() {
		scan();
		return entries;
	}

	public static int conflicts() {
		return conflicts;
	}

	/** Lists the textures again when the packs (or which of them are enabled) changed since the last time (cheap otherwise). */
	public static void scan() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (reloading) return;
		ResourcePackManager packs = mc.getResourcePackManager();
		Set<String> enabled = new HashSet<>();
		for (ResourcePackProfile profile : packs.getEnabledProfiles()) enabled.add(profile.getId());
		StringBuilder signature = new StringBuilder();
		Map<String, String> names = new HashMap<>();
		List<ResourcePackProfile> others = new ArrayList<>();
		for (ResourcePackProfile profile : packs.getProfiles()) {
			boolean on = enabled.contains(profile.getId());
			signature.append(on ? '+' : '-').append(profile.getId()).append('\n');
			names.put(profile.getId(), profile.getDisplayName().getString());
			if (!on) others.add(profile);
		}
		if (signature.toString().equals(scannedPacks)) return;
		scannedPacks = signature.toString();
		Map<Identifier, List<Resource>> found;
		try {
			found = mc.getResourceManager().findAllResources("textures", id -> id.getPath().endsWith(".png"));
		} catch (RuntimeException e) {
			V1zir.LOGGER.warn("Could not list the textures of the resource packs", e);
			return;
		}
		Map<Identifier, List<Version>> versions = new TreeMap<>();
		// The enabled packs' own versions, the top one first: the one the game uses.
		for (Map.Entry<Identifier, List<Resource>> e : found.entrySet()) {
			if (LegacyTextures.modern(e.getKey()) != null) continue;
			List<Resource> resources = e.getValue();
			List<Version> list = new ArrayList<>(resources.size() + 2);
			// The manager lists them from the lowest pack up.
			for (int i = resources.size() - 1; i >= 0; i--) {
				Resource r = resources.get(i);
				list.add(new Version(r.getPackId(), names.getOrDefault(r.getPackId(), prettyPack(r.getPackId())), r));
			}
			versions.put(e.getKey(), list);
		}
		// Enabled packs from before 1.13: the game never looks where they keep their textures.
		for (Map.Entry<Identifier, List<Resource>> e : found.entrySet()) {
			Identifier now = LegacyTextures.modern(e.getKey());
			List<Version> list = now == null ? null : versions.get(now);
			if (list == null) continue;
			List<Resource> resources = e.getValue();
			for (int i = resources.size() - 1; i >= 0; i--) {
				Resource r = resources.get(i);
				add(list, r.getPackId(), names.getOrDefault(r.getPackId(), prettyPack(r.getPackId())) + " (old format)", r);
			}
		}
		// Packs in the folder that are not enabled: opened to read their textures.
		Set<String> keep = new HashSet<>();
		for (ResourcePackProfile profile : others) {
			ResourcePack pack = opened(profile.getId());
			if (pack == null) continue;
			keep.add(profile.getId());
			String name = names.getOrDefault(profile.getId(), prettyPack(profile.getId()));
			try {
				pack.findResources(ResourceType.CLIENT_RESOURCES, "minecraft", "textures", (id, input) -> {
					if (!id.getPath().endsWith(".png")) return;
					Identifier now = LegacyTextures.modern(id);
					Identifier target = now != null ? now : id;
					List<Version> list = versions.get(target);
					if (list == null) return;
					add(list, profile.getId(), name + (now != null ? " (off, old format)" : " (off)"), new Resource(pack, input, metadata(pack, id)));
				});
			} catch (RuntimeException e) {
				V1zir.LOGGER.warn("Could not list the textures of the resource pack {}", profile.getId(), e);
			}
		}
		// Packs that are gone or enabled now are read through the game again.
		synchronized (OPENED) {
			OPENED.entrySet().removeIf(e -> {
				if (keep.contains(e.getKey())) return false;
				try {
					e.getValue().close();
				} catch (RuntimeException ignored) {
					// Closing anyway.
				}
				return true;
			});
		}
		List<Entry> list = new ArrayList<>(versions.size());
		int many = 0;
		for (Map.Entry<Identifier, List<Version>> e : versions.entrySet()) {
			Identifier id = e.getKey();
			list.add(new Entry(id, shortName(id), group(id), List.copyOf(e.getValue())));
			if (e.getValue().size() > 1) many++;
		}
		entries = List.copyOf(list);
		conflicts = many;
	}

	/** Adds a pack's version of a texture unless that pack already has one in the list. */
	private static void add(List<Version> list, String packId, String name, Resource resource) {
		for (Version v : list) if (v.packId().equals(packId)) return;
		list.add(new Version(packId, name, resource));
	}

	private static String shortName(Identifier id) {
		String path = id.getPath();
		int slash = path.lastIndexOf('/');
		String name = path.substring(slash + 1, path.length() - ".png".length());
		return id.getNamespace().equals("minecraft") ? name : id.getNamespace() + ":" + name;
	}

	private static String group(Identifier id) {
		return CATEGORIES[category(id.getPath())];
	}

	/** The tab of a texture, from its folder and, for items and blocks, its name. */
	private static int category(String path) {
		if (path.startsWith("textures/item/")) {
			String n = path.substring("textures/item/".length(), path.length() - ".png".length());
			if (n.endsWith("_sword")) return SWORDS;
			if (n.endsWith("_pickaxe")) return PICKAXES;
			if (n.endsWith("_axe")) return AXES;
			if (n.endsWith("_shovel") || n.endsWith("_hoe") || n.equals("shears") || n.startsWith("fishing_rod") || n.equals("flint_and_steel")
				|| n.equals("brush") || n.equals("spyglass")) return TOOLS;
			if (n.startsWith("bow") || n.startsWith("crossbow") || n.endsWith("arrow") || n.startsWith("tipped_arrow") || n.equals("trident")
				|| n.equals("mace")) return BOWS;
			if (n.endsWith("_helmet") || n.endsWith("_chestplate") || n.endsWith("_leggings") || n.endsWith("_boots") || n.startsWith("elytra")
				|| n.endsWith("horse_armor") || n.equals("wolf_armor")) return ARMOR;
			if (FOODS.contains(n) || n.startsWith("cooked_") || n.endsWith("_stew") || n.endsWith("_soup")) return FOOD;
			if (n.startsWith("potion") || n.endsWith("_potion") || POTION_ITEMS.contains(n)) return POTIONS;
			return ITEMS;
		}
		if (path.startsWith("textures/block/")) {
			String n = path.substring("textures/block/".length(), path.length() - ".png".length());
			if (n.endsWith("_ore") || n.startsWith("ancient_debris")) return ORES;
			if (n.endsWith("_wool") || n.contains("glass")) return WOOL_GLASS;
			return BLOCKS;
		}
		if (path.startsWith("textures/entity/equipment/") || path.startsWith("textures/models/armor/") || path.startsWith("textures/trims/")) return ARMOR;
		if (path.startsWith("textures/entity/")) return MOBS;
		if (path.startsWith("textures/gui/")) return GUI;
		if (path.startsWith("textures/mob_effect/")) return EFFECTS;
		if (path.startsWith("textures/particle/")) return PARTICLES;
		if (path.startsWith("textures/environment/")) return SKY;
		return OTHER;
	}

	/** The texture minecraft:textures/{path}.png, or null when no pack has it. */
	public static Entry find(String path) {
		Identifier id = Identifier.of("minecraft", "textures/" + path + ".png");
		for (Entry e : entries) if (e.id().equals(id)) return e;
		return null;
	}

	private static String prettyPack(String id) {
		String name = id.startsWith("file/") ? id.substring(5) : id;
		if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
		return name;
	}

	// ------------------------------------------------------------------ config

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(V1zir.MOD_ID).resolve("pack_mixer.json");
	}

	/** Reads the choices; called at start-up, before the first resource load. */
	public static synchronized void load() {
		if (loaded) return;
		loaded = true;
		Path path = file();
		if (!Files.isRegularFile(path)) return;
		try {
			JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
			JsonObject textures = root.getAsJsonObject().getAsJsonObject("textures");
			Map<Identifier, String> map = new LinkedHashMap<>();
			if (textures != null) {
				for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
					Identifier id = Identifier.tryParse(e.getKey());
					if (id != null && e.getValue().isJsonPrimitive()) map.put(id, e.getValue().getAsString());
				}
			}
			choices = Collections.unmodifiableMap(map);
		} catch (IOException | RuntimeException e) {
			V1zir.LOGGER.warn("Could not read {}", path, e);
		}
	}

	private static void save() {
		JsonObject textures = new JsonObject();
		new TreeMap<>(choices.entrySet().stream().collect(java.util.stream.Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue)))
			.forEach(textures::addProperty);
		JsonObject root = new JsonObject();
		root.add("textures", textures);
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			V1zir.LOGGER.warn("Could not save {}", path, e);
		}
	}
}
