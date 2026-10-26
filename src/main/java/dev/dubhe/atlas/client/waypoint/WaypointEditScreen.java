package dev.dubhe.atlas.client.waypoint;

import dev.dubhe.atlas.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/// 路径点创建与编辑表单。
///
/// 内容高度不足时可滚动，保存按钮固定在底部；窗口调整大小后保留未保存的输入。
public final class WaypointEditScreen extends Screen {
    private static final int FORM_WIDTH = 300;
    private static final int CONTENT_HEIGHT = 204;
    private static final int CONTENT_TOP = 20;
    private static final int MAX_VISIBLE_DISTANCE = 100000;
    private static final int[] PALETTE = {
            0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55,
            0x55FFFF, 0x5555FF, 0xFF55FF, 0xFFFFFF
    };

    private final Screen parent;
    private final Waypoint original;
    private final BlockPos initialPos;
    private final List<Button> paletteButtons = new ArrayList<>();

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox colorBox;
    private EditBox distanceBox;
    private Checkbox enabledBox;
    private Checkbox shareableBox;
    private Button saveButton;
    private Component errorMessage;
    private int previewColor;
    private int formTop;
    private int formScroll;

    private String nameValue;
    private String xValue;
    private String yValue;
    private String zValue;
    private String colorValue;
    private String distanceValue;
    private Boolean enabledValue;
    private Boolean shareableValue;

    /// 创建路径点，坐标由地图点击位置或玩家当前位置提供。
    public WaypointEditScreen(Screen parent, BlockPos initialPos) {
        super(Component.translatable("aleeve_atlas.screen.waypoint_edit.create_title"));
        this.parent = parent;
        this.original = null;
        this.initialPos = initialPos;
        this.previewColor = 0xFF55FFFF;
    }

    /// 编辑已有路径点，保留 UUID、维度、图标与来源信息。
    public WaypointEditScreen(Screen parent, Waypoint waypoint) {
        super(Component.translatable("aleeve_atlas.screen.waypoint_edit.edit_title"));
        this.parent = parent;
        this.original = waypoint;
        this.initialPos = waypoint.pos();
        this.previewColor = waypoint.colorArgb();
    }

