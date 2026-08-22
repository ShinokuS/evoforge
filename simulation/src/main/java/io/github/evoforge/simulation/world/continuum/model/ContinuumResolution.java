package io.github.evoforge.simulation.world.continuum.model;

/**
 * Nested technical sampling resolution for Continuum queries.
 *
 * <p>Level 0 samples every world coordinate. Each coarser level doubles the sample spacing.
 * This is representation policy for bounded queries, not simulation fidelity or geography semantics.</p>
 */
public record ContinuumResolution(int level) {
    public static final int MAX_LEVEL = 62;

    public ContinuumResolution {
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("resolution level must be in [0, " + MAX_LEVEL + "]");
        }
    }

    public static ContinuumResolution exact() {
        return new ContinuumResolution(0);
    }

    public long step() {
        return 1L << level;
    }

    public boolean isExact() {
        return level == 0;
    }

    public ContinuumResolution coarser() {
        if (level == MAX_LEVEL) {
            throw new IllegalStateException("already at coarsest representable resolution");
        }
        return new ContinuumResolution(level + 1);
    }

    public ContinuumResolution finer() {
        if (level == 0) {
            throw new IllegalStateException("exact resolution has no finer level");
        }
        return new ContinuumResolution(level - 1);
    }
}
