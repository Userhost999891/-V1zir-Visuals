package dev.v1zir.visuals.module.impl.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.setting.BoolSetting;
import dev.v1zir.visuals.module.setting.ColorSetting;
import dev.v1zir.visuals.module.setting.NumberSetting;
import dev.v1zir.visuals.util.ColorUtil;
import dev.v1zir.visuals.util.Theme;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;

/**
 * Recolours the enchantment glint of items and armour and changes its speed. The colour is baked
 * into vanilla's two glint textures, only when it changes, and the originals are written back when
 * the module turns off, so the glint render layers, shader and blending stay vanilla's. The speed
 * scales the glint scroll (TextureTransformMixin).
 */
public final class GlintColorModule extends Module {
	private static final Identifier[] TEXTURES = {ItemRenderer.ITEM_ENCHANTMENT_GLINT, ItemRenderer.ENTITY_ENCHANTMENT_GLINT};

	public final BoolSetting themeColor = add(new BoolSetting("Theme color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFB46CFF, false).visibleIf(() -> !themeColor.on()));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.3, 2.0, 0.05).suffix("x"));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.0, 5.0, 0.05).suffix("x"));

	private final NativeImage[] original = new NativeImage[TEXTURES.length];
	private final NativeImage[] tinted = new NativeImage[TEXTURES.length];
	/** The GPU texture each tint was written into; a resource reload replaces it, which is how a reload is noticed. */
	private final GpuTexture[] written = new GpuTexture[TEXTURES.length];
	private int lastColor;
	private float lastBrightness;
	/** Glint clock in milliseconds, advanced at the chosen speed so changing it never jumps. */
	private double glintTime;
	private long glintLast;

	public GlintColorModule() {
		super("Glint Color", "Colour and speed of the enchantment glint", Category.SHADERS);
		setSubCategory("World");
	}

	@Override
	protected void onEnable() {
		glintLast = System.nanoTime();
		// Vanilla's clock (Util.getMeasuringTimeMs), so the glint carries on where it was.
		glintTime = glintLast / 1.0e6;
	}

	@Override
	protected void onDisable() {
		for (int i = 0; i < TEXTURES.length; i++) {
			if (written[i] != null && written[i] == texture(i)) RenderSystem.getDevice().createCommandEncoder().writeToTexture(written[i], original[i]);
			written[i] = null;
		}
	}

	@Override
	public void onTick() {
		int argb = themeColor.on() ? Theme.accent() : color.argb();
		float bright = brightness.f();
		boolean changed = argb != lastColor || bright != lastBrightness;
		lastColor = argb;
		lastBrightness = bright;
		for (int i = 0; i < TEXTURES.length; i++) {
			GpuTexture gpu = texture(i);
			if (gpu == null) continue;
			if (gpu != written[i]) {
				// First time, or the textures were reloaded (maybe from another resource pack).
				if (!load(i)) continue;
			} else if (!changed) {
				continue;
			}
			tint(original[i], tinted[i], argb, bright);
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(gpu, tinted[i]);
			written[i] = gpu;
		}
	}

	private static GpuTexture texture(int i) {
		return mc.getTextureManager().getTexture(TEXTURES[i]).getGlTexture();
	}

	/** Reads the untinted glint from the resources. */
	private boolean load(int i) {
		try (InputStream in = mc.getResourceManager().open(TEXTURES[i])) {
			NativeImage image = NativeImage.read(in);
			if (original[i] != null) original[i].close();
			if (tinted[i] != null) tinted[i].close();
			original[i] = image;
			tinted[i] = new NativeImage(image.getWidth(), image.getHeight(), false);
			return true;
		} catch (IOException e) {
			V1zir.LOGGER.error("Could not read {}", TEXTURES[i], e);
			return false;
		}
	}

	/** The glint's brightness pattern in the chosen colour; alpha is kept. */
	private static void tint(NativeImage from, NativeImage to, int argb, float bright) {
		float r = ColorUtil.red(argb) * bright / 255f;
		float g = ColorUtil.green(argb) * bright / 255f;
		float b = ColorUtil.blue(argb) * bright / 255f;
		for (int y = 0; y < from.getHeight(); y++) {
			for (int x = 0; x < from.getWidth(); x++) {
				int p = from.getColorArgb(x, y);
				int v = Math.max(ColorUtil.red(p), Math.max(ColorUtil.green(p), ColorUtil.blue(p)));
				to.setColorArgb(x, y, ColorUtil.argb(ColorUtil.alpha(p), Math.round(r * v), Math.round(g * v), Math.round(b * v)));
			}
		}
	}

	/**
	 * The glint texture matrix (head of TextureTransform.getGlintTransformation), built like
	 * vanilla's on the module's clock; null when the module is off and vanilla's applies.
	 */
	public static Matrix4f glintTransform(float scale) {
		GlintColorModule module = ModuleManager.get(GlintColorModule.class);
		if (module == null || !module.isEnabled()) return null;
		long now = System.nanoTime();
		module.glintTime += (now - module.glintLast) / 1.0e6 * module.speed.get();
		module.glintLast = now;
		long l = (long) (module.glintTime * mc.options.getGlintSpeed().getValue() * 8.0);
		float u = (l % 110000L) / 110000f;
		float v = (l % 30000L) / 30000f;
		return new Matrix4f().translation(-u, v, 0f).rotateZ((float) (Math.PI / 18)).scale(scale);
	}
}
