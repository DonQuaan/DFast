package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameRecorderTest {
    private static final long MS = 1_000_000L;

    @Test
    void knownDistributionGivesExactPercentiles() {
        FrameRecorder recorder = new FrameRecorder(4096);
        for (int i = 1; i <= 1000; i++) {
            recorder.add(i * MS / 100);
        }
        FrameRecorder.Result r = recorder.result();
        assertEquals(1000, r.frames());
        assertEquals(5.005, r.seconds(), 1e-9);
        assertEquals(1000 / 5.005, r.averageFps(), 1e-9);
        assertEquals(5.00, r.p50Ms(), 1e-9);
        assertEquals(9.00, r.p90Ms(), 1e-9);
        assertEquals(9.90, r.p99Ms(), 1e-9);
        assertEquals(9.99, r.p999Ms(), 1e-9);
        assertEquals(10.00, r.maxMs(), 1e-9);
        assertEquals(10 * 1000.0 / (9.91 + 9.92 + 9.93 + 9.94 + 9.95 + 9.96 + 9.97 + 9.98 + 9.99 + 10.0), r.onePercentLowFps(), 1e-9);
        assertEquals(100.0, r.pointOnePercentLowFps(), 1e-9);
        assertFalse(r.overflowed());
    }

    @Test
    void nearestRankUsesExactIntegerMath() {
        assertEquals(1, FrameRecorder.nearestRank(1, 990));
        assertEquals(99, FrameRecorder.nearestRank(100, 990));
        assertEquals(100, FrameRecorder.nearestRank(101, 990));
        assertEquals(999, FrameRecorder.nearestRank(1000, 999));
        assertEquals(1000, FrameRecorder.nearestRank(1001, 999));
        assertEquals(50, FrameRecorder.nearestRank(100, 500));
        assertEquals(1, FrameRecorder.nearestRank(0, 990));
        for (int n = 1; n < 5000; n++) {
            for (int perMille : new int[] {500, 900, 990, 999}) {
                long rank = FrameRecorder.nearestRank(n, perMille);
                assertTrue(rank * 1000L >= (long) perMille * n && (rank - 1) * 1000L < (long) perMille * n,
                        "n=" + n + " perMille=" + perMille + " rank=" + rank);
            }
        }
    }

    @Test
    void keepsRecordingOrder() {
        FrameRecorder recorder = new FrameRecorder(8);
        recorder.add(3L);
        recorder.add(1L);
        recorder.add(0L);
        recorder.add(2L);
        assertArrayEquals(new long[] {3L, 1L, 2L}, recorder.frames());
        assertEquals(3, recorder.size());
    }

    @Test
    void overflowIsReportedNotHidden() {
        FrameRecorder recorder = new FrameRecorder(2);
        recorder.add(MS);
        recorder.add(MS);
        recorder.add(50 * MS);
        FrameRecorder.Result r = recorder.result();
        assertEquals(2, r.frames());
        assertEquals(1.0, r.maxMs(), 1e-9);
        assertTrue(r.overflowed());
    }

    @Test
    void emptyRecorderReportsZeros() {
        FrameRecorder.Result r = new FrameRecorder(1).result();
        assertEquals(0, r.frames());
        assertEquals(0.0, r.averageFps());
    }

    @Test
    void rejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new FrameRecorder(0));
    }
}
