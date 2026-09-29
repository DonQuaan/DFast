package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.donquaan.dfast.core.BenchEnvironment.Power;
import org.junit.jupiter.api.Test;

class BenchEnvironmentTest {
    @Test
    void classifiesPowerSources() {
        assertEquals(Power.NONE, BenchEnvironment.classify(0, 0));
        assertEquals(Power.AC, BenchEnvironment.classify(1, 1));
        assertEquals(Power.AC, BenchEnvironment.classify(2, 1));
        assertEquals(Power.BATTERY, BenchEnvironment.classify(1, 0));
        assertEquals(Power.UNKNOWN, BenchEnvironment.classify(-1, 0));
        assertEquals(Power.UNKNOWN, BenchEnvironment.classify(1, 2));
    }

    @Test
    void aChangeInPowerOrRefreshRateIsNotTheSameEnvironment() {
        BenchEnvironment start = new BenchEnvironment(Power.AC, 240);
        assertTrue(start.sameAs(new BenchEnvironment(Power.AC, 240)));
        assertFalse(start.sameAs(new BenchEnvironment(Power.AC, 60)));
        assertFalse(start.sameAs(new BenchEnvironment(Power.BATTERY, 240)));
        assertFalse(start.sameAs(null));
    }
}
