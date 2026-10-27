#version 330

// 小地图地图纹理的圆形裁剪。
//
// 与 GUI 纹理管线共享顶点格式与矩阵 uniform。顶点色 R 传递纹理裁剪比例的倒数，
// 按屏幕局部半径丢弃圆外像素，避免纹理 UV 裁剪改变遮罩位置。

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
    float uvScale = max(vertexColor.r * 8.0, 0.001);
    vec2 centered = (texCoord0 - vec2(0.5)) * uvScale;
    if (length(centered) * 2.0 > 1.0) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord0) * ColorModulator;
    color.rgb *= vertexColor.g;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color;
}
