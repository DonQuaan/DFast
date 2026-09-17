package com.donquaan.dfast.latency;

import com.donquaan.dfast.DFast;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * LatencyClientModule — điểm móc CLIENT của "Zero-Latency Mode" (DFast v0.2.0).
 *
 * <p>Dùng HOÀN TOÀN hook ổn định của fabric-api (HUD callback, keybind, client tick)
 * + LWJGL — KHÔNG mixin, KHÔNG native/FFM ở slice này → rủi ro sai mapping = 0, build
 * verify được qua CI. #2 NVAPI + #3 high-res timer (cần Java 22 FFM) làm ở slice sau.
 *
 * <p>Mỗi lần HUD render (1 lần/frame, trên render thread, có GL context):
 * chờ fence frame cũ (limiter) → đo frametime/1%-low (harness) → vẽ HUD nếu bật →
 * chèn fence frame mới.
 */
public final class LatencyClientModule {

    private static LatencyConfig config;
    private static LatencyHarness harness;
    private static RenderQueueLimiter limiter;
    private static KeyMapping toggleHud;

    private static long lastFrameNanos = 0L;
    private static long lastLowRefresh = 0L;

    /**
     * Delta lớn hơn ngưỡng này (1s) coi là GIÁN ĐOẠN (menu/alt-tab/tải thế giới) chứ
     * không phải 1 frame thật — HudRenderCallback ngừng chạy khi không vẽ HUD, nên khi
     * quay lại delta sẽ = cả khoảng gián đoạn. Bỏ mẫu đó để khỏi đầu độc avg/1%-low.
     */
    private static final long GAP_THRESHOLD_NANOS = 1_000_000_000L;

    private LatencyClientModule() {}

    public static void init() {
        config = LatencyConfig.load();
        harness = new LatencyHarness();
        limiter = new RenderQueueLimiter(config.framesInFlight, config.renderQueueLimiter);

        toggleHud = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.dfast.toggle_hud", InputConstants.Type.KEYSYM, config.hudKeyCode, "category.dfast"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHud.consumeClick()) {
                config.hudEnabled = !config.hudEnabled;
                config.save();
            }
        });

        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> onHud(guiGraphics));

        DFast.LOGGER.info("DFast Zero-Latency san sang: limiter={}, framesInFlight={}, HUD toggle=phim da gan (mac dinh F9)",
                config.renderQueueLimiter, config.framesInFlight);
    }

    private static void onHud(GuiGraphics g) {
        long now = System.nanoTime();

        // chặn CPU vượt GPU (chờ fence frame cũ) — đầu phần xử lý frame này
        limiter.beginFrame();

        // frametime = khoảng cách giữa 2 lần HUD render liên tiếp.
        // Bỏ mẫu sau gián đoạn (menu/alt-tab/tải): delta khổng lồ sẽ đầu độc avg/1%-low
        // suốt ~2048 frame — chính chỉ số gate của mod. Chỉ ghi frame thật.
        if (lastFrameNanos != 0L) {
            long delta = now - lastFrameNanos;
            if (delta < GAP_THRESHOLD_NANOS) {
                harness.record(delta);
            }
        }
        lastFrameNanos = now;

        // 1%-low tính giãn ~0.5s (O(n), không chạy mỗi frame)
        if (now - lastLowRefresh > 500_000_000L) {
            harness.refreshOnePercentLow();
            lastLowRefresh = now;
        }

        if (config.hudEnabled) {
            drawHud(g);
        }

        // chèn fence cuối frame
        limiter.endFrame();
    }

    private static void drawHud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int x = 4, y = 4;
        int line = mc.font.lineHeight + 2;
        int green = 0xFF55FF55;
        g.drawString(mc.font, "DFast Zero-Latency", x, y, 0xFFFFFF55);
        g.drawString(mc.font,
                String.format("FPS %.0f   frame %.2fms", harness.avgFps(), harness.lastFrameMs()),
                x, y + line, green);
        g.drawString(mc.font,
                String.format("1%%-low %.0f fps", harness.onePercentLowFps()),
                x, y + line * 2, green);
        String limit = limiter.isEnabled()
                ? String.format("GPU sync %.2fms", limiter.lastWaitMs())
                : "limiter OFF";
        g.drawString(mc.font, limit, x, y + line * 3, green);
    }
}
