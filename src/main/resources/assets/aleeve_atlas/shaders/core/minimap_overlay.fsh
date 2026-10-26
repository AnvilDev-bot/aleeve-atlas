#version 330

// 小地图叠加层：圆形/方形边框与水滴形玩家指示器。
//
// 约定：
// - UV 为小地图方形的归一化坐标，中心 (0.5, 0.5)。
// - 顶点色 R 通道编码玩家朝向角 yaw/(2π)；G 通道表示圆形形态；B 通道表示固定北向。
// - 水滴的半圆圆心就是旋转中心，尖端朝向玩家前方。
// - 叠加层使用 2×2 白色纹理，其 alpha 作为叠加强度。

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
const float DROPLET_RADIUS = 0.065;
const float DROPLET_TIP = 0.13;

/// 水滴形状的有向距离：负值在形状内，旋转中心为半圆圆心 (0,0)。
float dropletDistance(vec2 point) {
    // 圆与三角取并集，三角底边伸入圆内，避免抗锯齿在交界处形成缝隙。
    float circle = length(point) - DROPLET_RADIUS;
    float linearWidth = DROPLET_RADIUS * (point.y + DROPLET_TIP) / DROPLET_TIP;
    float circleWidth = sqrt(max(0.0, DROPLET_RADIUS * DROPLET_RADIUS - point.y * point.y));
    // 三角进入圆内的部分不得越过圆截面，避免底角凸出圆周。
    float halfWidth = point.y > 0.0 ? min(linearWidth, circleWidth) : linearWidth;
    float sideDistance = halfWidth - abs(point.x);
    float capDistance = min(point.y + DROPLET_TIP, 0.02 - point.y);
    float triangle = -min(sideDistance, capDistance);
    return min(circle, triangle);
}

void main() {
    float strength = texture(Sampler0, texCoord0).a;
    if (strength <= 0.0) {
        discard;
    }

    vec2 centered = texCoord0 - vec2(0.5);
    bool circular = vertexColor.g > 0.5;
    bool lockNorth = vertexColor.b > 0.5;
    float radius = length(centered) * 2.0;

    if (circular) {
        if (radius > 1.0) {
            discard;
        }
        if (radius > 0.972) {
            fragColor = BORDER_COLOR * strength;
            return;
        }
    } else {
        float edge = max(abs(centered.x), abs(centered.y)) * 2.0;
        if (edge > 0.985) {
            fragColor = BORDER_COLOR * strength;
            return;
        }
    }

    // 固定北向时，Minecraft yaw=0 表示朝南，水滴应指向屏幕下方；
    // 视角随动时地图已旋转到玩家朝向，水滴固定朝上。
    float yaw = lockNorth ? -3.14159265359 - vertexColor.r * 6.28318530718 : 0.0;
    float cosYaw = cos(yaw);
    float sinYaw = sin(yaw);
    vec2 rotated = vec2(
        centered.x * cosYaw - centered.y * sinYaw,
        centered.x * sinYaw + centered.y * cosYaw
    );
    float distanceToEdge = dropletDistance(rotated * 2.0);
    float antialias = max(fwidth(distanceToEdge), 0.001);
    float alpha = 1.0 - smoothstep(-antialias, antialias, distanceToEdge);
    if (alpha > 0.0) {
        fragColor = vec4(ARROW_COLOR.rgb, ARROW_COLOR.a * strength * alpha);
        return;
    }

    discard;
}
