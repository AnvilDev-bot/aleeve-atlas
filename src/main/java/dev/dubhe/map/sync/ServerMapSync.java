package dev.dubhe.map.sync;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.network.ChunkDeltaPayload;
import dev.dubhe.map.network.ChunkDigestPayload;
import dev.dubhe.map.team.TeamProviders;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// 服务端地图增量同步协调。
///
/// 职责：接收某玩家上报的区块摘要批次，聚合成完整摘要后转发给同队其他在线成员；
/// 由这些成员各自计算「自己已探索而对方缺失」的区块并回传差异。
/// 服务端不持久化任何玩家地图数据，仅在内存中暂存批次聚合结果。
public final class ServerMapSync {
    /// 摘要聚合状态：按「发起者 + 维度」暂存。
    ///
    /// 摘要在网络线程处理，多个玩家可能并发上报，容器与聚合判定都需同步。
    private static final Map<Key, Aggregation> PENDING = new ConcurrentHashMap<>();
    /// 聚合器超时（毫秒）：分片丢失时按此时间回收。
    private static final long AGGREGATION_TIMEOUT_MILLIS = 60_000L;

    private ServerMapSync() {
    }

    public static void handleDigest(ChunkDigestPayload payload, IPayloadContext context) {
        // 服务端关闭协同同步时丢弃同步类数据包。
        if (!AleeveAtlas.CONFIG.sync.enabled) {
            return;
        }
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        Key key = new Key(sender.getUUID(), payload.dimensionId());
        long now = System.currentTimeMillis();
        List<ChunkDigestPayload.Entry> completed;
        // 聚合判定涉及读改写，需与同键的并发分片互斥，否则可能重复转发或漏判。
        synchronized (PENDING) {
            // 清理超时未收齐的聚合：分片丢失时不应永久占用内存。
            PENDING.entrySet().removeIf(entry -> now - entry.getValue().lastUpdateMillis()
                    > AGGREGATION_TIMEOUT_MILLIS);
            Aggregation aggregation = PENDING.computeIfAbsent(key,
                    ignored -> new Aggregation(payload.batchCount()));
            aggregation.add(payload.batchIndex(), payload.entries(), payload.batchCount(), now);
            if (!aggregation.complete()) {
                return;
            }
            PENDING.remove(key);
            completed = aggregation.entries();
        }

        // 转发完整摘要给同队其他在线成员，由他们回传差异。
        for (ServerPlayer teammate : sender.level().getServer().getPlayerList().getPlayers()) {
            if (teammate == sender || !teammate.connection.hasChannel(ChunkDigestPayload.TYPE)
                    || !TeamProviders.active()
                    .sameTeam(sender.getUUID(), teammate.getUUID())) {
                continue;
            }
            for (ChunkDigestPayload packet : ChunkDigestPayload.split(
                    payload.dimensionId(), completed, sender.getUUID())) {
                PacketDistributor.sendToPlayer(teammate, packet);
            }
        }
    }

    /// 收到队友回传的差异分片：转发给当初上报摘要的发起者。
    ///
    /// 本处理器注册为网络线程执行，玩家与队伍查询属主线程 API，故切回主线程转发。
    public static void handleDelta(ChunkDeltaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> forwardDelta(payload, context));
    }

    /// 在主线程把差异分片转发给摘要发起者。
    private static void forwardDelta(ChunkDeltaPayload payload, IPayloadContext context) {
        // 服务端关闭协同同步时丢弃同步类数据包。
        if (!AleeveAtlas.CONFIG.sync.enabled) {
            return;
        }
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        // 差异只回传给摘要发起者，避免在全体队友间重复回流。
        UUID targetId = payload.targetPlayer();
        if (targetId == null) {
            return;
        }
        ServerPlayer target = sender.level().getServer().getPlayerList().getPlayer(targetId);
        if (target != null && target != sender && target.connection.hasChannel(ChunkDeltaPayload.TYPE)
                && TeamProviders.active().sameTeam(sender.getUUID(), target.getUUID())) {
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
    ///
    /// 收齐判定委托给 {@link BatchTracker}，与客户端领地分片共用同一套规则：
    /// 按序号去重、期望片数随最新分片更新、记录最近更新时间供超时回收。
    private static final class Aggregation {
        private final BatchTracker tracker;
        private final Map<Integer, List<ChunkDigestPayload.Entry>> batches =
                new ConcurrentHashMap<>();

        private Aggregation(int expectedBatches) {
            this.tracker = new BatchTracker(expectedBatches, System.currentTimeMillis());
        }

        private void add(int index, List<ChunkDigestPayload.Entry> entries, int batchCount,
                long now) {
            this.batches.put(index, entries);
            this.tracker.add(index, batchCount, now);
        }

        private long lastUpdateMillis() {
            return this.tracker.lastUpdateMillis();
        }

        private boolean complete() {
            return this.tracker.complete();
        }

        private List<ChunkDigestPayload.Entry> entries() {
            return this.batches.values().stream().flatMap(List::stream).toList();
        }
    }
}
