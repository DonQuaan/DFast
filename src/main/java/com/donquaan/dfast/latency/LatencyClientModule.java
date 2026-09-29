package com.donquaan.dfast.latency;

import com.donquaan.dfast.DFastLog;
import com.donquaan.dfast.core.FrameTimeStats;
import com.donquaan.dfast.core.RenderAheadPolicy;
import com.donquaan.dfast.core.RenderQueueLimiter;
import com.donquaan.dfast.core.Text;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
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
    static final String KEY_GPU_WAIT = "dfast.hud.gpu_wait";
    static final String KEY_TOGGLE = "key.dfast.toggle_hud";
    static final String KEY_CATEGORY = "category.dfast";

    private static final int FRAME_WINDOW = 2048;
    private static final long GAP_NANOS = 1_000_000_000L;
    private static final long HUD_REFRESH_NANOS = 250_000_000L;
    private static final int TITLE_COLOR = 0xFFFFFF55;
    private static final int TEXT_COLOR = 0xFF55FF55;
    private static final Component TITLE = Component.translatable(KEY_TITLE);

    private static final FrameTimeStats STATS = new FrameTimeStats(FRAME_WINDOW);
    private static LatencyConfig config;
    private static RenderQueueLimiter limiter;
    private static KeyMapping toggleHud;
    private static long lastFrameNanos;
    private static long lastRefreshNanos;
    private static FormattedCharSequence[] lines = new FormattedCharSequence[0];

    private LatencyClientModule() {
    }

    public static void init(Path configDir) {
        config = LatencyConfig.load(configDir.resolve("dfast-latency.properties"));
        RenderAheadPolicy.Owner owner = RenderAheadPolicy.owner(
                FabricLoader.getInstance().isModLoaded("sodium"), config.renderQueueLimiter());
        if (owner == RenderAheadPolicy.Owner.DFAST) {
            limiter = new RenderQueueLimiter(new LwjglFences(), config.framesInFlight(), DFastLog::warn);
        }
        DFastLog.info("render-ahead owner=" + owner);
        toggleHud = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                KEY_TOGGLE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHud.consumeClick()) {
                config.toggleHud();
            }
        });
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> onHud(graphics));
    }

    private static void onHud(GuiGraphics graphics) {
        if (limiter != null) {
            limiter.beforeFrame();
        }
        long now = System.nanoTime();
        long delta = now - lastFrameNanos;
        if (lastFrameNanos != 0L && delta < GAP_NANOS) {
            STATS.record(delta);
        }
        lastFrameNanos = now;
        Minecraft minecraft = Minecraft.getInstance();
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

    private static FormattedCharSequence[] hudLines() {
        boolean waitLine = limiter != null && limiter.active();
        FormattedCharSequence[] result = new FormattedCharSequence[waitLine ? 5 : 4];
        result[0] = TITLE.getVisualOrderText();
        result[1] = Component.translatable(KEY_FPS, Text.fixed(STATS.averageFps(), 0)).getVisualOrderText();
        result[2] = Component.translatable(KEY_FRAME, Text.fixed(STATS.lastFrameMs(), 2)).getVisualOrderText();
        result[3] = Component.translatable(KEY_LOW, Text.fixed(STATS.onePercentLowFps(), 0)).getVisualOrderText();
        if (waitLine) {
            result[4] = Component.translatable(KEY_GPU_WAIT, Text.fixed(limiter.lastWaitNanos() / 1_000_000.0, 2)).getVisualOrderText();
        }
        return result;
    }

    private static void draw(GuiGraphics graphics, Minecraft minecraft) {
        int x = 4;
        int y = 4;
        int step = minecraft.font.lineHeight + 2;
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(minecraft.font, lines[i], x, y + i * step, i == 0 ? TITLE_COLOR : TEXT_COLOR);
        }
    }
}
