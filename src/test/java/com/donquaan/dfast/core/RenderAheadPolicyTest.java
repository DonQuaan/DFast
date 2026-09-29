package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RenderAheadPolicyTest {
    @Test
    void sodiumAlwaysOwnsRenderAhead() {
        assertEquals(RenderAheadPolicy.Owner.SODIUM, RenderAheadPolicy.owner(true, true));
        assertEquals(RenderAheadPolicy.Owner.SODIUM, RenderAheadPolicy.owner(true, false));
    }

    @Test
    void withoutSodiumTheLimiterIsOffUnlessRequested() {
        assertEquals(RenderAheadPolicy.Owner.OFF, RenderAheadPolicy.owner(false, false));
        assertEquals(RenderAheadPolicy.Owner.DFAST, RenderAheadPolicy.owner(false, true));
    }
}
