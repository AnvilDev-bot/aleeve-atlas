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
        dispatcher.register(Commands.literal("aleeve_atlas")
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
                                                false)))))
                .then(Commands.literal("territory")
                        .then(Commands.literal("demo")
                                .executes(context -> demo(context.getSource())))
                        .then(Commands.literal("clear")
                                .executes(context -> clear(context.getSource())))));
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
