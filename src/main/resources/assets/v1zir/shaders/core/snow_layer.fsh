#version 150

// The snow lying close by (snow_layer.vsh), lit as the flat snow cover further off is (snow_cover.fsh): by the
// brightness of the frame behind it, which is the block it lies on, so the sky's light, shade, darkened corners and
// torches carry over and the two agree by day and at night. On that its own shape: slopes towards the sun or the moon
// a little brighter, those away from it and its sides darker, its own shade at the foot of its sides, bluish where it
// is in shade. No texture: smooth snow, tiny grains glittering in the sun up close, and the render distance fog as the
// game's terrain has it.
#moj_import <v1zir:reflect.glsl>

in vec3 vWorld;
in vec3 vRel;
in vec3 vNormal;
in float vLight;
in float vShade;

out vec4 fragColor;

float fHash(ivec2 c) {
	uint h = uint(c.x) * 374761393u + uint(c.y) * 668265263u;
	h = (h ^ (h >> 13u)) * 1274126177u;
	return float((h ^ (h >> 16u)) & 0xFFFFFFu) / 16777215.0;
}

void main() {
	vec3 n = normalize(vNormal);
	float luma = dot(texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0).rgb, vec3(0.2126, 0.7152, 0.0722));
	float lit = clamp(luma / 0.42, 0.0, 1.3);
	lit = mix(lit, max(lit, SnowCover.z), 0.6);
	float shape = 0.9 + 0.1 * n.y + 0.22 * LightDir.w * (max(dot(n, LightDir.xyz), 0.0) - LightDir.y * 0.8);
	float light = lit * shape * vShade;
	vec3 color = vec3(0.93, 0.955, 1.0) * SnowTint.rgb * light * mix(vec3(0.88, 0.94, 1.06), vec3(1.0), smoothstep(0.3, 0.85, light));
	float dist = length(vRel);
	// Glitter: tiny grains catch the sun here and there, from some angles only, close by.
	if (n.y > 0.5 && LightDir.w > 0.0 && dist < 20.0) {
		float h = fHash(ivec2(floor(vWorld.xz * 48.0)) + ivec2(911, 377));
		float facing = fract(h * 7.31 + dot(vRel / max(dist, 0.001), vec3(5.3, 2.1, 4.7)) * 3.0);
		float glint = step(0.985, h) * smoothstep(0.86, 0.98, facing) * (1.0 - smoothstep(6.0, 20.0, dist));
		color += vec3(1.0, 0.98, 0.95) * glint * LightDir.w * SnowCover.w * smoothstep(0.45, 0.8, lit) * 1.2;
	}
	float fogEnd = SnowCover.y;
	float fogStart = fogEnd - clamp(fogEnd / 10.0, 4.0, 64.0);
	float cylinder = max(length(vRel.xz), abs(vRel.y));
	color = mix(color, FogColor.rgb, clamp((cylinder - fogStart) / max(fogEnd - fogStart, 1.0), 0.0, 1.0));
	fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
