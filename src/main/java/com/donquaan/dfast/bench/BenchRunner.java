package com.donquaan.dfast.bench;

import com.donquaan.dfast.DFast;
import com.donquaan.dfast.DFastLog;
import com.donquaan.dfast.HardwareProfile;
import com.donquaan.dfast.core.BenchSpec;
import com.donquaan.dfast.core.CameraPath;
import com.donquaan.dfast.core.FrameRecorder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

public final class BenchRunner {
    private enum Phase { WAIT_MENU, LOADING, RUNNING, DONE }

    private static final int TICKS_PER_SECOND = 20;
    private static final int PATH_POINTS = 12;
    private static final int MAX_FRAMES_PER_SECOND = 10_000;
    private static final int MAX_RECORDED_FRAMES = 1 << 22;
    private static final int UNLIMITED_FPS = 260;
    private static final long LOAD_TIMEOUT_NANOS = 600_000_000_000L;
    private static final List<String> SETUP_COMMANDS = List.of(
            "gamerule doDaylightCycle false",
            "gamerule doWeatherCycle false",
            "gamerule doMobSpawning false",
            "time set 6000",
            "weather clear",
            "gamemode spectator @a");

    private final BenchSpec spec;
    private final Path outDir;
    private final FrameRecorder recorder;
    private final double[] pose = new double[5];
    private Phase phase = Phase.WAIT_MENU;
    private CameraPath path;
    private final long loadDeadline = System.nanoTime() + LOAD_TIMEOUT_NANOS;
    private int ticks;
    private float yaw;
    private boolean measuring;
    private long lastFrameNanos;
    private boolean optionsSaved;
    private boolean pauseOnLostFocus;
    private boolean vsync;
    private int maxFps;

    private BenchRunner(BenchSpec spec, Path outDir) {
        this.spec = spec;
        this.outDir = outDir;
        recorder = new FrameRecorder(Math.min(spec.durationSeconds() * MAX_FRAMES_PER_SECOND, MAX_RECORDED_FRAMES));
    }

