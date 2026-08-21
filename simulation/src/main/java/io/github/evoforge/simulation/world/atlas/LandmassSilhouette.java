package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/** Immutable V14 landmass silhouette fact produced before ordinary V12 relief synthesis. */
final class LandmassSilhouette {
    private static final int PPM = NormalizedValue.SCALE;

    private final WorldBounds bounds;
    private final int cellCount;
    private final boolean[] support;
    private final int[] potentialPpm;
    private final int supportCellCount;
    private final int influencePpm;
    private final boolean constrained;

    /**
     * Copy-safe constructor for callers that retain ownership of their input arrays.
     */
    LandmassSilhouette(
            WorldBounds bounds,
            boolean[] support,
            int[] potentialPpm,
            int supportCellCount,
            int influencePpm) {
        this(bounds, support, potentialPpm, supportCellCount, influencePpm, true);
    }

    /**
     * Transfers exclusive ownership of freshly produced silhouette arrays without copying them.
     *
     * <p>This is package-private on purpose: generation algorithms may use it only when they have
     * just allocated the arrays and never expose or mutate them again.</p>
     */
    static LandmassSilhouette takeOwnership(
            WorldBounds bounds,
            boolean[] support,
            int[] potentialPpm,
            int supportCellCount,
            int influencePpm) {
        return new LandmassSilhouette(
                bounds,
                support,
                potentialPpm,
                supportCellCount,
                influencePpm,
                false);
    }

    /**
     * Transfers ownership of a compact constrained silhouette whose positive potential entries are
     * exactly its support mask.
     *
     * <p>The accepted regularized-graph producer already assigns every supported cell a potential
     * of at least one and leaves every unsupported cell at zero. Keeping a second full-world
     * boolean mask would therefore duplicate the same fact. Explicit-support constructors remain
     * available for callers whose support and potential arrays intentionally have independent
     * semantics.</p>
     */
    static LandmassSilhouette takeOwnershipEncodedSupport(
            WorldBounds bounds,
            int[] potentialPpm,
            int supportCellCount,
            int influencePpm) {
        if (bounds == null || potentialPpm == null) {
            throw new IllegalArgumentException("landmass silhouette facts must not be null");
        }
        int expected = DenseElevationField.cellCount(bounds);
        if (potentialPpm.length != expected) {
            throw new IllegalArgumentException("landmass silhouette potential must match world bounds");
        }
        validateSupportCount(supportCellCount, expected);
        validateInfluence(influencePpm);

        int counted = 0;
        for (int potential : potentialPpm) {
            validatePotential(potential);
            if (potential > 0) counted++;
        }
        if (counted != supportCellCount) {
            throw new IllegalArgumentException(
                    "encoded landmass support count must match positive potential entries");
        }

        return new LandmassSilhouette(
                bounds,
                expected,
                null,
                potentialPpm,
                supportCellCount,
                influencePpm,
                true);
    }

    private LandmassSilhouette(
            WorldBounds bounds,
            boolean[] support,
            int[] potentialPpm,
            int supportCellCount,
            int influencePpm,
            boolean copyArrays) {
        if (bounds == null || support == null || potentialPpm == null) {
            throw new IllegalArgumentException("landmass silhouette facts must not be null");
        }
        int expected = DenseElevationField.cellCount(bounds);
        if (support.length != expected || potentialPpm.length != expected) {
            throw new IllegalArgumentException("landmass silhouette arrays must match world bounds");
        }
        validateSupportCount(supportCellCount, expected);
        validateInfluence(influencePpm);

        int counted = 0;
        for (int index = 0; index < expected; index++) {
            validatePotential(potentialPpm[index]);
            if (support[index]) counted++;
        }
        if (counted != supportCellCount) {
            throw new IllegalArgumentException("landmass silhouette support count must match support mask");
        }
        this.bounds = bounds;
        this.cellCount = expected;
        this.support = copyArrays ? Arrays.copyOf(support, support.length) : support;
        this.potentialPpm = copyArrays ? Arrays.copyOf(potentialPpm, potentialPpm.length) : potentialPpm;
        this.supportCellCount = supportCellCount;
        this.influencePpm = influencePpm;
        this.constrained = true;
    }

    private LandmassSilhouette(
            WorldBounds bounds,
            int cellCount,
            boolean[] support,
            int[] potentialPpm,
            int supportCellCount,
            int influencePpm,
            boolean constrained) {
        this.bounds = bounds;
        this.cellCount = cellCount;
        this.support = support;
        this.potentialPpm = potentialPpm;
        this.supportCellCount = supportCellCount;
        this.influencePpm = influencePpm;
        this.constrained = constrained;
    }

    /**
     * Lightweight sentinel for an unconstrained V12 call.
     *
     * <p>No per-cell support or potential arrays are required because every cell is supported and
     * silhouette influence is zero by definition.</p>
     */
    private LandmassSilhouette(WorldBounds bounds, int cellCount) {
        this(bounds, cellCount, null, null, cellCount, 0, false);
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
        if (!constrained) return true;
        return support != null ? support[index] : potentialPpm[index] > 0;
    }

    int potentialPpmAtIndex(int index) {
        requireIndex(index);
        return constrained ? potentialPpm[index] : PPM;
    }

    private static void validateSupportCount(int supportCellCount, int expected) {
        if (supportCellCount < 0 || supportCellCount > expected) {
            throw new IllegalArgumentException("landmass silhouette support count must fit bounds");
        }
    }

    private static void validateInfluence(int influencePpm) {
        if (influencePpm < 0 || influencePpm > PPM) {
            throw new IllegalArgumentException("landmass silhouette influence must be normalized");
        }
    }

    private static void validatePotential(int potentialPpm) {
        if (potentialPpm < 0 || potentialPpm > PPM) {
            throw new IllegalArgumentException("landmass silhouette potential must be normalized");
        }
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= cellCount) {
            throw new IllegalArgumentException("landmass silhouette index outside world domain: " + index);
        }
    }
}
