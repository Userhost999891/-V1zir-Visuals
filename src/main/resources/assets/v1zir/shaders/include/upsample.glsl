// Smooth upsampling of the reduced-resolution sky: a cubic B-spline built from four bilinear
// taps (no blocks or ringing), plus a full-resolution dither so 8-bit gradients do not band.

vec4 vCubicWeights(float v) {
	vec4 n = vec4(1.0, 2.0, 3.0, 4.0) - v;
	vec4 s = n * n * n;
	float x = s.x;
	float y = s.y - 4.0 * s.x;
	float z = s.z - 4.0 * s.y + 6.0 * s.x;
	float w = 6.0 - x - y - z;
	return vec4(x, y, z, w) * (1.0 / 6.0);
}

vec4 vTextureBicubic(sampler2D tex, vec2 uv) {
	vec2 size = vec2(textureSize(tex, 0));
	vec2 inv = 1.0 / size;
	uv = uv * size - 0.5;
	vec2 f = fract(uv);
	uv -= f;
	vec4 xc = vCubicWeights(f.x);
	vec4 yc = vCubicWeights(f.y);
	vec4 c = uv.xxyy + vec2(-0.5, 1.5).xyxy;
	vec4 s = vec4(xc.xz + xc.yw, yc.xz + yc.yw);
	vec4 offset = (c + vec4(xc.yw, yc.yw) / s) * inv.xxyy;
	vec4 s0 = texture(tex, offset.xz);
	vec4 s1 = texture(tex, offset.yz);
	vec4 s2 = texture(tex, offset.xw);
	vec4 s3 = texture(tex, offset.yw);
	float sx = s.x / (s.x + s.y);
	float sy = s.z / (s.z + s.w);
	return mix(mix(s3, s2, sx), mix(s1, s0, sx), sy);
}

vec3 vUpsampleDither(vec2 fragCoord) {
	vec2 p = fract(fragCoord * vec2(0.1031, 0.1030));
	p += dot(p, p.yx + 33.33);
	float a = fract((p.x + p.y) * p.x);
	vec2 q = fract((fragCoord + 17.7) * vec2(0.1031, 0.1030));
	q += dot(q, q.yx + 33.33);
	float b = fract((q.x + q.y) * q.x);
	return vec3((a + b - 1.0) / 255.0);
}
