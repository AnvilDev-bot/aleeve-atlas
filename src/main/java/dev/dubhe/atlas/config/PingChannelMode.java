package dev.dubhe.atlas.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// Ping Wheel 通道复用策略。
public enum PingChannelMode implements TranslatableEnum {
    AUTO,
    LISTEN_ONLY,
    TAKEOVER
}
