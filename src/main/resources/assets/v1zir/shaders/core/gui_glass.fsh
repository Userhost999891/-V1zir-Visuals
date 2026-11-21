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
	bool matte = (flags & V1ZIR_MATTE) != 0;
	// Matte: the backdrop loses some saturation and bright skies are pulled down, so the frost reads as a
	// soft diffuse surface (no gloss) and light text keeps its contrast; a faint milky veil diffuses it further.
	backdrop = mix(vec3(luma), backdrop, 0.85) * (1.0 - 0.3 * smoothstep(0.45, 1.0, luma));
	if (matte) backdrop = mix(backdrop, vec3(luma * 0.85 + 0.07), 0.14);
	vec3 col = mix(backdrop, vertexColor.rgb, vertexColor.a);
	if (matte) {
		// Where the panel is (0 at its top, 1 at its bottom): light falls on the frost from above, a soft sheen
		// over its upper part, and its inner edge catches it, brighter at the top than at the bottom.
		float v = clamp((localPos.y + shape.y) / max(2.0 * shape.y, 1.0), 0.0, 1.0);
		col += vec3(0.03) * (1.0 - smoothstep(0.0, 0.5, v));
		float inner = 1.0 - smoothstep(0.5, 3.0, -d);
		col += inner * mix(0.05, -0.015, v);
	}
	if ((flags & V1ZIR_LIT) != 0) col += v1zirCursorLight(gl_FragCoord.xy, 150.0) * 0.045;
	// Fine static grain; it also hides the banding of a dark tint over a smooth blur.
	col += v1zirGrain(gl_FragCoord.xy) * (matte ? 0.04 : 0.008);
	// Vertex alpha is the tint strength; the fade of the whole panel arrives in the thickness channel.
	float fade = clamp(style.x / 16.0, 0.0, 1.0);
	fragColor = vec4(col, coverage * fade) * ColorModulator;
}
