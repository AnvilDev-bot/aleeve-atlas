package dev.dubhe.atlas.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/// 颜色解析的单元测试。
///
/// 非法输入必须返回调用方给定的回退色，不得抛出异常导致渲染中断。
class ColorUtilTest {
    private static final int FALLBACK = 0xFF112233;

    @Test
    void parsesWithAndWithoutHashPrefix() {
        assertEquals(0xFFAABBCC, ColorUtil.parseHex("#AABBCC", FALLBACK));
        assertEquals(0xFFAABBCC, ColorUtil.parseHex("AABBCC", FALLBACK));
    }

    @Test
    void outputIsAlwaysOpaque() {
        // 解析结果必须补齐不透明通道，否则渲染时该颜色完全透明。
        assertEquals(0xFF000000, ColorUtil.parseHex("000000", FALLBACK) & 0xFF000000);
        assertEquals(0xFFFFFFFF, ColorUtil.parseHex("FFFFFF", FALLBACK));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals(0xFFAABBCC, ColorUtil.parseHex("  #AABBCC  ", FALLBACK));
    }

    @Test
    void rejectsWrongLength() {
        assertEquals(FALLBACK, ColorUtil.parseHex("#AABBC", FALLBACK));
        assertEquals(FALLBACK, ColorUtil.parseHex("AABBCCDD", FALLBACK));
        assertEquals(FALLBACK, ColorUtil.parseHex("", FALLBACK));
        assertEquals(FALLBACK, ColorUtil.parseHex("#", FALLBACK));
    }

    @Test
    void rejectsNonHexCharacters() {
        assertEquals(FALLBACK, ColorUtil.parseHex("xyzxyz", FALLBACK));
        assertEquals(FALLBACK, ColorUtil.parseHex("#GGHHII", FALLBACK));
    }

    @Test
    void rejectsNull() {
        assertEquals(FALLBACK, ColorUtil.parseHex(null, FALLBACK));
    }
}
