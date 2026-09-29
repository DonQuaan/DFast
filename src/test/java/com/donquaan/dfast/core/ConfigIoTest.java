package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigIoTest {
    @Test
    void parseSkipsLegacyCommentsBlankAndMalformedLines() {
        Map<String, String> values = ConfigIo.parse(List.of(
                "#DFast v0.2.0 - cau hinh Zero-Latency Mode",
                "#Mon Sep 29 10:00:00 ICT 2026",
                "",
                "hudEnabled=true",
                " framesInFlight = 2 ",
                "=orphan",
                "no-separator"));
        assertEquals(Map.of("hudEnabled", "true", "framesInFlight", "2"), values);
    }

    @Test
    void formatIsSortedAndCommentFree() {
        List<String> lines = ConfigIo.format(Map.of("b", "2", "a", "1"));
        assertEquals(List.of("a=1", "b=2"), lines);
    }

    @Test
    void atomicWriteRoundTripsAndLeavesNoTempFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested").resolve("dfast.properties");
        ConfigIo.writeAtomically(file, Map.of("hudEnabled", "false"));
        assertEquals(Map.of("hudEnabled", "false"), ConfigIo.read(file));
        assertFalse(Files.exists(file.resolveSibling("dfast.properties.tmp")));
        assertTrue(Files.readString(file).lines().noneMatch(line -> line.startsWith("#")));
    }

    @Test
    void booleansAreStrict() {
        assertTrue(ConfigIo.parseBoolean("TRUE", false));
        assertFalse(ConfigIo.parseBoolean("false", true));
        assertTrue(ConfigIo.parseBoolean("yes", true));
        assertFalse(ConfigIo.parseBoolean(null, false));
    }

    @Test
    void integersAreClampedOrFallBack() {
        assertEquals(3, ConfigIo.parseInt("9", 1, 1, 3));
        assertEquals(1, ConfigIo.parseInt("-4", 2, 1, 3));
        assertEquals(2, ConfigIo.parseInt("x", 2, 1, 3));
        assertEquals(2, ConfigIo.parseInt(null, 2, 1, 3));
    }
}
