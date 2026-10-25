#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:sdf_shapes.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
in vec4 shape;
in vec3 style;

out vec4 fragColor;

// A texture (album art) stretched over a rounded rectangle; the vertex colour tints it and carries its opacity.
void main() {
	float d = v1zirShapeDistance(localPos, shape, style.z);
	float coverage = v1zirShapeCoverage(d, shape, style);
	if (coverage <= 0.0) discard;
	vec2 uv = clamp(localPos / max(shape.xy * 2.0, vec2(1e-3)) + 0.5, 0.0, 1.0);
	vec4 texel = texture(Sampler0, uv);
	fragColor = vec4(texel.rgb * vertexColor.rgb, texel.a * vertexColor.a * coverage) * ColorModulator;
}
