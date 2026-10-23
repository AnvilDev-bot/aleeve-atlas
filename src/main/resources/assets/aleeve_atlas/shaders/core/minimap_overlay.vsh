#version 330

// 小地图 HUD 的圆形遮罩、圆形边框与玩家朝向箭头（规格 §2.2）。
//
// 本着色器只绘制「叠加层」：圆外区域、圆环边框与箭头，其余像素 discard，
// 因此可以覆盖在已绘制的小地图纹理之上。玩家朝向通过顶点色的 R 通道传入。

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
}