    @Override
    protected void init() {
        captureValues();
        paletteButtons.clear();
        int left = width / 2 - FORM_WIDTH / 2;
        Waypoint values = original;
        String name = nameValue != null ? nameValue : values == null
                ? Component.translatable("aleeve_atlas.waypoint.default_name").getString()
                : values.name();
        int color = values == null ? 0xFF55FFFF : values.colorArgb();
        int visibleDistance = values == null ? Waypoint.DEFAULT_VISIBLE_DISTANCE
                : values.visibleDistance();

        nameBox = addRenderableWidget(new EditBox(font, left, 0, FORM_WIDTH, 20,
                Component.translatable("aleeve_atlas.screen.waypoint_edit.name")));
        nameBox.setMaxLength(64);
        nameBox.setValue(name);

        int coordinateWidth = 92;
        xBox = coordinateBox(left, coordinateWidth,
                xValue != null ? xValue : Integer.toString(initialPos.getX()), "X");
        yBox = coordinateBox(left + 104, coordinateWidth,
                yValue != null ? yValue : Integer.toString(initialPos.getY()), "Y");
        zBox = coordinateBox(left + 208, coordinateWidth,
                zValue != null ? zValue : Integer.toString(initialPos.getZ()), "Z");

        colorBox = addRenderableWidget(new EditBox(font, left, 0, 140, 20,
                Component.translatable("aleeve_atlas.screen.waypoint_edit.color")));
        colorBox.setMaxLength(7);
        colorBox.setValue(colorValue != null ? colorValue : formatColor(color));

        for (int paletteColor : PALETTE) {
            String hex = formatColor(paletteColor);
            Button button = Button.builder(
                            Component.literal("■").withStyle(style -> style.withColor(paletteColor)),
                            pressed -> colorBox.setValue(hex))
                    .bounds(0, 0, 24, 20)
                    .tooltip(Tooltip.create(Component.literal(hex)))
                    .build();
            paletteButtons.add(addRenderableWidget(button));
        }

        distanceBox = addRenderableWidget(new EditBox(font, left, 0, 140, 20,
                Component.translatable("aleeve_atlas.screen.waypoint_edit.visible_distance")));
        distanceBox.setMaxLength(6);
        distanceBox.setFilter(value -> value.chars().allMatch(Character::isDigit));
        distanceBox.setValue(distanceValue != null ? distanceValue
                : Integer.toString(visibleDistance));

        enabledBox = addRenderableWidget(Checkbox.builder(
                        Component.translatable("aleeve_atlas.screen.waypoint_edit.enabled"), font)
                .pos(left, 0)
                .selected(enabledValue != null ? enabledValue : values == null || values.enabled())
                .build());
        shareableBox = addRenderableWidget(Checkbox.builder(
                        Component.translatable("aleeve_atlas.screen.waypoint_edit.shareable"), font)
                .pos(left + 150, 0)
                .selected(shareableValue != null ? shareableValue
                        : values == null || values.shareable())
                .build());

        saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("aleeve_atlas.screen.waypoint_edit.save"),
                        button -> save())
                .bounds(width / 2 - 105, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                        button -> onClose())
                .bounds(width / 2 + 5, height - 28, 100, 20).build());

        nameBox.setResponder(value -> validateForm());
        xBox.setResponder(value -> validateForm());
        yBox.setResponder(value -> validateForm());
        zBox.setResponder(value -> validateForm());
        colorBox.setResponder(value -> validateForm());
        distanceBox.setResponder(value -> validateForm());
        validateForm();
        layoutForm();
    }

    /// 保存 resize 前的输入，避免 init 重建控件时丢失修改。
    private void captureValues() {
        if (nameBox == null) {
            return;
        }
        nameValue = nameBox.getValue();
        xValue = xBox.getValue();
        yValue = yBox.getValue();
        zValue = zBox.getValue();
        colorValue = colorBox.getValue();
        distanceValue = distanceBox.getValue();
        enabledValue = enabledBox.selected();
        shareableValue = shareableBox.selected();
    }

    private EditBox coordinateBox(int x, int width, String value, String label) {
        EditBox box = addRenderableWidget(new EditBox(font, x, 0, width, 20,
                Component.literal(label)));
        box.setMaxLength(10);
        box.setValue(value);
        return box;
    }

    /// 按滚动位置布局内容，视口外的控件不参与渲染和点击。
    private void layoutForm() {
        int maxScroll = Math.max(0, CONTENT_HEIGHT - (height - 52));
        formScroll = Math.min(formScroll, maxScroll);
        formTop = 4 - formScroll;
        int contentBottom = height - 52;
        int left = width / 2 - FORM_WIDTH / 2;
        setPosition(nameBox, formTop + 26, contentBottom);
        setPosition(xBox, formTop + 60, contentBottom);
        setPosition(yBox, formTop + 60, contentBottom);
        setPosition(zBox, formTop + 60, contentBottom);
        setPosition(colorBox, formTop + 94, contentBottom);
        for (int i = 0; i < paletteButtons.size(); i++) {
            Button button = paletteButtons.get(i);
            button.setX(left + i * 28);
            setPosition(button, formTop + 118, contentBottom);
        }
        setPosition(distanceBox, formTop + 152, contentBottom);
        setPosition(enabledBox, formTop + 176, contentBottom);
        setPosition(shareableBox, formTop + 176, contentBottom);
    }

    private void setPosition(AbstractWidget widget, int y, int contentBottom) {
        widget.setY(y);
        widget.visible = y >= CONTENT_TOP && y + widget.getHeight() <= contentBottom;
    }

    /// 校验并保存；新建走 add，编辑走 update，以复用现有同步策略。
    private void save() {
        FormValues values = readForm();
        if (values == null) {
            validateForm();
            return;
        }
        Waypoint base = original == null
                ? Waypoint.create(values.name(), values.pos(), ClientWaypoints.dimension(),
                        values.colorArgb())
                : original;
        Waypoint result = base.withDetails(values.name(), values.pos(), values.colorArgb(),
                values.visibleDistance(), values.enabled(), values.shareable());
        Minecraft minecraft = Minecraft.getInstance();
        if (original == null) {
            ClientWaypoints.add(result);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "aleeve_atlas.message.waypoint_created", result.pos().getX(),
                        result.pos().getY(), result.pos().getZ()), true);
            }
        } else {
            ClientWaypoints.update(result);
        }
        minecraft.setScreen(parent);
    }

    private void validateForm() {
        FormValues values = readForm();
        if (saveButton != null) {
            saveButton.active = values != null;
        }
    }

    /// 读取表单并记录首个校验错误，供按钮禁用与错误文本共用。
    private FormValues readForm() {
        errorMessage = null;
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            errorMessage = Component.translatable(
                    "aleeve_atlas.screen.waypoint_edit.name_required");
            return null;
        }
        int x;
        int y;
        int z;
        int visibleDistance;
        try {
            x = Integer.parseInt(xBox.getValue().trim());
            y = Integer.parseInt(yBox.getValue().trim());
            z = Integer.parseInt(zBox.getValue().trim());
            visibleDistance = Integer.parseInt(distanceBox.getValue().trim());
        } catch (NumberFormatException e) {
            errorMessage = Component.translatable(
                    "aleeve_atlas.screen.waypoint_edit.invalid_number");
            return null;
        }
        if (visibleDistance < 0 || visibleDistance > MAX_VISIBLE_DISTANCE) {
            errorMessage = Component.translatable(
                    "aleeve_atlas.screen.waypoint_edit.invalid_distance",
                    MAX_VISIBLE_DISTANCE);
            return null;
        }
        int color;
        try {
            color = parseColor(colorBox.getValue());
        } catch (IllegalArgumentException e) {
            errorMessage = Component.translatable(
                    "aleeve_atlas.screen.waypoint_edit.invalid_color");
            return null;
        }
        previewColor = color;
        return new FormValues(name, new BlockPos(x, y, z), color, visibleDistance,
                enabledBox.selected(), shareableBox.selected());
    }

    /// 解析 `#RRGGBB`，统一补全不透明 alpha。
    static int parseColor(String raw) {
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6 || !value.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Invalid waypoint color");
        }
        return 0xFF000000 | Integer.parseInt(value, 16);
    }

    static String formatColor(int colorArgb) {
        return "#%06X".formatted(colorArgb & 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        int left = width / 2 - FORM_WIDTH / 2;
        graphics.drawString(font, title, left, 8, 0xFFFFFFFF, true);
        drawLabel(graphics, Component.translatable("aleeve_atlas.screen.waypoint_edit.name"),
                left, formTop + 16);
        drawLabel(graphics, Component.translatable("aleeve_atlas.screen.waypoint_edit.coordinates"),
                left, formTop + 50);
        drawLabel(graphics, Component.translatable("aleeve_atlas.screen.waypoint_edit.color"),
                left, formTop + 84);
        if (formTop + 96 >= CONTENT_TOP && formTop + 112 <= height - 52) {
            graphics.fill(left + 146, formTop + 96, left + 164, formTop + 112, previewColor);
        }
        drawLabel(graphics,
                Component.translatable("aleeve_atlas.screen.waypoint_edit.visible_distance"),
                left, formTop + 142);
        if (errorMessage != null) {
            graphics.drawCenteredString(font, errorMessage, width / 2, height - 44, 0xFFFF5555);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawLabel(GuiGraphics graphics, Component text, int x, int y) {
        if (y >= CONTENT_TOP && y + 9 <= height - 52) {
            graphics.drawString(font, text, x, y, 0xFFAAAAAA, true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            int maxScroll = Math.max(0, CONTENT_HEIGHT - (height - 52));
            int newScroll = Math.max(0, Math.min(maxScroll,
                    formScroll + (scrollY > 0 ? -12 : 12)));
            if (newScroll != formScroll) {
                formScroll = newScroll;
                layoutForm();
            }
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
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record FormValues(String name, BlockPos pos, int colorArgb, int visibleDistance,
            boolean enabled, boolean shareable) {
    }
}
