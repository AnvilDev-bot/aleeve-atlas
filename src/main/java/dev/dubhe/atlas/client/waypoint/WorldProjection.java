package dev.dubhe.atlas.client.waypoint;

import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/// 世界坐标 → 屏幕坐标投影（规格 §5.1：游戏内 3D 视界渲染光柱或悬浮图标+距离）。
///
/// 基于主相机的投影矩阵做透视投影；观察空间 z 轴朝向屏幕内为负，
/// 因此仅当目标位于相机前方（z < 0）时才返回有效屏幕坐标。
public final class WorldProjection {
    private WorldProjection() {
    }

    /// 投影结果：屏幕坐标与到相机的距离（方块）。
    public record Projected(double screenX, double screenY, double distance) {
    }

    /// 把世界坐标投影到 GUI 缩放后的屏幕坐标；不在视野前方时返回 {@link Optional#empty()}。
    public static Optional<Projected> project(Vec3 worldPosition) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized() || minecraft.level == null) {
            return Optional.empty();
        }
        Vec3 cameraPos = camera.position();
        double distance = cameraPos.distanceTo(worldPosition);
        if (distance < 0.1) {
            return Optional.empty();
        }

        Matrix4f projection = minecraft.gameRenderer.getProjectionMatrix(
                minecraft.options.fov().get());
        Matrix4f viewRotation = new Matrix4f()
                .rotation(camera.rotation().conjugate(new Quaternionf()));

        // 世界坐标 → 相机相对坐标 → 视空间。
        Vector4f relative = new Vector4f(
                (float) (worldPosition.x - cameraPos.x),
                (float) (worldPosition.y - cameraPos.y),
                (float) (worldPosition.z - cameraPos.z),
                1.0F);
        viewRotation.transform(relative);
        // 视空间中相机朝 -Z，目标必须在相机前方。
        if (relative.z >= 0.0F) {
            return Optional.empty();
        }

        Vector4f clip = new Vector4f(relative.x, relative.y, relative.z, 1.0F);
        projection.transform(clip);
        if (clip.w <= 0.0F) {
            return Optional.empty();
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (ndcX < -1.2F || ndcX > 1.2F || ndcY < -1.2F || ndcY > 1.2F) {
            return Optional.empty();
        }

        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        double screenX = (ndcX * 0.5 + 0.5) * guiWidth;
        double screenY = (0.5 - ndcY * 0.5) * guiHeight;
        return Optional.of(new Projected(screenX, screenY, distance));
    }
}
