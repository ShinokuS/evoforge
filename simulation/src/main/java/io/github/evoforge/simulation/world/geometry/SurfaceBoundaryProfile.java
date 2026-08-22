package io.github.evoforge.simulation.world.geometry;

/**
 * Cell-local height profile of a terrain surface along one horizontal cell boundary.
 *
 * <p>The two endpoint heights are ordered along the canonical world axis transverse
 * to the requested face: Y for X faces and X for Y faces. This makes opposite faces
 * directly comparable without Shape-type knowledge or orientation-specific cases in
 * consumers.</p>
 */
public record SurfaceBoundaryProfile(
        int negativeAxisHeight,
        int positiveAxisHeight) {

    public SurfaceBoundaryProfile {
        CellSpace.requireHeight(negativeAxisHeight);
        CellSpace.requireHeight(positiveAxisHeight);
    }

    public static SurfaceBoundaryProfile flat(int localHeight) {
        int height = CellSpace.requireHeight(localHeight);
        return new SurfaceBoundaryProfile(height, height);
    }

    /**
     * Returns whether two boundary profiles occupy the same world-space surface line.
     * Base Z values are terrain-anchor cell coordinates, not standing-cell coordinates.
     */
    public boolean alignsWith(
            int baseZ,
            SurfaceBoundaryProfile other,
            int otherBaseZ) {
        if (other == null) throw new IllegalArgumentException("other profile must not be null");
        long base = Math.multiplyExact((long) baseZ, CellSpace.FULL_HEIGHT);
        long otherBase = Math.multiplyExact((long) otherBaseZ, CellSpace.FULL_HEIGHT);
        return base + negativeAxisHeight == otherBase + other.negativeAxisHeight
                && base + positiveAxisHeight == otherBase + other.positiveAxisHeight;
    }
}
