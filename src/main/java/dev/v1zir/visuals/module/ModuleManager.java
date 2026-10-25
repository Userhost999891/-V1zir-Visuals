package dev.v1zir.visuals.module;

import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.module.impl.client.ClickGuiModule;
import dev.v1zir.visuals.module.impl.client.CustomUiModule;
import dev.v1zir.visuals.module.impl.client.MainMenuModule;
import dev.v1zir.visuals.module.impl.client.ThemeModule;
import dev.v1zir.visuals.module.impl.effects.ChinaHatModule;
import dev.v1zir.visuals.module.impl.effects.CrosshairModule;
import dev.v1zir.visuals.module.impl.effects.CrystalChamsModule;
import dev.v1zir.visuals.module.impl.effects.DamageNumbersModule;
import dev.v1zir.visuals.module.impl.effects.HitColorModule;
import dev.v1zir.visuals.module.impl.effects.HitMarkerModule;
import dev.v1zir.visuals.module.impl.effects.HitParticlesModule;
import dev.v1zir.visuals.module.impl.effects.JumpCirclesModule;
import dev.v1zir.visuals.module.impl.effects.KillEffectsModule;
import dev.v1zir.visuals.module.impl.effects.PopChamsModule;
import dev.v1zir.visuals.module.impl.effects.TargetEspModule;
import dev.v1zir.visuals.module.impl.effects.TotemAnimationModule;
import dev.v1zir.visuals.module.impl.effects.TrailsModule;
import dev.v1zir.visuals.module.impl.hud.ArrayListModule;
import dev.v1zir.visuals.module.impl.hud.CoordinatesModule;
import dev.v1zir.visuals.module.impl.hud.KeybindsModule;
import dev.v1zir.visuals.module.impl.hud.NotificationsModule;
import dev.v1zir.visuals.module.impl.hud.PotionsModule;
import dev.v1zir.visuals.module.impl.hud.TargetHudModule;
import dev.v1zir.visuals.module.impl.hud.WatermarkModule;
import dev.v1zir.visuals.module.impl.visuals.AmbienceModule;
import dev.v1zir.visuals.module.impl.visuals.AspectRatioModule;
import dev.v1zir.visuals.module.impl.visuals.CameraTweaksModule;
import dev.v1zir.visuals.module.impl.visuals.ColorGradingModule;
import dev.v1zir.visuals.module.impl.visuals.DamageTintModule;
import dev.v1zir.visuals.module.impl.visuals.EntityGlowModule;
import dev.v1zir.visuals.module.impl.visuals.FreeLookModule;
import dev.v1zir.visuals.module.impl.visuals.FullBrightModule;
import dev.v1zir.visuals.module.impl.visuals.HandChamsModule;
import dev.v1zir.visuals.module.impl.visuals.NoRenderModule;
import dev.v1zir.visuals.module.impl.visuals.ShaderSkyModule;
import dev.v1zir.visuals.module.impl.visuals.SwingAnimationsModule;
import dev.v1zir.visuals.module.impl.visuals.ViewModelModule;
import dev.v1zir.visuals.module.impl.visuals.WorldParticlesModule;
import dev.v1zir.visuals.module.impl.visuals.ZoomModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
	private static final Map<Class<? extends Module>, Module> BY_CLASS = new LinkedHashMap<>();
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleManager() {
	}

	public static void init() {
		if (!MODULES.isEmpty()) return;
		// Visuals
		register(new ShaderSkyModule());
		register(new AmbienceModule());
		register(new FullBrightModule());
		register(new ViewModelModule());
		register(new SwingAnimationsModule());
		register(new HandChamsModule());
		register(new ZoomModule());
		register(new FreeLookModule());
		register(new CameraTweaksModule());
		register(new AspectRatioModule());
		register(new NoRenderModule());
		register(new DamageTintModule());
		register(new ColorGradingModule());
		register(new EntityGlowModule());
		// Effects
		register(new TargetEspModule());
		register(new JumpCirclesModule());
		register(new ChinaHatModule());
		register(new TrailsModule());
		register(new HitParticlesModule());
		register(new CrosshairModule());
		register(new HitColorModule());
		register(new DamageNumbersModule());
		register(new HitMarkerModule());
		register(new KillEffectsModule());
		register(new PopChamsModule());
		register(new TotemAnimationModule());
		register(new CrystalChamsModule());
		// World
		register(new WorldParticlesModule());
		// HUD
		register(new WatermarkModule());
		register(new ArrayListModule());
		register(new KeybindsModule());
		register(new PotionsModule());
		register(new TargetHudModule());
		register(new CoordinatesModule());
		register(new NotificationsModule());
		// Client
		register(new ClickGuiModule());
		register(new ThemeModule());
		register(new MainMenuModule());
		register(new CustomUiModule());
		V1zir.LOGGER.info("Registered {} modules", MODULES.size());
	}

	private static void register(Module module) {
		BY_CLASS.put(module.getClass(), module);
		MODULES.add(module);
	}

	@SuppressWarnings("unchecked")
	public static <T extends Module> T get(Class<T> type) {
		return (T) BY_CLASS.get(type);
	}

	public static boolean isEnabled(Class<? extends Module> type) {
		Module module = BY_CLASS.get(type);
		return module != null && module.isEnabled();
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(MODULES);
	}

	public static List<Module> byCategory(Category category) {
		List<Module> list = new ArrayList<>();
		for (Module module : MODULES) if (module.category() == category) list.add(module);
		return list;
	}

	public static Module byName(String name) {
		for (Module module : MODULES) if (module.name().equalsIgnoreCase(name)) return module;
		return null;
	}

	public static void tick() {
		for (Module module : MODULES) {
			if (!module.isEnabled()) continue;
			try {
				module.onTick();
			} catch (RuntimeException e) {
				V1zir.LOGGER.error("Error ticking {}", module.name(), e);
			}
		}
	}

	/** Toggles modules bound to the key. Returns true if something was toggled. */
	public static boolean onKey(int key) {
		boolean handled = false;
		for (Module module : MODULES) {
			if (module.key() == key && module.canToggle()) {
				module.toggle();
				handled = true;
			}
		}
		return handled;
	}
}
