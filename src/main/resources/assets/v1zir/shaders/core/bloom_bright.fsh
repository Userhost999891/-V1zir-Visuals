#version 150

// Bright pass of the bloom (render/Bloom): the frame at half resolution, keeping only what is brighter
// than the threshold, with a soft knee so the glow does not switch on abruptly.
uniform sampler2D Sampler0;

layout(std140) uniform BloomParams {
	vec4 Bloom; // xy = source texel size, z = threshold, w = knee
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
	vec2 h = Bloom.xy;
	// A 2x2 box at the corners of the half resolution texel, so small bright spots are not missed.
	vec3 c = texture(Sampler0, texCoord + vec2(-h.x, -h.y)).rgb;
	c += texture(Sampler0, texCoord + vec2(h.x, -h.y)).rgb;
	c += texture(Sampler0, texCoord + vec2(-h.x, h.y)).rgb;
	c += texture(Sampler0, texCoord + vec2(h.x, h.y)).rgb;
	c *= 0.25;
	float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
	float soft = clamp(luma - Bloom.z + Bloom.w, 0.0, 2.0 * Bloom.w);
	soft = soft * soft / (4.0 * Bloom.w + 1e-4);
	float keep = max(soft, luma - Bloom.z) / max(luma, 1e-4);
	fragColor = vec4(c * keep, 1.0);
}
