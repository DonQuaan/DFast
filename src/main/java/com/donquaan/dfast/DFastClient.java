package com.donquaan.dfast;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. Phase 1: chỗ móc để phát hiện GPU (vendor/model qua GL_RENDERER)
 * khi render context sẵn sàng — dùng cho gate Nvidia RTX 2000+.
 * Phase 2+: high-res frame-pacing timer + advise HAGS/ReBAR.
 */
public final class DFastClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DFast.LOGGER.info("DFast client sẵn sàng. Phát hiện GPU sẽ chạy khi có render context.");
        // TODO Phase 1b: đọc GL_VENDOR/GL_RENDERER trong 1 client tick đầu để xác định Nvidia RTX + driver.
    }
}
