package dev.aleeve.atlas.api.territory;

/// 领地边框样式（规格 §7.6：由实现以记录类型提供，草案不锁定字段）。
///
/// @param colorArgb 边框颜色（含 alpha）
/// @param width     边框线宽（像素）
/// @param dashed    是否虚线
public record BorderStyle(int colorArgb, float width, boolean dashed) {
    public static final BorderStyle DEFAULT = new BorderStyle(0xFFFFFFFF, 1.0F, false);

    /// 以主题色生成默认边框。
    public static BorderStyle ofTheme(int themeColorRgb) {
        return new BorderStyle(0xFF000000 | (themeColorRgb & 0xFFFFFF), 1.0F, false);
    }
}
