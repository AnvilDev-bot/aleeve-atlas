package dev.dubhe.atlas.ping;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

/// 临时标点类型的显示属性测试。
class PingIconTest {
    @Test
    void allTypesHaveDistinctOpaqueColorsAndNames() {
        Set<Integer> colors = new HashSet<>();
        for (MapPingData.PingIcon icon : MapPingData.PingIcon.values()) {
            Assertions.assertEquals(0xFF, icon.colorArgb() >>> 24);
            Assertions.assertTrue(colors.add(icon.colorArgb()));
            Assertions.assertNotNull(icon.displayName());
        }
        Assertions.assertEquals(4, colors.size());
    }
}
