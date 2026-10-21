#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:v1zir_gui.glsl>
#moj_import <v1zir:upsample.glsl>

// The menu sky is rendered at reduced resolution (MenuSky); this upsamples it and adds the
// cinematic finish.
uniform sampler2D Sampler0;

in vec2 texCoord;
in vec2 screenUv;
in vec4 vertexColor;

out vec4 fragColor;

float vGrainHash(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

void main() {
	vec3 col = vTextureBicubic(Sampler0, texCoord).rgb;

	// Soft vignette and a very light film grain.
	vec2 v = screenUv - 0.5;
	float vignette = 1.0 - smoothstep(0.35, 0.95, length(v * vec2(1.15, 1.0)));
	col *= mix(0.55, 1.0, vignette);
	float grain = vGrainHash(gl_FragCoord.xy + fract(GuiTime.x * 13.7) * 311.0) - 0.5;
	col += grain * 0.01 + vUpsampleDither(gl_FragCoord.xy);
	col *= vertexColor.rgb;
	fragColor = vec4(col, vertexColor.a) * ColorModulator;
}
