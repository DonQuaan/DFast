package com.donquaan.dfast;

import com.donquaan.dfast.bench.BenchRunner;
import com.donquaan.dfast.ctl.CtlChannel;
import com.donquaan.dfast.latency.LatencyClientModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class DFastClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        LatencyClientModule latency = LatencyClientModule.init(loader.getConfigDir());
        BenchRunner.init(loader.getGameDir());
        CtlChannel.init(loader.getGameDir(), latency);
    }
}
