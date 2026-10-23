package dev.dubhe.atlas.config;

import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import net.neoforged.fml.config.ModConfig;

/// 客户端配置（规格 §9.1，`aleeve_atlas-client.toml`）。
///
/// 本类只在物理客户端加载：实例由 {@link AtlasClientConfigs#CONFIG} 持有，
/// 专用服务端不会解析本类。
@Config(name = "aleeve_atlas", type = ModConfig.Type.CLIENT)
public class AtlasClientConfig {
    @CollapsibleObject
    @Comment("Minimap settings.")
    public Minimap minimap = new Minimap();

    @CollapsibleObject
    @Comment("Entity radar settings.")
    public Radar radar = new Radar();

    @CollapsibleObject
    @Comment("Waypoint settings.")
    public Waypoint waypoint = new Waypoint();

    @CollapsibleObject
    @Comment("Territory overlay settings.")
    public Territory territory = new Territory();

    @CollapsibleObject
    @Comment("Map ping settings.")
    public Ping ping = new Ping();

    public static class Minimap {
        @Comment("Whether the minimap HUD is enabled.")
        public boolean enabled = true;

        @Comment("Minimap outline shape.")
        public MinimapShape shape = MinimapShape.CIRCLE;

        @Comment("Minimap zoom multiplier (0.5 ~ 4.0).")
        public double scale = 1.0;

        @Comment("Minimap screen corner.")
        public MinimapPosition position = MinimapPosition.TOP_RIGHT;

        @Comment("Custom X offset in GUI-scaled pixels, used when position is CUSTOM.")
        public int offsetX = 0;

        @Comment("Custom Y offset in GUI-scaled pixels, used when position is CUSTOM.")
        public int offsetY = 0;

        @Comment("Keep north fixed up instead of rotating with the camera.")
        public boolean lockNorth = false;

        @Comment("Show coordinates below the minimap.")
        public boolean showCoords = true;

        @Comment("Show the current biome name below the minimap.")
        public boolean showBiome = true;

        @Comment("Show the current light level below the minimap.")
        public boolean showLight = false;

        @Comment("Show the current in-game time below the minimap.")
        public boolean showTime = false;
    }

    public static class Radar {
        @Comment("Entity radar display mode.")
        public RadarDisplayMode displayMode = RadarDisplayMode.HYBRID;

        @Comment("Fade-out grace period in seconds for occluded markers (0.5 ~ 1.5).")
        public double fadeOutSeconds = 1.0;

        @Comment("In hybrid mode, also expand icons for all entity types while the player list key is held.")
        public boolean hybridExpandAllIcons = false;

        @CollapsibleObject
        @Comment("Per-category radar settings.")
        public Categories categories = new Categories();

        public static class Categories {
            @Comment("Show players on the radar.")
            public boolean showPlayers = true;

            @Comment("Team player dot color as a hex string.")
            public String teamPlayerColor = "#55FF55";

            @Comment("Non-team player dot color as a hex string.")
            public String otherPlayerColor = "#FFFFFF";

            @Comment("Show hostile mobs on the radar.")
            public boolean showHostiles = true;

            @Comment("Hostile mob dot color as a hex string.")
            public String hostileColor = "#FF5555";

            @Comment("Show friendly mobs on the radar.")
            public boolean showFriendly = true;

            @Comment("Friendly mob dot color as a hex string.")
            public String friendlyColor = "#7CFC00";

            @Comment("Show tiny ambient mobs such as bats on the radar.")
            public boolean showAmbient = false;

            @Comment("Ambient mob dot color as a hex string.")
            public String ambientColor = "#B0B0B0";

            @Comment("Show dropped items on the radar.")
            public boolean showItems = true;

            @Comment("Dropped item dot color as a hex string.")
            public String itemColor = "#FF55FF";

            @Comment("Show miscellaneous entities on the radar.")
            public boolean showMisc = false;

            @Comment("Miscellaneous entity dot color as a hex string.")
            public String miscColor = "#9B59B6";
        }
    }

    public static class Waypoint {
        @Comment("Send policy for newly created waypoints.")
        public WaypointSendPolicy sendPolicy = WaypointSendPolicy.MANUAL_SYNC;

        @Comment("Receive policy for waypoints shared by teammates.")
        public WaypointReceivePolicy receivePolicy = WaypointReceivePolicy.MANUAL_ACCEPT;

        @Comment("Keep historical death waypoints.")
        public boolean keepDeathPoints = true;
    }

    public static class Territory {
        @Comment("Render territory overlays on the minimap.")
        public boolean showOnMinimap = true;

        @Comment("Render territory names on the minimap.")
        public boolean showLabelOnMinimap = false;

        @Comment("Centroid attraction weight used by the weighted anchor algorithm (0.3 ~ 0.5).")
        public double anchorWeight = 0.4;
    }

    public static class Ping {
        @Comment("Ping Wheel channel reuse strategy.")
        public PingChannelMode channelMode = PingChannelMode.AUTO;
    }
}
