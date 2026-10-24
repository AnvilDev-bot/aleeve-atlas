package dev.dubhe.atlas.client.radar;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import dev.dubhe.atlas.map.MapPalette;

import dev.dubhe.atlas.AtlasSession;
import dev.dubhe.atlas.client.map.MinimapLayout;
import dev.dubhe.atlas.config.AtlasClientConfig;
import dev.dubhe.atlas.config.MinimapShape;
import dev.dubhe.atlas.config.NonTeamVisibility;
import dev.dubhe.atlas.config.RadarDisplayMode;
import dev.dubhe.atlas.team.TeamProviders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/// 实体雷达的小地图标记渲染（规格 §6）。
///
/// 圆点模式下按类别配色绘制；混合联动模式下按住玩家列表键（默认 Tab）时，
/// 玩家标记切换为皮肤头像（规格 §6.1 / §6.3）。
public final class RadarRenderer {
    /// 标记半径（GUI 像素）。
    private static final int DOT_RADIUS = 2;
    private static final int HEAD_SIZE = 8;
    private static final int ICON_SIZE = 8;
    /// 掉落物合并粒度（像素），同格内只绘制一个圆点（§6.2）。
    private static final double ITEM_MERGE_PIXELS = 4.0;
    /// 皮肤未就绪时的占位纹理（§6.3）。
    private static final Identifier DEFAULT_SKIN_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private RadarRenderer() {
    }

