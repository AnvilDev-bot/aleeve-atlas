package dev.aleeve.atlas.ping;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 服务端标点处理（规格 §3.2 / §10）：校验后广播给同队在线成员。
public final class ServerPingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/ping");

    private ServerPingHandler() {
    }

    public static void handle(PingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        MapPingData ping = payload.ping();
        // 服务端以实际发送者信息覆盖，防止伪造（规格 §10 降级约束：服务端必须校验发送方权限）。
        MapPingData verified = new MapPingData(ping.pingId(), sender.getUUID(),
                sender.getGameProfile().name(), ping.teamColorArgb(), ping.dimensionId(),
                ping.position(), ping.targetEntityId(), ping.icon(), ping.createdAtMillis(),
                ping.lifetimeMillis());

        var team = sender.getTeam();
        List<ServerPlayer> recipients = context.player().level().getServer().getPlayerList()
                .getPlayers().stream()
                .filter(player -> sameTeam(team, player))
                .toList();
        for (ServerPlayer recipient : recipients) {
            PacketDistributor.sendToPlayer(recipient, new PingBroadcastPayload(verified));
        }
        LOGGER.debug("Broadcast ping from {} to {} player(s)",
                sender.getGameProfile().name(), recipients.size());
    }

    private static boolean sameTeam(net.minecraft.world.scores.PlayerTeam team,
            ServerPlayer player) {
        return team != null && team == player.getTeam();
    }

    /// 供聊天提示的组件工具。
    public static Component description(MapPingData ping) {
        return Component.translatable("aleeve_atlas.message.ping_received", ping.senderName());
    }
}
