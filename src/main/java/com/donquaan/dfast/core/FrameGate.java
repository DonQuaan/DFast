package com.donquaan.dfast.core;

public final class FrameGate {
    public enum Event { SKIP, RESET, FRAME }

    private Object scope;
    private boolean armed;
    private long lastNanos;
    private long frameNanos;

    public Event next(long nowNanos, Object currentScope, boolean measuring) {
        if (currentScope != scope) {
            scope = currentScope;
            arm(nowNanos, measuring);
            return Event.RESET;
        }
        if (!measuring || !armed) {
            arm(nowNanos, measuring);
            return Event.SKIP;
        }
        frameNanos = nowNanos - lastNanos;
        lastNanos = nowNanos;
        return Event.FRAME;
    }

    public long frameNanos() {
        return frameNanos;
    }

    private void arm(long nowNanos, boolean measuring) {
        armed = measuring;
        lastNanos = nowNanos;
    }
}
