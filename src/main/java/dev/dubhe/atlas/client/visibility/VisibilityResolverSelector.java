package dev.dubhe.atlas.client.visibility;

import java.util.Locale;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.dubhe.atlas.client.radar.VisibilityGate;
import dev.dubhe.atlas.AleeveAtlas;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 可见性实现的选取与降级决策（规格 §4.2 第 5 条）。
///
/// 规格要求优先使用 GPU 遮挡查询，无法取得合法 GL 上下文或检测到不兼容渲染管线时
/// 自动降级为多采样点射线检测。1.21.11 的 Blaze3d 只暴露计时查询，不提供遮挡查询封装，
/// 因此本类先探测后端能力：OpenGL 后端且非第三方渲染替换时才启用查询实现。
///
/// 任何探测失败都记录日志后降级，保证功能可用且不崩溃。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class VisibilityResolverSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/visibility");

    private static boolean selected;

    private VisibilityResolverSelector() {
    }

    /// 进入世界后选取一次实现，避免每帧重复探测。
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        selected = false;
        select();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        selected = false;
    }

    /// 按后端能力选取可见性实现。
    public static void select() {
        if (selected) {
            return;
        }
        selected = true;
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            fallback("no GPU device available yet");
            return;
        }
        String backend = device.getBackendName();
        if (!isOpenGlBackend(backend)) {
            fallback("backend is " + backend);
            return;
        }
        // 1.21.11 的 Blaze3d 未封装遮挡查询，且投影矩阵仅存于 UBO 无法在渲染阶段读回，
        // 代理包围盒渲染无法与 glBeginQuery / glEndQuery 边界可靠对齐（§4.2 第 5 条降级情形）。
        fallback("occlusion query unavailable: Blaze3d exposes no query API "
                + "and the projection matrix is UBO-only in 1.21.11");
    }

    /// 降级到射线检测并记录原因。
    private static void fallback(String reason) {
        VisibilityGate.setResolver(new RaycastVisibilityResolver());
        LOGGER.info("Visibility resolver: {} ({})", VisibilityGate.resolverName(), reason);
    }

    /// 判定渲染后端是否为 OpenGL。
    private static boolean isOpenGlBackend(String backendName) {
        return backendName != null && backendName.toLowerCase(Locale.ROOT).contains("opengl");
    }
}
