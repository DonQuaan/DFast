package com.donquaan.dfast;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public final class DFastLog {
    private static Path file;
    private static String failure;

    private DFastLog() {
    }

    public static synchronized boolean open(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, "", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            file = path;
            failure = null;
            return true;
        } catch (IOException e) {
            file = null;
            failure = e.toString();
            return false;
        }
    }

    public static synchronized Path path() {
        return file;
    }

    public static synchronized String failure() {
        return failure;
    }

    public static void info(String message) {
        write(false, message);
    }

    public static void warn(String message) {
        write(true, message);
    }

    private static synchronized void write(boolean warning, String message) {
        if (file != null) {
            String line = LocalTime.now().truncatedTo(ChronoUnit.MILLIS)
                    + (warning ? " WARN " : " INFO ") + message + System.lineSeparator();
            try {
                Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                return;
            } catch (IOException e) {
                failure = e.toString();
                file = null;
            }
        }
        if (warning) {
            DFast.LOGGER.warn(message);
        } else {
            DFast.LOGGER.info(message);
        }
    }
}
