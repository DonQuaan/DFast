package com.donquaan.dfast.core;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public record BenchSpec(String preset, long seed, int warmupSeconds, int durationSeconds,
                        double radius, double height, float pitch) {
    public static final long DEFAULT_SEED = 2026L;
    public static final int MAX_DURATION_SECONDS = 400;

    public BenchSpec {
        check("warmup", warmupSeconds, 1, 600);
        check("duration", durationSeconds, 5, MAX_DURATION_SECONDS);
        check("radius", radius, 16, 512);
        check("height", height, -64, 320);
        check("pitch", pitch, -90, 90);
    }

    public static BenchSpec parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty bench spec");
        }
        String[] parts = text.trim().split(",");
        String preset = parts[0].trim().toLowerCase(Locale.ROOT);
        BenchSpec spec = switch (preset) {
            case "ci" -> new BenchSpec(preset, DEFAULT_SEED, 5, 10, 96, 140, 25);
            case "short" -> new BenchSpec(preset, DEFAULT_SEED, 10, 30, 96, 140, 25);
            case "long" -> new BenchSpec(preset, DEFAULT_SEED, 20, 120, 96, 140, 25);
            default -> throw new IllegalArgumentException("unknown bench preset '" + preset + "', expected ci, short or long");
        };
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("expected key=value, got '" + parts[i] + "'");
            }
            String key = parts[i].substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = parts[i].substring(eq + 1).trim();
            if (!seen.add(key)) {
                throw new IllegalArgumentException("duplicate key '" + key + "'");
            }
            spec = spec.with(key, value);
        }
        return spec;
    }

    public String worldName() {
        return "dfast-bench-" + seed;
    }

    private BenchSpec with(String key, String value) {
        try {
            return switch (key) {
                case "seed" -> new BenchSpec(preset, Long.parseLong(value), warmupSeconds, durationSeconds, radius, height, pitch);
                case "warmup" -> new BenchSpec(preset, seed, Integer.parseInt(value), durationSeconds, radius, height, pitch);
                case "duration" -> new BenchSpec(preset, seed, warmupSeconds, Integer.parseInt(value), radius, height, pitch);
                case "radius" -> new BenchSpec(preset, seed, warmupSeconds, durationSeconds, Double.parseDouble(value), height, pitch);
                case "height" -> new BenchSpec(preset, seed, warmupSeconds, durationSeconds, radius, Double.parseDouble(value), pitch);
                case "pitch" -> new BenchSpec(preset, seed, warmupSeconds, durationSeconds, radius, height, Float.parseFloat(value));
                default -> throw new IllegalArgumentException("unknown bench key '" + key + "'");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid number for '" + key + "': " + value, e);
        }
    }

    private static void check(String name, double value, double min, double max) {
        if (!(value >= min && value <= max)) {
            throw new IllegalArgumentException(name + " must be in [" + min + ", " + max + "], got " + value);
        }
    }
}
