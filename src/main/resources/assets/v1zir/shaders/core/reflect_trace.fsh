#version 150

// Trace pass of the reflections (render/Reflections), usually at half resolution: the reflected
// colour of every pixel that reflects, alpha 1 there and 0 everywhere else.
#moj_import <v1zir:reflect.glsl>

// The Shader Sky's panorama of the sky above the horizon (render/sky/SkyCache) while SkyInfo.x is 1, a stand-in otherwise.
uniform sampler2D Sampler5;

out vec4 fragColor;

// A direction above the horizon to its place in the panorama (vHemiOctEncode of sky_cache.glsl).
vec2 rHemiOct(vec3 d) {
	vec2 p = d.xz / (abs(d.x) + abs(d.y) + abs(d.z));
	return vec2(p.x + p.y, p.x - p.y) * 0.5 + 0.5;
}

// What a ray that finds nothing on screen sees. Above the horizon, under the Shader Sky, its own panorama: the real
// sky in every direction, on screen or not, clouds and all. Otherwise the sky on screen in its direction where it is
// uncovered, and past that a gradient from the horizon colour (the vanilla sky's own fog colour, close to it).
vec3 rSky(vec3 dir) {
	if (SkyInfo.x > 0.5 && dir.y > 0.0) return texture(Sampler5, rHemiOct(dir)).rgb;
	vec4 c = ViewProj * vec4(dir, 0.0);
	if (c.w > 0.0) {
		vec2 uv = c.xy / c.w * 0.5 + 0.5;
		if (all(greaterThan(uv, vec2(0.0))) && all(lessThan(uv, vec2(1.0))) && texelFetch(Sampler1, ivec2(uv * Screen.xy), 0).r >= 1.0) {
			return texture(Sampler0, uv).rgb;
		}
	}
	vec3 horizon = FogColor.rgb;
	vec3 zenith = horizon * vec3(0.62, 0.78, 1.0);
	// Below the horizon: the ground, darker than the sky.
	return dir.y >= 0.0 ? mix(horizon, zenith, sqrt(dir.y)) : horizon * mix(0.7, 0.3, sqrt(-dir.y));
}

void main() {
	// The full-resolution pixel this texel stands for.
	ivec2 p = ivec2(gl_FragCoord.xy * Screen.xy / Screen.zw);
	SurfaceInfo s;
	if (!rSurface(p, s) || max(s.tint.r, max(s.tint.g, s.tint.b)) < 0.002) {
		fragColor = vec4(0.0);
		return;
	}
	vec3 view = normalize(s.pos);
	// Interleaved gradient noise: a fixed per-pixel offset that hides the march steps.
	float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
	// Roughness is a blur in the composite, not jittered rays: no grain.
	vec3 dir = reflect(view, s.normal);
	// Under a roof a ray that finds nothing sees the dim room rather than the sky; sideways rays
	// still catch some of the horizon (under trees, at the edge of an overhang).
	vec3 miss = mix(texelFetch(Sampler0, p, 0).rgb * 0.3, rSky(dir), max(s.exposure, 0.4 - 0.4 * abs(dir.y)));
	vec3 hit = rMarch(s.pos + s.normal * (0.02 + length(s.pos) * 0.002), dir, noise);
	// Rays turning back towards the camera mostly meet what is behind it.
	float confidence = hit.z * smoothstep(-0.9, -0.4, dot(dir, Forward.xyz));
	fragColor = vec4(confidence > 0.0 ? mix(miss, texture(Sampler0, hit.xy).rgb, confidence) : miss, 1.0);
}
