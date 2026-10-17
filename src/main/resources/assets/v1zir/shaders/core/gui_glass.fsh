#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:v1zir_gui.glsl>
#moj_import <v1zir:sdf_shapes.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
in vec4 shape;
in vec3 style;

out vec4 fragColor;

void main() {
	float d = v1zirShapeDistance(localPos, shape, style.z);
	float coverage = v1zirShapeCoverage(d, shape, style);
	if (coverage <= 0.0) discard;
	// Sampler0 holds the blurred world behind the GUI (same orientation as the framebuffer).
	vec2 uv = gl_FragCoord.xy * ScreenInfo.zw;
	vec3 backdrop = texture(Sampler0, uv).rgb;
	float luma = dot(backdrop, vec3(0.2126, 0.7152, 0.0722));
	backdrop = mix(vec3(luma), backdrop, 1.15);
	vec3 col = mix(backdrop, vertexColor.rgb, vertexColor.a);
	// Faint inner light along the top edge sells the glass look.
	float topness = clamp(-localPos.y / max(shape.y, 1.0), 0.0, 1.0);
	float rim = smoothstep(-2.5, 0.0, d) * topness * topness * topness * 0.07;
	fragColor = vec4(col + rim, coverage) * ColorModulator;
}
