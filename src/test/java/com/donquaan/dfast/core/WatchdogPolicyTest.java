package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.donquaan.dfast.core.WatchdogPolicy.Verdict;
import org.junit.jupiter.api.Test;

class WatchdogPolicyTest {
    private static final long S = 1_000_000_000L;
    private final WatchdogPolicy policy = new WatchdogPolicy(120 * S, 180 * S, 1_000 * S);

    @Test
    void waitsWhileTicksKeepComing() {
        assertEquals(Verdict.WAIT, policy.check(500 * S, 499 * S, false, 0L));
    }

    @Test
    void stallsWhenNoTickForTooLong() {
        assertEquals(Verdict.WAIT, policy.check(380 * S, 200 * S, false, 0L));
        assertEquals(Verdict.STALLED, policy.check(381 * S, 200 * S, false, 0L));
    }

    @Test
    void hardDeadlineEndsARunThatKeepsTicking() {
        assertEquals(Verdict.WAIT, policy.check(1_000 * S, 999 * S, false, 0L));
        assertEquals(Verdict.DEADLINE, policy.check(1_001 * S, 1_000 * S, false, 0L));
    }

    @Test
    void afterTheBenchFinishedOnlyTheShutdownGraceCounts() {
        assertEquals(Verdict.WAIT, policy.check(5_000 * S, 10 * S, true, 4_900 * S));
        assertEquals(Verdict.SHUTDOWN_STUCK, policy.check(5_021 * S, 10 * S, true, 4_900 * S));
    }

    @Test
    void aFinishTimeOfZeroOrBelowIsStillAFinishTime() {
        assertEquals(Verdict.WAIT, policy.check(-5 * S, -500 * S, true, -10 * S));
        assertEquals(Verdict.WAIT, policy.check(100 * S, 0L, true, 0L));
        assertEquals(Verdict.SHUTDOWN_STUCK, policy.check(121 * S, 0L, true, 0L));
    }

    @Test
    void worksAcrossNanoTimeWrapAround() {
        long base = Long.MAX_VALUE - 10 * S;
        WatchdogPolicy wrapped = new WatchdogPolicy(120 * S, 180 * S, base + 100 * S);
        assertEquals(Verdict.WAIT, wrapped.check(base + 50 * S, base + 40 * S, false, 0L));
        assertEquals(Verdict.DEADLINE, wrapped.check(base + 101 * S, base + 100 * S, false, 0L));
    }
}
