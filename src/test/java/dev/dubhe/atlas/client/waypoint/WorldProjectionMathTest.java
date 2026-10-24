package dev.dubhe.atlas.client.waypoint;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/// 世界坐标投影的数学验证（不依赖游戏运行时）。
///
/// `WorldProjection.project` 依赖客户端相机实例，无法在单元测试中构造；
/// 此处验证其依赖的 JOML 透视投影性质，确保朝向与偏移方向符合预期。
class WorldProjectionMathTest {
    private static final float FOV_RADIANS = (float) Math.toRadians(70.0);
    private static final float ASPECT = 16.0F / 9.0F;

    @Test
    void targetAheadOfCameraProjectsNearCenter() {
        Matrix4f projection = new Matrix4f().perspective(FOV_RADIANS, ASPECT, 0.05F, 1000.0F);
        // 目标位于相机正前方 10 格（视空间 -Z）。
        Vector4f clip = new Vector4f(0.0F, 0.0F, -10.0F, 1.0F);
        projection.transform(clip);

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        assertTrue(Math.abs(ndcX) < 0.01F, "正前方目标应位于屏幕水平中心，实际 " + ndcX);
        assertTrue(Math.abs(ndcY) < 0.01F, "正前方目标应位于屏幕垂直中心，实际 " + ndcY);
    }

    @Test
    void offAxisTargetMovesAwayFromCenter() {
        Matrix4f projection = new Matrix4f().perspective(FOV_RADIANS, ASPECT, 0.05F, 1000.0F);
        Vector4f clip = new Vector4f(5.0F, 0.0F, -10.0F, 1.0F);
        projection.transform(clip);
        float ndcX = clip.x / clip.w;
        assertTrue(ndcX > 0.3F, "偏右目标应明显偏离中心，实际 " + ndcX);
    }

    @Test
    void targetBehindCameraHasNonPositiveClipW() {
        Matrix4f projection = new Matrix4f().perspective(FOV_RADIANS, ASPECT, 0.05F, 1000.0F);
        Vector4f behind = new Vector4f(0.0F, 0.0F, 5.0F, 1.0F);
        projection.transform(behind);
        // 透视投影的 w 分量为 -z_view，故视空间 z 为正（相机后方）时 w 为负；
        // `project` 据此判定目标不在视野前方并返回空结果。
        assertTrue(behind.w < 0.0F, "后方目标变换后裁剪 w 应为负，实际 " + behind.w);
    }
}
