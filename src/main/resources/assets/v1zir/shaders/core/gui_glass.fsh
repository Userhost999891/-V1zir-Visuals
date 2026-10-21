#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <v1zir:v1zir_gui.glsl>
#moj_import <v1zir:sdf_shapes.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
in vec4 shape;
in vec3 style;

out vec4 fragColor;

void main() {
	float d = v1zirShapeDistance(localPos, shape, style.z);
	float coverage = v1zirShapeCoverage(d, shape, style);
	if (coverage <= 0.0) discard;
	// Sampler0 holds the blurred world behind the GUI (same orientation as the framebuffer).
	vec2 uv = gl_FragCoord.xy * ScreenInfo.zw;
	vec3 backdrop = texture(Sampler0, uv).rgb;
	float luma = dot(backdrop, vec3(0.2126, 0.7152, 0.0722));
	// A little extra saturation; bright skies are pulled down so light text on the glass keeps its contrast.
	backdrop = mix(vec3(luma), backdrop, 1.15) * (1.0 - 0.3 * smoothstep(0.45, 1.0, luma));
	vec3 col = mix(backdrop, vertexColor.rgb, vertexColor.a);
	// Faint inner light along the top edge sells the glass look.
	float topness = clamp(-localPos.y / max(shape.y, 1.0), 0.0, 1.0);
	float rim = smoothstep(-2.5, 0.0, d) * topness * topness * topness * 0.07;
	// Half a step of interleaved gradient noise hides the banding of a dark tint over a smooth blur.
	float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5;
	// Vertex alpha is the tint strength; the fade of the whole panel arrives in the thickness channel.
	float fade = clamp(style.x / 16.0, 0.0, 1.0);
	fragColor = vec4(col + rim + noise / 255.0, coverage * fade) * ColorModulator;
}
