// TransitionParams - written by dev.v1zir.visuals.gui.clickgui.WindowTransition (std140).
// Positions are framebuffer pixels with the origin at the top left, like the GUI.
layout(std140) uniform TransitionParams {
	// window x, y, width, height
	vec4 Rect;
	// framebuffer size and 1 / size
	vec4 Screen;
	// x = progress (shatter: seconds), y = opacity, z = mode, w = window scale (fade)
	vec4 Anim;
	// x = window corner radius, y = focal length of the perspective, z = tilt (radians), w = depth
	vec4 Shape;
	// x = backdrop opacity, y = 1 when Sampler1 holds the blurred world, z = dim of the backdrop
	vec4 Backdrop;
	// xy = impact point of the shatter, z = gravity (pixels / s^2), w = seconds the cracks take to spread
	vec4 Origin;
};

// GUI position to texture coordinates of a render target (bottom-left origin).
vec2 transitionUv(vec2 p) {
	return vec2(p.x * Screen.z, 1.0 - p.y * Screen.w);
}
