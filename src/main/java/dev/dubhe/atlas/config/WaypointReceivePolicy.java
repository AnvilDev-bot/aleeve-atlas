package dev.dubhe.atlas.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// 路径点接收策略（规格 §5.3 / §9.1 `waypoint.receivePolicy`）。
public enum WaypointReceivePolicy implements TranslatableEnum {
    AUTO_ACCEPT,
    MANUAL_ACCEPT,
    AUTO_REJECT
}
