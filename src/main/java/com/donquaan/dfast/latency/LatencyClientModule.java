package com.donquaan.dfast.latency;

import com.donquaan.dfast.DFastLog;
import com.donquaan.dfast.core.FrameGate;
import com.donquaan.dfast.core.FrameTimeStats;
import com.donquaan.dfast.core.RenderAheadPolicy;
import com.donquaan.dfast.core.RenderQueueLimiter;
import com.donquaan.dfast.core.Text;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

public final class LatencyClientModule {
    static final String KEY_TITLE = "dfast.hud.title";
    static final String KEY_FPS = "dfast.hud.fps";
    static final String KEY_FRAME = "dfast.hud.frame";
    static final String KEY_LOW = "dfast.hud.low";
    static final String KEY_OWNER = "dfast.hud.owner";
    static final String KEY_GPU_WAIT = "dfast.hud.gpu_wait";
    static final String KEY_TOGGLE = "key.dfast.toggle_hud";
    static final String KEY_CATEGORY = "category.dfast";

    private static final long SHORT_WINDOW_NANOS = 5_000_000_000L;
    private static final long LONG_WINDOW_NANOS = 60_000_000_000L;
    private static final long HUD_REFRESH_NANOS = 250_000_000L;
    private static final int TITLE_COLOR = 0xFFFFFF55;
    private static final int TEXT_COLOR = 0xFF55FF55;

    private final LatencyConfig config;
    private final RenderAheadPolicy.Owner owner;
    private final RenderQueueLimiter limiter;
    private final Component title = Component.translatable(KEY_TITLE);
    private final Component[] ownerLines = ownerLines();
    private final FrameGate gate = new FrameGate();
    private final FrameTimeStats shortStats = new FrameTimeStats(SHORT_WINDOW_NANOS, 1 << 15);
    private final FrameTimeStats longStats = new FrameTimeStats(LONG_WINDOW_NANOS, 1 << 18);
    private final FrameTimeStats.Summary shortSummary = new FrameTimeStats.Summary();
    private final FrameTimeStats.Summary longSummary = new FrameTimeStats.Summary();
    private long lastRefreshNanos;
    private FormattedCharSequence[] lines = new FormattedCharSequence[0];

    private LatencyClientModule(LatencyConfig config, RenderAheadPolicy.Owner owner, RenderQueueLimiter limiter) {
        this.config = config;
        this.owner = owner;
        this.limiter = limiter;
    }

    static String ownerKey(RenderAheadPolicy.Owner value) {
        return KEY_OWNER + "." + value.name().toLowerCase(Locale.ROOT);
    }

    private static Component[] ownerLines() {
        RenderAheadPolicy.Owner[] values = RenderAheadPolicy.Owner.values();
        Component[] result = new Component[values.length];
        for (RenderAheadPolicy.Owner value : values) {
            result[value.ordinal()] = Component.translatable(KEY_OWNER, Component.translatable(ownerKey(value)));
        }
        return result;
    }

    public static LatencyClientModule init(Path configDir) {
        LatencyConfig config = LatencyConfig.load(configDir.resolve("dfast-latency.properties"));
        RenderAheadPolicy.Owner owner = RenderAheadPolicy.owner(
                FabricLoader.getInstance().isModLoaded("sodium"), config.renderQueueLimiter());
        RenderQueueLimiter limiter = owner == RenderAheadPolicy.Owner.DFAST
                ? new RenderQueueLimiter(new LwjglFences(), config.framesInFlight(), DFastLog::warn)
                : null;
        DFastLog.info("render-ahead owner=" + owner);
        LatencyClientModule module = new LatencyClientModule(config, owner, limiter);
        KeyMapping toggleHud = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                KEY_TOGGLE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHud.consumeClick()) {
                config.toggleHud();
            }
        });
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> module.onHud(graphics));
        return module;
    }

    public void summarize(FrameTimeStats.Summary shortOut, FrameTimeStats.Summary longOut) {
        shortStats.summarize(shortOut);
        longStats.summarize(longOut);
    }

    public boolean hudEnabled() {
        return config.hudEnabled();
    }

    public RenderAheadPolicy.Owner shownOwner() {
        boolean limiting = limiter != null && limiter.active();
        return owner == RenderAheadPolicy.Owner.DFAST && !limiting ? RenderAheadPolicy.Owner.OFF : owner;
    }

    private void onHud(GuiGraphics graphics) {
        if (limiter != null) {
            limiter.beforeFrame();
        }
        long now = System.nanoTime();
        Minecraft minecraft = Minecraft.getInstance();
        switch (gate.next(now, minecraft.level, minecraft.screen == null && minecraft.isWindowActive())) {
            case RESET -> {
                shortStats.reset();
                longStats.reset();
            }
            case FRAME -> {
                shortStats.record(now, gate.frameNanos());
                longStats.record(now, gate.frameNanos());
            }
            case SKIP -> {
            }
        }
        if (config.hudEnabled() && !minecraft.options.hideGui && !minecraft.getDebugOverlay().showDebugScreen()) {
            if (now - lastRefreshNanos >= HUD_REFRESH_NANOS) {
                lines = hudLines();
                lastRefreshNanos = now;
            }
            draw(graphics, minecraft);
        }
        if (limiter != null) {
            limiter.afterFrame();
        }
    }

    private FormattedCharSequence[] hudLines() {
        summarize(shortSummary, longSummary);
        boolean limiting = limiter != null && limiter.active();
        FormattedCharSequence[] result = new FormattedCharSequence[limiting ? 6 : 5];
        result[0] = title.getVisualOrderText();
        result[1] = Component.translatable(KEY_FPS, Text.fixed(shortSummary.averageFps, 0)).getVisualOrderText();
        result[2] = Component.translatable(KEY_FRAME,
                Text.fixed(shortSummary.lastFrameMs, 2), Text.fixed(shortSummary.p99Ms, 2)).getVisualOrderText();
        result[3] = Component.translatable(KEY_LOW,
                Text.fixed(longSummary.onePercentLowFps, 0), Text.fixed(longSummary.pointOnePercentLowFps, 0)).getVisualOrderText();
        result[4] = ownerLines[shownOwner().ordinal()].getVisualOrderText();
        if (limiting) {
            result[5] = Component.translatable(KEY_GPU_WAIT, Text.fixed(limiter.lastWaitNanos() / 1_000_000.0, 2)).getVisualOrderText();
        }
        return result;
    }

    private void draw(GuiGraphics graphics, Minecraft minecraft) {
        int x = 4;
        int y = 4;
        int step = minecraft.font.lineHeight + 2;
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(minecraft.font, lines[i], x, y + i * step, i == 0 ? TITLE_COLOR : TEXT_COLOR);
        }
    }
}
