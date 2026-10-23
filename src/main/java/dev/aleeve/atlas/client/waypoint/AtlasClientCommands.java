package dev.aleeve.atlas.client.waypoint;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import dev.aleeve.atlas.AleeveAtlas;
import java.util.UUID;

/// 客户端命令（规格 §5.3 手动接受交互）。
///
/// 仅注册客户端命令，不污染服务端命令表：
/// `/aleeve_atlas waypoint accept <id>` 与 `/aleeve_atlas waypoint ignore <id>`。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasClientCommands {
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
                                                StringArgumentType.getString(context, "id"), true))))
                        .then(Commands.literal("ignore")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> confirm(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                false))))));
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
}
