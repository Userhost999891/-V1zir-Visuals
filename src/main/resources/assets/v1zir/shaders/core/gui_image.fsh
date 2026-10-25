#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:sdf_shapes.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
in vec4 shape;
in vec3 style;

out vec4 fragColor;

// A texture (album art, the logo) stretched over a rounded rectangle; the vertex colour tints it and carries its opacity.
void main() {
	float d = v1zirShapeDistance(localPos, shape, style.z);
	float coverage = v1zirShapeCoverage(d, shape, style);
	vec2 uv = clamp(localPos / max(shape.xy * 2.0, vec2(1e-3)) + 0.5, 0.0, 1.0);
	// Derivatives before the discard, while every pixel of the quad still runs.
	vec2 fx = dFdx(uv);
	vec2 fy = dFdy(uv);
	if (coverage <= 0.0) discard;
	// Box filter over the pixel's footprint (up to 4x4 bilinear taps, as many as the minification needs):
	// a large image drawn small stays smooth instead of aliasing, without mipmaps.
	vec2 size = vec2(textureSize(Sampler0, 0));
	float texels = max(length(fx * size), length(fy * size));
	int n = int(clamp(ceil(texels * 0.5), 1.0, 4.0));
	vec4 texel = vec4(0.0);
	for (int i = 0; i < n; i++) {
		for (int j = 0; j < n; j++) {
			vec2 o = (vec2(float(i), float(j)) + 0.5) / float(n) - 0.5;
			texel += texture(Sampler0, uv + fx * o.x + fy * o.y);
		}
	}
	texel /= float(n * n);
	fragColor = vec4(texel.rgb * vertexColor.rgb, texel.a * vertexColor.a * coverage) * ColorModulator;
}
