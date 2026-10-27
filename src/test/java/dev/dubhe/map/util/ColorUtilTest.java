package dev.dubhe.map.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 颜色解析的单元测试。
///
/// 非法输入必须返回调用方给定的回退色，不得抛出异常导致渲染中断。
class ColorUtilTest {
    private static final int FALLBACK = 0xFF112233;

    @Test
    void parsesWithAndWithoutHashPrefix() {
        Assertions.assertEquals(0xFFAABBCC, ColorUtil.parseHex("#AABBCC", FALLBACK));
        Assertions.assertEquals(0xFFAABBCC, ColorUtil.parseHex("AABBCC", FALLBACK));
    }

    @Test
    void outputIsAlwaysOpaque() {
        // 解析结果必须补齐不透明通道，否则渲染时该颜色完全透明。
        Assertions.assertEquals(0xFF000000, ColorUtil.parseHex("000000", FALLBACK) & 0xFF000000);
        Assertions.assertEquals(0xFFFFFFFF, ColorUtil.parseHex("FFFFFF", FALLBACK));
    }

    @Test
    void trimsSurroundingWhitespace() {
        Assertions.assertEquals(0xFFAABBCC, ColorUtil.parseHex("  #AABBCC  ", FALLBACK));
    }

    @Test
    void rejectsWrongLength() {
        Assertions.assertEquals(FALLBACK, ColorUtil.parseHex("#AABBC", FALLBACK));
        Assertions.assertEquals(FALLBACK, ColorUtil.parseHex("AABBCCDD", FALLBACK));
        Assertions.assertEquals(FALLBACK, ColorUtil.parseHex("", FALLBACK));
        Assertions.assertEquals(FALLBACK, ColorUtil.parseHex("#", FALLBACK));
    }

    @Test
    void rejectsNonHexCharacters() {
        Assertions.assertEquals(FALLBACK, ColorUtil.parseHex("xyzxyz", FALLBACK));
        Assertions.assertEquals(FALLBACK, ColorUtil.parseHex("#GGHHII", FALLBACK));
    }

    @Test
    void rejectsNull() {
        Assertions.assertEquals(FALLBACK, ColorUtil.parseHex(null, FALLBACK));
    }
}
