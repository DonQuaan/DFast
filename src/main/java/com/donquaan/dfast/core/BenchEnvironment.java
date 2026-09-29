package com.donquaan.dfast.core;

public record BenchEnvironment(Power power, int refreshRate) {
    public enum Power { AC, BATTERY, NONE, UNKNOWN }

    public static Power classify(int sources, int online) {
        if (sources < 0 || online < 0 || online > sources) {
            return Power.UNKNOWN;
        }
        if (sources == 0) {
            return Power.NONE;
        }
        return online > 0 ? Power.AC : Power.BATTERY;
    }

    public boolean sameAs(BenchEnvironment other) {
        return other != null && power == other.power && refreshRate == other.refreshRate;
    }
}
