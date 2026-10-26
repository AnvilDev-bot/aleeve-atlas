package dev.dubhe.atlas.client.waypoint;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 路径点颜色输入与格式化测试。
class WaypointEditScreenTest {
    @Test
    void parsesHexColorWithOpaqueAlpha() {
        Assertions.assertEquals(0xFF55FFFF, WaypointEditScreen.parseColor("#55FFFF"));
        Assertions.assertEquals(0xFF123ABC, WaypointEditScreen.parseColor("123abc"));
    }

    @Test
    void rejectsMalformedColor() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> WaypointEditScreen.parseColor("#12345"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> WaypointEditScreen.parseColor("#GG0000"));
    }

    @Test
    void formatsColorForEditing() {
        Assertions.assertEquals("#55FFFF", WaypointEditScreen.formatColor(0xFF55FFFF));
    }
}
