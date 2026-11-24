// The Winter module's snowfall (render/winter/SnowRenderer).
layout(std140) uniform SnowParams {
	mat4 SnowProj;
	mat4 SnowView;
	// xyz = the camera's position modulo the box (blocks), w = time (seconds)
	vec4 SnowCamera;
	// xz = how far the wind has carried the snow so far (blocks, wrapped round the box), y = how fast flakes
	// fall (blocks a second), w = flake size (blocks)
	vec4 SnowWind;
	// xyz = the box of snow around the camera (blocks), w = how far before its edge flakes fade out
	vec4 SnowBox;
	// rgb = the light on the snow, a = opacity
	vec4 SnowLight;
	// x = the camera's height above the height map's base, y = 1 when the map is used,
	// zw = the camera's position within its block (x, z)
	vec4 SnowHeight;
	// xyz = the near box, a third of SnowBox each way (dense snow close by), w = the frame's height in pixels
	vec4 SnowNear;
	// xyz = towards the sun, or the moon at night (world axes), w = its strength (0 when hidden)
	vec4 SnowSun;
};
