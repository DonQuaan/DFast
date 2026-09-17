package com.donquaan.dfast.latency;

/**
 * LatencyHarness — engine đo hiệu năng/độ trễ của DFast (v0.2.0 "Zero-Latency Mode").
 *
 * <p>Mục tiêu: đo bằng SỐ THẬT, không cảm tính. Đây là "cổng" quyết định giữ/gỡ mỗi
 * kỹ thuật cắt độ trễ (luật nhà: kỹ thuật nào không cắt được ms thật → bỏ).
 *
 * <p>Thu 3 nhóm số mỗi frame:
 * <ul>
 *   <li><b>frametime</b> — thời gian dựng 1 frame (ms) → suy ra FPS tức thời.</li>
 *   <li><b>1%-low</b> — trung bình 1% frame CHẬM nhất trong cửa sổ → đo độ mượt thật
 *       (giật/stutter lộ ở đây, không lộ ở FPS trung bình).</li>
 *   <li><b>latency</b> — ước tính input-poll → buffer-swap (ms), do bên ngoài nạp vào
 *       qua {@link #recordLatency(long)}; đo được KHÔNG cần Reflex SDK.</li>
 * </ul>
 *
 * <p>Ràng buộc luồng: mọi số frame ghi/đọc trên RENDER THREAD (single-threaded). Không
 * khóa. Các trường đọc chéo luồng (báo cáo/log) để {@code volatile}.
 *
 * <p>Không phụ thuộc Fabric/LWJGL/native — thuần {@code java.*} để dễ kiểm thử độc lập.
 */
public final class LatencyHarness {

    /** Số frame giữ trong cửa sổ trượt (~vài giây ở FPS cao). */
    private static final int WINDOW = 2048;

    private final long[] frameNanos = new long[WINDOW];
    private int count = 0;      // số mẫu đã ghi (bão hoà ở WINDOW)
    private int head = 0;       // vị trí ghi kế tiếp (ring)

    private long lastFrameStartNanos = 0L;

    // Ảnh chụp cho báo cáo/HUD — cập nhật mỗi frame, đọc chéo luồng an toàn.
    private volatile double lastFrameMs = 0.0;
    private volatile double avgFps = 0.0;
    private volatile double onePercentLowFps = 0.0;
    private volatile double lastLatencyMs = -1.0;   // -1 = chưa đo được

    private double runningSumMs = 0.0;              // tổng frametime trong cửa sổ (ms) để tính avg nhanh

    /** Đánh dấu MỐC bắt đầu frame (nên gọi ngay sau khi poll input). */
    public void markFrameStart() {
        lastFrameStartNanos = System.nanoTime();
    }

    /**
     * Đóng 1 frame: đo từ {@link #markFrameStart()} tới lúc gọi (thường ngay trước/sau swap).
     * Nếu chưa từng gọi markFrameStart thì bỏ qua frame này (khởi động).
     */
    public void endFrame() {
        if (lastFrameStartNanos == 0L) {
            return;
        }
        record(System.nanoTime() - lastFrameStartNanos);
        lastFrameStartNanos = 0L;
    }

    /** Ghi trực tiếp một frametime (nanos). Dùng khi có sẵn số đo ngoài. */
    public void record(long nanos) {
        if (nanos <= 0L) {
            return;
        }
        // cập nhật tổng cửa sổ (trừ mẫu bị ghi đè nếu ring đã đầy)
        if (count == WINDOW) {
            runningSumMs -= frameNanos[head] / 1_000_000.0;
        } else {
            count++;
        }
        frameNanos[head] = nanos;
        double ms = nanos / 1_000_000.0;
        runningSumMs += ms;
        head = (head + 1) % WINDOW;

        lastFrameMs = ms;
        avgFps = runningSumMs > 0 ? (count * 1000.0) / runningSumMs : 0.0;
    }

    /** Ghi độ trễ input→present ước tính (nanos). -1 nếu không đo được. */
    public void recordLatency(long nanos) {
        lastLatencyMs = nanos < 0 ? -1.0 : nanos / 1_000_000.0;
    }

    /**
     * Tính lại 1%-low (FPS) trên cửa sổ hiện tại. O(n) — gọi giãn (vd mỗi ~0.5s),
     * KHÔNG gọi mỗi frame để khỏi phí. Trả về giá trị đã tính; cũng lưu ảnh chụp.
     */
    public double refreshOnePercentLow() {
        if (count == 0) {
            return onePercentLowFps;
        }
        long[] copy = new long[count];
        System.arraycopy(frameNanos, 0, copy, 0, count);
        java.util.Arrays.sort(copy); // tăng dần theo frametime; chậm nhất ở cuối
        int worst = Math.max(1, count / 100); // 1% chậm nhất
        long sum = 0L;
        for (int i = count - worst; i < count; i++) {
            sum += copy[i];
        }
        double avgWorstMs = (sum / (double) worst) / 1_000_000.0;
        onePercentLowFps = avgWorstMs > 0 ? 1000.0 / avgWorstMs : 0.0;
        return onePercentLowFps;
    }

    /** Xoá toàn bộ mẫu (vd khi bật/tắt một kỹ thuật để đo before/after sạch). */
    public void reset() {
        count = 0;
        head = 0;
        runningSumMs = 0.0;
        lastFrameStartNanos = 0L;
    }

    // ---- Ảnh chụp cho HUD / log (đọc chéo luồng) ----
    public double lastFrameMs()        { return lastFrameMs; }
    public double avgFps()             { return avgFps; }
    public double onePercentLowFps()   { return onePercentLowFps; }
    public double lastLatencyMs()      { return lastLatencyMs; }
    public int sampleCount()           { return count; }
}
