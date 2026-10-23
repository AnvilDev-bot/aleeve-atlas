package dev.dubhe.atlas.api.ping;

import dev.dubhe.atlas.ping.MapPingData;
import net.neoforged.bus.api.Event;

/// 本模组发起标点时触发的事件（规格 §3.2.2 Outbound：小地图驱动外部）。
///
/// `Ping Wheel` 等外部模组监听此事件，即可在游戏 3D 世界中同步生成光柱或悬浮 3D 图标。
/// 事件不可取消，仅作通知用途。
public class MapPingCreatedEvent extends Event {
    private final MapPingData ping;
    private final boolean localPlayerOrigin;

    public MapPingCreatedEvent(MapPingData ping, boolean localPlayerOrigin) {
        this.ping = ping;
        this.localPlayerOrigin = localPlayerOrigin;
    }

    /// 标点数据（含维度、精确坐标、图标类型与生命周期）。
    public MapPingData ping() {
        return ping;
    }

    /// 是否由本地玩家发起（否则为收到的队友标点）。
    public boolean localPlayerOrigin() {
        return localPlayerOrigin;
    }
}
