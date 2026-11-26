package dev.v1zir.visuals.mixin;

import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What the loading screen needs of the Mojang one it is drawn in place of (LoadingScreen). */
@Mixin(SplashOverlay.class)
public interface SplashOverlayAccessor {
	@Accessor("reloading")
	boolean v1zir$reloading();

	/** Its progress, already smoothed. */
	@Accessor("progress")
	float v1zir$progress();

	/** When the load was done (Util.getMeasuringTimeMs), -1 until then. */
	@Accessor("reloadCompleteTime")
	long v1zir$reloadCompleteTime();

	/** When a reload's overlay first showed, -1 until then (and always at start). */
	@Accessor("reloadStartTime")
	long v1zir$reloadStartTime();
}
