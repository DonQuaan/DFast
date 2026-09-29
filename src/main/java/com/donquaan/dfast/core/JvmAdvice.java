package com.donquaan.dfast.core;

import java.util.ArrayList;
import java.util.List;

public record JvmAdvice(Collector collector, int heapGiB, List<String> flags) {
    public enum Collector { G1, ZGC }

    public static JvmAdvice forMachine(int javaFeature, long ramGiB) {
        if (javaFeature < 21) {
            throw new IllegalArgumentException("Java 21 or newer is required, got " + javaFeature);
        }
        Collector collector = ramGiB > 8 ? Collector.ZGC : Collector.G1;
        int heap = heapGiB(ramGiB);
        List<String> flags = new ArrayList<>();
        if (collector == Collector.ZGC) {
            flags.add("-Xms" + heap + "G");
            flags.add("-Xmx" + heap + "G");
            flags.add("-XX:+UseZGC");
            if (javaFeature <= 22) {
                flags.add("-XX:+ZGenerational");
            }
            flags.add("-XX:+AlwaysPreTouch");
        } else {
            flags.add("-Xmx" + heap + "G");
            flags.add("-XX:+UseG1GC");
        }
        if (javaFeature == 24) {
            flags.add("-XX:+UnlockExperimentalVMOptions");
        }
        if (javaFeature >= 24 && javaFeature <= 26) {
            flags.add("-XX:+UseCompactObjectHeaders");
        }
        return new JvmAdvice(collector, heap, List.copyOf(flags));
    }

    static int heapGiB(long ramGiB) {
        if (ramGiB <= 0) {
            return 4;
        }
        int tier = ramGiB <= 8 ? 4 : ramGiB <= 12 ? 5 : ramGiB <= 16 ? 6 : ramGiB <= 32 ? 8 : ramGiB < 48 ? 10 : 12;
        return (int) Math.max(1, Math.min(tier, ramGiB / 2));
    }

    public String args() {
        return String.join(" ", flags);
    }
}
