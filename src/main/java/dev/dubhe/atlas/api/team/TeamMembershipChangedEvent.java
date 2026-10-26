package dev.dubhe.atlas.api.team;

import net.neoforged.bus.api.Event;

import java.util.UUID;

/// 队伍成员变更事件。
///
/// 队伍变更后触发，供小地图与世界地图刷新同队标记与地图同步目标。
public class TeamMembershipChangedEvent extends Event {
    /// 变更类型。
    public enum Change {
        JOINED,
        LEFT
    }

    private final UUID playerId;
    private final String teamId;
    private final Change change;

    public TeamMembershipChangedEvent(UUID playerId, String teamId, Change change) {
        this.playerId = playerId;
        this.teamId = teamId;
        this.change = change;
    }

    public UUID playerId() {
        return this.playerId;
    }

    public String teamId() {
        return this.teamId;
    }

    public Change change() {
        return this.change;
    }
}
