package dev.dubhe.atlas.team;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/// 队伍实现选择的单元测试。
///
/// 覆盖 `auto` 取最高优先级、`vanilla` 强制原版、外部 ID 精确匹配、未知值回退原版。
class TeamProvidersSelectTest {
    @Test
    void autoPrefersRegisteredProvider() {
        TeamProviders.register(new StubProvider("stub_auto"));
        TeamProviders.select("auto");
        Assertions.assertEquals("stub_auto", TeamProviders.active().id());
    }

    @Test
    void vanillaForcesBuiltInProvider() {
        TeamProviders.register(new StubProvider("stub_forced"));
        TeamProviders.select("vanilla");
        Assertions.assertEquals("vanilla", TeamProviders.active().id());
    }

    @Test
    void explicitIdMatchesRegisteredProvider() {
        TeamProviders.register(new StubProvider("stub_a"));
        TeamProviders.register(new StubProvider("stub_b"));
        TeamProviders.select("stub_a");
        Assertions.assertEquals("stub_a", TeamProviders.active().id());
    }

    @Test
    void unknownIdFallsBackToVanilla() {
        TeamProviders.select("no_such_provider");
        Assertions.assertEquals("vanilla", TeamProviders.active().id(), "未知实现必须回退原版");
    }

    @Test
    void blankConfigIsTreatedAsAuto() {
        TeamProviders.register(new StubProvider("stub_blank"));
        TeamProviders.select("");
        Assertions.assertEquals("stub_blank", TeamProviders.active().id());
        TeamProviders.select(null);
        Assertions.assertEquals("stub_blank", TeamProviders.active().id());
    }

    /// 最小外部队伍实现，仅用于验证选择逻辑。
    private record StubProvider(String id) implements TeamProvider {
        @Override
        public String teamIdOf(UUID playerId) {
            return null;
        }

        @Override
        public List<UUID> onlineMembersOf(String teamId) {
            return List.of();
        }

        @Override
        public boolean sameTeam(UUID first, UUID second) {
            return false;
        }

        @Override
        public Component teamDisplayName(String teamId) {
            return Component.empty();
        }

        @Override
        public int teamColor(String teamId) {
            return 0xFFFFFFFF;
        }
    }
}
