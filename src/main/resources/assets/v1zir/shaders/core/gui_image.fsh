#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:sdf_shapes.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
in vec4 shape;
in vec3 style;
in vec4 uvRect;
in float flags;

out vec4 fragColor;

vec4 v1zirPremultiplied(ivec2 p) {
	vec4 c = texelFetch(Sampler0, p, 0);
	return vec4(c.rgb * c.a, c.a);
}

// A texture region (album art, the logo, a skin's or a mob's face) stretched over a rounded rectangle;
// the vertex colour tints it and carries its opacity. Flags: 1 pixel art, 2 and 4 sharpened (low, medium, both high).
void main() {
	float d = v1zirShapeDistance(localPos, shape, style.z);
	float coverage = v1zirShapeCoverage(d, shape, style);
	vec2 uv = mix(uvRect.xy, uvRect.zw, clamp(localPos / max(shape.xy * 2.0, vec2(1e-3)) + 0.5, 0.0, 1.0));
	// Derivatives before the discard, while every pixel of the quad still runs.
	vec2 fx = dFdx(uv);
	vec2 fy = dFdy(uv);
	if (coverage <= 0.0) discard;
	vec2 size = vec2(textureSize(Sampler0, 0));
	int f = int(flags);
	int sharpen = (f / 2) % 4;
	vec4 texel = vec4(0.0);
	if (sharpen > 0) {
		// Sharpened (the browser's page): five taps a screen pixel apart (each the box of four texels when the page
		// was rendered at twice the size it is drawn), the centre pushed away from its neighbours by as much as the
		// room its contrast leaves, so text and edges get crisper without halos or noise in flat areas.
		vec4 c = texture(Sampler0, uv);
		vec3 n = texture(Sampler0, uv - fy).rgb;
		vec3 s = texture(Sampler0, uv + fy).rgb;
		vec3 e = texture(Sampler0, uv + fx).rgb;
		vec3 w = texture(Sampler0, uv - fx).rgb;
		vec3 lo = min(c.rgb, min(min(n, s), min(e, w)));
		vec3 hi = max(c.rgb, max(max(n, s), max(e, w)));
		vec3 room = sqrt(clamp(min(lo, 1.0 - hi) / max(hi, vec3(1e-4)), 0.0, 1.0));
		vec3 lobe = -room * mix(0.09, 0.2, float(sharpen - 1) / 2.0);
		vec3 rgb = (c.rgb + (n + s + e + w) * lobe) / (1.0 + 4.0 * lobe);
		texel = vec4(clamp(rgb, 0.0, 1.0), c.a);
	} else if ((f & 1) != 0) {
		// Pixel art: every texel a crisp square whose edges blend over one screen pixel, at any scale. The
		// blend is premultiplied, so transparent texels (a skin's hat) leave no dark fringe, and it never
		// reaches past the region into the neighbouring parts of the skin.
		vec2 px = uv * size;
		vec2 seam = floor(px + 0.5);
		px = seam + clamp((px - seam) / max((abs(fx) + abs(fy)) * size, vec2(1e-4)), -0.5, 0.5);
		px = clamp(px, min(uvRect.xy, uvRect.zw) * size + 0.5, max(uvRect.xy, uvRect.zw) * size - 0.5) - 0.5;
		ivec2 i = ivec2(floor(px));
		ivec2 j = min(i + 1, ivec2(size) - 1);
		vec2 w = px - floor(px);
		texel = mix(mix(v1zirPremultiplied(i), v1zirPremultiplied(ivec2(j.x, i.y)), w.x),
			mix(v1zirPremultiplied(ivec2(i.x, j.y)), v1zirPremultiplied(j), w.x), w.y);
		texel.rgb /= max(texel.a, 1e-4);
	} else {
		// Box filter over the pixel's footprint (up to 4x4 bilinear taps, as many as the minification needs):
		// a large image drawn small stays smooth instead of aliasing, without mipmaps.
		float texels = max(length(fx * size), length(fy * size));
		int n = int(clamp(ceil(texels * 0.5), 1.0, 4.0));
		for (int a = 0; a < n; a++) {
			for (int b = 0; b < n; b++) {
				vec2 o = (vec2(float(a), float(b)) + 0.5) / float(n) - 0.5;
				texel += texture(Sampler0, uv + fx * o.x + fy * o.y);
			}
		}
		texel /= float(n * n);
	}
	fragColor = vec4(texel.rgb * vertexColor.rgb, texel.a * vertexColor.a * coverage) * ColorModulator;
}
