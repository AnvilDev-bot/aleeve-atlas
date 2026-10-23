package dev.aleeve.atlas.waypoint;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 服务端路径点转发（规格 §5.2 / §10）。
///
/// 服务端不持久化任何玩家个人路径点（规格 §11 离线原则），仅校验并转发给同队在线成员。
public final class WaypointSyncHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/waypoints");

    private WaypointSyncHandler() {
    }

    public static void handle(WaypointSyncPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        // 以实际发送者覆盖来源字段，防止伪造。
        Waypoint waypoint = payload.waypoint().withSource(sender.getGameProfile().name());
        WaypointSyncPayload verified =
                new WaypointSyncPayload(payload.action(), waypoint);

        var team = sender.getTeam();
        if (team == null) {
            return;
        }
        List<ServerPlayer> recipients = sender.level().getServer().getPlayerList().getPlayers()
                .stream()
                .filter(player -> player != sender && player.getTeam() == team)
                .toList();
        for (ServerPlayer recipient : recipients) {
            PacketDistributor.sendToPlayer(recipient, verified);
        }
        LOGGER.debug("Forwarded waypoint {} from {} to {} teammate(s)",
                waypoint.name(), sender.getGameProfile().name(), recipients.size());
    }
}
