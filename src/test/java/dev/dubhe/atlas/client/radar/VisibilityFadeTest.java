package dev.dubhe.atlas.client.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 可见性渐隐的初始状态与确认可见行为测试。
class VisibilityFadeTest {
    @BeforeEach
    void clearFadeState() {
        VisibilityFade.clear();
    }

    @Test
    void neverSeenTargetIsFullyHidden() {
        assertEquals(0.0F, VisibilityFade.opacity(UUID.randomUUID()));
    }

    @Test
    void confirmedVisibleTargetStartsAtFullOpacity() {
        UUID target = UUID.randomUUID();
        VisibilityFade.markSeen(target);
        float opacity = VisibilityFade.opacity(target);
        assertTrue(opacity > 0.9F && opacity <= 1.0F, "opacity=" + opacity);
    }
}
