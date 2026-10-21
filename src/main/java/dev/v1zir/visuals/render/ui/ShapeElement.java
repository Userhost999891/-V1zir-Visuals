package dev.v1zir.visuals.render.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * One SDF shape (rounded rectangle, circle, outline, shadow or glass panel) submitted to the
 * vanilla GUI render state. Colours are given per corner: top-left, top-right, bottom-right, bottom-left.
 */
public record ShapeElement(
	RenderPipeline pipeline,
	TextureSetup textureSetup,
	Matrix3x2f pose,
	@Nullable ScreenRect scissorArea,
	@Nullable ScreenRect bounds,
	float x, float y, float width, float height,
	float radius, float softness, float thickness,
	int mode, int cornerMask,
	int colorTL, int colorTR, int colorBR, int colorBL
) implements SimpleGuiElementRenderState {
	public static final int FILL = 0;
	public static final int OUTLINE = 1;
	public static final int SHADOW = 2;

	public static float padding(int mode, float softness) {
		return (mode == SHADOW ? softness : 0f) + 2f;
	}

	@Override
	public void setupVertices(VertexConsumer vertices) {
		float pad = padding(mode, softness);
		float hw = width * 0.5f;
		float hh = height * 0.5f;
		float cx = x + hw;
		float cy = y + hh;
		float lx = hw + pad;
		float ly = hh + pad;
		int halfW = fixed(hw);
		int halfH = fixed(hh);
		int rad = fixed(radius);
		int soft = fixed(softness);
		float n1 = Math.min(thickness / 16f, 1f);
		float n2 = (mode + 0.5f) / 127f;
		float n3 = (cornerMask + 0.5f) / 127f;
		vertex(vertices, cx - lx, cy - ly, -lx, -ly, colorTL, halfW, halfH, rad, soft, n1, n2, n3);
		vertex(vertices, cx - lx, cy + ly, -lx, ly, colorBL, halfW, halfH, rad, soft, n1, n2, n3);
		vertex(vertices, cx + lx, cy + ly, lx, ly, colorBR, halfW, halfH, rad, soft, n1, n2, n3);
		vertex(vertices, cx + lx, cy - ly, lx, -ly, colorTR, halfW, halfH, rad, soft, n1, n2, n3);
	}

	private void vertex(VertexConsumer vertices, float px, float py, float localX, float localY, int color,
						int halfW, int halfH, int rad, int soft, float n1, float n2, float n3) {
		vertices.vertex(pose, px, py)
			.color(color)
			.texture(localX, localY)
			.overlay(halfW, halfH)
			.light(rad, soft)
			.normal(n1, n2, n3);
	}

	private static int fixed(float value) {
		return Math.max(0, Math.min(32767, Math.round(value * 8f)));
	}
}
