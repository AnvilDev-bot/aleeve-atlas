package dev.aleeve.atlas.util;

/// 颜色解析工具（规格 §6.2：玩家可自定义 HEX 配色）。
public final class ColorUtil {
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
            return fallback;
        }
    }
}
