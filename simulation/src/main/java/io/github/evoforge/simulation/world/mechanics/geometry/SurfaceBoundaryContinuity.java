package io.github.evoforge.simulation.world.mechanics.geometry;

/** Pure comparison of adjacent terrain surface boundaries in world space. */
public final class SurfaceBoundaryContinuity {
    private SurfaceBoundaryContinuity() {
    }

    public static boolean aligns(
            Shape first,
            int firstBaseZ,
            CellFace firstFace,
            Shape second,
            int secondBaseZ) {
        if (first == null || second == null) return false;
        if (firstFace == null || firstFace.dz() != 0) {
            throw new IllegalArgumentException("surface continuity requires a horizontal face");
        }
        return first.surfaceBoundaryProfile(firstFace).alignsWith(
                firstBaseZ,
                second.surfaceBoundaryProfile(firstFace.opposite()),
                secondBaseZ);
    }
}
