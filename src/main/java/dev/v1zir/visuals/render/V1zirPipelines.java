package dev.v1zir.visuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.v1zir.visuals.V1zir;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;

/**
 * Every custom pipeline of the mod.
 *
 * GUI pipelines go through vanilla's GuiRenderer (so they sort and layer like vanilla
 * elements); per-element parameters travel in vertex attributes:
 * <pre>
 *   SHAPE / GLASS (entity vertex format)
 *     UV0    float2  position relative to the shape centre (GUI px)
 *     UV1    short2  half size * 8
 *     UV2    short2  corner radius * 8, softness * 8
 *     Normal byte3   outline thickness / 16, mode, corner mask
 *   TEXT (particle vertex format)
 *     UV0    float2  SDF atlas coordinates
 *     UV2    short2  weight shift * 256, extra softness * 256
 * </pre>
 */
public final class V1zirPipelines {
	public static final BlendFunction ADDITIVE = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE);

	public static final RenderPipeline SHAPE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
		.withLocation(V1zir.id("pipeline/gui_shape"))
		.withVertexShader(V1zir.id("core/gui_shape"))
		.withFragmentShader(V1zir.id("core/gui_shape"))
		.withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
		.withCull(false)
		.build();

	public static final RenderPipeline GLASS = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
		.withLocation(V1zir.id("pipeline/gui_glass"))
		.withVertexShader(V1zir.id("core/gui_shape"))
		.withFragmentShader(V1zir.id("core/gui_glass"))
		.withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
		.withSampler("Sampler0")
		.withUniform("V1zirGui", UniformType.UNIFORM_BUFFER)
		.withCull(false)
		.build();

	/** Glassmorphism pane: frosted, refracting glass over the blurred scene (Theme style "Glassmorphism"). */
	public static final RenderPipeline FROSTED = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
		.withLocation(V1zir.id("pipeline/gui_frosted"))
		.withVertexShader(V1zir.id("core/gui_shape"))
		.withFragmentShader(V1zir.id("core/gui_frosted"))
		.withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
		.withSampler("Sampler0")
		.withUniform("V1zirGui", UniformType.UNIFORM_BUFFER)
		.withCull(false)
		.build();

	public static final RenderPipeline TEXT = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
		.withLocation(V1zir.id("pipeline/gui_sdf_text"))
		.withVertexShader(V1zir.id("core/gui_sdf_text"))
		.withFragmentShader(V1zir.id("core/gui_sdf_text"))
		.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
		.withSampler("Sampler0")
		.withCull(false)
		.build();

	public static final RenderPipeline MENU_BACKGROUND = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
		.withLocation(V1zir.id("pipeline/menu_background"))
		.withVertexShader(V1zir.id("core/menu_background"))
		.withFragmentShader(V1zir.id("core/menu_background"))
		.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
		.withSampler("Sampler0")
		.withUniform("V1zirGui", UniformType.UNIFORM_BUFFER)
		.withCull(false)
		.build();

	/** Full screen sky, drawn from inside the vanilla sky pass. */
	public static final RenderPipeline SKY = RenderPipeline.builder()
		.withLocation(V1zir.id("pipeline/shader_sky"))
		.withVertexShader(V1zir.id("core/shader_sky"))
		.withFragmentShader(V1zir.id("core/shader_sky"))
		.withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
		.withUniform("SkyParams", UniformType.UNIFORM_BUFFER)
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.withDepthWrite(false)
		.withoutBlend()
		.withCull(false)
		.build();

	/** Upsamples the reduced-resolution sky texture onto the screen (see SkyTarget). */
	public static final RenderPipeline SKY_UPSAMPLE = RenderPipeline.builder()
		.withLocation(V1zir.id("pipeline/sky_upsample"))
		.withVertexShader(V1zir.id("core/fullscreen"))
		.withFragmentShader(V1zir.id("core/sky_upsample"))
		.withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
		.withSampler("Sampler0")
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.withDepthWrite(false)
		.withoutBlend()
		.withCull(false)
		.build();

	public static final RenderPipeline BLUR_DOWN = blurPipeline("blur_down");
	public static final RenderPipeline BLUR_UP = blurPipeline("blur_up");

	/** World effects (target ESP, jump circles, trails...) - additive glow, depth tested. */
	public static final RenderPipeline WORLD_GLOW = worldPipeline("world_glow", ADDITIVE, true);
	/** Regular alpha blended world geometry (china hat body...). */
	public static final RenderPipeline WORLD_TRANSLUCENT = worldPipeline("world_translucent", BlendFunction.TRANSLUCENT, true);

	private static boolean registered;

	private V1zirPipelines() {
	}

	private static RenderPipeline blurPipeline(String name) {
		return RenderPipeline.builder()
			.withLocation(V1zir.id("pipeline/" + name))
			.withVertexShader(V1zir.id("core/fullscreen"))
			.withFragmentShader(V1zir.id("core/" + name))
			.withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
			.withSampler("Sampler0")
			.withUniform("BlurParams", UniformType.UNIFORM_BUFFER)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withoutBlend()
			.withCull(false)
			.build();
	}

	private static RenderPipeline worldPipeline(String name, BlendFunction blend, boolean depthTest) {
		// Effects are always depth tested against the world: purely visual, nothing shows through walls.
		return RenderPipeline.builder()
			.withLocation(V1zir.id("pipeline/" + name))
			.withVertexShader(V1zir.id("core/world_effect"))
			.withFragmentShader(V1zir.id("core/world_effect"))
			.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
			.withUniform("WorldParams", UniformType.UNIFORM_BUFFER)
			.withDepthTestFunction(depthTest ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withBlend(blend)
			.withCull(false)
			.build();
	}

	public static void register() {
		if (registered) return;
		registered = true;
		RenderPipelines.register(SHAPE);
		RenderPipelines.register(GLASS);
		RenderPipelines.register(FROSTED);
		RenderPipelines.register(TEXT);
		RenderPipelines.register(MENU_BACKGROUND);
		RenderPipelines.register(SKY);
		RenderPipelines.register(SKY_UPSAMPLE);
		RenderPipelines.register(BLUR_DOWN);
		RenderPipelines.register(BLUR_UP);
		RenderPipelines.register(WORLD_GLOW);
		RenderPipelines.register(WORLD_TRANSLUCENT);
	}

	public static boolean isOurs(RenderPipeline pipeline) {
		return pipeline.getLocation().getNamespace().equals(V1zir.MOD_ID);
	}
}
