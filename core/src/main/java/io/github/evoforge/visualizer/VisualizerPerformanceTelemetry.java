package io.github.evoforge.visualizer;

import com.badlogic.gdx.Gdx;

/**
 * Low-overhead presentation telemetry for diagnosing frame-time spikes.
 *
 * <p>The observed frame interval includes pacing/vsync effects, while the CPU
 * measurements cover work executed inside {@link ZLevelVisualizer#render()}.
 * Comparing the two helps distinguish presentation work from time spent
 * outside the render method.</p>
 */
public final class VisualizerPerformanceTelemetry {

    private static final long LOG_INTERVAL_NANOS = 1_000_000_000L;

    private long windowStartNanos = System.nanoTime();
    private int frames;

    private long observedFrameNanos;
    private long maxObservedFrameNanos;
    private long cpuNanos;
    private long maxCpuNanos;
    private long updateNanos;
    private long maxUpdateNanos;
    private long landscapeNanos;
    private long maxLandscapeNanos;
    private long overlayNanos;
    private long maxOverlayNanos;
    private long hudNanos;
    private long maxHudNanos;

    private long minJavaHeap = Long.MAX_VALUE;
    private long maxJavaHeap;
    private long lastJavaHeap;
    private long lastNativeHeap;

    public void record(
            float deltaSeconds,
            long frameCpuNanos,
            long updateCpuNanos,
            long landscapeCpuNanos,
            long overlayCpuNanos,
            long hudCpuNanos) {

        long frameIntervalNanos = Math.max(
                0L,
                Math.round((double) deltaSeconds * 1_000_000_000.0));

        frames++;
        observedFrameNanos += frameIntervalNanos;
        maxObservedFrameNanos = Math.max(
                maxObservedFrameNanos,
                frameIntervalNanos);

        cpuNanos += frameCpuNanos;
        maxCpuNanos = Math.max(maxCpuNanos, frameCpuNanos);
        updateNanos += updateCpuNanos;
        maxUpdateNanos = Math.max(maxUpdateNanos, updateCpuNanos);
        landscapeNanos += landscapeCpuNanos;
        maxLandscapeNanos = Math.max(maxLandscapeNanos, landscapeCpuNanos);
        overlayNanos += overlayCpuNanos;
        maxOverlayNanos = Math.max(maxOverlayNanos, overlayCpuNanos);
        hudNanos += hudCpuNanos;
        maxHudNanos = Math.max(maxHudNanos, hudCpuNanos);

        lastJavaHeap = Gdx.app.getJavaHeap();
        lastNativeHeap = Gdx.app.getNativeHeap();
        minJavaHeap = Math.min(minJavaHeap, lastJavaHeap);
        maxJavaHeap = Math.max(maxJavaHeap, lastJavaHeap);

        long now = System.nanoTime();
        if (now - windowStartNanos < LOG_INTERVAL_NANOS) {
            return;
        }

        long sampleFrames = Math.max(1, frames);
        Gdx.app.log(
                "VisualizerFramePerf",
                "fps=" + Gdx.graphics.getFramesPerSecond()
                        + " frame avg/max="
                        + millis(observedFrameNanos / sampleFrames)
                        + "/" + millis(maxObservedFrameNanos) + "ms"
                        + " cpu avg/max="
                        + millis(cpuNanos / sampleFrames)
                        + "/" + millis(maxCpuNanos) + "ms"
                        + " update="
                        + millis(updateNanos / sampleFrames)
                        + "/" + millis(maxUpdateNanos) + "ms"
                        + " landscape="
                        + millis(landscapeNanos / sampleFrames)
                        + "/" + millis(maxLandscapeNanos) + "ms"
                        + " overlay="
                        + millis(overlayNanos / sampleFrames)
                        + "/" + millis(maxOverlayNanos) + "ms"
                        + " hud="
                        + millis(hudNanos / sampleFrames)
                        + "/" + millis(maxHudNanos) + "ms"
                        + " javaHeap min/max/current="
                        + mebibytes(minJavaHeap)
                        + "/" + mebibytes(maxJavaHeap)
                        + "/" + mebibytes(lastJavaHeap) + "MiB"
                        + " nativeHeap="
                        + mebibytes(lastNativeHeap) + "MiB");

        resetWindow(now);
    }

    private void resetWindow(
            long now) {

        windowStartNanos = now;
        frames = 0;
        observedFrameNanos = 0L;
        maxObservedFrameNanos = 0L;
        cpuNanos = 0L;
        maxCpuNanos = 0L;
        updateNanos = 0L;
        maxUpdateNanos = 0L;
        landscapeNanos = 0L;
        maxLandscapeNanos = 0L;
        overlayNanos = 0L;
        maxOverlayNanos = 0L;
        hudNanos = 0L;
        maxHudNanos = 0L;
        minJavaHeap = Long.MAX_VALUE;
        maxJavaHeap = 0L;
    }

    private static double millis(
            long nanos) {

        return Math.round(nanos / 10_000.0) / 100.0;
    }

    private static double mebibytes(
            long bytes) {

        if (bytes == Long.MAX_VALUE) {
            return 0.0;
        }
        return Math.round(bytes / 10_485.76) / 100.0;
    }
}
