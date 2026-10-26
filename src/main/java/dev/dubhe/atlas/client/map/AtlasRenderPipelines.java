package dev.dubhe.atlas.client.map;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dubhe.atlas.AleeveAtlas;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/// 小地图叠加层渲染管线。
///
/// 圆形遮罩、圆形/方形边框与水滴形玩家指示器全部由片元着色器解析式绘制，
/// 避免逐行填充几何体，满足 HUD 帧耗要求。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasRenderPipelines {
    /// 地图纹理的圆形裁剪管线：圆外直接丢弃像素，使圆形小地图之外保持游戏画面原样。
    public static final RenderPipeline MINIMAP_CIRCLE = RenderPipeline
            .builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(AleeveAtlas.id("pipeline/minimap_circle"))
            .withVertexShader(AleeveAtlas.id("core/minimap_overlay"))
            .withFragmentShader(AleeveAtlas.id("core/minimap_circle"))
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    /// 叠加层管线：边框与水滴形玩家指示器，由片元着色器解析式绘制。
    public static final RenderPipeline MINIMAP_OVERLAY = RenderPipeline
            .builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(AleeveAtlas.id("pipeline/minimap_overlay"))
            .withVertexShader(AleeveAtlas.id("core/minimap_overlay"))
            .withFragmentShader(AleeveAtlas.id("core/minimap_overlay"))
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    /// 实体圆点标记管线。
    public static final RenderPipeline MAP_MARKER_DOT = markerPipeline(
            "map_marker_dot", "core/map_marker_dot");

    /// 光滑菱形标记管线。
    public static final RenderPipeline MAP_MARKER_DIAMOND = markerPipeline(
            "map_marker_diamond", "core/map_marker_diamond");

    /// 地图边缘方向箭头管线。
    public static final RenderPipeline MAP_MARKER_ARROW = markerPipeline(
            "map_marker_arrow", "core/map_marker_arrow");

    private static RenderPipeline markerPipeline(String name, String fragmentShader) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(AleeveAtlas.id("pipeline/" + name))
                .withVertexShader(AleeveAtlas.id("core/minimap_overlay"))
                .withFragmentShader(AleeveAtlas.id(fragmentShader))
                .withSampler("Sampler0")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build();
    }

    private AtlasRenderPipelines() {
    }

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(MINIMAP_CIRCLE);
        event.registerPipeline(MINIMAP_OVERLAY);
        event.registerPipeline(MAP_MARKER_DOT);
        event.registerPipeline(MAP_MARKER_DIAMOND);
        event.registerPipeline(MAP_MARKER_ARROW);
    }
}
