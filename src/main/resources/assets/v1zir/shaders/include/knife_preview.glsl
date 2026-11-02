// Uniforms of the knife previews on the Knives tab (render/knife/KnifePreview).

layout(std140) uniform KnifePreview {
	mat4 PreviewProj;
	mat4 PreviewModelView;
	vec4 PreviewLight0;
	vec4 PreviewLight1;
	// x = finish, y = brightness
	vec4 PreviewParams;
};
