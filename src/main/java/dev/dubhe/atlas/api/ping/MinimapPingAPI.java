package dev.dubhe.atlas.api.ping;

import dev.dubhe.atlas.ping.ClientPings;
import dev.dubhe.atlas.ping.MapPingData;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/// 临时标点接入 API（规格 §3.2.2 Inbound：外部驱动小地图）。
///
/// 本模组**不内置标点快捷键**：标点由 `Ping Wheel` 等外部模组在 3D 视界中发起，
/// 再经本 API 推送进来；小地图与世界地图据此在对应坐标渲染波纹标点。
///
/// 所有方法必须在客户端主线程调用。
public final class MinimapPingAPI {
    private MinimapPingAPI() {
    }

    /// 注册一个外部标点（由外部模组在 3D 视界标点后调用）。
    ///
    /// 标点会立即出现在小地图与世界地图上，并在超过生命周期后自动移除。
    public static void registerPing(MapPingData ping) {
        ClientPings.upsert(ping);
    }

    /// 以坐标注册标点，其余字段由本模组填充。
    public static MapPingData registerPing(Vec3 position, String dimensionId,
            MapPingData.PingIcon icon, UUID senderId, String senderName, int teamColorArgb) {
        MapPingData ping = new MapPingData(
                UUID.randomUUID(),
                senderId,
                senderName,
                teamColorArgb,
                dimensionId,
                position,
                null,
                icon,
                System.currentTimeMillis(),
                MapPingData.DEFAULT_LIFETIME_MILLIS);
        ClientPings.upsert(ping);
        return ping;
    }

    /// 移除一个标点（外部模组主动撤回时调用）。
    public static void removePing(UUID pingId) {
        ClientPings.remove(pingId);
    }

    /// 注册绑定实体的标点：位置随目标实体移动（规格 §3.2.1 可选绑定目标实体）。
    ///
    /// 用于玩家把标点打在移动的生物或玩家身上的场景；实体离开视野或消失后标点按生命周期过期。
    public static MapPingData registerEntityPing(UUID targetEntityId, String dimensionId,
            MapPingData.PingIcon icon, UUID senderId, String senderName, int teamColorArgb) {
        MapPingData ping = new MapPingData(
                UUID.randomUUID(),
                senderId,
                senderName,
                teamColorArgb,
                dimensionId,
                Vec3.ZERO,
                targetEntityId,
                icon,
                System.currentTimeMillis(),
                MapPingData.DEFAULT_LIFETIME_MILLIS);
        ClientPings.upsert(ping);
        return ping;
    }
}
