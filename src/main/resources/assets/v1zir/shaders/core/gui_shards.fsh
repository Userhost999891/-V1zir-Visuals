#version 150

#moj_import <v1zir:transition.glsl>

// Glass shard textured with the last frame of the click GUI: bright refractive rims (with a little
// chromatic split), a glint as it turns towards the light, fading as it falls.
uniform sampler2D Sampler0;

in vec2 uv;
in vec2 origin;
in float edge;
in float shade;
in float glint;
in float opacity;

out vec4 fragColor;

void main() {
	// Pixels from the shard's rim: edge runs from 0 on the rim to 1 at the centre.
	float fw = max(fwidth(edge), 1e-4);
	float rimDistance = edge / fw;
	vec2 inward = vec2(dFdx(edge), dFdy(edge));
	inward /= max(length(inward), 1e-5);
	float near = 1.0 - smoothstep(0.0, 6.0, rimDistance);
	vec2 bend = inward * near * 2.5 * Screen.zw;
	vec4 base = texture(Sampler0, uv + bend);
	float r = texture(Sampler0, uv + bend * 1.8).r;
	float b = texture(Sampler0, uv + bend * 0.4).b;
	vec3 col = vec3(r, base.g, b) * shade;

	// Cracks run out from the impact point before anything moves.
	float reach = max(Screen.x, Screen.y) * 1.2 * clamp(Anim.x / max(Origin.w, 1e-3), 0.0, 1.0);
	float cracked = 1.0 - smoothstep(reach - 40.0, reach, length(origin - Origin.xy));
	float rim = (1.0 - smoothstep(0.6, 1.8, rimDistance)) * 0.85 + near * 0.18;
	float cover = smoothstep(0.0, 0.5, base.a);
	vec3 ice = vec3(0.88, 0.95, 1.0);
	col += ice * (rim * cracked + glint * 0.45) * cover;
	float alpha = max(base.a, rim * cracked * cover * 0.9);
	fragColor = vec4(col, alpha) * opacity;
}
