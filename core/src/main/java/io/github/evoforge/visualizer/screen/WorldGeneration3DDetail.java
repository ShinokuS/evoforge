package io.github.evoforge.visualizer.screen;

/** Live presentation-only mesh density for the 3D world-generation preview. */
final class WorldGeneration3DDetail {
    /**
     * Initial large-world preview density. Detailed 2D views refine asynchronously from authoritative
     * terrain, so generation should not pay for a 160x160 presentation lattice before the world can
     * be shown. Developers can still raise the 3D mesh density explicitly from the live tuning UI.
     */
    static final int DEFAULT_MAX_AXIS_SAMPLES = 96;
    static final int MIN_AXIS_SAMPLES = 64;
    static final int MAX_AXIS_SAMPLES = 512;

    private static volatile int maxAxisSamples = DEFAULT_MAX_AXIS_SAMPLES;

    private WorldGeneration3DDetail() {
    }

    static int maxAxisSamples() {
        return maxAxisSamples;
    }

    static void maxAxisSamples(int value) {
        if (value < MIN_AXIS_SAMPLES || value > MAX_AXIS_SAMPLES) {
            throw new IllegalArgumentException(
                    "3D mesh axis samples must be between "
                            + MIN_AXIS_SAMPLES + " and " + MAX_AXIS_SAMPLES + ": " + value);
        }
        maxAxisSamples = value;
    }

    static void resetTuning() {
        maxAxisSamples = DEFAULT_MAX_AXIS_SAMPLES;
    }

    static int sampleCount(int dimension) {
        if (dimension <= 0) return 1;
        return Math.min(dimension, maxAxisSamples);
    }
}
