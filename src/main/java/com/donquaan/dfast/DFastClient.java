package com.donquaan.dfast;

import com.donquaan.dfast.bench.BenchRunner;
import com.donquaan.dfast.latency.LatencyClientModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class DFastClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LatencyClientModule.init(FabricLoader.getInstance().getConfigDir());
        BenchRunner.init(FabricLoader.getInstance().getGameDir());
    }
}
