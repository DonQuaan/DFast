package com.donquaan.dfast.core;

import java.util.Arrays;

public final class FrameTimeStats {
    public static final class Summary {
        public int frames;
        public long spanNanos;
        public double averageFps;
        public double lastFrameMs;
        public double p99Ms;
        public double onePercentLowFps;
        public double pointOnePercentLowFps;
    }

    static final int SUB_BITS = 6;
    static final int SUB_BUCKETS = 1 << SUB_BITS;
    static final int EXACT_MICROS = SUB_BUCKETS * 2;
    static final int MAX_EXPONENT = 30;
    static final int BUCKETS = (MAX_EXPONENT - SUB_BITS) * SUB_BUCKETS + EXACT_MICROS;

    private final long windowNanos;
    private final long[] ends;
    private final long[] durations;
    private final int[] counts = new int[BUCKETS];
    private final long[] sums = new long[BUCKETS];
    private int tail;
    private int size;
    private long sumNanos;
    private long lastNanos;

    public FrameTimeStats(long windowNanos, int capacity) {
        if (windowNanos <= 0L) {
            throw new IllegalArgumentException("window must be positive, got " + windowNanos);
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, got " + capacity);
        }
        this.windowNanos = windowNanos;
        ends = new long[capacity];
        durations = new long[capacity];
    }

    static int bucket(long frameNanos) {
        long micros = frameNanos / 1_000L;
        if (micros < EXACT_MICROS) {
            return (int) Math.max(0L, micros);
        }
        int exponent = 63 - Long.numberOfLeadingZeros(micros);
        if (exponent > MAX_EXPONENT) {
            return BUCKETS - 1;
        }
        int shift = exponent - SUB_BITS;
        return shift * SUB_BUCKETS + (int) (micros >>> shift);
    }

    public void record(long endNanos, long frameNanos) {
        if (frameNanos <= 0L) {
            return;
        }
        long cutoff = endNanos - windowNanos;
        while (size > 0 && ends[tail] <= cutoff) {
            dropOldest();
        }
        if (size == ends.length) {
            dropOldest();
        }
        int slot = (tail + size) % ends.length;
        ends[slot] = endNanos;
        durations[slot] = frameNanos;
        int b = bucket(frameNanos);
        counts[b]++;
        sums[b] += frameNanos;
        sumNanos += frameNanos;
        size++;
        lastNanos = frameNanos;
    }

    public void reset() {
        Arrays.fill(counts, 0);
        Arrays.fill(sums, 0L);
        tail = 0;
        size = 0;
        sumNanos = 0L;
        lastNanos = 0L;
    }

    public int frames() {
        return size;
    }

    public void summarize(Summary out) {
        out.frames = size;
        out.lastFrameMs = lastNanos / 1_000_000.0;
        out.spanNanos = size == 0 ? 0L
                : Math.min(windowNanos, ends[(tail + size - 1) % ends.length] - (ends[tail] - durations[tail]));
        if (size == 0) {
            out.averageFps = 0.0;
            out.p99Ms = 0.0;
            out.onePercentLowFps = 0.0;
            out.pointOnePercentLowFps = 0.0;
            return;
        }
        out.averageFps = size * 1_000_000_000.0 / sumNanos;
        out.p99Ms = slowRankMeanNanos(size - FrameRecorder.nearestRank(size, 990) + 1) / 1_000_000.0;
        out.onePercentLowFps = slowestAverageFps(Math.max(1, size / 100));
        out.pointOnePercentLowFps = slowestAverageFps(Math.max(1, size / 1000));
    }

    private double slowRankMeanNanos(int rankFromSlowest) {
        int seen = 0;
        for (int b = BUCKETS - 1; b >= 0; b--) {
            seen += counts[b];
            if (counts[b] > 0 && seen >= rankFromSlowest) {
                return (double) sums[b] / counts[b];
            }
        }
        return 0.0;
    }

    private double slowestAverageFps(int count) {
        int remaining = count;
        double total = 0.0;
        for (int b = BUCKETS - 1; b >= 0 && remaining > 0; b--) {
            int c = counts[b];
            if (c <= remaining) {
                total += sums[b];
                remaining -= c;
            } else {
                total += (double) sums[b] * remaining / c;
                remaining = 0;
            }
        }
        return count * 1_000_000_000.0 / total;
    }

    private void dropOldest() {
        long frameNanos = durations[tail];
        int b = bucket(frameNanos);
        counts[b]--;
        sums[b] -= frameNanos;
        sumNanos -= frameNanos;
        tail = (tail + 1) % ends.length;
        size--;
    }
}
