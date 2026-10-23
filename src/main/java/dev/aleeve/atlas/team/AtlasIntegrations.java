package dev.aleeve.atlas.team;

import dev.aleeve.atlas.AleeveAtlas;
import dev.anvilcraft.lib.v2.integration.IntegrationManager;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 第三方模组集成初始化（规格 §3.1 / §12.2）。
///
/// 由 AnvilLib `IntegrationManager` 扫描 `@Integration` 注解类：仅当目标模组存在且版本匹配时才加载，
/// 因此本模组始终不硬依赖 FTB Teams、Ping Wheel 等可选模组。
public final class AtlasIntegrations {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/integration");
    private static IntegrationManager manager;

    private AtlasIntegrations() {
    }

    /// 在 `@Mod` 构造期调用：编译集成清单并按环境加载。
    public static void initialize() {
        if (manager != null) {
            return;
        }
        manager = new IntegrationManager(AleeveAtlas.MOD_ID);
        manager.compileContent();
        if (FMLEnvironment.getDist().isClient()) {
            manager.loadAllClientIntegrations();
        }
        manager.loadAllIntegrations();
        LOGGER.debug("Integration scan completed");
    }
}
