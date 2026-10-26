package dev.dubhe.atlas.sync;

import com.mojang.authlib.GameProfile;
import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.AtlasSession;
import dev.dubhe.atlas.network.PlayerPositionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 客户端远端玩家位置缓存与周期上报。
///
/// 远端对象只用于地图标记，实际加载的同名实体优先，避免重复绘制。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class ClientPlayerPositions {
    private static final int REPORT_INTERVAL_TICKS = 20;
    private static final Map<UUID, RemotePlayer> REMOTE_PLAYERS = new HashMap<>();
    private static String currentDimension;
    private static int ticks;

    private ClientPlayerPositions() {
    }

    /// 接收服务端转发的位置；维度不匹配或收到移除时删除缓存。
    public static void handle(PlayerPositionPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!syncDimension(minecraft) || payload.removal()
                || !payload.dimensionId().equals(currentDimension)) {
            REMOTE_PLAYERS.remove(payload.playerId());
            return;
        }
        RemotePlayer remote = REMOTE_PLAYERS.computeIfAbsent(payload.playerId(),
                id -> new RemotePlayer((ClientLevel) minecraft.level,
                        new GameProfile(id, payload.playerName())));
        remote.setPos(payload.x(), payload.y(), payload.z());
    }

    /// 当前维度的远端玩家，供雷达合并到实体列表。
    public static List<RemotePlayer> remotePlayers() {
        if (!syncDimension(Minecraft.getInstance())) {
            return List.of();
        }
        return List.copyOf(REMOTE_PLAYERS.values());
    }

    /// 维度切换后丢弃旧维度位置，防止远端标记出现在新世界。
    private static boolean syncDimension(Minecraft minecraft) {
        if (minecraft.level == null) {
            return false;
        }
        String dimension = minecraft.level.dimension().identifier().toString();
        if (!dimension.equals(currentDimension)) {
            REMOTE_PLAYERS.clear();
            currentDimension = dimension;
        }
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!AtlasSession.synchronizedMode()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !syncDimension(minecraft)) {
            return;
        }
        if (++ticks < REPORT_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        ClientPacketDistributor.sendToServer(new PlayerPositionPayload(
                minecraft.player.getUUID(), minecraft.player.getGameProfile().name(),
                minecraft.level.dimension().identifier().toString(),
                minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(), false));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        REMOTE_PLAYERS.clear();
        currentDimension = null;
        ticks = 0;
    }
}
