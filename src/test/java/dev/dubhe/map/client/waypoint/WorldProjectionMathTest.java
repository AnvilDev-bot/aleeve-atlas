package dev.dubhe.map.client.waypoint;

import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorldProjectionMathTest {
    @Test
    void targetAheadOfCameraProjectsNearCenter() {
        Vec3 camera = new Vec3(1000000, 80, -1000000);
        var projected = WorldProjection.project(camera.add(0, 0, -10), camera,
                perspective(70), 1920, 1080).orElseThrow();
        Assertions.assertEquals(960, projected.screenX(), 0.00001);
        Assertions.assertEquals(540, projected.screenY(), 0.00001);
        Assertions.assertEquals(10, projected.distance(), 0.00001);
    }

    @Test
    void followsWorldBobbingAndDynamicFieldOfView() {
        var bobbed = WorldProjection.project(new Vec3(0, 0, -10), Vec3.ZERO,
                perspective(70).translate(0.2f, -0.1f, 0).rotateZ(0.05f), 1920, 1080).orElseThrow();
        Assertions.assertTrue(bobbed.screenX() > 960);
        Assertions.assertTrue(bobbed.screenY() > 540);
        Vec3 target = new Vec3(2, 0, -10);
        var normal = WorldProjection.project(target, Vec3.ZERO, perspective(70), 1920, 1080).orElseThrow();
        var wide = WorldProjection.project(target, Vec3.ZERO, perspective(100), 1920, 1080).orElseThrow();
        Assertions.assertTrue(wide.screenX() < normal.screenX());
    }

    @Test
    void appliesCameraRotationBeforeProjectionAndPreservesSubpixels() {
        var centered = WorldProjection.project(new Vec3(10, 0, 0), Vec3.ZERO,
                perspective(70).rotateY((float) Math.PI / 2), 1920, 1080).orElseThrow();
        Assertions.assertEquals(960, centered.screenX(), 0.0001);
        var moved = WorldProjection.project(new Vec3(0.001, 0, -10), Vec3.ZERO,
                perspective(70), 1920, 1080).orElseThrow();
        Assertions.assertTrue(moved.screenX() > 960 && moved.screenX() < 961);
    }

    @Test
    void hidesPointsBehindCameraOutsideViewAndAtCamera() {
        Matrix4f projection = perspective(70);
        Assertions.assertTrue(WorldProjection.project(new Vec3(0, 0, 10), Vec3.ZERO,
                projection, 1920, 1080).isEmpty());
        Assertions.assertTrue(WorldProjection.project(new Vec3(100, 0, -10), Vec3.ZERO,
                projection, 1920, 1080).isEmpty());
        Assertions.assertTrue(WorldProjection.project(Vec3.ZERO, Vec3.ZERO,
                projection, 1920, 1080).isEmpty());
    }

    private static Matrix4f perspective(float degrees) {
        return new Matrix4f().perspective((float) Math.toRadians(degrees), 16f / 9, 0.05f, 1000);
    }
}
