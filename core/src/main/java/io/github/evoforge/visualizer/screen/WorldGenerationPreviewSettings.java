package io.github.evoforge.visualizer.screen;

/** Mutable draft settings edited by the world-generation development UI. */
final class WorldGenerationPreviewSettings {
    static final int MIN_HORIZONTAL_DIMENSION = 32;
    static final int MAX_HORIZONTAL_DIMENSION = 2_048;

    private int width = 64;
    private int length = 64;
    private long seed = 1L;
    private int coveragePpm = 350_000;
    private int scalePpm = 750_000;
    private int fragmentationPpm = 250_000;
    private int reliefPpm = 600_000;

    int width() {
        return width;
    }

    int length() {
        return length;
    }

    void width(int value) {
        width = requireDimension(value, "width");
    }

    void length(int value) {
        length = requireDimension(value, "length");
    }

    long seed() {
        return seed;
    }

    void seed(long value) {
        seed = value;
    }

    void nextSeed() {
        seed = seed == Long.MAX_VALUE ? Long.MIN_VALUE : seed + 1L;
    }

    int coveragePpm() {
        return coveragePpm;
    }

    void coveragePpm(int value) {
        coveragePpm = requirePpm(value, "coveragePpm");
    }

    int scalePpm() {
        return scalePpm;
    }

    void scalePpm(int value) {
        scalePpm = requirePpm(value, "scalePpm");
    }

    int fragmentationPpm() {
        return fragmentationPpm;
    }

    void fragmentationPpm(int value) {
        fragmentationPpm = requirePpm(value, "fragmentationPpm");
    }

    int reliefPpm() {
        return reliefPpm;
    }

    void reliefPpm(int value) {
        reliefPpm = requirePpm(value, "reliefPpm");
    }

    long columnCount() {
        return (long) width * length;
    }

    int maxHorizontalDimension() {
        return Math.max(width, length);
    }

    WorldGenerationPreviewConfig snapshot() {
        return new WorldGenerationPreviewConfig(
                width,
                length,
                seed,
                coveragePpm,
                scalePpm,
                fragmentationPpm,
                reliefPpm);
    }

    private static int requireDimension(int value, String name) {
        if (value < MIN_HORIZONTAL_DIMENSION || value > MAX_HORIZONTAL_DIMENSION) {
            throw new IllegalArgumentException(
                    name + " must be in [" + MIN_HORIZONTAL_DIMENSION + ", " + MAX_HORIZONTAL_DIMENSION + "]");
        }
        return value;
    }

    private static int requirePpm(int value, String name) {
        if (value < 0 || value > 1_000_000) {
            throw new IllegalArgumentException(name + " must be in [0, 1000000]");
        }
        return value;
    }
}
