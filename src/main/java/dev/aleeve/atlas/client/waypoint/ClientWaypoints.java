package dev.aleeve.atlas.client.waypoint;

import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.config.AtlasClientConfig;
import dev.aleeve.atlas.config.AtlasClientConfigs;
import dev.aleeve.atlas.waypoint.Waypoint;
import dev.aleeve.atlas.waypoint.WaypointStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import dev.aleeve.atlas.AtlasSession;
import dev.aleeve.atlas.waypoint.WaypointSyncPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/// 客户端路径点管理器（规格 §5.1）。
///
/// 负责按服务器标识打开/切换路径点存储、新建路径点、记录死亡点，并在退出时落盘。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class ClientWaypoints {
    private static WaypointStore store;
    private static String currentServerKey;
    /// 待玩家确认的队友路径点（手动接受策略）。
    private static final java.util.Map<java.util.UUID, ClientWaypointSync.PendingWaypoint> PENDING =
            new java.util.concurrent.ConcurrentHashMap<>();

    private ClientWaypoints() {
    }

    public static java.util.Map<java.util.UUID, ClientWaypointSync.PendingWaypoint> pending() {
        return PENDING;
    }

    /// 当前路径点存储；未连接时返回 {@code null}。
    public static WaypointStore store() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        String key = serverKey(minecraft);
        if (store == null || !key.equals(currentServerKey)) {
            if (store != null) {
                store.flush();
            }
            store = WaypointStore.open(minecraft.gameDirectory.toPath(), key);
            currentServerKey = key;
            AtlasClientConfig config = AtlasClientConfigs.CONFIG;
            if (!config.waypoint.keepDeathPoints) {
                store.pruneDeathPoints();
            }
        }
        return store;
    }

    /// 在玩家当前位置新建路径点（`N` 键，规格 §8）。
    public static Waypoint createHere(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        WaypointStore waypointStore = store();
        if (minecraft.player == null || waypointStore == null) {
            return null;
        }
        BlockPos pos = minecraft.player.blockPosition();
        Waypoint waypoint = Waypoint.create(name, pos, minecraft.level.dimension(), 0xFF55FFFF);
        waypointStore.upsert(waypoint);
        return waypoint;
    }

    /// 新增路径点（自动同步策略下发往服务端，规格 §5.2）。
    public static void add(Waypoint waypoint) {
        WaypointStore waypointStore = store();
        if (waypointStore == null) {
            return;
        }
        waypointStore.upsert(waypoint);
        if (AtlasClientConfigs.CONFIG.waypoint.sendPolicy
                == dev.aleeve.atlas.config.WaypointSendPolicy.AUTO_SYNC) {
            share(waypoint);
        }
    }

    /// 某维度的全部启用路径点（世界地图展示用，不按玩家距离过滤）。
    public static java.util.List<Waypoint> waypointsFor(
            net.minecraft.resources.ResourceKey<Level> dimension) {
        WaypointStore waypointStore = store();
        if (waypointStore == null) {
            return java.util.List.of();
        }
        java.util.List<Waypoint> result = new java.util.ArrayList<>();
        for (Waypoint waypoint : waypointStore.all()) {
            if (waypoint.enabled() && waypoint.dimension().equals(dimension)) {
                result.add(waypoint);
            }
        }
        return result;
    }

    /// 把路径点分享给在线队友（规格 §5.2 手动同步）。
    public static void share(Waypoint waypoint) {
        if (!AtlasSession.synchronizedMode()) {
            return;
        }
        ClientPacketDistributor.sendToServer(
                new WaypointSyncPayload(WaypointSyncPayload.Action.UPSERT, waypoint));
    }

    /// 通知队友删除某路径点。
    public static void shareDelete(Waypoint waypoint) {
        if (!AtlasSession.synchronizedMode()) {
            return;
        }
        ClientPacketDistributor.sendToServer(
                new WaypointSyncPayload(WaypointSyncPayload.Action.DELETE, waypoint));
    }

    /// 记录玩家死亡位置，供复活后生成死亡点。
    @SubscribeEvent
    public static void onPlayerClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        // 仅在客户端侧记录：客户端玩家实体重建时保留死亡坐标。
        if (!event.isWasDeath()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        WaypointStore waypointStore = store();
        if (waypointStore == null) {
            return;
        }
        Waypoint death = Waypoint.deathPoint(minecraft.player.blockPosition(),
                minecraft.level.dimension());
        waypointStore.upsert(death);
        if (!AtlasClientConfigs.CONFIG.waypoint.keepDeathPoints) {
            waypointStore.pruneDeathPoints();
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.translatable("aleeve_atlas.message.death_point_added"), true);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (store != null) {
            store.flush();
            store.clear();
            store = null;
        }
        currentServerKey = null;
    }

    /// 供世界地图界面显示的路径点集合。
    public static java.util.List<Waypoint> visibleWaypoints() {
        Minecraft minecraft = Minecraft.getInstance();
        WaypointStore waypointStore = store();
        if (minecraft.player == null || minecraft.level == null || waypointStore == null) {
            return java.util.List.of();
        }
        return waypointStore.visibleIn(minecraft.level.dimension(), minecraft.player.getX(),
                minecraft.player.getZ());
    }

    private static String serverKey(Minecraft minecraft) {
        ServerData server = minecraft.getCurrentServer();
        if (server != null) {
            return dev.aleeve.atlas.storage.AtlasPaths.hashServerKey(server.ip);
        }
        if (minecraft.hasSingleplayerServer()) {
            return "singleplayer";
        }
        return "unknown";
    }

    /// 维度键工具（供其他模块复用）。
    public static net.minecraft.resources.ResourceKey<Level> dimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? Level.OVERWORLD : minecraft.level.dimension();
    }
}
