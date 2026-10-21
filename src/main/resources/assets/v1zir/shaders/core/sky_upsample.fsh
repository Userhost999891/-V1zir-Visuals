#version 150

#moj_import <v1zir:upsample.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord;

out vec4 fragColor;

void main() {
	vec3 col = vTextureBicubic(Sampler0, texCoord).rgb + vUpsampleDither(gl_FragCoord.xy);
	fragColor = vec4(col, 1.0);
}
