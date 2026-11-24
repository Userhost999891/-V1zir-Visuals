#version 150

// One direction of a separable Gaussian blur. Neighbouring texels are taken in pairs with one bilinear read
// placed between them by their weights, so a wide kernel costs half the reads; the result is smooth and steady
// (no blocks from tiny levels, nothing that crawls as the view moves).
uniform sampler2D Sampler0;

layout(std140) uniform BlurParams {
	vec4 Blur; // xy = one texel along the pass's direction (uv), z = sigma in texels
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
	float sigma = max(Blur.z, 0.5);
	float k = 1.0 / (2.0 * sigma * sigma);
	int radius = int(ceil(sigma * 3.0));
	vec3 sum = texture(Sampler0, texCoord).rgb;
	float total = 1.0;
	for (int i = 1; i <= 31; i += 2) {
		if (i > radius) break;
		float w0 = exp(-float(i * i) * k);
		float w1 = exp(-float((i + 1) * (i + 1)) * k);
		float w = w0 + w1;
		float o = float(i) + w1 / w;
		sum += (texture(Sampler0, texCoord + Blur.xy * o).rgb + texture(Sampler0, texCoord - Blur.xy * o).rgb) * w;
		total += 2.0 * w;
	}
	fragColor = vec4(sum / total, 1.0);
}
