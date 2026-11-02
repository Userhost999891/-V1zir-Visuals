#version 150

// Bloom composite (render/Bloom): the blurred bright pass, upsampled and added onto the frame.
uniform sampler2D Sampler0;

layout(std140) uniform BloomParams {
	vec4 Bloom; // x = intensity
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
	fragColor = vec4(texture(Sampler0, texCoord).rgb * Bloom.x, 1.0);
}
