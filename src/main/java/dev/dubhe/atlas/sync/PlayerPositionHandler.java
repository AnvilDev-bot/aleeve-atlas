package dev.dubhe.atlas.sync;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.config.NonTeamVisibility;
import dev.dubhe.atlas.network.PlayerPositionPayload;
import dev.dubhe.atlas.team.TeamProviders;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// 服务端在线玩家位置协调。
///
/// 仅在内存保存当前在线玩家的最新位置，登录时发送快照，退出时广播移除。
/// 服务端在此执行 `NEVER` 策略过滤，其他可见性策略由客户端结合 GPU 查询处理。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class PlayerPositionHandler {
    private static final Map<UUID, PlayerPositionPayload> POSITIONS = new ConcurrentHashMap<>();

    private PlayerPositionHandler() {
    }

    /// 接收并校验客户端位置，覆盖发起者身份后转发。
    public static void handle(PlayerPositionPayload payload, IPayloadContext context) {
        if (!AleeveAtlas.CONFIG.sync.enabled || payload.removal()
                || !(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        PlayerPositionPayload verified = new PlayerPositionPayload(sender.getUUID(),
                sender.getGameProfile().name(), payload.dimensionId(), payload.x(), payload.y(),
                payload.z(), false);
        PlayerPositionPayload previous = POSITIONS.put(sender.getUUID(), verified);
        if (previous != null && !previous.dimensionId().equals(verified.dimensionId())) {
            broadcastRemoval(sender, previous);
        }
        broadcast(sender, verified);
    }

    /// 玩家登录后发送当前维度的在线玩家快照。
    public static void sendSnapshot(ServerPlayer recipient) {
        if (!AleeveAtlas.CONFIG.sync.enabled) {
            return;
        }
        for (PlayerPositionPayload payload : POSITIONS.values()) {
            if (canReceive(payload.playerId(), recipient)) {
                PacketDistributor.sendToPlayer(recipient, payload);
            }
        }
    }

    /// 玩家退出后移除位置并通知其他在线玩家。
    public static void remove(ServerPlayer sender) {
        PlayerPositionPayload previous = POSITIONS.remove(sender.getUUID());
        if (previous != null) {
            broadcastRemoval(sender, previous);
        }
    }

    private static void broadcastRemoval(ServerPlayer sender, PlayerPositionPayload previous) {
        PlayerPositionPayload removal = new PlayerPositionPayload(previous.playerId(),
                previous.playerName(), previous.dimensionId(), previous.x(), previous.y(),
                previous.z(), true);
        broadcast(sender, removal);
    }

    private static void broadcast(ServerPlayer sender, PlayerPositionPayload payload) {
        for (ServerPlayer recipient : sender.level().getServer().getPlayerList().getPlayers()) {
            if (recipient != sender && canReceive(payload.playerId(), recipient)) {
                PacketDistributor.sendToPlayer(recipient, payload);
            }
        }
    }

    /// 服务端先过滤禁止显示的非同队玩家，避免把其位置下发给无权接收的客户端。
    private static boolean canReceive(UUID targetId, ServerPlayer recipient) {
        return AleeveAtlas.CONFIG.radar.nonTeamPlayerVisibility != NonTeamVisibility.NEVER
                || TeamProviders.active().sameTeam(targetId, recipient.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        POSITIONS.clear();
    }
}
