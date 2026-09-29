package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FrameTimeStatsTest {
    private static final long MS = 1_000_000L;

    @Test
    void constantFramesGiveExactAverage() {
        FrameTimeStats stats = new FrameTimeStats(128);
        for (int i = 0; i < 100; i++) {
            stats.record(10 * MS);
        }
        assertEquals(100.0, stats.averageFps(), 1e-9);
        assertEquals(10.0, stats.lastFrameMs(), 1e-9);
    }

    @Test
    void onePercentLowAveragesTheSlowestPercent() {
        FrameTimeStats stats = new FrameTimeStats(256);
        for (int i = 0; i < 198; i++) {
            stats.record(10 * MS);
        }
        stats.record(100 * MS);
        stats.record(50 * MS);
        assertEquals(1000.0 / 75.0, stats.onePercentLowFps(), 1e-9);
    }

    @Test
    void windowEvictsTheOldestFrames() {
        FrameTimeStats stats = new FrameTimeStats(4);
        for (int i = 0; i < 4; i++) {
            stats.record(10 * MS);
        }
        for (int i = 0; i < 4; i++) {
            stats.record(20 * MS);
        }
        assertEquals(4, stats.count());
        assertEquals(50.0, stats.averageFps(), 1e-9);
    }

    @Test
    void ignoresNonPositiveSamples() {
        FrameTimeStats stats = new FrameTimeStats(8);
        stats.record(0L);
        stats.record(-5L);
        assertEquals(0, stats.count());
        assertEquals(0.0, stats.averageFps());
        assertEquals(0.0, stats.onePercentLowFps());
    }

    @Test
    void rejectsEmptyWindow() {
        assertThrows(IllegalArgumentException.class, () -> new FrameTimeStats(0));
    }
}
