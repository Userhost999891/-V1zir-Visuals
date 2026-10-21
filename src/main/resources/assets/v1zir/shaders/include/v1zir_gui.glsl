// V1zirGui uniform block - written by dev.v1zir.visuals.render.FrameUniforms (std140).
layout(std140) uniform V1zirGui {
	// x = seconds, y = gui scale, zw = unused
	vec4 GuiTime;
	// xy = framebuffer size in pixels, zw = 1 / size
	vec4 ScreenInfo;
};
