package dev.dubhe.atlas.client.waypoint;

import dev.dubhe.atlas.config.WaypointSendPolicy;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.AtlasSession;
import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/// 路径点管理列表（规格 §5.2：玩家需在路径点管理列表中点击「分享给队伍」）。
///
/// 列出当前维度的全部路径点，逐项提供启用切换、分享、删除操作；
/// 独立模式下分享按钮置灰并提示（规格 §1）。
public final class WaypointListScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 36;
    private static final int BUTTON_WIDTH = 56;

    private final List<Waypoint> entries = new ArrayList<>();
    private int scrollOffset;

    public WaypointListScreen() {
        super(Component.translatable("aleeve_atlas.screen.waypoint_list"));
    }

    @Override
    protected void init() {
        reloadEntries();
    }

    private void reloadEntries() {
        clearWidgets();
        entries.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            entries.addAll(ClientWaypoints.waypointsFor(minecraft.level.dimension()));
        }
        buildRows();
    }

    private void buildRows() {
        boolean synchronizedMode = AtlasSession.synchronizedMode();
        int visibleRows = Math.max(1, (height - LIST_TOP - 32) / ROW_HEIGHT);
        int rowIndex = 0;
        for (int index = scrollOffset; index < entries.size(); index++) {
            if (rowIndex >= visibleRows) {
                break;
            }
            Waypoint waypoint = entries.get(index);
            int y = LIST_TOP + rowIndex * ROW_HEIGHT;
            int buttonX = width - BUTTON_WIDTH * 4 - 16;

            Button toggle = Button.builder(
                            Component.translatable(waypoint.enabled()
                                    ? "aleeve_atlas.screen.waypoint_list.hide"
                                    : "aleeve_atlas.screen.waypoint_list.show"),
                            button -> toggle(waypoint))
                    .bounds(buttonX, y, BUTTON_WIDTH, 20).build();
            addRenderableWidget(toggle);

            Button share = Button.builder(
                            Component.translatable("aleeve_atlas.screen.waypoint_list.share"),
                            button -> share(waypoint))
                    .bounds(buttonX + BUTTON_WIDTH + 4, y, BUTTON_WIDTH, 20)
                    .tooltip(Tooltip.create(synchronizedMode
                            ? Component.translatable("aleeve_atlas.screen.waypoint_list.share.tip")
                            : Component.translatable(
                                    "aleeve_atlas.message.server_unsupported")))
                    .build();
            share.active = synchronizedMode;
            addRenderableWidget(share);

            // 私密切换：单独关闭该路径点的自动同步（§5.2）。
            Button privacy = Button.builder(
                            Component.translatable(waypoint.shareable()
                                    ? "aleeve_atlas.screen.waypoint_list.public"
                                    : "aleeve_atlas.screen.waypoint_list.private"),
                            button -> toggleShareable(waypoint))
                    .bounds(buttonX + (BUTTON_WIDTH + 4) * 2, y, BUTTON_WIDTH, 20).build();
            addRenderableWidget(privacy);

            Button delete = Button.builder(
                            Component.translatable("aleeve_atlas.screen.waypoint_list.delete"),
                            button -> delete(waypoint))
                    .bounds(buttonX + (BUTTON_WIDTH + 4) * 3, y, BUTTON_WIDTH, 20).build();
            addRenderableWidget(delete);
            rowIndex++;
        }

        // 关闭按钮固定在底部中央。
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        button -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private void toggle(Waypoint waypoint) {
        var store = ClientWaypoints.store();
        if (store == null) {
            return;
        }
        store.upsert(waypoint.withEnabled(!waypoint.enabled()));
        reloadEntries();
    }

    private void toggleShareable(Waypoint waypoint) {
        var store = ClientWaypoints.store();
        if (store == null) {
            return;
        }
        store.upsert(waypoint.withShareable(!waypoint.shareable()));
        reloadEntries();
    }

    private void share(Waypoint waypoint) {
        ClientWaypoints.share(waypoint);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("aleeve_atlas.message.waypoint_shared_team",
                            waypoint.name()), true);
        }
    }

    private void delete(Waypoint waypoint) {
        var store = ClientWaypoints.store();
        if (store == null) {
            return;
        }
        store.remove(waypoint.id());
        if (AtlasClientConfigs.CONFIG.waypoint.sendPolicy
                == WaypointSendPolicy.AUTO_SYNC) {
            ClientWaypoints.shareDelete(waypoint);
        }
        reloadEntries();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不调用 renderBackground：1.21.11 每帧仅允许模糊一次。
        graphics.fill(0, 0, width, height, 0xC0101010);
        Minecraft minecraft = Minecraft.getInstance();
        graphics.drawString(minecraft.font, title, 12, 12, 0xFFFFFFFF, true);

        if (entries.isEmpty()) {
            graphics.drawString(minecraft.font,
                    Component.translatable("aleeve_atlas.screen.waypoint_list.empty"),
                    12, LIST_TOP, 0xFFAAAAAA, true);
        } else {
            for (int row = 0; row * ROW_HEIGHT < height - LIST_TOP - 32; row++) {
                int index = scrollOffset + row;
                if (index >= entries.size()) {
                    break;
                }
                Waypoint waypoint = entries.get(index);
                int y = LIST_TOP + row * ROW_HEIGHT;
                int color = waypoint.enabled() ? waypoint.colorArgb() : 0xFF666666;
                graphics.fill(6, y, 10, y + 18, color);
                Component line = Component.literal("%s  (%d, %d, %d)".formatted(
                        waypoint.name(), waypoint.pos().getX(), waypoint.pos().getY(),
                        waypoint.pos().getZ()))
                        .withStyle(waypoint.enabled() ? ChatFormatting.WHITE
                                : ChatFormatting.DARK_GRAY);
                graphics.drawString(minecraft.font, line, 14, y + 5, 0xFFFFFFFF, true);
                if (waypoint.deathPoint()) {
                    graphics.drawString(minecraft.font,
                            Component.translatable("aleeve_atlas.screen.waypoint_list.death"),
                            14 + minecraft.font.width(line) + 6, y + 5, 0xFFFF5555, true);
                }
            }
        }
        // 滚动提示。
        graphics.drawString(minecraft.font,
                Component.translatable("aleeve_atlas.screen.waypoint_list.scroll_hint"),
                12, height - 44, 0xFF888888, true);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            int maxOffset = Math.max(0, entries.size() - 1);
            scrollOffset = Math.max(0, Math.min(maxOffset,
                    scrollOffset + (scrollY > 0 ? -1 : 1)));
            buildRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
