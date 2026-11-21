package dev.v1zir.visuals.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.v1zir.visuals.V1zir;
import dev.v1zir.visuals.mixin.MinecraftClientAccessor;
import dev.v1zir.visuals.module.ModuleManager;
import dev.v1zir.visuals.module.impl.visuals.HandChamsModule;
import dev.v1zir.visuals.render.sky.CloudNoise;
import dev.v1zir.visuals.render.sky.SkyUniforms;
import dev.v1zir.visuals.render.world.WorldEffectRenderer;
import dev.v1zir.visuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Hand Chams' screen looks (Outline, Sky): the first-person hand, the arm and everything it holds, is drawn into a
 * texture of its own instead of the frame, and a full screen pass then lays it over the frame with the look
 * (assets/v1zir/shaders/core/hand_fx.fsh). Working on the picture of the hand, the outline follows its true silhouette
 * (the blade of a sword, not the square of its texture) and the sky fills exactly what is drawn.
 * <p>
 * The hand is sent there both ways a draw finds its target: the output overrides, which the game's render layers
 * honour, and the client's main framebuffer, which is this one while the hand is drawn. Nothing is allocated before
 * the first use, and the textures are freed a few seconds after the last.
 */
public final class HandFx {
	private static final int SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().get();
	private static final long IDLE_NANOS = 5_000_000_000L;
	private static final Params PARAMS = new Params();
	private static final SkyUniforms.Data SKY = new SkyUniforms.Data();
	private static final Matrix4f VIEW_PROJ = new Matrix4f();
	private static DynamicUniformStorage<Params> storage;
	private static SimpleFramebuffer target;
	private static boolean capturing;
	private static GpuTextureView savedColor;
	private static GpuTextureView savedDepth;
	private static Framebuffer savedMain;
	private static long lastUse;

	private HandFx() {
	}

	/** Once per frame: the uniform storage starts over, and an unused target is freed. */
	public static void flip() {
		if (storage != null) storage.clear();
		if (target != null && !capturing && System.nanoTime() - lastUse > IDLE_NANOS) {
			target.delete();
			target = null;
		}
	}

	/**
	 * Start of renderHand: when Hand Chams draws the hand with a screen look, everything drawn until {@link #end} goes
	 * into the hand's texture. Returns whether it does (and so whether end must follow).
	 */
	public static boolean begin() {
		HandChamsModule chams = ModuleManager.get(HandChamsModule.class);
		if (chams == null || !chams.screenLook() || capturing) return false;
		// Something else is already drawing into a target of its own (a panorama shot): leave the hand to it.
		if (RenderSystem.outputColorTextureOverride != null) return false;
		MinecraftClient mc = V1zir.mc();
		Framebuffer main = mc.getFramebuffer();
		int width = main.textureWidth;
		int height = main.textureHeight;
		if (width <= 0 || height <= 0) return false;
		if (target == null) target = new SimpleFramebuffer("V1zir hand", width, height, true);
		else if (target.textureWidth != width || target.textureHeight != height) target.resize(width, height);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.clearColorTexture(target.getColorAttachment(), 0);
		encoder.clearDepthTexture(target.getDepthAttachment(), 1.0);
		savedColor = RenderSystem.outputColorTextureOverride;
		savedDepth = RenderSystem.outputDepthTextureOverride;
		savedMain = main;
		RenderSystem.outputColorTextureOverride = target.getColorAttachmentView();
		RenderSystem.outputDepthTextureOverride = target.getDepthAttachmentView();
		((MinecraftClientAccessor) mc).v1zir$setFramebuffer(target);
		capturing = true;
		lastUse = System.nanoTime();
		return true;
	}

	/** End of renderHand, after {@link #begin} returned true: the frame is the frame again, and the hand goes onto it. */
	public static void end() {
		if (!capturing) return;
		capturing = false;
		MinecraftClient mc = V1zir.mc();
		((MinecraftClientAccessor) mc).v1zir$setFramebuffer(savedMain);
		RenderSystem.outputColorTextureOverride = savedColor;
		RenderSystem.outputDepthTextureOverride = savedDepth;
		Framebuffer main = savedMain;
		savedMain = null;
		savedColor = null;
		savedDepth = null;
		HandChamsModule chams = ModuleManager.get(HandChamsModule.class);
		if (chams == null || main == null) return;
		composite(mc, main, chams);
	}

	public static boolean capturing() {
		return capturing;
	}

	private static void composite(MinecraftClient mc, Framebuffer main, HandChamsModule chams) {
		int width = main.textureWidth;
		int height = main.textureHeight;
		boolean sky = chams.skyLook();
		Params p = PARAMS;
		p.screen.set(1f / width, 1f / height, width / (float) height, V1zir.time() % 3600f);
		p.look.set(sky ? 2f : 1f, chams.skyOpacity(), chams.shading.f(), chams.edgeGlow());
		int edge = chams.edgeColor();
		// The width is set for a 1080 pixel tall window and follows the window's size.
		p.edge.set(ColorUtil.red(edge) / 255f, ColorUtil.green(edge) / 255f, ColorUtil.blue(edge) / 255f, chams.outlinePixels() * height / 1080f);
		p.lift.set(0.42f, 0.55f, sky ? chams.skyLine() : 1f, 0f);
		if (storage == null) storage = new DynamicUniformStorage<>("V1zir hand UBO", SIZE, 2);
		GpuBufferSlice params = storage.write(p);
		// The sky behind the hand, as the world's camera sees it (the hand's own projection is narrower).
		chams.fillSky(SKY, mc.world, mc.getRenderTickCounter().getTickProgress(false));
		SKY.invViewProj.set(VIEW_PROJ.set(WorldEffectRenderer.projection()).mul(WorldEffectRenderer.view()).invert());
		GpuBufferSlice skyParams = SkyUniforms.write(SKY);
		GpuTextureView noise = CloudNoise.view();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (RenderPass pass = encoder.createRenderPass(() -> "V1zir hand", main.getColorAttachmentView(), OptionalInt.empty())) {
			pass.setPipeline(V1zirPipelines.HAND_FX);
			pass.setUniform("HandParams", params);
			pass.setUniform("SkyParams", skyParams);
			pass.bindTexture("Sampler0", target.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			CloudNoise.bind(pass, noise);
			pass.setVertexBuffer(0, Fullscreen.buffer());
			pass.draw(0, 3);
		}
	}

	/** One instance, refilled every frame: the storage copies it into the buffer on write. */
	private static final class Params implements DynamicUniformStorage.Uploadable {
		final Vector4f screen = new Vector4f();
		final Vector4f look = new Vector4f();
		final Vector4f edge = new Vector4f();
		final Vector4f lift = new Vector4f();

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder.intoBuffer(buffer).putVec4(screen).putVec4(look).putVec4(edge).putVec4(lift);
		}

		@Override
		public boolean equals(Object o) {
			return false;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this);
		}
	}
}
