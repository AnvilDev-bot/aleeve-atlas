package dev.dubhe.atlas.sync;

import dev.dubhe.atlas.network.ChunkDigestPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 服务端地图增量同步协调（规格 §3.3）。
///
/// 职责：接收某玩家上报的区块摘要批次，聚合成完整摘要后转发给同队其他在线成员；
/// 由这些成员各自计算「自己已探索而对方缺失」的区块并回传差异。
/// 服务端不持久化任何玩家地图数据（规格 §11 离线原则），仅在内存中暂存批次聚合结果。
public final class ServerMapSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/mapsync");

    /// 摘要聚合状态：按「发起者 + 维度」暂存。
    private static final Map<Key, Aggregation> PENDING = new HashMap<>();

    private ServerMapSync() {
    }

    public static void handleDigest(ChunkDigestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        Key key = new Key(sender.getUUID(), payload.dimensionId());
        Aggregation aggregation = PENDING.computeIfAbsent(key,
                ignored -> new Aggregation(payload.batchCount()));
        aggregation.add(payload.batchIndex(), payload.entries());
        if (!aggregation.complete()) {
            return;
        }
        PENDING.remove(key);

        // 转发完整摘要给同队其他在线成员，由他们回传差异。
        var team = sender.getTeam();
        if (team == null) {
            return;
        }
        for (ServerPlayer teammate : sender.level().getServer().getPlayerList().getPlayers()) {
            if (teammate == sender || teammate.getTeam() != team) {
                continue;
            }
            PacketDistributor.sendToPlayer(teammate,
                    new ChunkDigestPayload(payload.dimensionId(), aggregation.entries(),
                            0, 1, sender.getUUID()));
        }
        LOGGER.debug("Forwarded digest of {} chunk(s) from {} to teammates",
                aggregation.entries().size(), sender.getGameProfile().name());
    }

    /// 收到队友回传的差异分片：转发给当初上报摘要的发起者（规格 §3.3 分片流式传输）。
    public static void handleDelta(dev.dubhe.atlas.network.ChunkDeltaPayload payload,
            IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        // 差异只回传给摘要发起者，避免在全体队友间重复回流（§3.3）。
        java.util.UUID targetId = payload.targetPlayer();
        if (targetId == null) {
            return;
        }
        var team = sender.getTeam();
        if (team == null) {
            return;
        }
        ServerPlayer target = sender.level().getServer().getPlayerList().getPlayer(targetId);
        if (target != null && target != sender && target.getTeam() == team) {
            PacketDistributor.sendToPlayer(target, payload);
        }
    }

    /// 清理玩家断开后的聚合状态。
    public static void clear(UUID playerId) {
        PENDING.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    private record Key(UUID playerId, String dimensionId) {
    }

    /// 摘要批次聚合器。
    private static final class Aggregation {
        private final int expectedBatches;
        private final Map<Integer, List<ChunkDigestPayload.Entry>> batches = new HashMap<>();

        private Aggregation(int expectedBatches) {
            this.expectedBatches = Math.max(1, expectedBatches);
        }

        private void add(int index, List<ChunkDigestPayload.Entry> entries) {
            batches.put(index, entries);
        }

        private boolean complete() {
            return batches.size() >= expectedBatches;
        }

        private List<ChunkDigestPayload.Entry> entries() {
            return batches.values().stream().flatMap(List::stream).toList();
        }
    }
}
