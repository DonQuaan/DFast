package com.donquaan.dfast.latency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.donquaan.dfast.DFastLog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LatencyConfigTest {
    @Test
    void defaultsKeepTheLimiterOffAndWriteACleanFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("dfast-latency.properties");
        LatencyConfig config = LatencyConfig.load(file);
        assertFalse(config.renderQueueLimiter());
        assertFalse(config.hudEnabled());
        assertEquals(1, config.framesInFlight());
        assertEquals(List.of("configVersion=2", "framesInFlight=1", "hudEnabled=false", "renderQueueLimiter=false"),
                Files.readAllLines(file));
    }

    @Test
    void legacyAutoWrittenLimiterDefaultIsReset(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("dfast-latency.properties");
        Files.write(file, List.of(
                "#DFast v0.2.0 - cau hinh Zero-Latency Mode",
                "#Mon Sep 29 10:00:00 ICT 2026",
                "renderQueueLimiter=true",
                "framesInFlight=7",
                "hudEnabled=true",
                "hudKeyCode=298"));
        LatencyConfig config = LatencyConfig.load(file);
        assertFalse(config.renderQueueLimiter());
        assertTrue(config.hudEnabled());
        assertEquals(LatencyConfig.MAX_FRAMES, config.framesInFlight());
        List<String> written = Files.readAllLines(file);
        assertTrue(written.contains("configVersion=2"));
        assertTrue(written.stream().noneMatch(line -> line.startsWith("#") || line.startsWith("hudKeyCode")));
    }

    @Test
    void currentVersionKeepsAnExplicitLimiterChoice(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("dfast-latency.properties");
        Files.write(file, List.of("configVersion=2", "renderQueueLimiter=true"));
        assertTrue(LatencyConfig.load(file).renderQueueLimiter());
    }

    @Test
    void nonUtf8BytesDoNotResetSettings(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("dfast-latency.properties");
        Files.write(file, new byte[] {'h', 'u', 'd', 'E', 'n', 'a', 'b', 'l', 'e', 'd', '=', 't', 'r', 'u', 'e', '\n', (byte) 0xC3, '\n'});
        assertTrue(LatencyConfig.load(file).hudEnabled());
    }

    @Test
    void unreadableConfigIsNeverWrittenBack(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("dfast.log");
        assertTrue(DFastLog.open(log));
        Path file = dir.resolve("dfast-latency.properties");
        Files.createDirectories(file);
        LatencyConfig config = LatencyConfig.load(file);
        assertFalse(config.hudEnabled());
        config.toggleHud();
        List<String> lines = Files.readAllLines(log);
        assertTrue(lines.stream().anyMatch(line -> line.contains("unreadable")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("not saved")));
        assertTrue(Files.isDirectory(file));
    }

    @Test
    void toggleHudPersists(@TempDir Path dir) {
        Path file = dir.resolve("dfast-latency.properties");
        LatencyConfig.load(file).toggleHud();
        assertTrue(LatencyConfig.load(file).hudEnabled());
    }
}
