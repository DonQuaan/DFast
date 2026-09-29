package com.donquaan.dfast.ctl;

import com.donquaan.dfast.DFastLog;
import com.donquaan.dfast.core.FrameTimeStats;
import com.donquaan.dfast.latency.LatencyClientModule;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

public final class CtlChannel {
    private static final int POLL_TICKS = 10;
    private static final int MAX_READ_BYTES = 1 << 16;

    private final Path in;
    private final Path out;
    private final LatencyClientModule latency;
    private final FrameTimeStats.Summary shortSummary = new FrameTimeStats.Summary();
    private final FrameTimeStats.Summary longSummary = new FrameTimeStats.Summary();
    private long offset;
    private int ticks;

    private CtlChannel(Path dir, LatencyClientModule latency) throws IOException {
        Files.createDirectories(dir);
        in = dir.resolve("in.jsonl");
        out = dir.resolve("out.jsonl");
        this.latency = latency;
        offset = Files.exists(in) ? Files.size(in) : 0L;
    }

    public static void init(Path gameDir, LatencyClientModule latency) {
        if (!Boolean.getBoolean("dfast.ctl")) {
            return;
        }
        try {
            CtlChannel channel = new CtlChannel(gameDir.resolve("dfast").resolve("ctl"), latency);
            ClientTickEvents.END_CLIENT_TICK.register(channel::tick);
            DFastLog.info("ctl channel listening on " + channel.in);
        } catch (IOException e) {
            DFastLog.warn("ctl channel disabled: " + e);
        }
    }

    private void tick(Minecraft minecraft) {
        if (++ticks % POLL_TICKS != 0) {
            return;
        }
        try {
            for (String line : readNewLines()) {
                if (!line.isBlank()) {
                    respond(handle(minecraft, line));
                }
            }
        } catch (IOException e) {
            DFastLog.warn("ctl channel read failed: " + e);
        }
    }

    private String[] readNewLines() throws IOException {
        if (!Files.exists(in)) {
            offset = 0L;
            return new String[0];
        }
        long size = Files.size(in);
        if (size < offset) {
            offset = 0L;
        }
        if (size == offset) {
            return new String[0];
        }
        byte[] bytes = new byte[(int) Math.min(size - offset, MAX_READ_BYTES)];
        try (RandomAccessFile file = new RandomAccessFile(in.toFile(), "r")) {
            file.seek(offset);
            file.readFully(bytes);
        }
        int end = bytes.length;
        while (end > 0 && bytes[end - 1] != '\n') {
            end--;
        }
        if (end == 0 && bytes.length == MAX_READ_BYTES) {
            offset += bytes.length;
            DFastLog.warn("ctl channel skipped a request longer than " + MAX_READ_BYTES + " bytes");
            return new String[0];
        }
        offset += end;
        return new String(bytes, 0, end, StandardCharsets.UTF_8).split("\r?\n");
    }

    private JsonObject handle(Minecraft minecraft, String line) {
        JsonObject reply = new JsonObject();
        JsonObject request;
        try {
            JsonElement parsed = JsonParser.parseString(line);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("expected a JSON object");
            }
            request = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            reply.addProperty("ok", false);
            reply.addProperty("error", "malformed request: " + e.getMessage());
            return reply;
        }
        if (request.has("id")) {
            reply.add("id", request.get("id"));
        }
        String cmd = request.has("cmd") && request.get("cmd").isJsonPrimitive() ? request.get("cmd").getAsString() : "";
        switch (cmd) {
            case "dump" -> {
                reply.addProperty("ok", true);
                reply.add("state", dump(minecraft));
            }
            case "screenshot" -> {
                String name = "dfast-ctl-" + System.currentTimeMillis() + ".png";
                Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), message -> { });
                reply.addProperty("ok", true);
                reply.addProperty("file", minecraft.gameDirectory.toPath().resolve("screenshots").resolve(name).toString());
            }
            case "quit" -> {
                reply.addProperty("ok", true);
                minecraft.stop();
            }
            default -> {
                reply.addProperty("ok", false);
                reply.addProperty("error", "unknown cmd '" + cmd + "', expected dump, screenshot or quit");
            }
        }
        return reply;
    }

    private JsonObject dump(Minecraft minecraft) {
        JsonObject state = new JsonObject();
        state.addProperty("fps", minecraft.getFps());
        state.addProperty("screen", minecraft.screen == null ? null : minecraft.screen.getClass().getName());
        state.addProperty("inWorld", minecraft.level != null);
        state.addProperty("windowActive", minecraft.isWindowActive());
        if (minecraft.player != null) {
            state.addProperty("position", minecraft.player.blockPosition().toShortString());
        }
        latency.summarize(shortSummary, longSummary);
        state.add("hudShort", summary(shortSummary));
        state.add("hudLong", summary(longSummary));
        state.addProperty("hudEnabled", latency.hudEnabled());
        state.addProperty("renderAheadOwner", latency.shownOwner().name());
        return state;
    }

    private static JsonObject summary(FrameTimeStats.Summary s) {
        JsonObject json = new JsonObject();
        json.addProperty("frames", s.frames);
        json.addProperty("averageFps", s.averageFps);
        json.addProperty("lastFrameMs", s.lastFrameMs);
        json.addProperty("p99Ms", s.p99Ms);
        json.addProperty("onePercentLowFps", s.onePercentLowFps);
        json.addProperty("pointOnePercentLowFps", s.pointOnePercentLowFps);
        return json;
    }

    private void respond(JsonObject reply) throws IOException {
        Files.writeString(out, reply + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
