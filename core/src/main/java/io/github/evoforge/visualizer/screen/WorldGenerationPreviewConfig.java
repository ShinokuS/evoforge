package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable generator inputs captured when a world preview is generated. */
record WorldGenerationPreviewConfig(
        int width,
        int length,
        long seed,
        int coveragePpm,
        int scalePpm,
        int fragmentationPpm,
        int reliefPpm,
        int localReliefPpm,
        int landformScalePpm,
        int ruggednessPpm) {

    WorldGenerationPreviewConfig {
        if (width <= 0 || length <= 0) {
            throw new IllegalArgumentException("preview width and length must be positive");
        }
        requirePpm(coveragePpm, "coveragePpm");
        requirePpm(scalePpm, "scalePpm");
        requirePpm(fragmentationPpm, "fragmentationPpm");
        requirePpm(reliefPpm, "reliefPpm");
        requirePpm(localReliefPpm, "localReliefPpm");
        requirePpm(landformScalePpm, "landformScalePpm");
        requirePpm(ruggednessPpm, "ruggednessPpm");
    }

    WorldBounds bounds() {
        int minX = -width / 2;
        int minY = -length / 2;
        return new WorldBounds(
                minX,
                minX + width - 1,
                minY,
                minY + length - 1,
                -12,
                12);
    }

    WorldGenerationIntent intent() {
        return new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(coveragePpm),
                NormalizedValue.ofPartsPerMillion(scalePpm),
                NormalizedValue.ofPartsPerMillion(fragmentationPpm),
                NormalizedValue.ofPartsPerMillion(reliefPpm),
                NormalizedValue.ofPartsPerMillion(localReliefPpm),
                NormalizedValue.ofPartsPerMillion(landformScalePpm),
                NormalizedValue.ofPartsPerMillion(ruggednessPpm));
    }

    long columnCount() {
        return (long) width * length;
    }

    int maxHorizontalDimension() {
        return Math.max(width, length);
    }

    private static void requirePpm(int value, String name) {
        if (value < 0 || value > NormalizedValue.SCALE) {
            throw new IllegalArgumentException(name + " must be in [0, 1000000]");
        }
    }
}
