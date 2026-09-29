package com.donquaan.dfast;

import com.donquaan.dfast.core.JvmAdvice;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DFast implements ModInitializer {
    public static final String MOD_ID = "dfast";
    public static final Logger LOGGER = LoggerFactory.getLogger("DFast");

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        boolean separateLog = DFastLog.open(loader.getGameDir().resolve(MOD_ID).resolve("dfast.log"));
        if (separateLog) {
            LOGGER.info("DFast {} ready, details in {}", version(loader), DFastLog.path());
        } else {
            LOGGER.warn("DFast {} ready, dfast.log unavailable ({}), details follow here", version(loader), DFastLog.failure());
        }
        HardwareProfile profile = HardwareProfile.detect();
        DFastLog.info("hardware " + profile.summary());
        JvmAdvice advice = JvmAdvice.forMachine(profile.javaFeature(), profile.ramGiB());
        DFastLog.info("jvm-advice collector=" + advice.collector() + " heap=" + advice.heapGiB() + "G args=" + advice.args());
    }

    private static String version(FabricLoader loader) {
        return loader.getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
