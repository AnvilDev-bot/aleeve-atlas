package dev.dubhe.atlas.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 颜色解析工具。
public final class ColorUtil {
    private static final Logger LOGGER =
            LoggerFactory.getLogger("aleeve_atlas/util");
    private ColorUtil() {
    }

    /// 解析 `#RRGGBB` 或 `RRGGBB` 形式的颜色；非法输入返回回退色。
    public static int parseHex(String hex, int fallback) {
        if (hex == null) {
            return fallback;
        }
        String value = hex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return fallback;
        }
        try {
            return 0xFF000000 | Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid hex color '{}', using fallback", hex, e);
            return fallback;
        }
    }
}
