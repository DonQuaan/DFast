package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BenchSpecTest {
    @Test
    void presetsHaveFixedDefaults() {
        assertEquals(new BenchSpec("ci", BenchSpec.DEFAULT_SEED, 5, 10, 96, 140, 25), BenchSpec.parse("ci"));
        assertEquals(new BenchSpec("short", BenchSpec.DEFAULT_SEED, 10, 30, 96, 140, 25), BenchSpec.parse(" SHORT "));
        assertEquals(new BenchSpec("long", BenchSpec.DEFAULT_SEED, 20, 120, 96, 140, 25), BenchSpec.parse("long"));
    }

    @Test
    void overridesApplyOnTopOfThePreset() {
        BenchSpec spec = BenchSpec.parse("short, seed=-7 ,duration=60,radius=128.5,height=90,pitch=-10,warmup=3");
        assertEquals(new BenchSpec("short", -7L, 3, 60, 128.5, 90, -10), spec);
        assertEquals("dfast-bench--7", spec.worldName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "medium", "short,seed", "short,=5", "short,speed=3", "short,seed=1,seed=2",
            "short,duration=4", "short,duration=3601", "short,warmup=0", "short,radius=NaN", "short,pitch=91",
            "short,height=abc", "short,seed=1.5"})
    void rejectsInvalidSpecs(String text) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> BenchSpec.parse(text));
        assertTrue(e.getMessage() != null && !e.getMessage().isBlank());
    }
}
