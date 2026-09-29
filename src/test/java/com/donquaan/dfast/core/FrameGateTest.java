package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.donquaan.dfast.core.FrameGate.Event;
import org.junit.jupiter.api.Test;

class FrameGateTest {
    private final Object world = new Object();
    private final FrameGate gate = new FrameGate();

    @Test
    void firstFrameInAScopeOnlyResets() {
        assertEquals(Event.RESET, gate.next(100L, world, true));
        assertEquals(Event.FRAME, gate.next(116L, world, true));
        assertEquals(16L, gate.frameNanos());
    }

    @Test
    void theGapWhileNotMeasuringIsNeverAFrame() {
        gate.next(0L, world, true);
        gate.next(10L, world, true);
        assertEquals(Event.SKIP, gate.next(20L, world, false));
        assertEquals(Event.SKIP, gate.next(5_000L, world, false));
        assertEquals(Event.SKIP, gate.next(9_000L, world, true));
        assertEquals(Event.FRAME, gate.next(9_012L, world, true));
        assertEquals(12L, gate.frameNanos());
    }

    @Test
    void scopeChangeResetsEvenWhenNotMeasuring() {
        gate.next(0L, world, true);
        assertEquals(Event.RESET, gate.next(10L, null, false));
        assertEquals(Event.SKIP, gate.next(20L, null, false));
        assertEquals(Event.RESET, gate.next(30L, world, true));
        assertEquals(Event.FRAME, gate.next(47L, world, true));
        assertEquals(17L, gate.frameNanos());
    }

    @Test
    void longHitchesWhileMeasuringAreKept() {
        gate.next(0L, world, true);
        assertEquals(Event.FRAME, gate.next(3_000_000_000L, world, true));
        assertEquals(3_000_000_000L, gate.frameNanos());
    }

    @Test
    void scopeIsComparedByIdentity() {
        String a = new String("level");
        String b = new String("level");
        gate.next(0L, a, true);
        assertEquals(Event.RESET, gate.next(5L, b, true));
    }
}
