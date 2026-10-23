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

/// 小地图叠加层渲染管线（规格 §2.2）。
///
/// 圆形遮罩、圆形/方形边框与玩家朝向箭头全部由片元着色器解析式绘制，
/// 避免逐行填充几何体，符合规格 §12.1 的 HUD 帧耗要求。
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

    /// 叠加层管线：边框与玩家朝向箭头，由片元着色器解析式绘制。
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

    private AtlasRenderPipelines() {
    }

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(MINIMAP_CIRCLE);
        event.registerPipeline(MINIMAP_OVERLAY);
    }
}
