#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:v1zir_gui.glsl>
#moj_import <v1zir:sdf_shapes.glsl>

in vec4 vertexColor;
in vec2 localPos;
in vec4 shape;
in vec3 style;

out vec4 fragColor;

void main() {
	float d = v1zirShapeDistance(localPos, shape, style.z);
	float coverage = v1zirShapeCoverage(d, shape, style);
	if (coverage <= 0.0) discard;
	vec4 color = vertexColor;
	int flags = int(style.y);
	if ((flags & V1ZIR_LIT) != 0) {
		// The cursor carries a soft light: rims catch most of it, surfaces brighten a little.
		float light = v1zirCursorLight(gl_FragCoord.xy, 120.0);
		if ((flags & 7) == 1) {
			color = vec4(mix(color.rgb, vec3(1.0), light * 0.5), color.a + (1.0 - color.a) * light * 0.3);
		} else {
			color = vec4(color.rgb + light * 0.07, color.a + (1.0 - color.a) * light * 0.05);
		}
	}
	if ((flags & V1ZIR_MATTE) != 0) color.rgb += v1zirGrain(gl_FragCoord.xy) * 0.03;
	fragColor = vec4(color.rgb, color.a * coverage) * ColorModulator;
}
