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
	int flags = int(style.y);
	// Black glass: the smooth blur behind, a little desaturated and with bright skies pulled down, under the
	// tint. No grain and no veil: the surface is clean.
	backdrop = mix(vec3(luma), backdrop, 0.8) * (1.0 - 0.35 * smoothstep(0.45, 1.0, luma));
	vec3 col = mix(backdrop, vertexColor.rgb, vertexColor.a);
	// Its inner edge catches a faint light, brighter at the top.
	if ((flags & V1ZIR_MATTE) != 0) {
		float v = clamp((localPos.y + shape.y) / max(2.0 * shape.y, 1.0), 0.0, 1.0);
		float inner = 1.0 - smoothstep(0.5, 2.5, -d);
		col += inner * mix(0.035, 0.0, v);
	}
	if ((flags & V1ZIR_LIT) != 0) col += v1zirCursorLight(gl_FragCoord.xy, 150.0) * 0.035;
	// Half a step of dither against banding in the dark tint: below what the eye can see as grain.
	col += v1zirGrain(gl_FragCoord.xy) * (1.0 / 255.0);
	// Vertex alpha is the tint strength; the fade of the whole panel arrives in the thickness channel.
	float fade = clamp(style.x / 16.0, 0.0, 1.0);
	fragColor = vec4(col, coverage * fade) * ColorModulator;
}
