#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 glyphStyle;

out vec4 fragColor;

void main() {
	// Single channel SDF: 0.5 is the glyph edge. Sampling a small neighbourhood
	// (supersampling) keeps tiny text crisp instead of blurry.
	vec2 dx = dFdx(texCoord0) * 0.25;
	vec2 dy = dFdy(texCoord0) * 0.25;
	float sd = texture(Sampler0, texCoord0).r;
	float edge = 0.5 - glyphStyle.x;
	float w = max(fwidth(sd) * 0.7, 1e-4) + glyphStyle.y;
	float a = smoothstep(edge - w, edge + w, sd) * 0.5;
	a += smoothstep(edge - w, edge + w, texture(Sampler0, texCoord0 + dx + dy).r) * 0.125;
	a += smoothstep(edge - w, edge + w, texture(Sampler0, texCoord0 - dx - dy).r) * 0.125;
	a += smoothstep(edge - w, edge + w, texture(Sampler0, texCoord0 + dx - dy).r) * 0.125;
	a += smoothstep(edge - w, edge + w, texture(Sampler0, texCoord0 - dx + dy).r) * 0.125;
	if (a <= 0.0) discard;
	fragColor = vec4(vertexColor.rgb, vertexColor.a * a) * ColorModulator;
}
