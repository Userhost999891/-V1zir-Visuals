#version 150

// The panorama keeps how visible the stars are in alpha; the screen pass draws them (sky_lib.glsl).
#define V1ZIR_SKY_CACHE

#moj_import <v1zir:sky_params.glsl>
#moj_import <v1zir:sky_lib.glsl>
#moj_import <v1zir:sky_cache.glsl>

// Renders rows of a sky cache panorama: every texel is the full sky shader in its direction.

in vec2 texCoord;

out vec4 fragColor;

void main() {
	vec3 dir = vHemiOctDecode(texCoord, CacheParams.y);
	vec3 col = v1zirSky(dir, gl_FragCoord.xy);
	fragColor = vec4(col, clamp(gStarWeight, 0.0, 1.0));
}
