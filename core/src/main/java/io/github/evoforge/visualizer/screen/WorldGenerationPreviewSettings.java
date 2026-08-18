package io.github.evoforge.visualizer.screen;

/** Mutable draft settings edited by the world-generation development UI. */
final class WorldGenerationPreviewSettings {
    private static final Integer[] HORIZONTAL_PRESETS = {32, 64, 128, 256, 512, 1024, 2048};
    private static final int DEFAULT_PRESET_INDEX = 1;

    private int widthIndex = DEFAULT_PRESET_INDEX;
    private int heightIndex = DEFAULT_PRESET_INDEX;
    private long seed = 1L;
    private int coveragePpm = 350_000;
    private int scalePpm = 750_000;
    private int fragmentationPpm = 250_000;
    private int reliefPpm = 600_000;

    int width() {
        return HORIZONTAL_PRESETS[widthIndex];
    }

    int height() {
        return HORIZONTAL_PRESETS[heightIndex];
    }

    Integer[] horizontalPresets() {
        return HORIZONTAL_PRESETS.clone();
    }

    void width(int value) {
        widthIndex = presetIndex(value);
    }

    void height(int value) {
        heightIndex = presetIndex(value);
    }

    void adjustWidth(int direction) {
        widthIndex = clampIndex(widthIndex + Integer.signum(direction));
    }

    void adjustHeight(int direction) {
        heightIndex = clampIndex(heightIndex + Integer.signum(direction));
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
        return (long) width() * height();
    }

    int maxHorizontalDimension() {
        return Math.max(width(), height());
    }

    WorldGenerationPreviewConfig snapshot() {
        return new WorldGenerationPreviewConfig(
                width(),
                height(),
                seed,
                coveragePpm,
                scalePpm,
                fragmentationPpm,
                reliefPpm);
    }

    private static int presetIndex(int value) {
        for (int index = 0; index < HORIZONTAL_PRESETS.length; index++) {
            if (HORIZONTAL_PRESETS[index] == value) {
                return index;
            }
        }
        throw new IllegalArgumentException("unsupported preview dimension: " + value);
    }

    private static int clampIndex(int index) {
        return Math.max(0, Math.min(HORIZONTAL_PRESETS.length - 1, index));
    }

    private static int requirePpm(int value, String name) {
        if (value < 0 || value > 1_000_000) {
            throw new IllegalArgumentException(name + " must be in [0, 1000000]");
        }
        return value;
    }
}
