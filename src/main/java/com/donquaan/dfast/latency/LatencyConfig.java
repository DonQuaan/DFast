package com.donquaan.dfast.latency;

import com.donquaan.dfast.DFastLog;
import com.donquaan.dfast.core.ConfigIo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public final class LatencyConfig {
    static final String CONFIG_VERSION = "configVersion";
    static final String RENDER_QUEUE_LIMITER = "renderQueueLimiter";
    static final String FRAMES_IN_FLIGHT = "framesInFlight";
    static final String HUD_ENABLED = "hudEnabled";
    static final int VERSION = 2;
    static final int MIN_FRAMES = 1;
    static final int MAX_FRAMES = 3;

    private final Path file;
    private boolean writable = true;
    private boolean renderQueueLimiter;
    private int framesInFlight = 1;
    private boolean hudEnabled;

    LatencyConfig(Path file) {
        this.file = file;
    }

    public static LatencyConfig load(Path file) {
        LatencyConfig config = new LatencyConfig(file);
        if (Files.exists(file)) {
            try {
                config.apply(ConfigIo.read(file));
            } catch (IOException e) {
                config.writable = false;
                DFastLog.warn("config " + file.getFileName() + " unreadable, using defaults without overwriting it: " + e);
                return config;
            }
        }
        config.save();
        return config;
    }

    void apply(Map<String, String> values) {
        if (ConfigIo.parseInt(values.get(CONFIG_VERSION), 1, 1, VERSION) >= VERSION) {
            renderQueueLimiter = ConfigIo.parseBoolean(values.get(RENDER_QUEUE_LIMITER), renderQueueLimiter);
        } else if (ConfigIo.parseBoolean(values.get(RENDER_QUEUE_LIMITER), false)) {
            DFastLog.info("config " + file.getFileName() + " migrated to version " + VERSION
                    + ", auto-written renderQueueLimiter=true reset to false");
        }
        framesInFlight = ConfigIo.parseInt(values.get(FRAMES_IN_FLIGHT), framesInFlight, MIN_FRAMES, MAX_FRAMES);
        hudEnabled = ConfigIo.parseBoolean(values.get(HUD_ENABLED), hudEnabled);
    }

    Map<String, String> values() {
        Map<String, String> values = new TreeMap<>();
        values.put(CONFIG_VERSION, Integer.toString(VERSION));
        values.put(RENDER_QUEUE_LIMITER, Boolean.toString(renderQueueLimiter));
        values.put(FRAMES_IN_FLIGHT, Integer.toString(framesInFlight));
        values.put(HUD_ENABLED, Boolean.toString(hudEnabled));
        return values;
    }

    void save() {
        if (!writable) {
            return;
        }
        try {
            ConfigIo.writeAtomically(file, values());
        } catch (IOException e) {
            DFastLog.warn("config " + file.getFileName() + " not saved: " + e);
        }
    }

    public boolean renderQueueLimiter() {
        return renderQueueLimiter;
    }

    public int framesInFlight() {
        return framesInFlight;
    }

    public boolean hudEnabled() {
        return hudEnabled;
    }

    public void toggleHud() {
        hudEnabled = !hudEnabled;
        save();
    }
}
