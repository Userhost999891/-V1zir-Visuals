package dev.v1zir.visuals.render.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.v1zir.visuals.render.V1zirPipelines;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/** Full screen quad running the sky shader with a virtual camera (main menu background). */
public record MenuBackgroundElement(
	Matrix3x2f pose,
	@Nullable ScreenRect scissorArea,
	@Nullable ScreenRect bounds,
	float width,
	float height,
	int color
) implements SimpleGuiElementRenderState {
	@Override
	public void setupVertices(VertexConsumer vertices) {
		vertices.vertex(pose, 0, 0).texture(0, 0).color(color);
		vertices.vertex(pose, 0, height).texture(0, 1).color(color);
		vertices.vertex(pose, width, height).texture(1, 1).color(color);
		vertices.vertex(pose, width, 0).texture(1, 0).color(color);
	}

	@Override
	public RenderPipeline pipeline() {
		return V1zirPipelines.MENU_BACKGROUND;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.empty();
	}
}
