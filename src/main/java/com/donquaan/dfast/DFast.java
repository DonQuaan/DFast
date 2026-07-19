package com.donquaan.dfast;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DFast — Don Fast.
 * Zero-config, hardware-aware performance meta-optimizer chuyên Windows + Intel + Nvidia RTX 2000+.
 *
 * <p>Phase 1 (MVP): tự nhận phần cứng (CPU/RAM/OS/Java) và log khuyến nghị JVM profile
 * (Compact Object Headers + G1 cho máy ít RAM, Generational ZGC cho máy nhiều RAM) + stack.
 * Các phase sau: lớp Windows-native (high-res timer, priority) qua Panama FFM.
 */
public final class DFast implements ModInitializer {
    public static final String MOD_ID = "dfast";
    public static final Logger LOGGER = LoggerFactory.getLogger("DFast");

    @Override
    public void onInitialize() {
        LOGGER.info("DFast đang khởi động — meta-optimizer cho Windows/Intel/Nvidia.");
        HardwareProfile profile = HardwareProfile.detect();
        LOGGER.info("Phần cứng phát hiện: {}", profile.summary());
        LOGGER.info("Khuyến nghị JVM: {}", profile.recommendedJvmProfile());
        // Phase 1: mới ở mức phát hiện + khuyến nghị (log). Auto-apply & bundle làm ở bước sau.
    }
}
