// Sky cache: the world sky is stored in two hemispherical octahedral panoramas, one above and one
// below the horizon (render/sky/SkyCache). A direction maps to the square [0,1]^2: the zenith (or
// nadir) is the centre and the horizon runs along the border, so the texture has no seams in the sky.

// SkyCache uniform block - written by dev.v1zir.visuals.render.sky.SkyCache (std140).
// Screen pass: CacheParams x = crossfade from the previous to the current panorama (1 = current only).
// Build pass:  CacheParams x = weight of the new sample against the history (1 = no history),
//              y = +1 for the upper panorama, -1 for the lower one, zw = sub-texel jitter of this build.
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

// Cubic B-spline sampling from four bilinear taps at LOD 0 (safe in non-uniform control flow):
// the panorama is magnified about 1.5 to 2 times on screen, and bilinear shows its texels.
vec4 vPanoBicubic(sampler2D tex, vec2 uv) {
	vec2 size = vec2(textureSize(tex, 0));
	vec2 inv = 1.0 / size;
	uv = uv * size - 0.5;
	vec2 f = fract(uv);
	uv -= f;
	vec4 n = vec4(1.0, 2.0, 3.0, 4.0) - f.x;
	vec4 sx3 = n * n * n;
	vec4 xc = vec4(sx3.x, sx3.y - 4.0 * sx3.x, sx3.z - 4.0 * sx3.y + 6.0 * sx3.x, 0.0);
	xc.w = 6.0 - xc.x - xc.y - xc.z;
	n = vec4(1.0, 2.0, 3.0, 4.0) - f.y;
	vec4 sy3 = n * n * n;
	vec4 yc = vec4(sy3.x, sy3.y - 4.0 * sy3.x, sy3.z - 4.0 * sy3.y + 6.0 * sy3.x, 0.0);
	yc.w = 6.0 - yc.x - yc.y - yc.z;
	vec4 c = uv.xxyy + vec2(-0.5, 1.5).xyxy;
	vec4 s = vec4(xc.xz + xc.yw, yc.xz + yc.yw);
	vec4 offset = (c + vec4(xc.yw, yc.yw) / s) * inv.xxyy;
	vec4 s0 = textureLod(tex, offset.xz, 0.0);
	vec4 s1 = textureLod(tex, offset.yz, 0.0);
	vec4 s2 = textureLod(tex, offset.xw, 0.0);
	vec4 s3 = textureLod(tex, offset.yw, 0.0);
	float wx = s.x / (s.x + s.y);
	float wy = s.z / (s.z + s.w);
	return mix(mix(s3, s2, wx), mix(s1, s0, wx), wy);
}
