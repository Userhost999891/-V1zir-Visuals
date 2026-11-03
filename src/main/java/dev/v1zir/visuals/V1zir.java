package dev.v1zir.visuals;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class V1zir {
	public static final String MOD_ID = "v1zir";
	public static final String NAME = "V1zir Visuals";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	private V1zir() {
	}

	public static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
	}

	/** Seconds since the client started, used to drive every animation. */
	public static float time() {
		return (float) ((System.nanoTime() - START_NANOS) / 1.0e9);
	}

	private static final long START_NANOS = System.nanoTime();
}
