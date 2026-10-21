#version 150

#moj_import <minecraft:dynamictransforms.glsl>
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
	fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
}
