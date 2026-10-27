package dev.v1zir.visuals.render.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * A region (u0, v0)-(u1, v1) of a texture filling a rounded rectangle, submitted to the vanilla GUI render
 * state (V1zirPipelines.IMAGE; its javadoc has the vertex layout). The colour tints it and carries its opacity.
 */
public record ImageElement(
	RenderPipeline pipeline,
	TextureSetup textureSetup,
	Matrix3x2f pose,
	@Nullable ScreenRect scissorArea,
	@Nullable ScreenRect bounds,
	float x, float y, float width, float height, float radius, int color,
	float u0, float v0, float u1, float v1, int flags
) implements SimpleGuiElementRenderState {
	/** Flag: pixel art (skins), every texel a crisp square with anti-aliased edges. */
	public static final int PIXEL_ART = 16;
	/** Flag: the texture holds premultiplied colour (offscreen entity renders). */
	public static final int PREMULTIPLIED = 32;
	/** How far the quad reaches past the image, for the anti-aliased rim; gui_image.vsh derives the half size from it. */
	static final float PAD = 2f;
	private static final float UV_SCALE = 16384f;

	@Override
	public void setupVertices(VertexConsumer vertices) {
		emit(vertices, pose, x, y, width, height, radius, color, u0, v0, u1, v1, flags);
	}

	static void emit(VertexConsumer vertices, Matrix3x2f pose, float x, float y, float w, float h, float radius, int color,
					 float u0, float v0, float u1, float v1, int flags) {
		float lx = w * 0.5f + PAD;
		float ly = h * 0.5f + PAD;
		float cx = x + w * 0.5f;
		float cy = y + h * 0.5f;
		int su0 = uv(u0);
		int sv0 = uv(v0);
		int su1 = uv(u1);
		int sv1 = uv(v1);
		// Radius in 1/8 px split over two 7-bit bytes; the third carries the corner mask (all corners) and the flags.
		int rad = Math.max(0, Math.min(16383, Math.round(radius * 8f)));
		float n1 = ((rad >> 7) + 0.5f) / 127f;
		float n2 = ((rad & 127) + 0.5f) / 127f;
		float n3 = (flags + 0.5f) / 127f;
		vertex(vertices, pose, cx - lx, cy - ly, -lx, -ly, color, su0, sv0, su1, sv1, n1, n2, n3);
		vertex(vertices, pose, cx - lx, cy + ly, -lx, ly, color, su0, sv0, su1, sv1, n1, n2, n3);
		vertex(vertices, pose, cx + lx, cy + ly, lx, ly, color, su0, sv0, su1, sv1, n1, n2, n3);
		vertex(vertices, pose, cx + lx, cy - ly, lx, -ly, color, su0, sv0, su1, sv1, n1, n2, n3);
	}

	private static void vertex(VertexConsumer vertices, Matrix3x2f pose, float px, float py, float localX, float localY, int color,
							   int u0, int v0, int u1, int v1, float n1, float n2, float n3) {
		vertices.vertex(pose, px, py)
			.color(color)
			.texture(localX, localY)
			.overlay(u0, v0)
			.light(u1, v1)
			.normal(n1, n2, n3);
	}

	private static int uv(float value) {
		return Math.max(-32768, Math.min(32767, Math.round(value * UV_SCALE)));
	}
}
