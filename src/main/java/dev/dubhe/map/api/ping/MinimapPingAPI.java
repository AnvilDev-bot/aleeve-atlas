package dev.dubhe.map.api.ping;

import dev.dubhe.map.ping.ClientPings;
import dev.dubhe.map.ping.MapPingData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/// 临时标点接入 API。
///
/// 世界内中键、地图界面和外部模组均可创建标点，小地图、世界地图与 3D 视界统一渲染。
///
/// 所有方法必须在客户端主线程调用。
public final class MinimapPingAPI {
    private static boolean worldPingsEnabled = true;
    private static boolean worldInputEnabled = true;
    private static @Nullable MapPingInput mapInput;

    private MinimapPingAPI() {
    }

    /// 设置是否在 3D 视界中显示临时标点，不影响小地图与世界地图标点。
    public static void setWorldPingsEnabled(boolean enabled) {
        worldPingsEnabled = enabled;
    }

    /// 查询内置世界标点覆层是否启用。
    public static boolean isWorldPingsEnabled() {
        return worldPingsEnabled;
    }

    /// 设置内置世界内按键和轮盘是否启用，外部模组接入时可单独关闭。
    public static void setWorldInputEnabled(boolean enabled) {
        worldInputEnabled = enabled;
    }

    /// 查询内置世界内标点输入是否启用。
    public static boolean isWorldInputEnabled() {
        return worldInputEnabled;
    }

    /// 注册世界地图中键操作；传入 null 恢复内置操作。
    public static void setMapInput(@Nullable MapPingInput input) {
        mapInput = input;
    }

    /// 获取外部世界地图中键操作，null 表示使用内置操作。
    public static @Nullable MapPingInput mapInput() {
        return mapInput;
    }

    /// 获取当前标点的只读快照，实体坐标由客户端 tick 更新。
    public static List<MapPingData> activePings() {
        return ClientPings.active();
    }

    /// 更新仅用于本地显示的标点，禁止向服务端发送，适用于已有网络广播的外部模组。
    public static void registerLocalPing(MapPingData ping) {
        ClientPings.upsert(ping);
    }

    /// 注册一个外部标点（由外部模组在 3D 视界标点后调用）。
    ///
    /// 标点会立即出现在小地图、世界地图和已启用的 3D 视界覆层中，并在超过生命周期后自动移除。
    /// 必须提供发送者 UUID；发送者未知的标点使用 {@link #registerLocalPing(MapPingData)}。
    /// {@link #setWorldPingsEnabled(boolean)} 只控制 3D 视界覆层。
    public static void registerPing(MapPingData ping) {
        ClientPings.upsertAndRelay(ping);
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
        ClientPings.upsertAndRelay(ping);
        return ping;
    }

    /// 移除一个标点（外部模组主动撤回时调用）。
    public static void removePing(UUID pingId) {
        ClientPings.remove(pingId);
    }

    /// 注册绑定实体的标点：位置随目标实体移动。
    ///
    /// 用于玩家把标点打在移动的生物或玩家身上的场景；实体离开视野或消失后标点按生命周期过期。
    public static MapPingData registerEntityPing(UUID targetEntityId, String dimensionId,
            MapPingData.PingIcon icon, UUID senderId, String senderName, int teamColorArgb) {
        return registerEntityPing(targetEntityId, Vec3.ZERO, dimensionId, icon,
                senderId, senderName, teamColorArgb);
    }

    /// 注册实体标点并提供初始坐标；目标在当前维度加载时更新位置，否则保留最后坐标直到过期。
    public static MapPingData registerEntityPing(UUID targetEntityId, Vec3 position, String dimensionId,
            MapPingData.PingIcon icon, UUID senderId, String senderName, int teamColorArgb) {
        MapPingData ping = new MapPingData(
                UUID.randomUUID(),
                senderId,
                senderName,
                teamColorArgb,
                dimensionId,
                position,
                targetEntityId,
                icon,
                System.currentTimeMillis(),
                MapPingData.DEFAULT_LIFETIME_MILLIS);
        ClientPings.upsertAndRelay(ping);
        return ping;
    }
}
