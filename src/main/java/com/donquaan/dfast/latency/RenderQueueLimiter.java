package com.donquaan.dfast.latency;

import com.donquaan.dfast.DFast;
import org.lwjgl.opengl.GL32C;

/**
 * RenderQueueLimiter — giới hạn số frame CPU xếp trước GPU (frames-in-flight) bằng
 * GL fence sync, nhằm cắt độ trễ chuột-tới-hình. PHẢI chạy trên RENDER THREAD.
 *
 * <p>Cơ chế: cuối mỗi frame chèn 1 {@code glFenceSync}; đầu frame kế, CHỜ fence của
 * frame cách đây {@code framesInFlight} frame ({@code glClientWaitSync}). CPU không
 * chạy vượt GPU quá số frame cho phép → hàng đợi ngắn → độ trễ thấp. Thời gian phải
 * chờ ({@link #lastWaitMs()}) là tín hiệu độ trễ GPU thật đang được cắt bớt.
 *
 * <p>Bất kỳ lỗi GL nào → tự tắt + log 1 lần (fallback im lặng, luật nhà). Game không sập.
 */
public final class RenderQueueLimiter {

    private final long[] fences;
    private final int framesInFlight;
    private int slot = 0;
    private boolean enabled;
    private boolean warned = false;
    private volatile double lastWaitMs = 0.0;

    public RenderQueueLimiter(int framesInFlight, boolean enabled) {
        this.framesInFlight = Math.max(1, framesInFlight);
        this.enabled = enabled;
        this.fences = new long[this.framesInFlight];
    }

    public void setEnabled(boolean e) { this.enabled = e; }
    public boolean isEnabled()        { return enabled; }
    public double lastWaitMs()        { return lastWaitMs; }

    /** Gọi ĐẦU frame: chờ fence cũ để chặn CPU vượt GPU. */
    public void beginFrame() {
        if (!enabled) return;
        long fence = fences[slot];
        if (fence != 0L) {
            try {
                long t0 = System.nanoTime();
                // chờ tối đa 50ms để không treo cứng nếu GPU kẹt
                GL32C.glClientWaitSync(fence, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 50_000_000L);
                lastWaitMs = (System.nanoTime() - t0) / 1_000_000.0;
                GL32C.glDeleteSync(fence);
                fences[slot] = 0L;
            } catch (Throwable t) {
                disable(t);
            }
        }
    }

    /** Gọi CUỐI frame: chèn fence mới cho slot hiện tại rồi xoay slot. */
    public void endFrame() {
        if (!enabled) return;
        try {
            fences[slot] = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            slot = (slot + 1) % framesInFlight;
        } catch (Throwable t) {
            disable(t);
        }
    }

    private void disable(Throwable t) {
        enabled = false;
        if (!warned) {
            warned = true;
            DFast.LOGGER.warn("DFast RenderQueueLimiter tat (GL fence loi): {}", t.toString());
        }
    }
}