    public static void init(Path gameDir) {
        String text = System.getProperty("dfast.bench");
        if (text == null) {
            return;
        }
        BenchSpec spec;
        try {
            spec = BenchSpec.parse(text);
        } catch (IllegalArgumentException e) {
            DFast.LOGGER.warn("DFast bench not started: {}", e.getMessage());
            return;
        }
        String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + spec.preset();
        BenchRunner runner = new BenchRunner(spec, gameDir.resolve("dfast").resolve("bench").resolve(runId));
        DFastLog.info("bench requested " + spec);
        ClientTickEvents.END_CLIENT_TICK.register(runner::tick);
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> runner.frame());
    }

    private void frame() {
        if (!measuring) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            recorder.add(now - lastFrameNanos);
        }
        lastFrameNanos = now;
    }

    private void tick(Minecraft minecraft) {
        switch (phase) {
            case WAIT_MENU -> {
                if (minecraft.getOverlay() == null && minecraft.level == null && minecraft.screen != null) {
                    pauseOnLostFocus = minecraft.options.pauseOnLostFocus;
                    vsync = minecraft.options.enableVsync().get();
                    maxFps = minecraft.options.framerateLimit().get();
                    optionsSaved = true;
                    minecraft.options.pauseOnLostFocus = false;
                    minecraft.options.enableVsync().set(false);
                    minecraft.options.framerateLimit().set(UNLIMITED_FPS);
                    phase = Phase.LOADING;
                    openWorld(minecraft);
                } else if (System.nanoTime() > loadDeadline) {
                    finish(minecraft, "the main menu was not reached within " + LOAD_TIMEOUT_NANOS / 1_000_000_000L + " s");
                }
            }
            case LOADING -> {
                if (minecraft.level != null && minecraft.player != null && minecraft.screen == null) {
                    start(minecraft);
                } else if (System.nanoTime() > loadDeadline) {
                    finish(minecraft, "world did not load within " + LOAD_TIMEOUT_NANOS / 1_000_000_000L + " s");
                }
            }
            case RUNNING -> run(minecraft);
            case DONE -> {
            }
        }
    }

    private void openWorld(Minecraft minecraft) {
        String name = spec.worldName();
        if (minecraft.getLevelSource().levelExists(name)) {
            minecraft.createWorldOpenFlows().openWorld(name, () -> finish(minecraft, "opening " + name + " was cancelled"));
            return;
        }
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(spec.seed(), true, false),
                WorldPresets::createNormalWorldDimensions, new TitleScreen());
    }

    private void start(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            finish(minecraft, "bench needs a singleplayer world");
            return;
        }
        server.execute(() -> {
            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
            for (String command : SETUP_COMMANDS) {
                server.getCommands().performPrefixedCommand(source, command);
            }
        });
        BlockPos spawn = minecraft.level.getSharedSpawnPos();
        path = CameraPath.orbit(spawn.getX() + 0.5, spawn.getZ() + 0.5, spec.radius(), spec.height(), PATH_POINTS, spec.pitch());
        ticks = 0;
        phase = Phase.RUNNING;
        place(minecraft.player, true);
        DFastLog.info("bench world ready spawn=" + spawn.toShortString());
    }

    private void run(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            finish(minecraft, "left the world during the run");
            return;
        }
        if (measuring && minecraft.screen != null) {
            finish(minecraft, "a screen was opened during measurement: " + minecraft.screen.getClass().getName());
            return;
        }
        ticks++;
        int warmupTicks = spec.warmupSeconds() * TICKS_PER_SECOND;
        int endTicks = warmupTicks + spec.durationSeconds() * TICKS_PER_SECOND;
        if (ticks == warmupTicks) {
            lastFrameNanos = 0L;
            measuring = true;
        }
        if (ticks >= endTicks) {
            finish(minecraft, null);
            return;
        }
        place(player, false);
    }

    private void place(LocalPlayer player, boolean snap) {
        int warmupTicks = spec.warmupSeconds() * TICKS_PER_SECOND;
        path.sample((double) (ticks - warmupTicks) / (spec.durationSeconds() * TICKS_PER_SECOND), pose);
        yaw = snap ? (float) pose[3] : CameraPath.unwrap(yaw, (float) pose[3]);
        player.getAbilities().flying = true;
        player.setDeltaMovement(Vec3.ZERO);
        if (snap) {
            player.moveTo(pose[0], pose[1], pose[2], yaw, (float) pose[4]);
        } else {
            player.setPos(pose[0], pose[1], pose[2]);
            player.setYRot(yaw);
            player.setXRot((float) pose[4]);
        }
    }

    private void finish(Minecraft minecraft, String failure) {
        measuring = false;
        phase = Phase.DONE;
        FrameRecorder.Result result = recorder.result();
        try {
            Files.createDirectories(outDir);
            Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
            Files.writeString(outDir.resolve("result.json"), gson.toJson(result(result, failure)), StandardCharsets.UTF_8);
            Files.writeString(outDir.resolve("system.json"), gson.toJson(system(minecraft)), StandardCharsets.UTF_8);
            Files.writeString(outDir.resolve("frametimes.txt"),
                    LongStream.of(recorder.frames()).mapToObj(Long::toString).collect(Collectors.joining("\n", "", "\n")),
                    StandardCharsets.UTF_8);
            DFast.LOGGER.info("DFast bench {}: {} frames, {} avg FPS, results in {}", failure == null ? "ok" : "failed",
                    result.frames(), Math.round(result.averageFps()), outDir);
        } catch (IOException | RuntimeException e) {
            DFast.LOGGER.error("DFast bench could not write results to {}", outDir, e);
        }
        if (optionsSaved) {
            minecraft.options.pauseOnLostFocus = pauseOnLostFocus;
            minecraft.options.enableVsync().set(vsync);
            minecraft.options.framerateLimit().set(maxFps);
        }
        minecraft.stop();
    }

    private JsonObject result(FrameRecorder.Result result, String failure) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", 1);
        json.addProperty("status", failure == null ? "ok" : "failed");
        json.addProperty("failure", failure);
        json.addProperty("dfastVersion", version("dfast"));
        JsonObject specJson = new JsonObject();
        specJson.addProperty("preset", spec.preset());
        specJson.addProperty("seed", spec.seed());
        specJson.addProperty("world", spec.worldName());
        specJson.addProperty("warmupSeconds", spec.warmupSeconds());
        specJson.addProperty("durationSeconds", spec.durationSeconds());
        specJson.addProperty("radius", spec.radius());
        specJson.addProperty("height", spec.height());
        specJson.addProperty("pitch", spec.pitch());
        specJson.addProperty("uncapped", true);
        json.add("spec", specJson);
        json.addProperty("frames", result.frames());
        json.addProperty("seconds", result.seconds());
        json.addProperty("averageFps", result.averageFps());
        json.addProperty("p50Ms", result.p50Ms());
        json.addProperty("p90Ms", result.p90Ms());
        json.addProperty("p99Ms", result.p99Ms());
        json.addProperty("p999Ms", result.p999Ms());
        json.addProperty("maxMs", result.maxMs());
        json.addProperty("onePercentLowFps", result.onePercentLowFps());
        json.addProperty("pointOnePercentLowFps", result.pointOnePercentLowFps());
        json.addProperty("overflowed", result.overflowed());
        return json;
    }

    private JsonObject system(Minecraft minecraft) {
        HardwareProfile hardware = HardwareProfile.detect();
        JsonObject json = new JsonObject();
        json.addProperty("schema", 1);
        json.addProperty("os", hardware.osName());
        json.addProperty("cpuThreads", hardware.cpuThreads());
        json.addProperty("ramGiB", hardware.ramGiB());
        json.addProperty("heapMaxMiB", hardware.maxHeapBytes() >> 20);
        json.addProperty("java", System.getProperty("java.vm.name") + " " + Runtime.version());
        json.addProperty("jvmFlags", ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-X"))
                .collect(Collectors.joining(" ")));
        json.add("gl", GlProbe.probe());
        JsonObject options = new JsonObject();
        options.addProperty("renderDistance", minecraft.options.renderDistance().get());
        options.addProperty("simulationDistance", minecraft.options.simulationDistance().get());
        options.addProperty("graphics", minecraft.options.graphicsMode().get().name());
        if (optionsSaved) {
            options.addProperty("userVsync", vsync);
            options.addProperty("userMaxFps", maxFps);
        }
        options.addProperty("fov", minecraft.options.fov().get());
        options.addProperty("guiScale", minecraft.options.guiScale().get());
        options.addProperty("fullscreen", minecraft.getWindow().isFullscreen());
        options.addProperty("framebuffer", minecraft.getWindow().getWidth() + "x" + minecraft.getWindow().getHeight());
        options.addProperty("refreshRate", minecraft.getWindow().getRefreshRate());
        json.add("options", options);
        JsonArray mods = new JsonArray();
        FabricLoader.getInstance().getAllMods().stream()
                .filter(mod -> mod.getContainingMod().isEmpty())
                .map(mod -> mod.getMetadata().getId() + " " + mod.getMetadata().getVersion().getFriendlyString())
                .sorted()
                .forEach(mods::add);
        json.add("mods", mods);
        return json;
    }

    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
