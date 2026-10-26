package dev.dubhe.atlas.client.waypoint;

import dev.dubhe.atlas.AtlasSession;
import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.config.WaypointSendPolicy;
import dev.dubhe.atlas.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

/// 当前维度的路径点管理列表。
///
/// 提供新建、编辑、启用切换、分享、私密和删除操作；右侧滚动条支持拖动，
/// 超出文本区域的名称与坐标在鼠标悬停时横向滚动。
public final class WaypointListScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 36;
    private static final int SCROLLBAR_WIDTH = 6;

    private final Screen parent;
    private final List<Waypoint> entries = new ArrayList<>();
    private int scrollOffset;
    private boolean scrollbarDragging;
    private UUID hoveredLabel;
    private long hoverStartedAt;

    public WaypointListScreen(Screen parent) {
        super(Component.translatable("aleeve_atlas.screen.waypoint_list"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reloadEntries();
    }

    @Override
    public void added() {
        super.added();
        reloadEntries();
    }

    void reloadEntries() {
        entries.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            // 管理界面始终只显示当前维度，隐藏条目也保留在列表中。
            entries.addAll(ClientWaypoints.allInDimension(minecraft.level.dimension()));
        }
        scrollOffset = Math.min(scrollOffset, maxScrollOffset());
        rebuildRows();
    }

    private void rebuildRows() {
        clearWidgets();
        buildRows();
    }

    private void buildRows() {
        boolean synchronizedMode = AtlasSession.synchronizedMode();
        int visibleRows = visibleRows();
        int buttonWidth = buttonWidth();
        int buttonX = buttonsX();
        int rowIndex = 0;
        for (int index = scrollOffset; index < entries.size(); index++) {
            if (rowIndex >= visibleRows) {
                break;
            }
            Waypoint waypoint = entries.get(index);
            int y = LIST_TOP + rowIndex * ROW_HEIGHT;

            addRenderableWidget(Button.builder(
                            Component.translatable("aleeve_atlas.screen.waypoint_list.edit"),
                            button -> edit(waypoint))
                    .bounds(buttonX, y, buttonWidth, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable(waypoint.enabled()
                                    ? "aleeve_atlas.screen.waypoint_list.hide"
                                    : "aleeve_atlas.screen.waypoint_list.show"),
                            button -> toggle(waypoint))
                    .bounds(buttonX + buttonWidth + 4, y, buttonWidth, 20).build());

            Button share = Button.builder(
                            Component.translatable("aleeve_atlas.screen.waypoint_list.share"),
                            button -> share(waypoint))
                    .bounds(buttonX + (buttonWidth + 4) * 2, y, buttonWidth, 20)
                    .tooltip(Tooltip.create(synchronizedMode
                            ? Component.translatable("aleeve_atlas.screen.waypoint_list.share.tip")
                            : Component.translatable(
                                    "aleeve_atlas.message.server_unsupported")))
                    .build();
            share.active = synchronizedMode;
            addRenderableWidget(share);

            addRenderableWidget(Button.builder(
                            Component.translatable(waypoint.shareable()
                                    ? "aleeve_atlas.screen.waypoint_list.public"
                                    : "aleeve_atlas.screen.waypoint_list.private"),
                            button -> toggleShareable(waypoint))
                    .bounds(buttonX + (buttonWidth + 4) * 3, y, buttonWidth, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("aleeve_atlas.screen.waypoint_list.delete"),
                            button -> delete(waypoint))
                    .bounds(buttonX + (buttonWidth + 4) * 4, y, buttonWidth, 20).build());
            rowIndex++;
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("aleeve_atlas.screen.waypoint_list.add"),
                        button -> create())
                .bounds(width - 126, 8, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                        button -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private void create() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new WaypointEditScreen(this, minecraft.player.blockPosition()));
        }
    }

    private void edit(Waypoint waypoint) {
        Minecraft.getInstance().setScreen(new WaypointEditScreen(this, waypoint));
    }

    private void toggle(Waypoint waypoint) {
        ClientWaypoints.update(waypoint.withEnabled(!waypoint.enabled()));
        reloadEntries();
    }

    private void toggleShareable(Waypoint waypoint) {
        ClientWaypoints.update(waypoint.withShareable(!waypoint.shareable()));
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
        graphics.fill(0, 0, width, height, 0xC0101010);
        graphics.drawString(font, title, 12, 12, 0xFFFFFFFF, true);

        if (entries.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("aleeve_atlas.screen.waypoint_list.empty"),
                    12, LIST_TOP, 0xFFAAAAAA, true);
        } else {
            for (int row = 0; row < visibleRows(); row++) {
                int index = scrollOffset + row;
                if (index >= entries.size()) {
                    break;
                }
                Waypoint waypoint = entries.get(index);
                int y = LIST_TOP + row * ROW_HEIGHT;
                int color = waypoint.enabled() ? waypoint.colorArgb() : 0xFF666666;
                graphics.fill(6, y, 10, y + 18, color);
                drawWaypointLabel(graphics, waypoint, y, mouseX, mouseY);
            }
        }
        graphics.drawString(font,
                Component.translatable("aleeve_atlas.screen.waypoint_list.scroll_hint"),
                12, height - 44, 0xFF888888, true);
        drawScrollbar(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /// 绘制标签，长文本在悬停时横向滚动并使用独立裁剪区。
    private void drawWaypointLabel(GuiGraphics graphics, Waypoint waypoint, int y,
            int mouseX, int mouseY) {
        int labelX = 14;
        int maxWidth = Math.max(0, buttonsX() - labelX - 6);
        if (maxWidth <= 0) {
            return;
        }
        Component line = Component.literal("%s  (%d, %d, %d)".formatted(
                        waypoint.name(), waypoint.pos().getX(), waypoint.pos().getY(),
                        waypoint.pos().getZ()))
                .withStyle(waypoint.enabled() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
        Component death = waypoint.deathPoint()
                ? Component.translatable("aleeve_atlas.screen.waypoint_list.death")
                : Component.empty();
        int deathWidth = waypoint.deathPoint() ? font.width(death) + 6 : 0;
        int textWidth = font.width(line) + deathWidth;
        boolean overflow = textWidth > maxWidth;
        boolean hovered = overflow && mouseX >= 12 && mouseX <= 12 + maxWidth
                && mouseY >= y && mouseY < y + ROW_HEIGHT;
        double offset = 0.0;
        if (hovered) {
            if (!waypoint.id().equals(hoveredLabel)) {
                hoveredLabel = waypoint.id();
                hoverStartedAt = System.currentTimeMillis();
            }
            offset = Math.min(textWidth - maxWidth,
                    (System.currentTimeMillis() - hoverStartedAt) / 50.0);
        } else if (waypoint.id().equals(hoveredLabel)) {
            hoveredLabel = null;
        }
        if (overflow) {
            graphics.enableScissor(12, y, 12 + maxWidth, y + 18);
        }
        int drawX = (int) Math.round(labelX - offset);
        graphics.drawString(font, line, drawX, y + 5, 0xFFFFFFFF, true);
        if (waypoint.deathPoint()) {
            graphics.drawString(font, death, drawX + font.width(line) + 6, y + 5,
                    0xFFFF5555, true);
        }
        if (overflow) {
            graphics.disableScissor();
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int x = width - SCROLLBAR_WIDTH - 4;
        int trackHeight = listHeight();
        graphics.fill(x, LIST_TOP, x + SCROLLBAR_WIDTH, LIST_TOP + trackHeight, 0xFF202020);
        int thumbHeight = scrollbarThumbHeight();
        int travel = Math.max(0, maxScrollOffset());
        int thumbY = LIST_TOP + (travel == 0 ? 0
                : Math.round((float) (trackHeight - thumbHeight) * scrollOffset / travel));
        graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFAAAAAA);
    }

    private int scrollbarThumbHeight() {
        if (entries.isEmpty()) {
            return listHeight();
        }
        return Math.min(listHeight(),
                Math.max(12, listHeight() * visibleRows() / entries.size()));
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return mouseX >= width - SCROLLBAR_WIDTH - 6 && mouseX <= width
                && mouseY >= LIST_TOP && mouseY <= LIST_TOP + listHeight();
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxOffset = maxScrollOffset();
        if (maxOffset <= 0) {
            scrollOffset = 0;
            return;
        }
        int thumbHeight = scrollbarThumbHeight();
        int travel = Math.max(1, listHeight() - thumbHeight);
        double ratio = (mouseY - LIST_TOP - thumbHeight / 2.0) / travel;
        int newOffset = (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * maxOffset);
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset;
            rebuildRows();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && overScrollbar(event.x(), event.y())) {
            scrollbarDragging = true;
            updateScrollFromMouse(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (scrollbarDragging) {
            updateScrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            int newOffset = Math.max(0, Math.min(maxScrollOffset(),
                    scrollOffset + (scrollY > 0 ? -1 : 1)));
            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                rebuildRows();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP - 56) / ROW_HEIGHT);
    }

    private int listHeight() {
        return visibleRows() * ROW_HEIGHT;
    }

    private int maxScrollOffset() {
        return Math.max(0, entries.size() - visibleRows());
    }

    /// 当前首行索引，供界面自检验证拖动结果。
    int scrollOffset() {
        return scrollOffset;
    }

    private int buttonWidth() {
        return Math.max(28, Math.min(52, (width - 102) / 5));
    }

    private int buttonsX() {
        return width - (buttonWidth() + 4) * 5 - 22;
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
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
