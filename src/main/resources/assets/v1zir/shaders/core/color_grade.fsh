#version 150

// Colour grading of the world (render/ColorGrade): one texel of the copied frame per pixel.
uniform sampler2D Sampler0;

layout(std140) uniform GradeParams {
	vec4 Gain;   // rgb = white balance times brightness
	vec4 Tone;   // x = contrast, y = saturation, z = vibrance, w = vignette strength
	vec4 Screen; // xy = 1 / size, z = aspect ratio, w = 1 / distance from the centre to a corner
	vec4 Shadow; // rgb = colour of a look's shadows
	vec4 Mid;    // rgb = colour of its midtones
	vec4 Light;  // rgb = colour of its highlights, w = how much of the look
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
	if (Light.w > 0.0) {
		// A look: the picture again in a gradient from the shadow through the midtone to the
		// highlight colour by its brightness, at the same brightness, laid over it, so the colours
		// change and the light stays.
		const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
		float l = clamp(dot(c, LUMA), 0.0, 1.0);
		vec3 g = l < 0.5 ? mix(Shadow.rgb, Mid.rgb, l * 2.0) : mix(Mid.rgb, Light.rgb, l * 2.0 - 1.0);
		g = clamp(g * (l / max(dot(g, LUMA), 0.001)), 0.0, 1.0);
		c = mix(c, g, Light.w);
	}
	if (Tone.w > 0.0) {
		vec2 p = (gl_FragCoord.xy * Screen.xy - 0.5) * vec2(Screen.z, 1.0);
		float v = smoothstep(0.4, 1.0, length(p) * Screen.w);
		c *= 1.0 - Tone.w * v * v;
	}
	fragColor = vec4(clamp(c, 0.0, 1.0), source.a);
}
