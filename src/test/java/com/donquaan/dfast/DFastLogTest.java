package com.donquaan.dfast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DFastLogTest {
    @Test
    void openTruncatesAndWritesLevels(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("dfast").resolve("dfast.log");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "previous session\n");
        assertTrue(DFastLog.open(file));
        DFastLog.info("hello");
        DFastLog.warn("careful");
        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).endsWith(" INFO hello"));
        assertTrue(lines.get(1).endsWith(" WARN careful"));
    }

    @Test
    void failedOpenReportsWhyAndFallsBack(@TempDir Path dir) throws Exception {
        Path blocker = dir.resolve("not-a-directory");
        Files.writeString(blocker, "");
        assertFalse(DFastLog.open(blocker.resolve("dfast.log")));
        assertNull(DFastLog.path());
        assertNotNull(DFastLog.failure());
        DFastLog.info("goes to the game log instead");
    }
}
