package dev.aleeve.atlas.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// Ping Wheel 通道复用策略（规格 §3.2.2 / §9.1 `ping.channelMode`）。
public enum PingChannelMode implements TranslatableEnum {
    AUTO,
    LISTEN_ONLY,
    TAKEOVER
}
