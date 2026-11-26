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

// The blurred world behind, spread wider by four taps around the pixel: frosted glass scatters what is behind it
// further than a clear blur does.
vec3 frosted(vec2 uv) {
	vec2 o = ScreenInfo.zw * 5.0;
	vec3 c = texture(Sampler0, uv).rgb * 0.36;
	c += texture(Sampler0, uv + vec2(o.x, o.y * 0.5)).rgb * 0.16;
	c += texture(Sampler0, uv + vec2(-o.x * 0.5, o.y)).rgb * 0.16;
	c += texture(Sampler0, uv + vec2(-o.x, -o.y * 0.5)).rgb * 0.16;
	c += texture(Sampler0, uv + vec2(o.x * 0.5, -o.y)).rgb * 0.16;
	return c;
}

void main() {
	float d = v1zirShapeDistance(localPos, shape, style.z);
	float coverage = v1zirShapeCoverage(d, shape, style);
	if (coverage <= 0.0) discard;
	int flags = int(style.y);
	bool frost = (flags & V1ZIR_FROST) != 0;
	bool matte = (flags & V1ZIR_MATTE) != 0;
	// Sampler0 holds the blurred world behind the GUI (same orientation as the framebuffer).
	vec2 uv = gl_FragCoord.xy * ScreenInfo.zw;
	vec3 backdrop = frost ? frosted(uv) : texture(Sampler0, uv).rgb;
	float luma = dot(backdrop, vec3(0.2126, 0.7152, 0.0722));
	vec3 col;
	if (frost) {
		// Matte glass: the world behind loses most of its colour and its bright parts are held down, so the glass
		// reads as one soft diffuse surface and light text keeps its contrast; under the tint a faint milky haze.
		backdrop = mix(vec3(luma), backdrop, 0.6) * (1.0 - 0.4 * smoothstep(0.35, 1.0, luma));
		col = mix(backdrop, vertexColor.rgb, vertexColor.a);
		col = mix(col, vec3(0.74, 0.76, 0.8), 0.045);
	} else {
		// Black glass: the smooth blur behind, a little desaturated and with bright skies pulled down, under the
		// tint. No grain and no veil: the surface is clean.
		backdrop = mix(vec3(luma), backdrop, 0.8) * (1.0 - 0.35 * smoothstep(0.45, 1.0, luma));
		col = mix(backdrop, vertexColor.rgb, vertexColor.a);
	}
	if (matte) {
		// Where the panel is (0 at its top, 1 at its bottom). Light falls on it from above: its inner edge catches
		// it, brighter at the top; matte glass also takes a soft, even sheen over its upper part (no gloss).
		float v = clamp((localPos.y + shape.y) / max(2.0 * shape.y, 1.0), 0.0, 1.0);
		if (frost) {
			col += 0.026 * (1.0 - smoothstep(0.0, 0.6, v));
			float inner = 1.0 - smoothstep(0.5, 3.0, -d);
			col += inner * mix(0.05, 0.012, v);
		} else {
			float inner = 1.0 - smoothstep(0.5, 2.5, -d);
			col += inner * mix(0.035, 0.0, v);
		}
	}
	if ((flags & V1ZIR_LIT) != 0) col += v1zirCursorLight(gl_FragCoord.xy, frost ? 170.0 : 150.0) * (frost ? 0.045 : 0.035);
	if (frost) {
		// The frost's fine grain, fixed to the glass (it moves with it and never crawls), and faint. Counted from a
		// little outside the top left corner: the anti-aliased rim lies just past the shape, and the hash wants no
		// negative coordinates.
		col += v1zirGrain((localPos + shape.xy + 4.0) * GuiTime.y) * 0.018;
	} else {
		// Half a step of dither against banding in the dark tint: below what the eye can see as grain.
		col += v1zirGrain(gl_FragCoord.xy) * (1.0 / 255.0);
	}
	// Vertex alpha is the tint strength; the fade of the whole panel arrives in the thickness channel.
	float fade = clamp(style.x / 16.0, 0.0, 1.0);
	fragColor = vec4(col, coverage * fade) * ColorModulator;
}
