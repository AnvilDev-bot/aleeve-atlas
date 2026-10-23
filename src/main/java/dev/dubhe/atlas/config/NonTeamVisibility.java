package dev.dubhe.atlas.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// 非同队玩家标点显示策略（规格 §4.1 / §9.2 `radar.nonTeamPlayerVisibility`）。
public enum NonTeamVisibility implements TranslatableEnum {
    ALWAYS,
    NEVER,
    VISIBLE_ONLY
}
