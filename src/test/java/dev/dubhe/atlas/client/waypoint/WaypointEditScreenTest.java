package dev.dubhe.atlas.client.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/// 路径点颜色输入与格式化测试。
class WaypointEditScreenTest {
    @Test
    void parsesHexColorWithOpaqueAlpha() {
        assertEquals(0xFF55FFFF, WaypointEditScreen.parseColor("#55FFFF"));
        assertEquals(0xFF123ABC, WaypointEditScreen.parseColor("123abc"));
    }

    @Test
    void rejectsMalformedColor() {
        assertThrows(IllegalArgumentException.class,
                () -> WaypointEditScreen.parseColor("#12345"));
        assertThrows(IllegalArgumentException.class,
                () -> WaypointEditScreen.parseColor("#GG0000"));
    }

    @Test
    void formatsColorForEditing() {
        assertEquals("#55FFFF", WaypointEditScreen.formatColor(0xFF55FFFF));
    }
}
