package dev.dubhe.map.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// 洞穴映射范围：地表、按高度分层、穿过顶棚后的完整洞穴。
public enum CaveMapMode implements TranslatableEnum {
    OFF, LAYERED, FULL
}
