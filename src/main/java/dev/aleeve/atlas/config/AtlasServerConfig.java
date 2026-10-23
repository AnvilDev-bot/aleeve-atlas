package dev.aleeve.atlas.config;

import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import net.neoforged.fml.config.ModConfig;

/// 服务端配置（规格 §9.2，`aleeve_atlas-server.toml`）。
///
/// 使用 NeoForge `ModConfig.Type.SERVER`：自动同步到客户端并按 `world/serverconfig` 允许存档覆盖，
/// 因此 §4.1 的连接时锁定可直接复用该机制。实例由 {@code AleeveAtlas.CONFIG} 持有。
@Config(name = "aleeve_atlas", type = ModConfig.Type.SERVER)
public class AtlasServerConfig {
    @CollapsibleObject
    @Comment("Server-side map synchronization settings.")
    public Sync sync = new Sync();

    @CollapsibleObject
    @Comment("Server-side radar settings.")
    public Radar radar = new Radar();

    @CollapsibleObject
    @Comment("Team provider settings.")
    public Team team = new Team();

    public static class Sync {
        @Comment("Enable synchronized mode features.")
        public boolean enabled = true;

        @Comment("Interval in ticks between live map delta pushes.")
        @BoundedDiscrete(min = 10, max = 200)
        public int mapDeltaIntervalTicks = 40;

        @Comment("Bandwidth cap in bytes per second for map delta sync.")
        @BoundedDiscrete(min = 8192, max = 1048576)
        public int mapDeltaMaxBytesPerSecond = 65536;
    }

    public static class Radar {
        @Comment("Visibility policy applied to non-team player markers.")
        public NonTeamVisibility nonTeamPlayerVisibility = NonTeamVisibility.VISIBLE_ONLY;

        @Comment("Maximum radar detection distance in blocks.")
        @BoundedDiscrete(min = 16, max = 512)
        public int maxRadarDistance = 128;
    }

    public static class Team {
        @Comment("Team provider implementation: auto, vanilla, or an external mod id.")
        public String provider = "auto";
    }
}
