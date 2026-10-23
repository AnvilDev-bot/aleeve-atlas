#version 330

// 小地图地图纹理的圆形裁剪（规格 §2.2）。
//
// 与 GUI 纹理管线共享顶点格式与矩阵 uniform，仅在片元阶段按 UV 半径丢弃圆外像素，
// 使圆形小地图之外不渲染任何东西（而非压暗）。

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord0 - vec2(0.5);
    if (length(centered) * 2.0 > 1.0) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
