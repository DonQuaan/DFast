package com.donquaan.dfast.bench;

import com.donquaan.dfast.DFast;
import com.donquaan.dfast.DFastLog;
import com.donquaan.dfast.core.WatchdogPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class BenchWatchdog {
    private static final long SECOND = 1_000_000_000L;
    private static final int STACK_DEPTH = 12;

    private final Path outDir;
    private final WatchdogPolicy policy;
    private volatile long lastTickNanos = System.nanoTime();
    private volatile long doneNanos;
    private volatile boolean done;
    private volatile boolean passed;

    BenchWatchdog(Path outDir, long runSeconds) {
        this.outDir = outDir;
        policy = new WatchdogPolicy(120 * SECOND, 180 * SECOND, System.nanoTime() + (runSeconds + 180) * SECOND);
    }

    void start() {
        Thread thread = new Thread(this::watch, "DFast bench watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    void tick() {
        lastTickNanos = System.nanoTime();
    }

    void done(boolean ok) {
        passed = ok;
        doneNanos = System.nanoTime();
        done = true;
    }

    private void watch() {
        while (true) {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                return;
            }
            WatchdogPolicy.Verdict verdict = policy.check(System.nanoTime(), lastTickNanos, done, doneNanos);
            switch (verdict) {
                case WAIT -> {
                }
                case SHUTDOWN_STUCK -> halt("the game did not exit within 120 s after the bench finished", passed ? 0 : 1);
                case STALLED -> {
                    writeFailure("the game stopped ticking for 180 s");
                    halt("the game stopped ticking for 180 s", 1);
                }
                case DEADLINE -> {
                    writeFailure("the bench exceeded its time limit");
                    halt("the bench exceeded its time limit", 1);
                }
            }
        }
    }

    private void writeFailure(String reason) {
        Path result = outDir.resolve("result.json");
        if (Files.exists(result)) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("schema", 1);
        json.addProperty("status", "failed");
        json.addProperty("failure", "watchdog: " + reason);
        try {
            Files.createDirectories(outDir);
            Files.writeString(result, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DFastLog.warn("watchdog could not write " + result + ": " + e);
        }
    }

    private static void halt(String reason, int status) {
        DFastLog.warn("bench watchdog: " + reason + "; thread dump follows, then the process exits with status " + status);
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            StringBuilder line = new StringBuilder("thread \"").append(thread.getName()).append("\" ").append(thread.getState());
            StackTraceElement[] stack = entry.getValue();
            for (int i = 0; i < Math.min(STACK_DEPTH, stack.length); i++) {
                line.append("\n    at ").append(stack[i]);
            }
            DFastLog.warn(line.toString());
        }
        DFast.LOGGER.warn("DFast bench watchdog: {}; see dfast/dfast.log, exiting with status {}", reason, status);
        Runtime.getRuntime().halt(status);
    }
}
