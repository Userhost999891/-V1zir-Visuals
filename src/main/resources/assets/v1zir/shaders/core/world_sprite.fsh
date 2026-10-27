#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
in vec4 uvRect;

out vec4 fragColor;

vec4 v1zirPremultiplied(ivec2 p) {
	vec4 c = texelFetch(Sampler0, p, 0);
	return vec4(c.rgb * c.a, c.a);
}

// A pixel-art sprite as gui_image.fsh draws one: every texel a crisp square whose edges blend over one screen
// pixel at any distance, never reaching past the sprite's region into its neighbours on the atlas. The vertex
// colour tints it and carries its opacity; premultiplied output.
void main() {
	vec2 size = vec2(textureSize(Sampler0, 0));
	vec2 px = texCoord0 * size;
	vec2 seam = floor(px + 0.5);
	px = seam + clamp((px - seam) / max(fwidth(px), vec2(1e-4)), -0.5, 0.5);
	px = clamp(px, uvRect.xy * size + 0.5, uvRect.zw * size - 0.5) - 0.5;
	ivec2 i = ivec2(floor(px));
	ivec2 j = min(i + 1, ivec2(size) - 1);
	vec2 w = px - floor(px);
	vec4 texel = mix(mix(v1zirPremultiplied(i), v1zirPremultiplied(ivec2(j.x, i.y)), w.x),
		mix(v1zirPremultiplied(ivec2(i.x, j.y)), v1zirPremultiplied(j), w.x), w.y);
	float alpha = texel.a * vertexColor.a;
	if (alpha <= 0.003) discard;
	fragColor = vec4(texel.rgb * vertexColor.rgb * vertexColor.a, alpha);
}
