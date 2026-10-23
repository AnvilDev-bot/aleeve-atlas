package dev.aleeve.atlas.team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;

/// 原版计分板队伍适配（规格 §3.1 默认实现）。
public final class VanillaTeamProvider implements TeamProvider {
    /// 客户端侧只读访问：通过本地玩家的计分板连接获取队伍信息。
    @Override
    public String id() {
        return "vanilla";
    }

    @Override
    public String teamIdOf(UUID playerId) {
        PlayerTeam team = teamOf(playerId);
        return team == null ? null : team.getName();
    }

    @Override
    public List<UUID> onlineMembersOf(String teamId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || teamId == null) {
            return List.of();
        }
        List<UUID> members = new ArrayList<>();
        for (AbstractClientPlayer player : level.players()) {
            PlayerTeam team = player.getTeam();
            if (team != null && teamId.equals(team.getName())) {
                members.add(player.getUUID());
            }
        }
        return members;
    }

    @Override
    public boolean sameTeam(UUID first, UUID second) {
        String firstTeam = teamIdOf(first);
        return firstTeam != null && firstTeam.equals(teamIdOf(second));
    }

    @Override
    public Component teamDisplayName(String teamId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (teamId == null || minecraft.level == null) {
            return Component.empty();
        }
        PlayerTeam team = minecraft.level.getScoreboard().getPlayerTeam(teamId);
        return team == null ? Component.empty() : team.getDisplayName();
    }

    @Override
    public int teamColor(String teamId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (teamId == null || minecraft.level == null) {
            return 0xFFFFFFFF;
        }
        PlayerTeam team = minecraft.level.getScoreboard().getPlayerTeam(teamId);
        if (team == null || team.getColor() == null) {
            return 0xFFFFFFFF;
        }
        Integer rgb = team.getColor().getColor();
        if (rgb == null) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | rgb;
    }

    private static PlayerTeam teamOf(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }
        net.minecraft.world.entity.player.Player player = level.getPlayerByUUID(playerId);
        return player == null ? null : player.getTeam();
    }
}
