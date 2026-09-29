package com.donquaan.dfast.core;

import java.util.Arrays;

public final class FrameTimeStats {
    private final long[] frames;
    private final long[] scratch;
    private int head;
    private int count;
    private long sumNanos;
    private long lastNanos;

    public FrameTimeStats(int window) {
        if (window < 1) {
            throw new IllegalArgumentException("window must be at least 1, got " + window);
        }
        frames = new long[window];
        scratch = new long[window];
    }

    public void record(long frameNanos) {
        if (frameNanos <= 0L) {
            return;
        }
        if (count == frames.length) {
            sumNanos -= frames[head];
        } else {
            count++;
        }
        frames[head] = frameNanos;
        sumNanos += frameNanos;
        head = (head + 1) % frames.length;
        lastNanos = frameNanos;
    }

    public int count() {
        return count;
    }

    public double lastFrameMs() {
        return lastNanos / 1_000_000.0;
    }

    public double averageFps() {
        return sumNanos > 0L ? count * 1_000_000_000.0 / sumNanos : 0.0;
    }

    public double onePercentLowFps() {
        if (count == 0) {
            return 0.0;
        }
        System.arraycopy(frames, 0, scratch, 0, count);
        Arrays.sort(scratch, 0, count);
        int worst = Math.max(1, count / 100);
        long worstSum = 0L;
        for (int i = count - worst; i < count; i++) {
            worstSum += scratch[i];
        }
        return worstSum > 0L ? worst * 1_000_000_000.0 / worstSum : 0.0;
    }
}
