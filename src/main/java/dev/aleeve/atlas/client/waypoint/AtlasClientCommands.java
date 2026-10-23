package dev.aleeve.atlas.client.waypoint;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.api.territory.AtlasTerritory;
import dev.aleeve.atlas.territory.SimpleTerritory;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/// 客户端命令（规格 §5.3 手动接受交互、§7 领地演示）。
///
/// 仅注册客户端命令，不污染服务端命令表：
/// `/aleeve_atlas waypoint accept|ignore <id>`、`/aleeve_atlas territory demo|clear`。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasClientCommands {
    private static final String DEMO_ID = "aleeve_atlas_demo";

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
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> build(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root) {
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
                .then(Commands.literal("perf")
                        .executes(context -> reportPerformance(context.getSource())));
    }

    /// 输出渲染性能统计（规格 §12.1 验收）。
    private static int reportPerformance(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        player.displayClientMessage(Component.literal(
                dev.aleeve.atlas.client.profiling.AtlasProfiling.minimapSummary()), false);
        dev.aleeve.atlas.client.profiling.AtlasProfiling.reset();
        return 1;
    }

    private static int confirm(CommandSourceStack source, String rawId, boolean accepted) {
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
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
            dev.aleeve.atlas.waypoint.WaypointTransfer.exportTo(file, waypointStore.all());
            player.displayClientMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_exported", file.toString()), false);
            return 1;
        } catch (java.io.IOException e) {
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
            var imported = dev.aleeve.atlas.waypoint.WaypointTransfer.importFrom(file);
            var result = dev.aleeve.atlas.waypoint.WaypointTransfer.merge(imported,
                    waypointStore.all());
            for (var waypoint : result.added()) {
                waypointStore.upsert(waypoint);
            }
            for (var waypoint : result.replaced()) {
                waypointStore.upsert(waypoint);
            }
            waypointStore.flush();
            player.displayClientMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_imported",
                    result.added().size(), result.replaced().size()), false);
            return 1;
        } catch (java.io.IOException e) {
            player.displayClientMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_import_failed"), false);
            return 0;
        }
    }

    /// 导入导出目录：客户端游戏目录下的 `aleeve_atlas_exports`（规格 §11）。
    private static java.nio.file.Path exportDir() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
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
                Component.literal("Demo Territory"), 0x33AAFF));
        player.displayClientMessage(
                Component.translatable("aleeve_atlas.message.territory_demo_added"), true);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        AtlasTerritory.client().removeTerritory(DEMO_ID);
        var player = source.getPlayer();
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("aleeve_atlas.message.territory_demo_cleared"), true);
        }
        return 1;
    }
}
