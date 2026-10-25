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
 * A hole (GUI coordinates, empty when x1 <= x0) leaves out a middle part an opaque surface covers.
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
	int colorTL, int colorTR, int colorBR, int colorBL,
	float holeX0, float holeY0, float holeX1, float holeY1
) implements SimpleGuiElementRenderState {
	public static final int FILL = 0;
	public static final int OUTLINE = 1;
	public static final int SHADOW = 2;
	/** Flag: fine static grain over the fill (the matte surface of panels and cards). */
	public static final int MATTE = 8;
	/** Flag: the shape catches the soft light that follows the cursor (FrameUniforms.cursor). */
	public static final int LIT = 16;

	/** How far the quad reaches past the shape: a shadow fades out exactly at its softness, edges need room to anti-alias. */
	public static float padding(int mode, float softness) {
		return (mode & 7) == SHADOW ? Math.max(softness, 0.5f) + 0.25f : 2f;
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
		if (holeX1 > holeX0 && holeY1 > holeY0) {
			// A frame of four quads around the hole (single colour only): the distance field is the same, the
			// covered middle is just never shaded.
			float x0 = cx - lx;
			float y0 = cy - ly;
			float x1 = cx + lx;
			float y1 = cy + ly;
			float hx0 = Math.max(x0, holeX0);
			float hy0 = Math.max(y0, holeY0);
			float hx1 = Math.min(x1, holeX1);
			float hy1 = Math.min(y1, holeY1);
			quad(vertices, x0, y0, x1, hy0, cx, cy, halfW, halfH, rad, soft, n1, n2, n3);
			quad(vertices, x0, hy1, x1, y1, cx, cy, halfW, halfH, rad, soft, n1, n2, n3);
			quad(vertices, x0, hy0, hx0, hy1, cx, cy, halfW, halfH, rad, soft, n1, n2, n3);
			quad(vertices, hx1, hy0, x1, hy1, cx, cy, halfW, halfH, rad, soft, n1, n2, n3);
			return;
		}
		vertex(vertices, cx - lx, cy - ly, -lx, -ly, colorTL, halfW, halfH, rad, soft, n1, n2, n3);
		vertex(vertices, cx - lx, cy + ly, -lx, ly, colorBL, halfW, halfH, rad, soft, n1, n2, n3);
		vertex(vertices, cx + lx, cy + ly, lx, ly, colorBR, halfW, halfH, rad, soft, n1, n2, n3);
		vertex(vertices, cx + lx, cy - ly, lx, -ly, colorTR, halfW, halfH, rad, soft, n1, n2, n3);
	}

	private void quad(VertexConsumer vertices, float x0, float y0, float x1, float y1, float cx, float cy,
					  int halfW, int halfH, int rad, int soft, float n1, float n2, float n3) {
		vertex(vertices, x0, y0, x0 - cx, y0 - cy, colorTL, halfW, halfH, rad, soft, n1, n2, n3);
		vertex(vertices, x0, y1, x0 - cx, y1 - cy, colorTL, halfW, halfH, rad, soft, n1, n2, n3);
		vertex(vertices, x1, y1, x1 - cx, y1 - cy, colorTL, halfW, halfH, rad, soft, n1, n2, n3);
		vertex(vertices, x1, y0, x1 - cx, y0 - cy, colorTL, halfW, halfH, rad, soft, n1, n2, n3);
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
