// Sky cache: the world sky is stored in two hemispherical octahedral panoramas, one above and one
// below the horizon (render/sky/SkyCache). A direction maps to the square [0,1]^2: the zenith (or
// nadir) is the centre and the horizon runs along the border, so the texture has no seams in the sky.

// SkyCache uniform block - written by dev.v1zir.visuals.render.sky.SkyCache (std140).
// CacheParams x = crossfade from the previous to the current panorama (1 = current only),
//             y = +1 when building the upper panorama, -1 for the lower one.
layout(std140) uniform SkyCache {
	vec4 CacheParams;
};

vec2 vHemiOctEncode(vec3 d) {
	vec2 p = d.xz / (abs(d.x) + abs(d.y) + abs(d.z));
	return vec2(p.x + p.y, p.x - p.y) * 0.5 + 0.5;
}

vec3 vHemiOctDecode(vec2 uv, float hemisphere) {
	vec2 q = uv * 2.0 - 1.0;
	vec2 p = vec2(q.x + q.y, q.x - q.y) * 0.5;
	return normalize(vec3(p.x, (1.0 - abs(p.x) - abs(p.y)) * hemisphere, p.y));
}
