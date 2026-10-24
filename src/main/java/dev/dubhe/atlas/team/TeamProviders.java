package dev.dubhe.atlas.team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 队伍适配层的实现注册与选择（规格 §3.1）。
///
/// 外部队伍模组（如 FTB Teams）可注册自己的 {@link TeamProvider}；
/// 选择顺序为：显式注册的优先实现 → 配置指定 → 原版计分板回退。
public final class TeamProviders {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/team");
    private static final List<TeamProvider> REGISTERED = new ArrayList<>();
    private static final TeamProvider VANILLA = new VanillaTeamProvider();
    private static TeamProvider active = VANILLA;

    private TeamProviders() {
    }

    /// 注册外部队伍实现（按注册顺序优先，越早注册优先级越高）。
    public static synchronized void register(TeamProvider provider) {
        if (provider == null || provider == VANILLA) {
            return;
        }
        REGISTERED.add(0, provider);
        LOGGER.info("Registered team provider: {}", provider.id());
        active = REGISTERED.get(0);
    }

    /// 按配置选择实现：`auto` 取最高优先级；`vanilla` 强制原版；
    /// 其他值匹配已注册实现的 `id()`，未匹配时回退原版。
    public static synchronized void select(String configured) {
        if (configured == null || configured.isBlank() || "auto".equalsIgnoreCase(configured)) {
            active = REGISTERED.isEmpty() ? VANILLA : REGISTERED.get(0);
            return;
        }
        if ("vanilla".equalsIgnoreCase(configured)) {
            active = VANILLA;
            return;
        }
        for (TeamProvider provider : REGISTERED) {
            if (provider.id().equalsIgnoreCase(configured)) {
                active = provider;
                return;
            }
        }
        LOGGER.warn("Team provider '{}' not available, falling back to vanilla", configured);
        active = VANILLA;
    }

    /// 当前生效的队伍实现。
    public static TeamProvider active() {
        return active;
    }

    /// 当前队伍适配层的诊断描述：实现名与指定玩家所属队伍（§3.1）。
    public static String describe(UUID playerId) {
        TeamProvider provider = active();
        String teamId = provider.teamIdOf(playerId);
        if (teamId == null) {
            return "team: provider=%s, player has no team".formatted(provider.id());
        }
        return "team: provider=%s, team=%s, color=#%06X".formatted(
                provider.id(), teamId, provider.teamColor(teamId) & 0xFFFFFF);
    }
}
