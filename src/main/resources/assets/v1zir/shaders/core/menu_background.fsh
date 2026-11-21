#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:v1zir_gui.glsl>
#moj_import <v1zir:sdf_shapes.glsl>
#moj_import <v1zir:upsample.glsl>

// A wallpaper target (rendered at reduced resolution by WallpaperRenderer) upsampled into a rectangle - the whole
// screen, the sliding lock screen or a card of the wallpaper picker - with the cinematic finish. The vertex colour
// dims it (the Dim adjustment) and carries its opacity.
uniform sampler2D Sampler0;

in vec2 texCoord;
in vec2 screenUv;
in vec4 vertexColor;
in vec2 localPos;
in vec4 shape;
in float cornerMask;

out vec4 fragColor;

float vGrainHash(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

void main() {
	float d = v1zirShapeDistance(localPos, shape, cornerMask);
	float coverage = clamp(0.5 - d / max(fwidth(d), 1e-4), 0.0, 1.0);
	// A texture at (or above) the drawn resolution, like a picture, is sampled directly; the B-spline would soften it.
	vec2 size = vec2(textureSize(Sampler0, 0));
	float texels = length(dFdx(texCoord * size));
	if (coverage <= 0.0) discard;
	vec3 col = texels > 0.9 ? texture(Sampler0, texCoord).rgb : vTextureBicubic(Sampler0, texCoord).rgb;

	// Soft vignette and a very light film grain.
	vec2 v = screenUv - 0.5;
	float vignette = 1.0 - smoothstep(0.35, 0.95, length(v * vec2(1.15, 1.0)));
	col *= mix(0.55, 1.0, vignette);
	float grain = vGrainHash(gl_FragCoord.xy + fract(GuiTime.x * 13.7) * 311.0) - 0.5;
	col += grain * 0.01 + vUpsampleDither(gl_FragCoord.xy);
	col *= vertexColor.rgb;
	fragColor = vec4(col, vertexColor.a * coverage) * ColorModulator;
}
