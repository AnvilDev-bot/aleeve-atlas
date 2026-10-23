package dev.dubhe.atlas.client.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dubhe.atlas.util.ColorUtil;
import org.junit.jupiter.api.Test;

/// 雷达配色解析的单元测试（规格 §6.2）。
///
/// `RadarCategory.classify` 依赖真实实体实例，无法在无游戏环境的单元测试中构造，
/// 因此此处只覆盖可直接验证的配色解析逻辑。
class RadarCategoryTest {
    @Test
    void hexColorParsingFallsBackOnInvalidInput() {
        int fallback = 0xFF112233;
        assertEquals(0xFFAABBCC, ColorUtil.parseHex("#AABBCC", fallback));
        assertEquals(0xFFAABBCC, ColorUtil.parseHex("aabbcc", fallback));
        assertEquals(fallback, ColorUtil.parseHex("xyz", fallback));
        assertEquals(fallback, ColorUtil.parseHex("#AABBC", fallback));
        assertEquals(fallback, ColorUtil.parseHex(null, fallback));
    }
}
