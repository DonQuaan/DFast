package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JvmAdviceTest {
    private static final String COH = "-XX:+UseCompactObjectHeaders";
    private static final String UNLOCK = "-XX:+UnlockExperimentalVMOptions";
    private static final String GENERATIONAL = "-XX:+ZGenerational";

    @ParameterizedTest
    @ValueSource(ints = {21, 22, 23})
    void compactObjectHeadersNeverBeforeJava24(int feature) {
        assertFalse(JvmAdvice.forMachine(feature, 32).flags().contains(COH));
    }

    @ParameterizedTest
    @ValueSource(ints = {24, 25, 26})
    void compactObjectHeadersFromJava24To26(int feature) {
        assertTrue(JvmAdvice.forMachine(feature, 32).flags().contains(COH));
    }

    @Test
    void java24UnlocksExperimentalHeadersBeforeEnablingThem() {
        JvmAdvice advice = JvmAdvice.forMachine(24, 32);
        assertTrue(advice.flags().indexOf(UNLOCK) >= 0);
        assertTrue(advice.flags().indexOf(UNLOCK) < advice.flags().indexOf(COH));
    }

    @ParameterizedTest
    @ValueSource(ints = {25, 26, 27})
    void onlyJava24NeedsTheUnlockFlag(int feature) {
        assertFalse(JvmAdvice.forMachine(feature, 32).flags().contains(UNLOCK));
    }

    @Test
    void java27LeavesCompactObjectHeadersToTheDefault() {
        assertFalse(JvmAdvice.forMachine(27, 32).flags().contains(COH));
    }

    @ParameterizedTest
    @ValueSource(ints = {21, 22})
    void generationalFlagOnJava21And22(int feature) {
        assertTrue(JvmAdvice.forMachine(feature, 32).flags().contains(GENERATIONAL));
    }

    @ParameterizedTest
    @ValueSource(ints = {23, 24, 25, 26, 27})
    void noGenerationalFlagFromJava23(int feature) {
        assertFalse(JvmAdvice.forMachine(feature, 32).flags().contains(GENERATIONAL));
    }

    @Test
    void java25OnThirtyTwoGigMachine() {
        assertEquals("-Xms8G -Xmx8G -XX:+UseZGC -XX:+AlwaysPreTouch -XX:+UseCompactObjectHeaders",
                JvmAdvice.forMachine(25, 32).args());
    }

    @Test
    void eightGigMachineGetsGrowableG1Heap() {
        assertEquals("-Xmx4G -XX:+UseG1GC -XX:+UseCompactObjectHeaders", JvmAdvice.forMachine(25, 8).args());
    }

    @ParameterizedTest
    @CsvSource({"8,G1", "9,ZGC", "16,ZGC", "-1,G1"})
    void collectorThreshold(long ram, JvmAdvice.Collector expected) {
        assertEquals(expected, JvmAdvice.forMachine(25, ram).collector());
    }

    @ParameterizedTest
    @CsvSource({"-1,4", "2,1", "4,2", "6,3", "8,4", "9,4", "10,5", "12,5", "13,6", "16,6", "17,8", "32,8", "33,10", "47,10", "48,12", "256,12"})
    void heapTable(long ram, int expected) {
        assertEquals(expected, JvmAdvice.heapGiB(ram));
    }

    @Test
    void heapGrowsWithRamButNeverTakesMoreThanHalf() {
        int previous = JvmAdvice.heapGiB(4);
        for (long ram = 4; ram <= 256; ram++) {
            int heap = JvmAdvice.heapGiB(ram);
            assertTrue(heap >= previous, "heap shrank at " + ram + " GiB");
            assertTrue(heap * 2L <= ram, "heap exceeds half of RAM at " + ram + " GiB");
            previous = heap;
        }
    }

    @Test
    void rejectsJavaOlderThan21() {
        assertThrows(IllegalArgumentException.class, () -> JvmAdvice.forMachine(17, 16));
    }
}
