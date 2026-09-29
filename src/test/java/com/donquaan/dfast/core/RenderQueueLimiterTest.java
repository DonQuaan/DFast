package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RenderQueueLimiterTest {
    private static final class FakeFences implements FenceApi {
        final Deque<WaitResult> scripted = new ArrayDeque<>();
        final List<Long> waited = new ArrayList<>();
        final List<Long> timeouts = new ArrayList<>();
        final Set<Long> live = new HashSet<>();
        boolean failInsert;
        long awaitSleepMillis;
        long next = 1;

        @Override
        public long insert() {
            if (failInsert) {
                return 0L;
            }
            long sync = next++;
            live.add(sync);
            return sync;
        }

        @Override
        public WaitResult await(long sync, long timeoutNanos) {
            waited.add(sync);
            timeouts.add(timeoutNanos);
            if (awaitSleepMillis > 0) {
                try {
                    Thread.sleep(awaitSleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return scripted.isEmpty() ? WaitResult.SIGNALED : scripted.poll();
        }

        @Override
        public void delete(long sync) {
            assertTrue(live.remove(sync), "fence deleted twice or never created: " + sync);
        }
    }

    private final FakeFences fences = new FakeFences();
    private final List<String> warnings = new ArrayList<>();

    private RenderQueueLimiter limiter(int maxInFlight) {
        return new RenderQueueLimiter(fences, maxInFlight, warnings::add);
    }

    private static void frame(RenderQueueLimiter limiter) {
        limiter.beforeFrame();
        limiter.afterFrame();
    }

    @Test
    void oneFrameInFlightWaitsForThePreviousFrame() {
        RenderQueueLimiter limiter = limiter(1);
        frame(limiter);
        frame(limiter);
        assertEquals(List.of(1L), fences.waited);
        assertEquals(List.of(RenderQueueLimiter.WAIT_TIMEOUT_NANOS), fences.timeouts);
        assertEquals(Set.of(2L), fences.live);
    }

    @Test
    void twoFramesInFlightWaitOnlyForTheOldest() {
        RenderQueueLimiter limiter = limiter(2);
        frame(limiter);
        frame(limiter);
        frame(limiter);
        assertEquals(List.of(1L), fences.waited);
        assertEquals(Set.of(2L, 3L), fences.live);
    }

    @Test
    void waitTimeIsMeasuredOnlyWhenTheLimiterBlocks() {
        RenderQueueLimiter limiter = limiter(1);
        fences.awaitSleepMillis = 5;
        limiter.beforeFrame();
        assertTrue(limiter.lastWaitNanos() < 5_000_000L);
        limiter.afterFrame();
        limiter.beforeFrame();
        assertTrue(limiter.lastWaitNanos() >= 5_000_000L);
    }

    @Test
    void timeoutKeepsTheFenceAndWarnsOnce() {
        RenderQueueLimiter limiter = limiter(1);
        frame(limiter);
        fences.scripted.add(FenceApi.WaitResult.TIMEOUT);
        limiter.beforeFrame();
        assertEquals(1, limiter.outstanding());
        assertTrue(fences.live.contains(1L));
        assertEquals(1, warnings.size());
        limiter.afterFrame();
        limiter.beforeFrame();
        assertEquals(0, limiter.outstanding());
        assertTrue(limiter.active());
    }

    @Test
    void consecutiveTimeoutsShutTheLimiterDown() {
        RenderQueueLimiter limiter = limiter(1);
        frame(limiter);
        for (int i = 0; i < RenderQueueLimiter.MAX_CONSECUTIVE_TIMEOUTS; i++) {
            fences.scripted.add(FenceApi.WaitResult.TIMEOUT);
            frame(limiter);
        }
        assertFalse(limiter.active());
        assertTrue(fences.live.isEmpty());
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(1).contains("consecutive fence timeouts"));
    }

    @Test
    void signalResetsTheTimeoutStreak() {
        RenderQueueLimiter limiter = limiter(1);
        frame(limiter);
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < RenderQueueLimiter.MAX_CONSECUTIVE_TIMEOUTS - 1; i++) {
                fences.scripted.add(FenceApi.WaitResult.TIMEOUT);
                frame(limiter);
            }
            fences.scripted.add(FenceApi.WaitResult.SIGNALED);
            frame(limiter);
        }
        assertTrue(limiter.active());
    }

    @Test
    void fullRingDropsTheNewestFenceInsteadOfOverwriting() {
        RenderQueueLimiter limiter = limiter(1);
        frame(limiter);
        fences.scripted.add(FenceApi.WaitResult.TIMEOUT);
        frame(limiter);
        fences.scripted.add(FenceApi.WaitResult.TIMEOUT);
        frame(limiter);
        assertEquals(2, limiter.outstanding());
        assertEquals(Set.of(1L, 2L), fences.live);
    }

    @Test
    void waitFailureShutsDownAndReleasesEveryFence() {
        RenderQueueLimiter limiter = limiter(2);
        frame(limiter);
        frame(limiter);
        fences.scripted.add(FenceApi.WaitResult.FAILED);
        frame(limiter);
        assertFalse(limiter.active());
        assertTrue(fences.live.isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("glClientWaitSync"));
    }

    @Test
    void missingFenceShutsDown() {
        RenderQueueLimiter limiter = limiter(1);
        fences.failInsert = true;
        frame(limiter);
        assertFalse(limiter.active());
        assertTrue(warnings.get(0).contains("glFenceSync"));
    }

    @Test
    void closeReleasesFencesAfterTheRingWrapped() {
        RenderQueueLimiter limiter = limiter(2);
        for (int i = 0; i < 5; i++) {
            frame(limiter);
        }
        limiter.close();
        assertTrue(fences.live.isEmpty());
        assertEquals(0, limiter.outstanding());
        frame(limiter);
        assertTrue(fences.live.isEmpty());
    }

    @Test
    void rejectsZeroFramesInFlight() {
        assertThrows(IllegalArgumentException.class, () -> limiter(0));
    }
}
