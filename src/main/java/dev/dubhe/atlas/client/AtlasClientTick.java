package dev.dubhe.atlas.client;

import dev.dubhe.atlas.sync.ClientMapSync;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.client.map.AtlasWorldMap;
import dev.dubhe.atlas.client.waypoint.ClientWaypoints;
import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.ping.ClientPings;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import dev.dubhe.atlas.ping.MapPingData;
import dev.dubhe.atlas.waypoint.Waypoint;
import dev.dubhe.atlas.config.ConfigBounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// 客户端 tick 入口（规格 §8）：在 `ClientTickEvent.Post` 中 while 消费键位。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasClientTick {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("aleeve_atlas/selftest");

    private AtlasClientTick() {
    }

    /// 自检只执行一次。
    private static boolean scrollSelfTestDone;
    /// 联机自检只执行一次。
    private static boolean connectSelfTestDone;
    /// 实体生成自检只执行一次。
    private static boolean entitySummonDone;
    /// 路径点与标点自检只执行一次。
    private static boolean markerDemoDone;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // 自动化验收：在单机世界生成各类实体，验证雷达分类与配色（正常游玩不触发）。
        if (!entitySummonDone && System.getenv("ALEEVE_ATLAS_SUMMON_ENTITIES") != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.hasSingleplayerServer()) {
                entitySummonDone = true;
                summonDemoEntities(minecraft);
            }
        }
        // 自动化验收：放置路径点与标点，验证图标与波纹渲染（正常游玩不触发）。
        if (!markerDemoDone && System.getenv("ALEEVE_ATLAS_DEMO_MARKERS") != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null) {
                markerDemoDone = true;
                placeDemoMarkers(minecraft);
            }
        }
        // 自动化验收：打开世界地图并连续平移，验证像素搬移路径（正常游玩不触发）。
        if (!scrollSelfTestDone && System.getenv("ALEEVE_ATLAS_SCROLL_SELFTEST") != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null
                    && minecraft.screen == null) {
                scrollSelfTestDone = true;
                AtlasWorldMap.open();
            }
        }
        // 自动化验收：程序化按住玩家列表键，验证混合模式的头像切换（正常游玩不触发）。
        String holdTab = System.getenv("ALEEVE_ATLAS_HOLD_TAB");
        if (holdTab != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                KeyMapping.set(minecraft.options.keyPlayerList.getDefaultKey(), true);
            }
        }
        // 自动化验收：直接连接指定服务器，验证双端握手与协同模式（正常游玩不触发）。
        String serverAddress = System.getenv("ALEEVE_ATLAS_CONNECT_SERVER");
        if (!connectSelfTestDone && serverAddress != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                connectSelfTestDone = true;
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                        minecraft.screen,
                        minecraft,
                        ServerAddress.parseString(serverAddress),
                        new net.minecraft.client.multiplayer.ServerData(
                                "atlas_selftest", serverAddress,
                                net.minecraft.client.multiplayer.ServerData.Type.OTHER),
                        false,
                        null);
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // 标点由外部模组经 API 注入，本模组只负责生命周期推进与渲染（规格 §3.2.2）。
        ClientPings.tick();
        // 地图增量分片按 tick 限速写入本地缓存（规格 §3.3 / §12.1）。
        ClientMapSync.tick();
        if (minecraft.screen != null) {
            return;
        }
        while (AtlasKeyMappings.OPEN_WORLD_MAP.consumeClick()) {
            AtlasWorldMap.open();
        }
        while (AtlasKeyMappings.NEW_WAYPOINT.consumeClick()) {
            var waypoint = ClientWaypoints.createHere(Component.translatable(
                    "aleeve_atlas.waypoint.default_name").getString());
            if (waypoint != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("aleeve_atlas.message.waypoint_created",
                                waypoint.pos().getX(), waypoint.pos().getY(),
                                waypoint.pos().getZ()).withStyle(ChatFormatting.GREEN),
                        true);
            }
        }
    }

    /// 供 HUD 读取的缩放倍率，已按规格 §9.1 钳制到 `0.5 ~ 4.0`。
    public static float minimapScale() {
        return (float) ConfigBounds.clamp(AtlasClientConfigs.CONFIG.minimap.scale, 0.5, 4.0);
    }

    /// 在单机世界生成各类实体，覆盖雷达的全部类别（§6.2）。
    ///
    /// 通过内建服务器派发命令，无需玩家手动操作。
    private static void summonDemoEntities(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        String[] commands = {
                "execute at @p run summon minecraft:zombie ~3 ~ ~3",
                "execute at @p run summon minecraft:skeleton ~-3 ~ ~3",
                "execute at @p run summon minecraft:pig ~2 ~ ~-3",
                "execute at @p run summon minecraft:cow ~-2 ~ ~-3",
                "execute at @p run summon minecraft:chicken ~0 ~ ~4",
                "execute at @p run summon minecraft:oak_boat ~5 ~ ~0"
        };
        server.execute(() -> {
            var source = server.createCommandSourceStack();
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(source, command);
            }
            LOGGER.info("Summoned {} demo entities for radar verification", commands.length);
        });
    }

    /// 在玩家周围放置普通路径点、死亡点与各类型标点，验证渲染区分（§5.1 / §3.2.2）。
    ///
    /// 仅用于自动化验收，正常游玩不触发。
    private static void placeDemoMarkers(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        var dimension = minecraft.level.dimension();
        var base = player.blockPosition();
        // 普通路径点分别置于四个方向，便于观察菱形与颜色。
        ClientWaypoints.add(Waypoint.create("north", base.offset(0, 0, -20), dimension,
                0xFF55FFFF));
        ClientWaypoints.add(Waypoint.create("east", base.offset(20, 0, 0), dimension,
                0xFFFFFF55));
        // 死亡点使用十字图标（§5.1 特殊路径点）。
        ClientWaypoints.add(Waypoint.deathPoint(base.offset(-15, 0, -15), dimension));
        // 四种标点类型环绕玩家，验证形状区分与波纹扩散。
        long now = System.currentTimeMillis();
        MapPingData.PingIcon[] icons = MapPingData.PingIcon.values();
        for (int i = 0; i < icons.length; i++) {
            double angle = Math.PI * 2 * i / icons.length;
            Vec3 pos = new Vec3(player.getX() + Math.cos(angle) * 10,
                    player.getY(), player.getZ() + Math.sin(angle) * 10);
            ClientPings.upsert(new MapPingData(UUID.randomUUID(), player.getUUID(),
                    player.getName().getString(), 0xFF55FF55,
                    MapPingData.dimensionId(dimension), pos, null, icons[i], now, 600000L));
        }
        LOGGER.info("Placed demo waypoints and {} ping icons for render verification",
                icons.length);
    }
}
