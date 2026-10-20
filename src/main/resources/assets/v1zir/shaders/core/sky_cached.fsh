#version 150

#moj_import <v1zir:sky_params.glsl>
#moj_import <v1zir:sky_lib.glsl>
#moj_import <v1zir:sky_cache.glsl>
#moj_import <v1zir:upsample.glsl>

// Draws the world sky from the cached panoramas, crossfading between the last two complete builds
// so the animation stays smooth, and adds the stars crisp at full resolution where the panorama
// says they are visible.
uniform sampler2D Sampler0; // above the horizon, current
uniform sampler2D Sampler1; // above the horizon, previous
uniform sampler2D Sampler2; // below the horizon, current
uniform sampler2D Sampler3; // below the horizon, previous

in vec3 viewRay;

out vec4 fragColor;

void main() {
	vec3 d = normalize(viewRay);
	vec3 dx = dFdx(d);
	vec3 dy = dFdy(d);
	vec2 uv = vHemiOctEncode(vec3(d.x, abs(d.y), d.z));
	vec4 current;
	vec4 previous;
	if (d.y >= 0.0) {
		current = textureLod(Sampler0, uv, 0.0);
		previous = textureLod(Sampler1, uv, 0.0);
	} else {
		current = textureLod(Sampler2, uv, 0.0);
		previous = textureLod(Sampler3, uv, 0.0);
	}
	vec4 sky = mix(previous, current, CacheParams.x);
	vec3 col = sky.rgb;

	if (sky.a > 0.003) {
		int preset = int(Camera.w + 0.5);
		float density = Settings.z * (preset == PRESET_NEBULA ? 1.6 : (preset == PRESET_SUNSET ? 0.5 : 1.0));
		float exposure = preset == PRESET_AURORA ? 1.6 : (preset == PRESET_BLOOD_MOON ? 1.3 : (preset == PRESET_SUNSET ? 1.1 : 1.0));
		vec3 tint = preset == PRESET_BLOOD_MOON ? vec3(1.0, 0.55, 0.5) : vec3(1.0);
		vec3 stars = vCrispStars(d, dx, dy, Timing.x, density) * tint * (sky.a * exposure * Settings.x);
		col += vToDisplay(vAces(stars));
	}
	fragColor = vec4(clamp(col + vUpsampleDither(gl_FragCoord.xy), 0.0, 1.0), 1.0);
}
