#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:sky_params.glsl>
#moj_import <v1zir:v1zir_gui.glsl>
#moj_import <v1zir:sky_lib.glsl>

in vec3 viewRay;
in vec2 screenUv;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
	vec3 col = v1zirSky(normalize(viewRay), gl_FragCoord.xy);

	// Cinematic finish: soft vignette, gentle film grain and a fade-in driven by vertex alpha.
	vec2 v = screenUv - 0.5;
	float vignette = 1.0 - smoothstep(0.35, 0.95, length(v * vec2(1.15, 1.0)));
	col *= mix(0.55, 1.0, vignette);
	float grain = vHash12(gl_FragCoord.xy + fract(GuiTime.x * 13.7) * 311.0) - 0.5;
	col += grain * 0.018;
	col *= vertexColor.rgb;
	fragColor = vec4(col, vertexColor.a) * ColorModulator;
}
