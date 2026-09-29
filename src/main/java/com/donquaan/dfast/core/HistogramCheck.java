package com.donquaan.dfast.core;

import java.util.Arrays;
import java.util.List;

public record HistogramCheck(int frames, List<Metric> metrics) {
    public static final double TOLERANCE = 1.0 / 64.0;

    public record Metric(String name, double histogram, double exact) {
        public double relativeError() {
            return exact == 0.0 ? Math.abs(histogram) : Math.abs(histogram - exact) / Math.abs(exact);
        }
    }

    public static HistogramCheck of(FrameTimeStats stats, long[] frames) {
        FrameTimeStats.Summary summary = new FrameTimeStats.Summary();
        stats.summarize(summary);
        int n = Math.min(summary.frames, frames.length);
        FrameRecorder tail = new FrameRecorder(Math.max(1, n));
        for (long frame : Arrays.copyOfRange(frames, frames.length - n, frames.length)) {
            tail.add(frame);
        }
        FrameRecorder.Result exact = tail.result();
        return new HistogramCheck(summary.frames, List.of(
                new Metric("averageFps", summary.averageFps, exact.averageFps()),
                new Metric("p99Ms", summary.p99Ms, exact.p99Ms()),
                new Metric("onePercentLowFps", summary.onePercentLowFps, exact.onePercentLowFps()),
                new Metric("pointOnePercentLowFps", summary.pointOnePercentLowFps, exact.pointOnePercentLowFps())));
    }

    public double maxRelativeError() {
        return metrics.stream().mapToDouble(Metric::relativeError).max().orElse(0.0);
    }

    public boolean passed() {
        return frames > 0 && maxRelativeError() <= TOLERANCE;
    }
}
