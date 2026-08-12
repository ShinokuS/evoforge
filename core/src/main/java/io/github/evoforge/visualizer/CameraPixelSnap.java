package io.github.evoforge.visualizer;

/** Pixel-aligns a presentation camera without quantizing its logical target. */
final class CameraPixelSnap {

    private CameraPixelSnap() {
    }

    static float axis(
            float targetCenter,
            float visibleWorldSize,
            int screenPixels) {

        if (!(visibleWorldSize > 0f)) {
            throw new IllegalArgumentException(
                    "visibleWorldSize must be positive");
        }
        if (screenPixels <= 0) {
            throw new IllegalArgumentException(
                    "screenPixels must be positive");
        }

        double worldPerPixel = (double) visibleWorldSize / screenPixels;
        double visibleMin = targetCenter - visibleWorldSize * 0.5;
        double snappedMin = Math.rint(visibleMin / worldPerPixel)
                * worldPerPixel;

        return (float) (snappedMin + visibleWorldSize * 0.5);
    }
}
