package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/** Immutable V14 landmass silhouette fact produced before ordinary V12 relief synthesis. */
final class LandmassSilhouette {
    private static final int PPM = NormalizedValue.SCALE;

    private final WorldBounds bounds;
    private final boolean[] support;
    private final int[] potentialPpm;
    private final int supportCellCount;
    private final int influencePpm;
    private final boolean constrained;

    LandmassSilhouette(
            WorldBounds bounds,
            boolean[] support,
            int[] potentialPpm,
            int supportCellCount,
            int influencePpm) {
        if (bounds == null || support == null || potentialPpm == null) {
            throw new IllegalArgumentException("landmass silhouette facts must not be null");
        }
        int expected = DenseElevationField.cellCount(bounds);
        if (support.length != expected || potentialPpm.length != expected) {
            throw new IllegalArgumentException("landmass silhouette arrays must match world bounds");
        }
        if (supportCellCount < 0 || supportCellCount > expected) {
            throw new IllegalArgumentException("landmass silhouette support count must fit bounds");
        }
        if (influencePpm < 0 || influencePpm > PPM) {
            throw new IllegalArgumentException("landmass silhouette influence must be normalized");
        }
        int counted = 0;
        for (int index = 0; index < expected; index++) {
            if (potentialPpm[index] < 0 || potentialPpm[index] > PPM) {
                throw new IllegalArgumentException("landmass silhouette potential must be normalized");
            }
            if (support[index]) counted++;
        }
        if (counted != supportCellCount) {
            throw new IllegalArgumentException("landmass silhouette support count must match support mask");
        }
        this.bounds = bounds;
        this.support = Arrays.copyOf(support, support.length);
        this.potentialPpm = Arrays.copyOf(potentialPpm, potentialPpm.length);
        this.supportCellCount = supportCellCount;
        this.influencePpm = influencePpm;
        this.constrained = true;
    }

    private LandmassSilhouette(WorldBounds bounds, int cellCount) {
        this.bounds = bounds;
        this.support = new boolean[cellCount];
        this.potentialPpm = new int[cellCount];
        Arrays.fill(this.support, true);
        Arrays.fill(this.potentialPpm, PPM);
        this.supportCellCount = cellCount;
        this.influencePpm = 0;
        this.constrained = false;
    }

    static LandmassSilhouette unconstrained(WorldBounds bounds) {
        if (bounds == null) throw new IllegalArgumentException("world bounds must not be null");
        return new LandmassSilhouette(bounds, DenseElevationField.cellCount(bounds));
    }

    WorldBounds bounds() {
        return bounds;
    }

    int supportCellCount() {
        return supportCellCount;
    }

    int influencePpm() {
        return influencePpm;
    }

    boolean constrained() {
        return constrained;
    }

    boolean supportsIndex(int index) {
        requireIndex(index);
        return support[index];
    }

    int potentialPpmAtIndex(int index) {
        requireIndex(index);
        return potentialPpm[index];
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= support.length) {
            throw new IllegalArgumentException("landmass silhouette index outside world domain: " + index);
        }
    }
}
