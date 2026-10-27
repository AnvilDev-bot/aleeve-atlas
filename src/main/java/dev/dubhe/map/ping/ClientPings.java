package dev.dubhe.map.ping;

import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.api.ping.MapPingCreatedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/// 客户端标点收集与生命周期管理。
///
/// 本模组**不内置标点快捷键**：标点由外部模组（如 `Ping Wheel`）或地图界面交互发起，
/// 经 {@link dev.dubhe.map.api.ping.MinimapPingAPI} 推送进来；此处统一维护生命周期与渲染数据。
/// 绑定实体的标点每 tick 跟随更新位置，过期自动移除。
public final class ClientPings {
    private static final List<MapPingData> ACTIVE = new CopyOnWriteArrayList<>();
    /// 广播限频：标点属高频事件，需限频避免突发流量。
    private static final BroadcastCooldown BROADCAST_COOLDOWN = new BroadcastCooldown(250L);

    private ClientPings() {
    }

    /// 收到服务端广播的队友标点。
    public static void handle(PingBroadcastPayload payload, IPayloadContext context) {
        upsert(payload.ping());
    }

    /// 本地发起标点：供世界地图双击/右键与外部模组调用，触发对外的标点创建事件。
    public static MapPingData createLocal(Vec3 position, MapPingData.PingIcon icon) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        MapPingData ping = new MapPingData(
                UUID.randomUUID(),
                minecraft.player.getUUID(),
                minecraft.player.getGameProfile().name(),
                0xFFFFFFFF,
                MapPingData.dimensionId(minecraft.level.dimension()),
                position,
                null,
                icon,
                System.currentTimeMillis(),
                MapPingData.DEFAULT_LIFETIME_MILLIS);
        ACTIVE.removeIf(existing -> existing.pingId().equals(ping.pingId()));
        ACTIVE.add(ping);
        NeoForge.EVENT_BUS.post(new MapPingCreatedEvent(ping, true));
        // 通道复用策略决定是否由本模组广播，避免与外部标点模组重复发送。
        // 连续发起时按冷却间隔丢弃后续广播，本地标记仍保留。
        if (AtlasSession.synchronizedMode() && PingChannelPolicy.shouldBroadcast()
                && BROADCAST_COOLDOWN.tryAcquire(System.currentTimeMillis())) {
            ClientPacketDistributor.sendToServer(new PingPayload(ping));
        }
        return ping;
    }

    /// 外部模组注入或服务端广播标点。
    public static void upsert(MapPingData ping) {
        ACTIVE.removeIf(existing -> existing.pingId().equals(ping.pingId()));
        ACTIVE.add(ping);
        NeoForge.EVENT_BUS.post(new MapPingCreatedEvent(ping, false));
    }

    /// 外部模组注入标点，并在接管模式下由本模组统一转发。
    ///
    /// `TAKEOVER` 表示外部模组不自行广播、由本模组经队伍同步网络统一转发，
    /// 避免同一次标点被两个系统各广播一次。
    public static void upsertAndRelay(MapPingData ping) {
        upsert(ping);
        // 独立模式下服务端无同步能力，与本地发起路径保持一致，不发送网络包。
        if (AtlasSession.synchronizedMode() && PingChannelPolicy.relaysExternalPings()
                && BROADCAST_COOLDOWN.tryAcquire(System.currentTimeMillis())) {
            ClientPacketDistributor.sendToServer(new PingPayload(ping));
        }
    }

    /// 移除指定标点（外部模组撤回或本地清理）。
    public static void remove(UUID pingId) {
        ACTIVE.removeIf(existing -> existing.pingId().equals(pingId));
    }

    /// 每个客户端 tick 清理过期标点并更新跟随实体位置。
    public static void tick() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        List<MapPingData> expired = new ArrayList<>();
        for (MapPingData ping : ACTIVE) {
            if (ping.expired(now)) {
                expired.add(ping);
                continue;
            }
            if (!ping.followsEntity() || level == null) {
                continue;
            }
            Entity target = level.getEntity(ping.targetEntityId());
            if (target == null) {
                continue;
            }
            int index = ACTIVE.indexOf(ping);
            if (index >= 0) {
                ACTIVE.set(index, ping.withPosition(target.position()));
            }
        }
        ACTIVE.removeAll(expired);
    }

    public static List<MapPingData> active() {
        return List.copyOf(ACTIVE);
    }

    public static void clear() {
        ACTIVE.clear();
        // 冷却状态属会话级状态，切换服务器后从零开始。
        BROADCAST_COOLDOWN.reset();
    }
}
