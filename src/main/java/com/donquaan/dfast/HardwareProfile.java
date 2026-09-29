package com.donquaan.dfast;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.Locale;

public record HardwareProfile(String osName, int cpuThreads, long maxHeapBytes, long totalRamBytes, int javaFeature) {
    private static final double BYTES_PER_GIB = 1L << 30;

    public static HardwareProfile detect() {
        Runtime runtime = Runtime.getRuntime();
        return new HardwareProfile(
                System.getProperty("os.name", "unknown"),
                runtime.availableProcessors(),
                runtime.maxMemory(),
                readTotalRam(),
                Runtime.version().feature());
    }

    static long readTotalRam() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean) {
                return bean.getTotalMemorySize();
            }
            DFastLog.warn("total RAM unavailable: operating system MXBean is not com.sun.management");
        } catch (LinkageError e) {
            DFastLog.warn("total RAM unavailable: " + e);
        }
        return -1L;
    }

    public long ramGiB() {
        return totalRamBytes > 0 ? Math.round(totalRamBytes / BYTES_PER_GIB) : -1L;
    }

    public String summary() {
        return String.format(Locale.ROOT, "os=%s threads=%d ramGiB=%d heapMaxMiB=%d java=%d",
                osName, cpuThreads, ramGiB(), maxHeapBytes >> 20, javaFeature);
    }
}
