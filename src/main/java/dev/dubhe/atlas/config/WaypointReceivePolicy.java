package dev.dubhe.atlas.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// 路径点接收策略。
public enum WaypointReceivePolicy implements TranslatableEnum {
    AUTO_ACCEPT,
    MANUAL_ACCEPT,
    AUTO_REJECT
}
