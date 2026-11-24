// The Winter module's snowfall (render/winter/SnowRenderer).
layout(std140) uniform SnowParams {
	mat4 SnowProj;
	mat4 SnowView;
	// xyz = the camera's position modulo the box (blocks), w = time (seconds)
	vec4 SnowCamera;
	// xz = drift of the wind (blocks a second), y = how fast flakes fall, w = flake size (blocks)
	vec4 SnowWind;
	// xyz = the box of snow around the camera (blocks), w = how far before its edge flakes fade out
	vec4 SnowBox;
	// rgb = the light on the snow, a = opacity
	vec4 SnowLight;
	// x = the camera's height above the height map's base, y = 1 when the map is used,
	// zw = the camera's position within its block (x, z)
	vec4 SnowHeight;
};
