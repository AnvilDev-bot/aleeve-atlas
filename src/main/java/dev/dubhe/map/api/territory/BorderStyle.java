package dev.dubhe.map.api.territory;

import net.minecraft.network.chat.Component;

/// 领地边框样式。
///
/// @param colorArgb 边框颜色（含 alpha）
/// @param width     边框线宽（像素）
/// @param dashed    是否虚线
public record BorderStyle(int colorArgb, float width, boolean dashed) {
    public static final BorderStyle DEFAULT = new BorderStyle(0xFFFFFFFF, 1.0F, false);

    /// 边框样式的玩家可见名称。
    public Component displayName() {
        String key = this.dashed ? "aleeve_atlas.territory.border.dashed"
                : "aleeve_atlas.territory.border.solid";
        return Component.translatable(key);
    }

    /// 以主题色生成默认边框。
    public static BorderStyle ofTheme(int themeColorRgb) {
        return new BorderStyle(0xFF000000 | (themeColorRgb & 0xFFFFFF), 1.0F, false);
    }
}
