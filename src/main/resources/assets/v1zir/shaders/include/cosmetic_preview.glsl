// Uniforms of cosmetics drawn outside the entity renderer: the Cosmetics tab's previews and pets in the world
// (render/cosmetic/CosmeticPreview, PetRenderer).

layout(std140) uniform CosmeticPreview {
	mat4 PreviewProj;
	mat4 PreviewModelView;
	vec4 PreviewLight0;
	vec4 PreviewLight1;
	// x = time (seconds), y = brightness, z = fog (0..1)
	vec4 PreviewParams;
	vec4 PreviewFogColor;
};
