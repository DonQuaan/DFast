package com.donquaan.dfast.core;

public final class RenderAheadPolicy {
    public enum Owner { SODIUM, DFAST, OFF }

    private RenderAheadPolicy() {
    }

    public static Owner owner(boolean sodiumLoaded, boolean dfastLimiterRequested) {
        if (sodiumLoaded) {
            return Owner.SODIUM;
        }
        return dfastLimiterRequested ? Owner.DFAST : Owner.OFF;
    }
}
