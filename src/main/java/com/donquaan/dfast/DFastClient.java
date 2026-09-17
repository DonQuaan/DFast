package com.donquaan.dfast;

import com.donquaan.dfast.latency.LatencyClientModule;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. Phase 1: chỗ móc để phát hiện GPU (vendor/model qua GL_RENDERER)
 * khi render context sẵn sàng — dùng cho gate Nvidia RTX 2000+.
 * v0.2.0: khởi động "Zero-Latency Mode" (RenderQueueLimiter + harness + HUD).
 */
public final class DFastClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DFast.LOGGER.info("DFast client sẵn sàng. Phát hiện GPU sẽ chạy khi có render context.");
        // v0.2.0 Zero-Latency Mode: giới hạn hàng đợi frame + đo latency + HUD (bật bằng phím).
        LatencyClientModule.init();
        // TODO Phase 1b: đọc GL_VENDOR/GL_RENDERER trong 1 client tick đầu để xác định Nvidia RTX + driver.
    }
}
