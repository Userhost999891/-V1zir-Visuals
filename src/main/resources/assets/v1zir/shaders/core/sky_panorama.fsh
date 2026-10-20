#version 150

// The panorama keeps how visible the stars are in alpha; the screen pass draws them (sky_lib.glsl).
#define V1ZIR_SKY_CACHE

#moj_import <v1zir:sky_params.glsl>
#moj_import <v1zir:sky_lib.glsl>
#moj_import <v1zir:sky_cache.glsl>

// Renders rows of a sky cache panorama. Every build samples the sky at a slightly different
// sub-texel direction and blends into the previous build, so over a few builds the panorama
// converges to a supersampled, noise-free image: the raymarch jitter of the aurora and nebula and
// fine detail no longer show up as grain or stair steps.
uniform sampler2D Sampler0; // the previous complete build of this panorama (history)

in vec2 texCoord;

out vec4 fragColor;

void main() {
	vec2 texel = vec2(dFdx(texCoord.x), dFdy(texCoord.y));
	vec3 dir = vHemiOctDecode(texCoord + CacheParams.zw * texel, CacheParams.y);
	vec4 current = vec4(v1zirSky(dir, gl_FragCoord.xy), clamp(gStarWeight, 0.0, 1.0));
	vec4 history = textureLod(Sampler0, texCoord, 0.0);
	fragColor = mix(history, current, CacheParams.x);
}
