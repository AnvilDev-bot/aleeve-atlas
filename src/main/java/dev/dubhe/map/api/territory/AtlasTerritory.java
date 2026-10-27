package dev.dubhe.map.api.territory;

import dev.dubhe.map.territory.TerritoryRegistry;

/// 领地覆层 API 的获取入口。
///
/// 客户端与服务端各自持有独立注册表：客户端注册的覆层仅本地可见，
/// 服务端注册的覆层需由调用方自行下发给客户端后，在客户端侧注册。
public final class AtlasTerritory {
    private static final TerritoryRegistry CLIENT = new TerritoryRegistry();
    private static final TerritoryRegistry SERVER = new TerritoryRegistry();

    private AtlasTerritory() {
    }

    /// 客户端侧 API 实例（常用于渲染模组直接注入）。
    public static TerritoryOverlayApi client() {
        return CLIENT;
    }

    /// 服务端侧 API 实例（用于全服/视野内领地下发前的数据准备）。
    public static TerritoryOverlayApi server() {
        return SERVER;
    }

    /// 客户端注册表（供本模组渲染读取）。
    public static TerritoryRegistry clientRegistry() {
        return CLIENT;
    }
}
