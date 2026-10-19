#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:v1zir_gui.glsl>
#moj_import <v1zir:sdf_shapes.glsl>

// Glassmorphism: a frosted pane over the heavily blurred scene behind it. The blur itself is
// done once per frame (BackdropBlur); this shader only samples it, so a pane costs about as
// much as a plain rounded rectangle.
uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
in vec4 shape;
in vec3 style;

out vec4 fragColor;

float vFrostHash(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

void main() {
	float d = v1zirShapeDistance(localPos, shape, style.z);
	float coverage = v1zirShapeCoverage(d, shape, vec3(0.0, 0.0, style.z));
	if (coverage <= 0.0) discard;

	// Edge refraction: within a few pixels of the rim the pane bends the view towards its centre,
	// like the rounded edge of a thick sheet of glass.
	float bevel = 10.0;
	float edge = clamp(1.0 + d / bevel, 0.0, 1.0);
	vec2 grad = vec2(dFdx(d), dFdy(d));
	vec2 dir = dot(grad, grad) > 1e-8 ? normalize(grad) : vec2(0.0);
	vec2 uv = gl_FragCoord.xy * ScreenInfo.zw - dir * edge * edge * 6.0 * ScreenInfo.zw * GuiTime.y;
	vec3 back = texture(Sampler0, uv).rgb;

	// Frost: a touch more saturation, a milky white haze, and on bright scenes a slight darkening
	// so text on the pane stays readable.
	float luma = dot(back, vec3(0.2126, 0.7152, 0.0722));
	back = mix(vec3(luma), back, 1.18);
	back *= mix(1.0, 0.72, smoothstep(0.45, 0.95, luma));
	vec3 col = mix(back, vertexColor.rgb, vertexColor.a);
	col += 0.035;

	// Light catching the edge: a soft inner glow along the rim, strongest towards the top-left.
	vec2 rel = localPos / max(shape.xy, vec2(1.0));
	float light = clamp(0.55 - (rel.x + rel.y) * 0.3, 0.0, 1.0);
	float rim = exp(d / 3.0) * (0.10 + 0.14 * light);
	// Broad diagonal sheen across the pane.
	float sheen = light * light * 0.035;
	// Static micro-grain gives the frosted texture without any flicker.
	float grain = (vFrostHash(floor(localPos * GuiTime.y)) - 0.5) * 0.012;

	float opacity = clamp(style.x / 16.0, 0.0, 1.0);
	fragColor = vec4(col + rim + sheen + grain, coverage * opacity) * ColorModulator;
}
