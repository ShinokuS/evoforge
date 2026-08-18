package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Mutable development-tool settings for the generated-world preview footprint. */
final class WorldGenerationPreviewSettings {
    private static final int[] HORIZONTAL_PRESETS = {32, 64, 128, 256, 512, 1024, 2048};
    private static final int DEFAULT_PRESET_INDEX = 1;
    private static final int MIN_Z = -12;
    private static final int MAX_Z = 12;

    private int widthIndex = DEFAULT_PRESET_INDEX;
    private int heightIndex = DEFAULT_PRESET_INDEX;

    int width() {
        return HORIZONTAL_PRESETS[widthIndex];
    }

    int height() {
        return HORIZONTAL_PRESETS[heightIndex];
    }

    void adjustWidth(int direction) {
        widthIndex = clampIndex(widthIndex + Integer.signum(direction));
    }

    void adjustHeight(int direction) {
        heightIndex = clampIndex(heightIndex + Integer.signum(direction));
    }

    WorldBounds bounds() {
        int minX = -width() / 2;
        int minY = -height() / 2;
        return new WorldBounds(
                minX,
                minX + width() - 1,
                minY,
                minY + height() - 1,
                MIN_Z,
                MAX_Z);
    }

    long columnCount() {
        return (long) width() * height();
    }

    int maxHorizontalDimension() {
        return Math.max(width(), height());
    }

    private static int clampIndex(int index) {
        return Math.max(0, Math.min(HORIZONTAL_PRESETS.length - 1, index));
    }
}