    /// 在小地图上绘制雷达标记。
    public static void render(GuiGraphics graphics, AtlasClientConfig config,
            MinimapLayout layout, Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        RadarDisplayMode mode = config.radar.displayMode;
        boolean showIcons = mode == RadarDisplayMode.ICON;
        boolean hybrid = mode == RadarDisplayMode.HYBRID;
        boolean playerListDown = hybrid && minecraft.options.keyPlayerList.isDown();
        // 混合模式可配置为按住玩家列表键时展开全体实体图标（§6.1）。
        boolean expandAllIcons = showIcons
                || (playerListDown && config.radar.hybridExpandAllIcons);
        boolean circular = config.minimap.shape == MinimapShape.CIRCLE;
        // 本次绘制已占用的掉落物格位，用于密集合并（§6.2）。
        Set<Long> itemDots = new HashSet<>();

        // 服务端下发的最大雷达距离优先于客户端配置（规格 §4.2）。
        int maxDistance = AtlasSession.maxRadarDistance();
        double maxDistanceSq = (double) maxDistance * maxDistance;
        // 非同队玩家可见性策略由服务端下发（规格 §4.1）。
        NonTeamVisibility nonTeamPolicy = AtlasSession.nonTeamVisibility();

        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || entity.isRemoved()) {
                continue;
            }
            if (entity.distanceToSqr(player) > maxDistanceSq) {
                continue;
            }
            RadarCategory category = RadarCategory.classify(entity);
            if (category == null
                    || !RadarStyle.visible(config.radar.categories, category)) {
                continue;
            }
            // 前置逻辑剔除 + 可见性判定（规格 §4.2 第 1、5 条）。
            float opacity = 1.0F;
            if (category == RadarCategory.PLAYER && !RadarStyle.isTeammate(entity)) {
                if (nonTeamPolicy == NonTeamVisibility.NEVER) {
                    continue;
                }
                if (nonTeamPolicy == NonTeamVisibility.VISIBLE_ONLY) {
                    // CPU 前置剔除：隐身无护甲或位于视锥体外时直接判不可见（§4.2 第 1 条）。
                    if (VisibilityPreFilter.shouldCull(player, entity)) {
                        opacity = VisibilityFade.opacity(entity);
                        if (opacity <= 0.0F) {
                            continue;
                        }
                    } else if (VisibilityGate.isVisible(level, player, entity)) {
                        VisibilityFade.markSeen(entity);
                    } else {
                        // 被遮挡后按渐隐期衰减，避免标点高频闪烁（§4.2 第 4 条）。
                        opacity = VisibilityFade.opacity(entity);
                        if (opacity <= 0.0F) {
                            continue;
                        }
                    }
                }
            }
            double screenX = layout.worldToScreenX(entity.getX());
            double screenZ = layout.worldToScreenZ(entity.getZ());
            if (!layout.contains(screenX, screenZ, circular)) {
                continue;
            }
            if (category == RadarCategory.PLAYER
                    && shouldDrawPlayerHead(mode, playerListDown)) {
                drawPlayerHead(graphics, entity, screenX, screenZ);
                continue;
            }
            // 图标模式下生物使用刷怪蛋图标；混合联动模式下按住玩家列表键亦可展开（§6.1）。
            if (expandAllIcons && category != RadarCategory.ITEM
                    && drawEntityIcon(graphics, entity, screenX, screenZ)) {
                continue;
            }
            int color = applyOpacity(
                    RadarStyle.color(config.radar.categories, category, entity), opacity);
            // 敌对生物按高度差调整明暗（§6.2 附加视觉反馈）。
            if (category == RadarCategory.HOSTILE) {
                color = applyHeightShade(color, entity.getY() - player.getY());
            }
            // 掉落物密集合并：同格内已有标记时不再重复绘制（§6.2）。
            if (category == RadarCategory.ITEM
                    && !itemDots.add(packScreen(screenX, screenZ))) {
                continue;
            }
            drawDot(graphics, screenX, screenZ, color);
            // 同队玩家附带队伍颜色描边（§6.2）。
            if (category == RadarCategory.PLAYER && RadarStyle.isTeammate(entity)) {
                drawTeamOutline(graphics, screenX, screenZ, entity);
            }
        }
    }

    /// 高度差明暗：高于玩家偏亮、低于偏暗，幅度限制在 70%~130%。
    private static int applyHeightShade(int argb, double deltaY) {
        double ratio = 1.0 + Math.max(-0.3, Math.min(0.3, deltaY / 64.0));
        int percent = (int) Math.round(ratio * 100.0);
        return MapPalette.scale(argb, percent);
    }

    /// 同队玩家圆点外层描边为队伍颜色。
    private static void drawTeamOutline(GuiGraphics graphics, double screenX, double screenZ,
            Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        var team = TeamProviders.active().teamIdOf(entity.getUUID());
        int color = TeamProviders.active().teamColor(team);
        int x = (int) Math.round(screenX);
        int z = (int) Math.round(screenZ);
        int radius = DOT_RADIUS + 1;
        graphics.fill(x - radius, z - radius, x + radius, z - radius + 1, color);
        graphics.fill(x - radius, z + radius - 1, x + radius, z + radius, color);
        graphics.fill(x - radius, z - radius, x - radius + 1, z + radius, color);
        graphics.fill(x + radius - 1, z - radius, x + radius, z + radius, color);
    }

    /// 把屏幕坐标按 4 像素粒度打包，用于合并同格掉落物。
    private static long packScreen(double screenX, double screenZ) {
        long gx = Math.round(screenX / ITEM_MERGE_PIXELS);
        long gz = Math.round(screenZ / ITEM_MERGE_PIXELS);
        return (gx << 32) ^ (gz & 0xFFFFFFFFL);
    }

    /// 按不透明度缩放颜色的 alpha 通道。
    private static int applyOpacity(int argb, float opacity) {
        if (opacity >= 1.0F) {
            return argb;
        }
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, opacity));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static void drawDot(GuiGraphics graphics, double screenX, double screenZ, int color) {
        int x = (int) Math.round(screenX);
        int z = (int) Math.round(screenZ);
        graphics.fill(x - DOT_RADIUS, z - DOT_RADIUS, x + DOT_RADIUS, z + DOT_RADIUS, color);
        // 深色描边提高在浅色地形上的可辨识度（规格 §6.2 附加视觉反馈）。
        int outline = 0xC0000000;
        graphics.fill(x - DOT_RADIUS, z - DOT_RADIUS, x + DOT_RADIUS, z - DOT_RADIUS + 1, outline);
        graphics.fill(x - DOT_RADIUS, z + DOT_RADIUS - 1, x + DOT_RADIUS, z + DOT_RADIUS, outline);
    }

    /// 生物图标：用刷怪蛋物品图标表示实体类型；无对应刷怪蛋时返回 false 由调用方回退圆点。
    private static boolean drawEntityIcon(GuiGraphics graphics, Entity entity, double screenX,
            double screenZ) {
        SpawnEggItem egg =
                SpawnEggItem.byId(entity.getType());
        if (egg == null) {
            return false;
        }
        int x = (int) Math.round(screenX) - ICON_SIZE / 2;
        int z = (int) Math.round(screenZ) - ICON_SIZE / 2;
        graphics.renderItem(new ItemStack(egg), x, z);
        return true;
    }

    /// 是否以玩家头像渲染玩家标记（规格 §6.1）。
    ///
    /// 图标模式下恒为头像；混合联动模式下仅在按住玩家列表键时切换为头像。
    static boolean shouldDrawPlayerHead(RadarDisplayMode mode, boolean playerListDown) {
        return mode == RadarDisplayMode.ICON
                || (mode == RadarDisplayMode.HYBRID && playerListDown);
    }

    /// 玩家皮肤头像：基础层 + 帽子层（规格 §6.3）。
    ///
    /// 远端皮肤尚未完成异步加载时回退到默认 Steven/Alex 占位纹理，
    /// 避免绘制空白或抛错。
    private static void drawPlayerHead(GuiGraphics graphics, Entity entity, double screenX,
            double screenZ) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        Identifier skinTexture = resolveSkinTexture(player);
        int x = (int) Math.round(screenX) - HEAD_SIZE / 2;
        int z = (int) Math.round(screenZ) - HEAD_SIZE / 2;
        // 玩家皮肤纹理中头部正面区域为 (8, 8, 8, 8)，帽子层为 (40, 8, 8, 8)。
        graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, x, z, 8.0F, 8.0F,
                HEAD_SIZE, HEAD_SIZE, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, x, z, 40.0F, 8.0F,
                HEAD_SIZE, HEAD_SIZE, 8, 8, 64, 64);
    }

    /// 解析皮肤纹理；纹理不可用时返回默认皮肤（§6.3 离线/未加载降级）。
    private static Identifier resolveSkinTexture(AbstractClientPlayer player) {
        var texture = player.getSkin().body();
        if (texture != null && texture.texturePath() != null) {
            return texture.texturePath();
        }
        return DEFAULT_SKIN_TEXTURE;
    }
}
