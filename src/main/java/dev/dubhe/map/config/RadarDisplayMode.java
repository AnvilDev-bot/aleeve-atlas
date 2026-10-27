package dev.dubhe.map.config;

import com.google.gson.annotations.SerializedName;
import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// 生物图标显示模式。
public enum RadarDisplayMode implements TranslatableEnum {
    @SerializedName("Always Show Icons")
    ICON,
    @SerializedName("Dots")
    DOT,
    @SerializedName("Player List Held")
    HYBRID,
    @SerializedName("Off")
    OFF;

    @Override
    public String getTranslationKey() {
        String mode = switch (this) {
            case ICON -> "always";
            case DOT -> "dots";
            case HYBRID -> "player_list_held";
            case OFF -> "off";
        };
        return "anvillib.configuration.enum.dev.dubhe.map.config.radar_display_mode." + mode;
    }
}
