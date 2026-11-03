// V1zirGui uniform block - written by dev.v1zir.visuals.render.FrameUniforms (std140).
layout(std140) uniform V1zirGui {
	// x = seconds, y = gui scale, zw = cursor in framebuffer pixels (bottom-left origin), negative when unlit
	vec4 GuiTime;
	// xy = framebuffer size in pixels, zw = 1 / size
	vec4 ScreenInfo;
};

// Soft light around the cursor that lit surfaces catch; radius in GUI pixels.
float v1zirCursorLight(vec2 fragCoord, float radius) {
	if (GuiTime.z < 0.0) return 0.0;
	float d = clamp(length(fragCoord - GuiTime.zw) / (radius * GuiTime.y), 0.0, 1.0);
	float l = 1.0 - d * d * (3.0 - 2.0 * d);
	return l * l;
}
