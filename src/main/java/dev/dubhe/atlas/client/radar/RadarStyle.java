package dev.dubhe.atlas.client.radar;

import dev.dubhe.atlas.team.TeamProviders;

import dev.dubhe.atlas.config.AtlasClientConfig;
import dev.dubhe.atlas.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/// 实体雷达的配色与显隐规则（规格 §6.2）。
///
/// 配色取自客户端配置的 HEX 字符串，玩家可在设置中自定义并逐类开关。
public final class RadarStyle {
    private RadarStyle() {
    }

    /// 该类别当前是否启用。
    public static boolean visible(AtlasClientConfig.Radar.Categories categories,
            RadarCategory category) {
        return switch (category) {
            case PLAYER -> categories.showPlayers;
            case HOSTILE -> categories.showHostiles;
            case FRIENDLY -> categories.showFriendly;
            case AMBIENT -> categories.showAmbient;
            case ITEM -> categories.showItems;
            case MISC -> categories.showMisc;
        };
    }

    /// 该类别的标记颜色（ARGB）。
    public static int color(AtlasClientConfig.Radar.Categories categories, RadarCategory category,
            Entity entity) {
        String hex = switch (category) {
            case PLAYER -> isTeammate(entity) ? categories.teamPlayerColor
                    : categories.otherPlayerColor;
            case HOSTILE -> categories.hostileColor;
            case FRIENDLY -> categories.friendlyColor;
            case AMBIENT -> categories.ambientColor;
            case ITEM -> categories.itemColor;
            case MISC -> categories.miscColor;
        };
        return ColorUtil.parseHex(hex, defaultColor(category));
    }

    /// 同队判定：经队伍适配层决定，默认对接原版计分板队伍（规格 §3.1）。
    public static boolean isTeammate(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(entity instanceof Player player) || minecraft.player == null
                || player == minecraft.player) {
            return false;
        }
        return TeamProviders.active()
                .sameTeam(minecraft.player.getUUID(), player.getUUID());
    }

    private static int defaultColor(RadarCategory category) {
        return switch (category) {
            case PLAYER -> 0xFFFFFFFF;
            case HOSTILE -> 0xFFFF5555;
            case FRIENDLY -> 0xFF7CFC00;
            case AMBIENT -> 0xFFB0B0B0;
            case ITEM -> 0xFFFF55FF;
            case MISC -> 0xFF9B59B6;
        };
    }
}
