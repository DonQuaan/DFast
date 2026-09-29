package com.donquaan.dfast.core;

public record WatchdogPolicy(long shutdownGraceNanos, long stallNanos, long deadlineNanos) {
    public enum Verdict { WAIT, SHUTDOWN_STUCK, STALLED, DEADLINE }

    public Verdict check(long nowNanos, long lastTickNanos, boolean done, long doneNanos) {
        if (done) {
            return nowNanos - doneNanos > shutdownGraceNanos ? Verdict.SHUTDOWN_STUCK : Verdict.WAIT;
        }
        if (nowNanos - lastTickNanos > stallNanos) {
            return Verdict.STALLED;
        }
        return nowNanos - deadlineNanos > 0L ? Verdict.DEADLINE : Verdict.WAIT;
    }
}
