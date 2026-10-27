package dev.dubhe.map.team;

import dev.anvilcraft.lib.v2.integration.Integration;
import dev.anvilcraft.lib.v2.integration.IntegrationType;
import dev.dubhe.map.AleeveAtlas;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// FTB Teams 队伍适配。
///
/// 通过 AnvilLib `@Integration` 声明的软依赖：仅当客户端同时安装 FTB Teams 且版本满足要求时，
/// AnvilLib 才会加载本类并调用 `applyClient()`；未安装时本类不被解析，因此不构成硬依赖。
///
/// 对 FTB Teams 的访问统一走反射隔离层，任何异常都回退到原版计分板实现，
/// 保证第三方 API 变动不会导致本模组崩溃。
@Integration(value = "ftbteams", version = "[2111.1.1,)",
        type = { IntegrationType.CLIENT, IntegrationType.DEDICATED_SERVER })
public final class FtbTeamsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/team/ftb");

    /// 双端通用注册：队伍适配层的选择由配置驱动，此处仅登记实现。
    public void apply() {
        TeamProviders.register(new FtbTeamsProvider());
        LOGGER.info("FTB Teams team provider registered");
    }

    /// FTB Teams 的队伍数据在客户端侧可用，因此在此触发一次选择刷新。
    public void applyClient() {
        TeamProviders.select(AleeveAtlas.CONFIG.team.provider);
    }

    /// FTB Teams 队伍适配实现。
    static final class FtbTeamsProvider implements TeamProvider {
        private static final TeamProvider FALLBACK = new VanillaTeamProvider();

        @Override
        public String id() {
            return "ftbteams";
        }

        @Override
        public String teamIdOf(UUID playerId) {
            String teamId = FtbTeamsApi.teamIdOf(playerId);
            return teamId != null ? teamId : FALLBACK.teamIdOf(playerId);
        }

        @Override
        public List<UUID> onlineMembersOf(String teamId) {
            List<UUID> members = FtbTeamsApi.membersOf(teamId);
            return members.isEmpty() ? FALLBACK.onlineMembersOf(teamId) : members;
        }

        @Override
        public boolean sameTeam(UUID first, UUID second) {
            String firstTeam = this.teamIdOf(first);
            String secondTeam = this.teamIdOf(second);
            if (firstTeam != null && secondTeam != null) {
                return firstTeam.equals(secondTeam);
            }
            return FALLBACK.sameTeam(first, second);
        }

        @Override
        public Component teamDisplayName(String teamId) {
            Component name = FtbTeamsApi.displayNameOf(teamId);
            return name != null ? name : FALLBACK.teamDisplayName(teamId);
        }

        @Override
        public int teamColor(String teamId) {
            int color = FtbTeamsApi.colorOf(teamId);
            return color != 0 ? color : FALLBACK.teamColor(teamId);
        }
    }

    /// FTB Teams API 反射隔离层。
    ///
    /// FTB Teams 的 API 类名与方法在版本间可能变化，故统一反射访问；
    /// 任一调用失败返回空值，由调用方回退原版实现。
    static final class FtbTeamsApi {
        private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";

        private FtbTeamsApi() {
        }

        static String teamIdOf(UUID playerId) {
            Object team = teamForPlayer(playerId);
            if (team == null) {
                return null;
            }
            Object shortName = invoke(team, "getShortName");
            if (shortName instanceof String name && !name.isBlank()) {
                return name;
            }
            Object uuid = invoke(team, "getId");
            return uuid == null ? null : uuid.toString();
        }

        static List<UUID> membersOf(String teamId) {
            Object team = teamByShortName(teamId);
            Object members = invoke(team, "getMembers");
            if (!(members instanceof Collection<?> collection)) {
                return List.of();
            }
            List<UUID> result = new ArrayList<>();
            for (Object member : collection) {
                if (member instanceof UUID uuid) {
                    result.add(uuid);
                }
            }
            return result;
        }

        static Component displayNameOf(String teamId) {
            Object team = teamByShortName(teamId);
            Object name = invoke(team, "getName");
            return name instanceof Component component ? component : null;
        }

        static int colorOf(String teamId) {
            Object team = teamByShortName(teamId);
            Object color = invoke(team, "getColor");
            if (color instanceof Integer rgb) {
                return 0xFF000000 | (rgb & 0xFFFFFF);
            }
            return 0;
        }

        private static Object teamForPlayer(UUID playerId) {
            Object api = apiInstance();
            if (api == null) {
                return null;
            }
            return unwrapOptional(invoke(api, "getTeamForPlayerID", new Class<?>[] { UUID.class },
                    playerId));
        }

        private static Object teamByShortName(String teamId) {
            Object api = apiInstance();
            if (api == null || teamId == null) {
                return null;
            }
            return unwrapOptional(invoke(api, "getTeamByShortName",
                    new Class<?>[] { String.class }, teamId));
        }

        private static Object apiInstance() {
            try {
                Class<?> type = Class.forName(API_CLASS);
                Object instance = invokeStatic(type, "getInstance");
                return instance != null ? instance : invokeStatic(type, "api");
            } catch (Throwable t) {
                LOGGER.debug("FTB Teams API unavailable: {}", t.toString());
                return null;
            }
        }

        private static Object unwrapOptional(Object value) {
            if (value instanceof Optional<?> optional) {
                return optional.orElse(null);
            }
            return value;
        }

        private static Object invoke(Object target, String name, Class<?>[] parameterTypes,
                Object... args) {
            if (target == null) {
                return null;
            }
            try {
                return target.getClass().getMethod(name, parameterTypes).invoke(target, args);
            } catch (Throwable t) {
                LOGGER.debug("FTB Teams method {} failed: {}", name, t.toString());
                return null;
            }
        }

        private static Object invoke(Object target, String name) {
            if (target == null) {
                return null;
            }
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (Throwable t) {
                LOGGER.debug("FTB Teams method {} failed: {}", name, t.toString());
                return null;
            }
        }

        private static Object invokeStatic(Class<?> type, String name) {
            try {
                return type.getMethod(name).invoke(null);
            } catch (Throwable t) {
                LOGGER.debug("FTB Teams static method {} failed: {}", name, t.toString());
                return null;
            }
        }
    }
}
