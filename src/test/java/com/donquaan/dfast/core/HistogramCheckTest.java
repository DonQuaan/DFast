package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class HistogramCheckTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void matchingStreamsPass() {
        SplittableRandom random = new SplittableRandom(99);
        long[] frames = new long[5000];
        FrameTimeStats stats = new FrameTimeStats(3600 * SECOND, 8192);
        long now = 0L;
        for (int i = 0; i < frames.length; i++) {
            frames[i] = 500_000L + random.nextLong(20_000_000L);
            now += frames[i];
            stats.record(now, frames[i]);
        }
        HistogramCheck check = HistogramCheck.of(stats, frames);
        assertEquals(5000, check.frames());
        assertEquals(4, check.metrics().size());
        assertTrue(check.passed(), "max error " + check.maxRelativeError());
    }

    @Test
    void comparesOnlyTheFramesStillInTheWindow() {
        long[] frames = {50_000_000L, 50_000_000L, 1_000_000L, 1_000_000L, 1_000_000L};
        FrameTimeStats stats = new FrameTimeStats(2_500_000L, 64);
        long now = 0L;
        for (long frame : frames) {
            now += frame;
            stats.record(now, frame);
        }
        HistogramCheck check = HistogramCheck.of(stats, frames);
        assertEquals(3, check.frames());
        assertTrue(check.passed());
    }

    @Test
    void divergingStreamsFail() {
        FrameTimeStats stats = new FrameTimeStats(SECOND, 64);
        stats.record(10_000_000L, 10_000_000L);
        HistogramCheck check = HistogramCheck.of(stats, new long[] {20_000_000L});
        assertFalse(check.passed());
        assertEquals(1.0, check.maxRelativeError(), 1e-9);
    }

    @Test
    void emptyCheckFails() {
        HistogramCheck check = HistogramCheck.of(new FrameTimeStats(SECOND, 4), new long[0]);
        assertEquals(0, check.frames());
        assertFalse(check.passed());
    }
}
