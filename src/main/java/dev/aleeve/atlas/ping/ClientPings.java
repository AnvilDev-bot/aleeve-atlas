package dev.aleeve.atlas.ping;

import dev.aleeve.atlas.AtlasSession;
import dev.aleeve.atlas.api.ping.MapPingCreatedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// 客户端标点收集与生命周期管理（规格 §3.2）。
///
/// 本模组**不内置标点快捷键**：标点由外部模组（如 `Ping Wheel`）或地图界面交互发起，
/// 经 {@link dev.aleeve.atlas.api.ping.MinimapPingAPI} 推送进来；此处统一维护生命周期与渲染数据。
/// 绑定实体的标点每 tick 跟随更新位置，过期自动移除。
public final class ClientPings {
    private static final List<MapPingData> ACTIVE = new CopyOnWriteArrayList<>();

    private ClientPings() {
    }

    /// 收到服务端广播的队友标点。
    public static void handle(PingBroadcastPayload payload, IPayloadContext context) {
        upsert(payload.ping());
    }

    /// 本地发起标点（供世界地图双击/右键与外部模组调用，规格 §2.3 / §3.2.2 Outbound）。
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
        // 通道复用策略决定是否由本模组广播，避免与外部标点模组重复发送（§3.2.2）。
        if (AtlasSession.synchronizedMode() && PingChannelPolicy.shouldBroadcast()) {
            ClientPacketDistributor.sendToServer(new PingPayload(ping));
        }
        return ping;
    }

    /// 外部模组注入或服务端广播标点（规格 §3.2.2 Inbound）。
    public static void upsert(MapPingData ping) {
        ACTIVE.removeIf(existing -> existing.pingId().equals(ping.pingId()));
        ACTIVE.add(ping);
        NeoForge.EVENT_BUS.post(new MapPingCreatedEvent(ping, false));
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
    }
}
