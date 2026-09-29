package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FrameTimeStatsTest {
    private static final long MS = 1_000_000L;
    private static final long SECOND = 1_000_000_000L;

    private final FrameTimeStats.Summary summary = new FrameTimeStats.Summary();

    private static long feed(FrameTimeStats stats, long start, long frameNanos, int count) {
        long now = start;
        for (int i = 0; i < count; i++) {
            now += frameNanos;
            stats.record(now, frameNanos);
        }
        return now;
    }

    @Test
    void constantFramesGiveExactNumbers() {
        FrameTimeStats stats = new FrameTimeStats(5 * SECOND, 4096);
        feed(stats, 0L, 10 * MS, 100);
        stats.summarize(summary);
        assertEquals(100, summary.frames);
        assertEquals(100.0, summary.averageFps, 1e-9);
        assertEquals(10.0, summary.lastFrameMs, 1e-9);
        assertEquals(10.0, summary.p99Ms, 1e-9);
        assertEquals(100.0, summary.onePercentLowFps, 1e-9);
        assertEquals(100.0, summary.pointOnePercentLowFps, 1e-9);
    }

    @Test
    void slowFramesDriveTheTailMetrics() {
        FrameTimeStats stats = new FrameTimeStats(60 * SECOND, 4096);
        long now = feed(stats, 0L, 10 * MS, 1990);
        now = feed(stats, now, 50 * MS, 9);
        feed(stats, now, 100 * MS, 1);
        stats.summarize(summary);
        assertEquals(2000, summary.frames);
        assertEquals(10.0, summary.p99Ms, 1e-9);
        assertEquals(20 * 1000.0 / (9 * 50 + 100 + 10 * 10), summary.onePercentLowFps, 1e-9);
        assertEquals(2 * 1000.0 / (100 + 50), summary.pointOnePercentLowFps, 1e-9);
    }

    @Test
    void p99UsesTheNearestRank() {
        FrameTimeStats stats = new FrameTimeStats(60 * SECOND, 4096);
        long now = feed(stats, 0L, 10 * MS, 98);
        feed(stats, now, 30 * MS, 2);
        stats.summarize(summary);
        assertEquals(30.0, summary.p99Ms, 1e-9);
    }

    @Test
    void framesOlderThanTheWindowAreEvicted() {
        FrameTimeStats stats = new FrameTimeStats(SECOND, 4096);
        long now = feed(stats, 0L, 100 * MS, 10);
        feed(stats, now, 50 * MS, 20);
        stats.summarize(summary);
        assertEquals(20, summary.frames);
        assertEquals(20.0, summary.averageFps, 1e-9);
    }

    @Test
    void aLongHitchInsideTheWindowIsKept() {
        FrameTimeStats stats = new FrameTimeStats(5 * SECOND, 4096);
        long now = feed(stats, 0L, 10 * MS, 50);
        now += 2 * SECOND;
        stats.record(now, 2 * SECOND);
        stats.summarize(summary);
        assertEquals(51, summary.frames);
        assertEquals(0.5, summary.pointOnePercentLowFps, 1e-9);
    }

    @Test
    void fullRingDropsTheOldestFrame() {
        FrameTimeStats stats = new FrameTimeStats(60 * SECOND, 4);
        long now = feed(stats, 0L, 10 * MS, 4);
        feed(stats, now, 20 * MS, 2);
        stats.summarize(summary);
        assertEquals(4, summary.frames);
        assertEquals(4 * 1000.0 / 60.0, summary.averageFps, 1e-9);
    }

    @Test
    void resetClearsEverything() {
        FrameTimeStats stats = new FrameTimeStats(5 * SECOND, 64);
        feed(stats, 0L, 10 * MS, 10);
        stats.reset();
        stats.summarize(summary);
        assertEquals(0, summary.frames);
        assertEquals(0.0, summary.averageFps);
        assertEquals(0.0, summary.lastFrameMs);
    }

    @Test
    void ignoresNonPositiveDurations() {
        FrameTimeStats stats = new FrameTimeStats(5 * SECOND, 64);
        stats.record(10 * MS, 0L);
        stats.record(20 * MS, -1L);
        assertEquals(0, stats.frames());
    }

    @Test
    void bucketsAreContiguousAndMonotonic() {
        int previous = FrameTimeStats.bucket(0L);
        assertEquals(0, previous);
        for (long micros = 1; micros < (1L << 21); micros++) {
            int current = FrameTimeStats.bucket(micros * 1_000L);
            int step = current - previous;
            assertTrue(step == 0 || step == 1, "bucket jump at " + micros + "us: " + previous + " -> " + current);
            previous = current;
        }
        assertEquals(FrameTimeStats.BUCKETS - 1, FrameTimeStats.bucket(((1L << 31) - 1) * 1_000L));
        assertEquals(FrameTimeStats.BUCKETS - 1, FrameTimeStats.bucket((1L << 31) * 1_000L));
        assertEquals(FrameTimeStats.BUCKETS - 1, FrameTimeStats.bucket(Long.MAX_VALUE));
    }

    @Test
    void bucketWidthStaysWithinOneSixtyFourth() {
        for (long micros = FrameTimeStats.EXACT_MICROS; micros < (1L << 20); micros = micros * 3 / 2 + 7) {
            int b = FrameTimeStats.bucket(micros * 1_000L);
            long low = micros;
            while (low > 0 && FrameTimeStats.bucket((low - 1) * 1_000L) == b) {
                low--;
            }
            long high = micros;
            while (FrameTimeStats.bucket((high + 1) * 1_000L) == b) {
                high++;
            }
            assertTrue((double) (high + 1 - low) / low <= 1.0 / 64.0, "bucket " + b + " spans " + low + ".." + high);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 7L, 42L, 2026L})
    void tailMetricsMatchExactSortWithinBucketPrecision(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        int n = 20_000;
        long[] frames = new long[n];
        FrameTimeStats stats = new FrameTimeStats(3_600 * SECOND, n);
        long now = 0L;
        for (int i = 0; i < n; i++) {
            long base = 300_000L + random.nextLong(16 * MS);
            frames[i] = random.nextInt(200) == 0 ? base + random.nextLong(250 * MS) : base;
            now += frames[i];
            stats.record(now, frames[i]);
        }
        stats.summarize(summary);
        long[] sorted = frames.clone();
        Arrays.sort(sorted);
        double exactP99 = sorted[(int) Math.ceil(0.99 * n) - 1] / 1e6;
        assertEquals(exactP99, summary.p99Ms, exactP99 / 64.0);
        double exactLow = exactSlowestFps(sorted, n / 100);
        assertEquals(exactLow, summary.onePercentLowFps, exactLow / 64.0);
        double exactPointLow = exactSlowestFps(sorted, n / 1000);
        assertEquals(exactPointLow, summary.pointOnePercentLowFps, exactPointLow / 64.0);
        assertEquals(n * 1e9 / Arrays.stream(frames).sum(), summary.averageFps, 1e-6);
    }

    private static double exactSlowestFps(long[] sorted, int count) {
        long total = 0L;
        for (int i = sorted.length - count; i < sorted.length; i++) {
            total += sorted[i];
        }
        return count * 1e9 / total;
    }

    @Test
    void evictionKeepsTheHistogramConsistent() {
        FrameTimeStats stats = new FrameTimeStats(SECOND, 1 << 12);
        long now = feed(stats, 0L, 200 * MS, 5);
        now = feed(stats, now, 5 * MS, 400);
        stats.summarize(summary);
        assertEquals(200, summary.frames);
        assertEquals(5.0, summary.p99Ms, 1e-9);
        assertEquals(200.0, summary.onePercentLowFps, 1e-9);
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new FrameTimeStats(0L, 10));
        assertThrows(IllegalArgumentException.class, () -> new FrameTimeStats(SECOND, 0));
    }
}
