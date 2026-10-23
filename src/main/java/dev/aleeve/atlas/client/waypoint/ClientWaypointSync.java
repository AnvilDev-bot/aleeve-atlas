package dev.aleeve.atlas.client.waypoint;

import dev.aleeve.atlas.api.waypoint.WaypointReceivedEvent;
import dev.aleeve.atlas.config.AtlasClientConfig;
import dev.aleeve.atlas.config.AtlasClientConfigs;
import dev.aleeve.atlas.waypoint.Waypoint;
import dev.aleeve.atlas.waypoint.WaypointStore;
import dev.aleeve.atlas.waypoint.WaypointSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// 客户端路径点接收策略（规格 §5.3）。
///
/// `AUTO_ACCEPT` 静默入库并轻提示；`MANUAL_ACCEPT` 在聊天栏给出可点击的「接受/忽略」；
/// `AUTO_REJECT` 直接丢弃。
public final class ClientWaypointSync {
    private ClientWaypointSync() {
    }

    public static void handle(WaypointSyncPayload payload, IPayloadContext context) {
        AtlasClientConfig config = AtlasClientConfigs.CONFIG;
        Waypoint waypoint = payload.waypoint();
        switch (config.waypoint.receivePolicy) {
            case AUTO_REJECT -> {
                // 直接丢弃，不打扰玩家。
            }
            case AUTO_ACCEPT -> accept(waypoint, payload.action());
            case MANUAL_ACCEPT -> promptManual(waypoint, payload.action());
        }
    }

    private static void accept(Waypoint waypoint, WaypointSyncPayload.Action action) {
        WaypointStore store = ClientWaypoints.store();
        if (store == null) {
            return;
        }
        if (action == WaypointSyncPayload.Action.DELETE) {
            store.remove(waypoint.id());
        } else {
            store.upsert(waypoint);
        }
        NeoForge.EVENT_BUS.post(new WaypointReceivedEvent(waypoint, true));
        notify(Component.translatable("aleeve_atlas.message.waypoint_received",
                waypoint.name(), waypoint.sourcePlayer()).withStyle(ChatFormatting.GREEN));
    }

    private static void promptManual(Waypoint waypoint, WaypointSyncPayload.Action action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Component accept = Component.translatable("aleeve_atlas.message.waypoint_accept")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand(
                                "/aleeve_atlas waypoint accept " + waypoint.id()))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("aleeve_atlas.message.waypoint_accept"))));
        Component ignore = Component.translatable("aleeve_atlas.message.waypoint_ignore")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent.RunCommand(
                                "/aleeve_atlas waypoint ignore " + waypoint.id())));

        minecraft.player.displayClientMessage(
                Component.translatable("aleeve_atlas.message.waypoint_shared",
                        waypoint.name(), waypoint.sourcePlayer(),
                        waypoint.pos().getX(), waypoint.pos().getY(), waypoint.pos().getZ()),
                false);
        minecraft.player.displayClientMessage(
                Component.empty().append(accept).append(Component.literal("  ")).append(ignore),
                false);
        // 暂存待确认项，供接受命令使用。
        ClientWaypoints.pending().put(waypoint.id(), forAction(waypoint, action));
    }

    private static PendingWaypoint forAction(Waypoint waypoint, WaypointSyncPayload.Action action) {
        return new PendingWaypoint(waypoint, action);
    }

    /// 接受一个待确认路径点（由聊天栏按钮触发）。
    public static void confirm(java.util.UUID id, boolean accepted) {
        PendingWaypoint pending = ClientWaypoints.pending().remove(id);
        if (pending == null) {
            return;
        }
        if (accepted) {
            accept(pending.waypoint(), pending.action());
        }
    }

    /// 待确认路径点。
    public record PendingWaypoint(Waypoint waypoint, WaypointSyncPayload.Action action) {
    }

    private static void notify(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }
}
