#version 150

// Colour grading of the world (render/ColorGrade): one texel of the copied frame per pixel.
uniform sampler2D Sampler0;

layout(std140) uniform GradeParams {
	vec4 Gain;   // rgb = white balance times brightness
	vec4 Tone;   // x = contrast, y = saturation, z = vibrance, w = vignette strength
	vec4 Screen; // xy = 1 / size, z = aspect ratio, w = 1 / distance from the centre to a corner
};

out vec4 fragColor;

void main() {
	vec4 source = texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0);
	vec3 c = source.rgb * Gain.rgb;
	c = (c - 0.5) * Tone.x + 0.5;
	float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
	// Vibrance raises the saturation of dull colours more than that of already vivid ones.
	float chroma = clamp(max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b)), 0.0, 1.0);
	c = mix(vec3(luma), c, max(Tone.y * (1.0 + Tone.z * (1.0 - chroma)), 0.0));
	if (Tone.w > 0.0) {
		vec2 p = (gl_FragCoord.xy * Screen.xy - 0.5) * vec2(Screen.z, 1.0);
		float v = smoothstep(0.4, 1.0, length(p) * Screen.w);
		c *= 1.0 - Tone.w * v * v;
	}
	fragColor = vec4(clamp(c, 0.0, 1.0), source.a);
}
