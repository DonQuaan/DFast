package com.donquaan.dfast.latency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.donquaan.dfast.core.FenceApi.WaitResult;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL32C;

class LwjglFencesTest {
    @Test
    void mapsEveryGlClientWaitSyncResult() {
        assertEquals(WaitResult.SIGNALED, LwjglFences.classify(GL32C.GL_ALREADY_SIGNALED));
        assertEquals(WaitResult.SIGNALED, LwjglFences.classify(GL32C.GL_CONDITION_SATISFIED));
        assertEquals(WaitResult.TIMEOUT, LwjglFences.classify(GL32C.GL_TIMEOUT_EXPIRED));
        assertEquals(WaitResult.FAILED, LwjglFences.classify(GL32C.GL_WAIT_FAILED));
        assertEquals(WaitResult.FAILED, LwjglFences.classify(0));
    }
}
