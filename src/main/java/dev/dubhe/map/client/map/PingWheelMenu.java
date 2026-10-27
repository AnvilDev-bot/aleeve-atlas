package dev.dubhe.map.client.map;

import dev.anvilcraft.lib.v2.wheel.api.WheelMenuBuilder;
import dev.anvilcraft.lib.v2.wheel.api.WheelMenuModel;
import dev.anvilcraft.lib.v2.wheel.api.WheelOpenMode;
import dev.anvilcraft.lib.v2.wheel.client.gui.screen.WheelScreen;
import dev.dubhe.map.ping.MapPingData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/// 在世界地图上显示 AnvilLib 轮盘，选择和取消后返回同一地图。
final class PingWheelMenu extends WheelScreen {
    private final WorldMapScreen parent;
    private boolean closing;

    private PingWheelMenu(WorldMapScreen parent) {
        super(model(parent), WheelOpenMode.HOLD);
        this.parent = parent;
    }

    static void open(WorldMapScreen parent) {
        Minecraft.getInstance().setScreen(new PingWheelMenu(parent));
    }

    private static WheelMenuModel model(WorldMapScreen parent) {
        WheelMenuBuilder builder = WheelMenuBuilder.create().slotsPerPage(4);
        for (MapPingData.PingIcon icon : MapPingData.PingIcon.values()) {
            builder.action(icon.name(), icon.displayName(), (graphics, pose, width, height) -> {
                Component label = icon.displayName();
                var font = Minecraft.getInstance().font;
                graphics.drawString(font, label, -font.width(label) / 2,
                        -font.lineHeight / 2, 0xFFFFFFFF, true);
            }, context -> parent.createWheelPing(icon));
        }
        return builder.build();
    }

    @Override
    protected void init() {
        if (this.parent.width != this.width || this.parent.height != this.height) {
            this.parent.resize(this.width, this.height);
        }
        super.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.parent.render(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (!this.closing) {
                this.triggerFromHoldRelease();
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.onClose();
        return true;
    }

    @Override
    public void onClose() {
        if (!this.closing) {
            this.closing = true;
            Minecraft.getInstance().setScreen(this.parent);
        }
    }

    @Override
    public void removed() {
        this.closing = true;
        this.parent.finishWheel();
        super.removed();
    }
}
