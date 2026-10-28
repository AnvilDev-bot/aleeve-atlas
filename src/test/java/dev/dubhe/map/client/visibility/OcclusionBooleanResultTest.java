package dev.dubhe.map.client.visibility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OcclusionBooleanResultTest {
    @Test
    void onePassingSampleIsVisibleAndPendingIsNot() {
        assertTrue(OcclusionProxyRenderer.passesThreshold(1));
        assertFalse(OcclusionProxyRenderer.passesThreshold(0));
        assertFalse(OcclusionProxyRenderer.passesThreshold(-1));
    }
}
