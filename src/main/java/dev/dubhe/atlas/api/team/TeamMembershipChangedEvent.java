package dev.dubhe.atlas.api.team;

import java.util.UUID;
import net.neoforged.bus.api.Event;

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
        return playerId;
    }

    public String teamId() {
        return teamId;
    }

    public Change change() {
        return change;
    }
}
