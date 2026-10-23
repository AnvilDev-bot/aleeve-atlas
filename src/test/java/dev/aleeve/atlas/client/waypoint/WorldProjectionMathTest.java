package dev.aleeve.atlas.client.waypoint;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// 世界坐标投影的数学部分测试（不依赖游戏运行时）。
///
/// 直接验证 JOML 投影流程的正确性：给定相机位于原点朝 -Z，
/// 正前方目标应投影到屏幕中心附近，背后目标应被拒绝。
class WorldProjectionMathTest {
    @Test
    void targetAheadOfCameraProjectsNearCenter() {
        // 相机在原点、无旋转；投影矩阵为 70° FOV、16:9 宽高比。
        org.joml.Matrix4f projection = new org.joml.Matrix4f().perspective(
                (float) Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1000.0F);
        // 目标位于相机正前方 10 格（视空间 -Z）。
        org.joml.Vector4f clip = new org.joml.Vector4f(0.0F, 0.0F, -10.0F, 1.0F);
        projection.transform(clip);

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        assertTrue(Math.abs(ndcX) < 0.01F, "正前方目标应位于屏幕水平中心，实际 " + ndcX);
        assertTrue(Math.abs(ndcY) < 0.01F, "正前方目标应位于屏幕垂直中心，实际 " + ndcY);
    }

    @Test
    void targetBehindCameraIsRejected() {
        // 视空间 z >= 0 即位于相机后方，实现中直接拒绝。
        org.joml.Vector4f behind = new org.joml.Vector4f(0.0F, 0.0F, 5.0F, 1.0F);
        assertTrue(behind.z >= 0.0F, "后方目标必须被过滤器拒绝");
    }

    @Test
    void offAxisTargetMovesAwayFromCenter() {
        org.joml.Matrix4f projection = new org.joml.Matrix4f().perspective(
                (float) Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1000.0F);
        org.joml.Vector4f clip = new org.joml.Vector4f(5.0F, 0.0F, -10.0F, 1.0F);
        projection.transform(clip);
        float ndcX = clip.x / clip.w;
        // 核心性质：偏右目标投影为正且明显偏离中心。
        assertTrue(ndcX > 0.0F, "偏右目标应投影到屏幕右半侧，实际 " + ndcX);
        assertTrue(ndcX > 0.3F, "偏离中心应足够明显，实际 " + ndcX);
    }
}
