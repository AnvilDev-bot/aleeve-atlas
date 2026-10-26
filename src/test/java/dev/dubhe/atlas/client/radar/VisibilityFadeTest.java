package dev.dubhe.atlas.client.radar;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/// 可见性渐隐的初始状态与确认可见行为测试。
class VisibilityFadeTest {
    @BeforeEach
    void clearFadeState() {
        VisibilityFade.clear();
    }

    @Test
    void neverSeenTargetIsFullyHidden() {
        Assertions.assertEquals(0.0F, VisibilityFade.opacity(UUID.randomUUID()));
    }

    @Test
    void confirmedVisibleTargetStartsAtFullOpacity() {
        UUID target = UUID.randomUUID();
        VisibilityFade.markSeen(target);
        float opacity = VisibilityFade.opacity(target);
        Assertions.assertTrue(opacity > 0.9F && opacity <= 1.0F, "opacity=" + opacity);
    }
}
