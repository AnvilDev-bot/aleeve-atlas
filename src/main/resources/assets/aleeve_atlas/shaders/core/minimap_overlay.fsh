#version 330

// 小地图叠加层：圆形/方形边框与玩家朝向箭头。
//
// 约定：
// - UV 为小地图方形的归一化坐标，中心 (0.5, 0.5)。
// - 顶点色 R 通道编码玩家朝向角 yaw/(2π)；G 通道非零表示启用圆形形态。
// - 叠加层使用 2×2 白色纹理，其 alpha 作为叠加强度，保证 Sampler0 被真正使用。
// - 圆外与圆形形态下的非边框区域一律 discard，不绘制任何内容。

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

const vec4 BORDER_COLOR = vec4(0.06, 0.06, 0.06, 1.0);
const vec4 ARROW_COLOR = vec4(1.0, 1.0, 1.0, 1.0);
const float ARROW_LENGTH = 0.115;
const float ARROW_HALF_WIDTH = 0.075;
const float ARROW_BASE = 0.062;

/// 判断点是否落在朝向箭头内（局部坐标已按 yaw 逆旋转，正上方为 -Y）。
bool insideArrow(vec2 local) {
    if (local.y > ARROW_BASE || local.y < -ARROW_LENGTH) {
        return false;
    }
    float t = (local.y + ARROW_LENGTH) / (ARROW_LENGTH + ARROW_BASE);
    float halfWidth = t * ARROW_HALF_WIDTH;
    return abs(local.x) <= halfWidth;
}

void main() {
    float strength = texture(Sampler0, texCoord0).a;
    if (strength <= 0.0) {
        discard;
    }

    vec2 centered = texCoord0 - vec2(0.5);
    bool circular = vertexColor.g > 0.5;
    float radius = length(centered) * 2.0;

    if (circular) {
        // 圆外不渲染。
        if (radius > 1.0) {
            discard;
        }
        // 圆环边框。
        if (radius > 0.972) {
            fragColor = BORDER_COLOR * strength;
            return;
        }
    } else {
        // 方形形态：仅在边缘绘制细边框。
        float edge = max(abs(centered.x), abs(centered.y)) * 2.0;
        if (edge > 0.985) {
            fragColor = BORDER_COLOR * strength;
            return;
        }
    }

    // 玩家朝向箭头：把局部坐标逆旋转回正北朝上。
    float yaw = vertexColor.r * 6.28318530718;
    float cosYaw = cos(yaw);
    float sinYaw = sin(yaw);
    vec2 rotated = vec2(
        centered.x * cosYaw - centered.y * sinYaw,
        centered.x * sinYaw + centered.y * cosYaw
    );
    if (insideArrow(rotated * 2.0)) {
        fragColor = ARROW_COLOR * strength;
        return;
    }

    discard;
}
