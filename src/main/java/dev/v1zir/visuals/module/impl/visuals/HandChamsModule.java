package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;

/**
 * Tints the first-person arm and held items. Items are tinted through the vertex colour of their
 * quads while a first-person item is drawn (ItemRendererMixin), the arm through the model part tint
 * (PlayerEntityRendererMixin). Both go through vanilla's own draw, so the cost is nothing extra.
 * <p>
 * Glow makes them emissive: full brightness, so they light up in the dark, and for the arm the
 * emissive layer without shading, which reads as a soft self-lit glow. A real halo would need an
 * extra full-screen blur pass for two small models, which is not worth the frame time.
 */
public final class HandChamsModule extends Module {
	/** Packed light of a fully lit vertex (block and sky light 15). */
	private static final int FULL_BRIGHT = 0xF000F0;

	public final BoolSetting items = add(new BoolSetting("Items", true));
	public final BoolSetting hand = add(new BoolSetting("Hand", true));
	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF8FB4FF, false).visibleIf(() -> !themeColor.on()));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.8, 0.15, 1.0, 0.05));
	public final BoolSetting glow = add(new BoolSetting("Glow", true));

	// Read for every quad of a first-person item, so they are plain static fields set once per item.
	private static boolean tinting;
	private static float red;
	private static float green;
	private static float blue;
	private static float alpha;
	private static boolean fullBright;

	public HandChamsModule() {
		super("Hand Chams", "Tint your hand and held items", Category.VISUALS);
		setSubCategory("Render");
	}

	/** Start of an item draw; only first-person items are tinted. */
	public static void beginItem(boolean firstPerson) {
		tinting = false;
		if (!firstPerson) return;
		HandChamsModule module = ModuleManager.get(HandChamsModule.class);
		if (module == null || !module.isEnabled() || !module.items.on()) return;
		int c = module.argb();
		red = ColorUtil.red(c) / 255f;
		green = ColorUtil.green(c) / 255f;
		blue = ColorUtil.blue(c) / 255f;
		alpha = ColorUtil.alpha(c) / 255f;
		fullBright = module.glow.on();
		tinting = true;
	}

	public static void endItem() {
		tinting = false;
	}

	public static float red(float r) {
		return tinting ? r * red : r;
	}

	public static float green(float g) {
		return tinting ? g * green : g;
	}

	public static float blue(float b) {
		return tinting ? b * blue : b;
	}

	public static float alpha(float a) {
		return tinting ? a * alpha : a;
	}

	public static int itemLight(int light) {
		return tinting && fullBright ? FULL_BRIGHT : light;
	}

	public boolean tintsHand() {
		return isEnabled() && hand.on();
	}

	public int handLight(int light) {
		return glow.on() ? FULL_BRIGHT : light;
	}

	public boolean glowingHand() {
		return tintsHand() && glow.on();
	}

	/** Tint colour with the opacity as alpha; glow lifts it towards white so it reads as lit. */
	public int argb() {
		int c = themeColor.on() ? Theme.accent() : color.argb();
		if (glow.on()) c = ColorUtil.brighten(c, 0.2f);
		return ColorUtil.withAlpha(c, Math.round(255 * opacity.f()));
	}
}
