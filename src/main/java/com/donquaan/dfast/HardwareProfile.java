package com.donquaan.dfast;

/**
 * Phát hiện phần cứng/JVM và đưa ra khuyến nghị hardware-aware.
 *
 * <p>Quyết định đã chốt (PLAN §7): JVM profile tự chọn theo RAM —
 * máy ≤16GB → Compact Object Headers + G1 (tiết kiệm ~22% heap);
 * máy ≥32GB → Generational ZGC (frametime mượt nhất). COH và ZGC loại trừ nhau.
 */
public record HardwareProfile(
        String osName,
        int cpuThreads,
        long maxHeapBytes,
        long totalRamBytes,
        int javaMajor,
        boolean isWindows
) {
    public static HardwareProfile detect() {
        String os = System.getProperty("os.name", "unknown");
        int threads = Runtime.getRuntime().availableProcessors();
        long maxHeap = Runtime.getRuntime().maxMemory();
        long totalRam = readTotalRam();
        int javaMajor = Runtime.version().feature();
        boolean win = os.toLowerCase().contains("win");
        return new HardwareProfile(os, threads, maxHeap, totalRam, javaMajor, win);
    }

    /** Đọc RAM vật lý qua OperatingSystemMXBean (không cần native ở Phase 1). */
    private static long readTotalRam() {
        try {
            var bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            var m = bean.getClass().getMethod("getTotalMemorySize");
            m.setAccessible(true);
            return (long) m.invoke(bean);
        } catch (Throwable t) {
            return -1L;
        }
    }

    private long totalRamGb() {
        return totalRamBytes > 0 ? totalRamBytes / (1024L * 1024L * 1024L) : -1L;
    }

    public String summary() {
        return String.format("OS=%s | CPU threads=%d | RAM=%dGB | heap max=%dMB | Java %d",
                osName, cpuThreads, totalRamGb(), maxHeapBytes / (1024 * 1024), javaMajor);
    }

    /** Khuyến nghị JVM profile theo RAM (PLAN §7 phương án C — auto-select). */
    public String recommendedJvmProfile() {
        long gb = totalRamGb();
        if (javaMajor < 21) {
            return "Nên nâng Java ≥21 (đang " + javaMajor + ").";
        }
        if (gb >= 0 && gb <= 16) {
            return "COH + G1GC — máy " + gb + "GB: -XX:+UseCompactObjectHeaders (giảm ~22% heap) "
                    + "+ G1 (KHÔNG dùng ZGC vì COH loại trừ ZGC).";
        }
        // ≥32GB (hoặc không đọc được RAM → mặc định an toàn mượt)
        return "Generational ZGC — máy " + (gb > 0 ? gb + "GB" : "RAM lớn/không xác định")
                + ": -XX:+UseZGC (Java 21 thêm +ZGenerational; Java ≥24 mặc định) — frametime mượt nhất.";
    }
}
