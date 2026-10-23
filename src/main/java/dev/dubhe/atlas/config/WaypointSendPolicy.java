package dev.dubhe.atlas.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// 路径点发送策略（规格 §5.2 / §9.1 `waypoint.sendPolicy`）。
public enum WaypointSendPolicy implements TranslatableEnum {
    AUTO_SYNC,
    MANUAL_SYNC
}
