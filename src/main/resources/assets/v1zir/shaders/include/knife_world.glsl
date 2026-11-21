// Vanilla's light and fog uniform blocks as the knife pipeline reads them (bound with every entity render layer).

layout(std140) uniform Lighting {
	vec3 Light0_Direction;
	vec3 Light1_Direction;
};

layout(std140) uniform Fog {
	vec4 FogColor;
	float FogEnvironmentalStart;
	float FogEnvironmentalEnd;
	float FogRenderDistanceStart;
	float FogRenderDistanceEnd;
	float FogSkyEnd;
	float FogCloudsEnd;
};

float knifeFogValue(float distance, float start, float end) {
	return clamp((distance - start) / max(end - start, 1e-4), 0.0, 1.0);
}

// Vanilla's falloff: environmental fog by distance, render distance fog by the cylindrical distance.
float knifeFog(vec3 position) {
	float spherical = length(position);
	float cylindrical = max(length(position.xz), abs(position.y));
	return max(knifeFogValue(spherical, FogEnvironmentalStart, FogEnvironmentalEnd),
		knifeFogValue(cylindrical, FogRenderDistanceStart, FogRenderDistanceEnd));
}
