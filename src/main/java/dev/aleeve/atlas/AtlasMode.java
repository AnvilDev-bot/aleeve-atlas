package dev.aleeve.atlas;

/// 运行模式（规格 §1）：仅客户端安装时为独立模式，双端均安装时为协同模式。
public enum AtlasMode {
    STANDALONE,
    SYNCHRONIZED
}
