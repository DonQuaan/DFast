package com.donquaan.dfast.core;

import java.util.Arrays;

public final class FrameRecorder {
    public record Result(int frames, double seconds, double averageFps, double p50Ms, double p90Ms, double p99Ms,
                         double p999Ms, double maxMs, double onePercentLowFps, double pointOnePercentLowFps,
                         boolean overflowed) {
    }

    private final long[] frames;
    private int size;
    private boolean overflowed;

    public FrameRecorder(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, got " + capacity);
        }
        frames = new long[capacity];
    }

    public void add(long frameNanos) {
        if (frameNanos <= 0L) {
            return;
        }
        if (size == frames.length) {
            overflowed = true;
            return;
        }
        frames[size++] = frameNanos;
    }

    public int size() {
        return size;
    }

    public long[] frames() {
        return Arrays.copyOf(frames, size);
    }

    public Result result() {
        if (size == 0) {
            return new Result(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, overflowed);
        }
        long[] sorted = frames();
        Arrays.sort(sorted);
        long total = 0L;
        for (long frame : sorted) {
            total += frame;
        }
        return new Result(size, total / 1e9, size * 1e9 / total,
                rankMs(sorted, 500), rankMs(sorted, 900), rankMs(sorted, 990), rankMs(sorted, 999),
                sorted[size - 1] / 1e6, slowestFps(sorted, size / 100), slowestFps(sorted, size / 1000), overflowed);
    }

    static int nearestRank(int n, int perMille) {
        return (int) Math.max(1L, ((long) perMille * n + 999L) / 1000L);
    }

    private static double rankMs(long[] sorted, int perMille) {
        return sorted[nearestRank(sorted.length, perMille) - 1] / 1e6;
    }

    private static double slowestFps(long[] sorted, int count) {
        int n = Math.max(1, count);
        long total = 0L;
        for (int i = sorted.length - n; i < sorted.length; i++) {
            total += sorted[i];
        }
        return n * 1e9 / total;
    }
}
