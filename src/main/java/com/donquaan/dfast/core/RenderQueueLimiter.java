package com.donquaan.dfast.core;

import java.util.function.Consumer;

public final class RenderQueueLimiter {
    static final long WAIT_TIMEOUT_NANOS = 250_000_000L;
    static final int MAX_CONSECUTIVE_TIMEOUTS = 8;

    private final FenceApi fences;
    private final Consumer<String> warn;
    private final int maxInFlight;
    private final long[] ring;
    private int head;
    private int size;
    private boolean active = true;
    private int timeoutStreak;
    private long lastWaitNanos;

    public RenderQueueLimiter(FenceApi fences, int maxInFlight, Consumer<String> warn) {
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("maxInFlight must be at least 1, got " + maxInFlight);
        }
        this.fences = fences;
        this.warn = warn;
        this.maxInFlight = maxInFlight;
        this.ring = new long[maxInFlight + 1];
    }

    public void beforeFrame() {
        long start = System.nanoTime();
        while (active && size >= maxInFlight) {
            long sync = ring[head];
            FenceApi.WaitResult result = fences.await(sync, WAIT_TIMEOUT_NANOS);
            if (result == FenceApi.WaitResult.FAILED) {
                shutdown("glClientWaitSync failed");
                break;
            }
            if (result == FenceApi.WaitResult.TIMEOUT) {
                onTimeout();
                break;
            }
            timeoutStreak = 0;
            fences.delete(sync);
            head = (head + 1) % ring.length;
            size--;
        }
        lastWaitNanos = System.nanoTime() - start;
    }

    public void afterFrame() {
        if (!active) {
            return;
        }
        long sync = fences.insert();
        if (sync == 0L) {
            shutdown("glFenceSync returned no fence");
            return;
        }
        if (size == ring.length) {
            fences.delete(sync);
            return;
        }
        ring[(head + size) % ring.length] = sync;
        size++;
    }

    public void close() {
        active = false;
        while (size > 0) {
            fences.delete(ring[head]);
            head = (head + 1) % ring.length;
            size--;
        }
    }

    public boolean active() {
        return active;
    }

    public long lastWaitNanos() {
        return lastWaitNanos;
    }

    int outstanding() {
        return size;
    }

    private void onTimeout() {
        timeoutStreak++;
        if (timeoutStreak == 1) {
            warn.accept("render queue limiter: GPU fence not signalled within " + WAIT_TIMEOUT_NANOS / 1_000_000 + " ms");
        }
        if (timeoutStreak >= MAX_CONSECUTIVE_TIMEOUTS) {
            shutdown(MAX_CONSECUTIVE_TIMEOUTS + " consecutive fence timeouts");
        }
    }

    private void shutdown(String reason) {
        close();
        warn.accept("render queue limiter disabled: " + reason);
    }
}
