#version 150

// Snow lying on the world (the Winter module's Snow cover, render/Reflections), over the finished world and under
// the falling snow; close by the snow lies on it as real geometry too (snow_layer.vsh). Every surface that faces up
// under the open sky is covered, leaves are dappled and water and lava stay clear; further off than the geometry
// reaches, a lip of snow over the top of the sides below stands in for its overhang. No texture: smooth snow that
// rises and falls softly in drifts, lit by what lit the surface (the frame's own brightness, so shade, darkened
// corners and torches carry over) and by the day, bluish in the shade, tiny grains glittering in the sun up close,
// fading into the render distance fog. Pixels it leaves alone are discarded.
#moj_import <v1zir:reflect.glsl>

out vec4 fragColor;

float sHash(ivec2 c) {
	uint h = uint(c.x) * 374761393u + uint(c.y) * 668265263u;
	h = (h ^ (h >> 13u)) * 1274126177u;
	return float((h ^ (h >> 16u)) & 0xFFFFFFu) / 16777215.0;
}

float sNoise(vec2 x) {
	vec2 i = floor(x);
	vec2 f = x - i;
	f = f * f * (3.0 - 2.0 * f);
	ivec2 c = ivec2(i);
	return mix(mix(sHash(c), sHash(c + ivec2(1, 0)), f.x), mix(sHash(c + ivec2(0, 1)), sHash(c + ivec2(1, 1)), f.x), f.y);
}

void main() {
	if (SnowCover.x <= 0.0) discard;
	ivec2 p = ivec2(gl_FragCoord.xy);
	float depth = rDepth(p);
	if (depth >= 1.0) discard;
	vec3 pos = rPosition(vec2(p) + 0.5, depth);
	vec3 n = rNormal(p, pos);
	// Faces turned down never hold snow.
	if (n.y < -0.3) discard;
	// The block the surface belongs to, just inside it, and the column it stands in.
	vec3 inside = pos - n * 0.03;
	ivec3 v = ivec3(floor(CameraFrac.xyz + inside));
	vec2 column = rColumn(v.xz);
	// Columns not known yet stay clear (far water must not turn white).
	if (column.x < -1.0e8) discard;
	int material = rMaterial(inside);
	if (material == AIR || material == WATER || material == LAVA) discard;
	// World position: x and z modulo 4096, y as it is.
	vec3 world = CameraPos.xyz + pos;
	float cover = 0.0;
	bool top = n.y > 0.5;
	if (top) {
		// Facing up: covered when nothing above keeps the snow off. A slab or a lower step of stairs sits below the
		// top of its column and still is.
		cover = smoothstep(0.5, 0.85, n.y) * clamp((world.y - column.x + 0.7) * 5.0, 0.0, 1.0);
		// Leaves hold it in patches.
		if (material == FOLIAGE) cover *= 0.95 * smoothstep(0.12, 0.42, sNoise(world.xz * 1.9) * 0.7 + sNoise(world.xz * 6.1 + 3.0) * 0.3);
	} else if (abs(n.y) < 0.3 && material != FOLIAGE && (SnowTint.w <= 0.0 || length(pos.xz) > 20.0)) {
		// The side of a column's top block, further off than the snow lying as geometry reaches: a lip of snow hanging
		// over its top edge, uneven along it, with a longer tongue here and there.
		float below = column.x - world.y;
		float along = abs(n.x) > 0.5 ? world.z : world.x;
		float side = n.x * 7.0 + n.z * 13.0;
		float lip = 0.05 + 0.07 * sNoise(vec2(along * 4.0, side));
		lip += 0.12 * pow(sNoise(vec2(along * 7.0 + 31.0, side + 5.0)), 5.0);
		cover = below > -0.01 ? 1.0 - smoothstep(lip - 0.02, lip, below) : 0.0;
		if (SnowTint.w > 0.0) cover *= smoothstep(20.0, 24.0, length(pos.xz));
	}
	cover *= SnowCover.x;
	if (cover <= 0.004) discard;

	vec4 scene = texelFetch(Sampler0, p, 0);
	float luma = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
	// The frame's own brightness is what lit the surface, taking most blocks to be of middling brightness; the
	// day's light keeps snow on dark blocks from turning grey.
	float lit = clamp(luma / 0.42, 0.0, 1.3);
	lit = mix(lit, max(lit, SnowCover.z), 0.6);
	// Snow: white with the faintest blue, rising and falling softly in drifts, bluish where it is in shade.
	float drift = sNoise(world.xz * 0.35) * 0.6 + sNoise(world.xz * 1.3) * 0.4;
	vec3 color = vec3(0.93, 0.955, 1.0) * SnowTint.rgb * (0.95 + 0.06 * drift);
	// The lip's underside is in its own shade.
	if (!top) color *= 0.9;
	color *= lit * mix(vec3(0.88, 0.94, 1.06), vec3(1.0), smoothstep(0.3, 0.85, lit));
	float dist = length(pos);
	// Glitter: tiny grains catch the sun here and there, from some angles only, close by.
	if (top && LightDir.w > 0.0 && dist < 20.0) {
		float h = sHash(ivec2(floor(world.xz * 48.0)) + ivec2(911, 377));
		float facing = fract(h * 7.31 + dot(pos / max(dist, 0.001), vec3(5.3, 2.1, 4.7)) * 3.0);
		float glint = step(0.985, h) * smoothstep(0.86, 0.98, facing) * (1.0 - smoothstep(6.0, 20.0, dist));
		color += vec3(1.0, 0.98, 0.95) * glint * LightDir.w * SnowCover.w * smoothstep(0.45, 0.8, lit) * 1.2;
	}
	// The render distance fog, as the game's: over the last stretch of the cylindrical distance.
	float fogEnd = SnowCover.y;
	float fogStart = fogEnd - clamp(fogEnd / 10.0, 4.0, 64.0);
	float cylinder = max(length(pos.xz), abs(pos.y));
	color = mix(color, FogColor.rgb, clamp((cylinder - fogStart) / max(fogEnd - fogStart, 1.0), 0.0, 1.0));
	fragColor = vec4(mix(scene.rgb, clamp(color, 0.0, 1.0), cover), scene.a);
}
