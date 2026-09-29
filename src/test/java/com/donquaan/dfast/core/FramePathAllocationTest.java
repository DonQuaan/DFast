package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

class FramePathAllocationTest {
    private static final int FRAMES = 1_000_000;
    private static final long BUDGET_BYTES = 4_096L;

    private final Object level = new Object();
    private final FrameGate gate = new FrameGate();
    private final FrameTimeStats shortStats = new FrameTimeStats(5_000_000_000L, 1 << 15);
    private final FrameTimeStats longStats = new FrameTimeStats(60_000_000_000L, 1 << 18);
    private final FrameTimeStats.Summary summary = new FrameTimeStats.Summary();
    private long now;

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }

    private void frames(int count) {
        for (int i = 0; i < count; i++) {
            now += 900_000L + (i % 7) * 100_000L;
            if (gate.next(now, level, true) == FrameGate.Event.FRAME) {
                shortStats.record(now, gate.frameNanos());
                longStats.record(now, gate.frameNanos());
            }
            if (i % 250 == 0) {
                shortStats.summarize(summary);
                longStats.summarize(summary);
            }
        }
    }

    @Test
    void perFramePathDoesNotAllocate() {
        frames(FRAMES);
        long before = allocatedBytes();
        frames(FRAMES);
        long allocated = allocatedBytes() - before;
        assertTrue(allocated < BUDGET_BYTES, "allocated " + allocated + " bytes over " + FRAMES + " frames");
        assertEquals(50_000, longStats.frames());
    }
}
