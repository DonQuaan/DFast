package com.donquaan.dfast.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ConfigIo {
    private ConfigIo() {
    }

    public static Map<String, String> parse(List<String> lines) {
        Map<String, String> values = new TreeMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            values.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
        }
        return values;
    }

    public static List<String> format(Map<String, String> values) {
        List<String> lines = new ArrayList<>(values.size());
        new TreeMap<>(values).forEach((key, value) -> lines.add(key + "=" + value));
        return lines;
    }

    public static Map<String, String> read(Path file) throws IOException {
        return parse(Files.readAllLines(file, StandardCharsets.ISO_8859_1));
    }

    public static void writeAtomically(Path file, Map<String, String> values) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = parent.resolve(file.getFileName() + ".tmp");
        Files.write(temp, format(values), StandardCharsets.ISO_8859_1);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            moveOrDiscard(temp, file);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private static void moveOrDiscard(Path temp, Path file) throws IOException {
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    public static boolean parseBoolean(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    public static int parseInt(String value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        try {
            return Math.clamp(Integer.parseInt(value.strip()), min, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
