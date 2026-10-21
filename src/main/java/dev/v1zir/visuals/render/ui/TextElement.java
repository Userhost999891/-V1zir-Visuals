package dev.v1zir.visuals.render.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * A run of SDF glyph quads sharing one atlas page.
 * quads: x0, y0, x1, y1, u0, v0, u1, v1 per glyph; colors: left and right colour per glyph.
 */
public record TextElement(
	RenderPipeline pipeline,
	TextureSetup textureSetup,
	Matrix3x2f pose,
	@Nullable ScreenRect scissorArea,
	@Nullable ScreenRect bounds,
	float[] quads,
	int[] colors,
	int count,
	int weight,
	int softness
) implements SimpleGuiElementRenderState {
	@Override
	public void setupVertices(VertexConsumer vertices) {
		for (int i = 0; i < count; i++) {
			int q = i * 8;
			float x0 = quads[q];
			float y0 = quads[q + 1];
			float x1 = quads[q + 2];
			float y1 = quads[q + 3];
			float u0 = quads[q + 4];
			float v0 = quads[q + 5];
			float u1 = quads[q + 6];
			float v1 = quads[q + 7];
			int left = colors[i * 2];
			int right = colors[i * 2 + 1];
			vertices.vertex(pose, x0, y0).texture(u0, v0).color(left).light(weight, softness);
			vertices.vertex(pose, x0, y1).texture(u0, v1).color(left).light(weight, softness);
			vertices.vertex(pose, x1, y1).texture(u1, v1).color(right).light(weight, softness);
			vertices.vertex(pose, x1, y0).texture(u1, v0).color(right).light(weight, softness);
		}
	}
}
