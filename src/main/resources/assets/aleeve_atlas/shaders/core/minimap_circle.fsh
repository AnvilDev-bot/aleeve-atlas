#version 330

// 小地图地图纹理的圆形裁剪。
//
// 与 GUI 纹理管线共享顶点格式与矩阵 uniform。顶点色 R 传递纹理裁剪比例的倒数，
// G 传递亮度，B 传递圆形边界的归一化内缩量。

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
    float clipRadius = max(1.0 - vertexColor.b, 0.1);
    vec2 centered = (texCoord0 - vec2(0.5)) * uvScale;
    if (length(centered) * 2.0 > clipRadius) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord0) * ColorModulator;
    color.rgb *= vertexColor.g;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color;
}
