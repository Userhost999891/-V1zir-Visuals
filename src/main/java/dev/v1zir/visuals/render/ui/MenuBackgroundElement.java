package dev.v1zir.visuals.render.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * A wallpaper target (rendered at reduced resolution by WallpaperRenderer) upsampled into a rounded rectangle: the
 * whole screen, the sliding lock screen or a card of the wallpaper picker. Vertices follow ShapeElement's layout.
 */
public record MenuBackgroundElement(
	TextureSetup textureSetup,
	Matrix3x2f pose,
	@Nullable ScreenRect scissorArea,
	@Nullable ScreenRect bounds,
	float x, float y, float width, float height,
	float radius, int corners, int color
) implements SimpleGuiElementRenderState {
	@Override
	public void setupVertices(VertexConsumer vertices) {
		// One pixel past the edge leaves room for the anti-aliased rim; the texture clamps there.
		float hw = width * 0.5f;
		float hh = height * 0.5f;
		float lx = hw + 1f;
		float ly = hh + 1f;
		float cx = x + hw;
		float cy = y + hh;
		int halfW = fixed(hw);
		int halfH = fixed(hh);
		int rad = fixed(radius);
		float mask = (corners + 0.5f) / 127f;
		vertex(vertices, cx - lx, cy - ly, -lx, -ly, halfW, halfH, rad, mask);
		vertex(vertices, cx - lx, cy + ly, -lx, ly, halfW, halfH, rad, mask);
		vertex(vertices, cx + lx, cy + ly, lx, ly, halfW, halfH, rad, mask);
		vertex(vertices, cx + lx, cy - ly, lx, -ly, halfW, halfH, rad, mask);
	}

	private void vertex(VertexConsumer vertices, float px, float py, float localX, float localY, int halfW, int halfH, int rad, float mask) {
		vertices.vertex(pose, px, py)
			.color(color)
			.texture(localX, localY)
			.overlay(halfW, halfH)
			.light(rad, 0)
			.normal(0f, 0f, mask);
	}

	private static int fixed(float value) {
		return Math.max(0, Math.min(32767, Math.round(value * 8f)));
	}

	@Override
	public RenderPipeline pipeline() {
		return V1zirPipelines.MENU_BACKGROUND;
	}
}
