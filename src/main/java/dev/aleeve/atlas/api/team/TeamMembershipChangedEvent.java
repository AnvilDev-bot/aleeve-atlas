package dev.aleeve.atlas.api.team;

import java.util.UUID;
import net.neoforged.bus.api.Event;

/// 队伍成员变更事件（规格 §3.1：队伍适配层需支持监听入队/退队事件）。
///
/// 队伍变更后触发，供小地图/世界地图刷新同队标记与地图同步 대상。
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
