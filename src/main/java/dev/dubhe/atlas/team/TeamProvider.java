package dev.dubhe.atlas.team;

import dev.dubhe.atlas.api.team.TeamMembershipChangedEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/// 队伍适配层接口。
///
/// 默认对接原版计分板队伍；外部队伍模组（如 FTB Teams）可通过注册机制替换实现。
public interface TeamProvider {
    /// 提供者标识，用于配置选择（`auto` / `vanilla` / 外部模组 ID）。
    String id();

    /// 玩家所属队伍 ID；无队伍返回 {@code null}。
    String teamIdOf(UUID playerId);

    /// 同队在线成员 UUID 列表。
    List<UUID> onlineMembersOf(String teamId);

    /// 两名玩家是否同队。
    boolean sameTeam(UUID first, UUID second);

    /// 队伍显示名（含队伍颜色）。
    Component teamDisplayName(String teamId);

    /// 队伍颜色（ARGB），无队伍时返回白色。
    int teamColor(String teamId);

    /// 主动推送队伍变更。
    ///
    /// 原版计分板无变更回调，由 {@link TeamMembershipWatcher} 轮询检测；
    /// 外部队伍实现若具备事件源，应调用本方法以降低延迟。
    default void reportMembershipChange(UUID playerId, String teamId,
            TeamMembershipChangedEvent.Change change) {
        TeamMembershipWatcher.notifyChange(playerId, teamId, change);
    }
}
