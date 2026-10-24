package dev.dubhe.atlas.client.waypoint;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.api.territory.AtlasTerritory;
import dev.dubhe.atlas.api.territory.BorderStyle;
import dev.dubhe.atlas.client.profiling.AtlasProfiling;
import dev.dubhe.atlas.ping.ClientPings;
import dev.dubhe.atlas.ping.MapPingData;
import dev.dubhe.atlas.ping.PingChannelPolicy;
import dev.dubhe.atlas.team.TeamProviders;
import dev.dubhe.atlas.territory.SimpleTerritory;
import dev.dubhe.atlas.waypoint.WaypointTransfer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 客户端命令（规格 §5.3 手动接受交互、§7 领地演示）。
///
/// 仅注册客户端命令，不污染服务端命令表：
/// `/aleeve_atlas waypoint accept|ignore <id>`、`/aleeve_atlas territory demo|clear`。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasClientCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/command");
    private static final String DEMO_ID = "aleeve_atlas_demo";
    private static final String DEMO_OVERLAY_ID = "aleeve_atlas_demo_overlay";

    private AtlasClientCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // 主名用不含下划线的 aleeveatlas（便于输入），同时保留 aleeve_atlas 作为别名。
        dispatcher.register(build(Commands.literal("aleeveatlas")));
        dispatcher.register(build(Commands.literal("aleeve_atlas")));
    }

    /// 组装命令树。
    private static LiteralArgumentBuilder<CommandSourceStack> build(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root
                .then(Commands.literal("waypoint")
                        .then(Commands.literal("accept")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> confirm(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                true))))
                        .then(Commands.literal("ignore")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> confirm(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                false))))
                        .then(Commands.literal("export")
                                .then(Commands.argument("file", StringArgumentType.string())
                                        .executes(context -> exportWaypoints(context.getSource(),
                                                StringArgumentType.getString(context, "file")))))
                        .then(Commands.literal("import")
                                .then(Commands.argument("file", StringArgumentType.string())
                                        .executes(context -> importWaypoints(context.getSource(),
                                                StringArgumentType.getString(context, "file"))))))
                .then(Commands.literal("territory")
                        .then(Commands.literal("demo")
                                .executes(context -> demo(context.getSource())))
                        .then(Commands.literal("clear")
                                .executes(context -> clear(context.getSource()))))
                .then(Commands.literal("pingdemo")
                        .executes(context -> pingDemo(context.getSource())))
                .then(Commands.literal("perf")
                        .executes(context -> reportPerformance(context.getSource())));
    }

    /// 在玩家前方注入一个临时标点，用于验收波纹扩散渲染（规格 §3.2.2）。
    private static int pingDemo(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        var target = player.position().add(player.getViewVector(1.0F).scale(12.0));
        var ping = ClientPings.createLocal(target,
                MapPingData.PingIcon.NORMAL);
        return ping == null ? 0 : 1;
    }

    /// 输出渲染性能统计（规格 §12.1 验收）。
    private static int reportPerformance(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        player.displayClientMessage(Component.literal(
                AtlasProfiling.minimapSummary()), false);
        player.displayClientMessage(Component.literal(
                AtlasProfiling.canvasSummary()), false);
        // 标点通道诊断：确认是否存在与外部模组重复广播的风险（§3.2.2）。
        player.displayClientMessage(Component.literal(
                PingChannelPolicy.describe()), false);
        if (PingChannelPolicy.duplicateRisk()) {
            player.displayClientMessage(
                    Component.translatable("aleeve_atlas.message.ping_channel_duplicate"),
                    false);
        }
        // 队伍适配层诊断：确认当前实现与所属队伍（§3.1）。
        player.displayClientMessage(Component.literal(
                TeamProviders.describe(player.getUUID())), false);
        AtlasProfiling.reset();
        return 1;
    }

    private static int confirm(CommandSourceStack source, String rawId, boolean accepted) {
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid waypoint id for confirm: {}", rawId, e);
            return 0;
        }
        ClientWaypointSync.confirm(id, accepted);
        return 1;
    }

    /// 导出当前路径点到文件（规格 §11）。
    private static int exportWaypoints(CommandSourceStack source, String fileName) {
        var waypointStore = ClientWaypoints.store();
        var player = source.getPlayer();
        if (waypointStore == null || player == null) {
            return 0;
        }
        var file = exportDir().resolve(fileName);
        try {
            WaypointTransfer.exportTo(file,
                    ClientWaypoints.allAcrossDimensions());
            player.displayClientMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_exported", file.toString()), false);
            return 1;
        } catch (IOException e) {
            player.displayClientMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_export_failed"), false);
            return 0;
        }
    }

    /// 从文件导入路径点并合并（规格 §11）。
    private static int importWaypoints(CommandSourceStack source, String fileName) {
        var waypointStore = ClientWaypoints.store();
        var player = source.getPlayer();
        if (waypointStore == null || player == null) {
            return 0;
        }
        var file = exportDir().resolve(fileName);
        try {
            var imported = WaypointTransfer.importFrom(file);
            var result = WaypointTransfer.merge(imported,
                    ClientWaypoints.allAcrossDimensions());
            // 按各自维度写入，跨维度导入不会串到当前维度（§11）。
            for (var waypoint : result.added()) {
                ClientWaypoints.upsertInDimension(waypoint);
            }
            for (var waypoint : result.replaced()) {
                ClientWaypoints.upsertInDimension(waypoint);
            }
            player.displayClientMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_imported",
                    result.added().size(), result.replaced().size()), false);
            return 1;
        } catch (IOException e) {
            player.displayClientMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_import_failed"), false);
            return 0;
        }
    }

    /// 导入导出目录：客户端游戏目录下的 `aleeve_atlas_exports`（规格 §11）。
    private static Path exportDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("aleeve_atlas_exports");
    }

    /// 注册一个含飞地与内孔洞的演示领地，用于验证 §7.2/§7.3（AC-8）。
    private static int demo(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null || source.getLevel() == null) {
            return 0;
        }
        var dimension = source.getLevel().dimension();
        int baseX = player.chunkPosition().x;
        int baseZ = player.chunkPosition().z;

        Set<ChunkPos> chunks = new HashSet<>();
        // 主区域：7×7 实心方块。
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                chunks.add(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        // 内孔洞：中央 3×3 挖空（形成环形，验证内圈闭合边框）。
        for (int dx = 2; dx <= 4; dx++) {
            for (int dz = 2; dz <= 4; dz++) {
                chunks.remove(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        // 飞地 1：对角相接（8 邻域下仍属于同一领地，但独立渲染）。
        chunks.add(new ChunkPos(baseX + 8, baseZ + 8));
        // 飞地 2：完全分离的小岛。
        chunks.add(new ChunkPos(baseX - 3, baseZ - 3));
        chunks.add(new ChunkPos(baseX - 2, baseZ - 3));

        AtlasTerritory.client().upsertTerritory(SimpleTerritory.of(DEMO_ID, dimension, chunks,
                Component.translatable("aleeve_atlas.territory.demo_name"), 0x33AAFF));

        // 叠加一个 zOrder 更高的重叠领地，用于验证边框取舍（§7.7 第 2 条）。
        Set<ChunkPos> overlay = new HashSet<>();
        for (int dx = 1; dx <= 3; dx++) {
            for (int dz = 1; dz <= 3; dz++) {
                overlay.add(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        AtlasTerritory.client().upsertTerritory(new SimpleTerritory(DEMO_OVERLAY_ID, dimension,
                overlay, Component.translatable("aleeve_atlas.territory.demo_overlay_name"),
                0xFFAA33, 0.55F, BorderStyle.ofTheme(0xFFAA33), List.of(), 10));

        player.displayClientMessage(
                Component.translatable("aleeve_atlas.message.territory_demo_added"), true);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        AtlasTerritory.client().removeTerritory(DEMO_ID);
        AtlasTerritory.client().removeTerritory(DEMO_OVERLAY_ID);
        var player = source.getPlayer();
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("aleeve_atlas.message.territory_demo_cleared"), true);
        }
        return 1;
    }
}
