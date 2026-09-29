package com.donquaan.dfast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HardwareProfileTest {
    private static HardwareProfile withRam(long bytes) {
        return new HardwareProfile("test", 8, 1L << 30, bytes, 21);
    }

    @Test
    void readsPhysicalRamOnHotSpot() {
        assertTrue(HardwareProfile.readTotalRam() > 0);
        assertTrue(HardwareProfile.detect().ramGiB() > 0);
    }

    @Test
    void roundsToTheNearestGiB() {
        assertEquals(32, withRam(34_070_192_128L).ramGiB());
        assertEquals(8, withRam((long) (7.8 * (1L << 30))).ramGiB());
        assertEquals(16, withRam(16L << 30).ramGiB());
    }

    @Test
    void unknownRamStaysUnknown() {
        assertEquals(-1, withRam(0).ramGiB());
        assertEquals(-1, withRam(-1).ramGiB());
    }

    @Test
    void summaryIsMachineReadable() {
        assertEquals("os=test threads=8 ramGiB=32 heapMaxMiB=1024 java=21", withRam(32L << 30).summary());
    }
}
