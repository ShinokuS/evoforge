package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable generator inputs captured when a world preview is generated. */
record WorldGenerationPreviewConfig(
        int width,
        int height,
        long seed,
        int coveragePpm,
        int scalePpm,
        int fragmentationPpm,
        int reliefPpm) {

    WorldGenerationPreviewConfig {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("preview dimensions must be positive");
        }
        requirePpm(coveragePpm, "coveragePpm");
        requirePpm(scalePpm, "scalePpm");
        requirePpm(fragmentationPpm, "fragmentationPpm");
        requirePpm(reliefPpm, "reliefPpm");
    }

    WorldBounds bounds() {
        int minX = -width / 2;
        int minY = -height / 2;
        return new WorldBounds(
                minX,
                minX + width - 1,
                minY,
                minY + height - 1,
                -12,
                12);
    }

    WorldGenerationIntent intent() {
        return new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(coveragePpm),
                NormalizedValue.ofPartsPerMillion(scalePpm),
                NormalizedValue.ofPartsPerMillion(fragmentationPpm),
                NormalizedValue.ofPartsPerMillion(reliefPpm));
    }

    long columnCount() {
        return (long) width * height;
    }

    int maxHorizontalDimension() {
        return Math.max(width, height);
    }

    private static void requirePpm(int value, String name) {
        if (value < 0 || value > NormalizedValue.SCALE) {
            throw new IllegalArgumentException(name + " must be in [0, 1000000]");
        }
    }
}
