package dev.dubhe.atlas.api.waypoint;

import dev.dubhe.atlas.waypoint.Waypoint;
import net.neoforged.bus.api.Event;

/// 收到队友分享路径点时触发。
///
/// `accepted` 表示该路径点是否已被写入本地列表（自动接受为 true，
/// 手动接受在玩家点击「接受」后仍会再次触发本事件）。
public class WaypointReceivedEvent extends Event {
    private final Waypoint waypoint;
    private final boolean accepted;

    public WaypointReceivedEvent(Waypoint waypoint, boolean accepted) {
        this.waypoint = waypoint;
        this.accepted = accepted;
    }

    public Waypoint waypoint() {
        return waypoint;
    }

    public boolean accepted() {
        return accepted;
    }
}
