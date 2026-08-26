package io.github.evoforge.simulation.world.geophysics;

/**
 * Consumer-neutral structural geophysical context at one world coordinate.
 *
 * <p>This is Stage 5 cause, not Terrain result. It gives later stages a reproducible macro
 * structural context without exposing the hidden reconstruction algorithm or allocating a
 * full-world plate/region raster.</p>
 */
public record MacroGeophysicalStructure(
        double continentalSupport,
        double marginInfluence,
        MacroGeophysicalRegionId primaryRegion,
        MacroGeophysicalRegionId secondaryRegion,
        double boundaryInfluence,
        MacroGeophysicalBoundaryRegime boundaryRegime,
        double boundaryStrength,
        double boundaryNormalX,
        double boundaryNormalY) {

    public MacroGeophysicalStructure {
        if (!Double.isFinite(continentalSupport)
                || continentalSupport < -1.0d
                || continentalSupport > 1.0d) {
            throw new IllegalArgumentException("continentalSupport must be finite in [-1, 1]");
        }
        requireUnitInterval("marginInfluence", marginInfluence);
        requireUnitInterval("boundaryInfluence", boundaryInfluence);
        requireUnitInterval("boundaryStrength", boundaryStrength);
        if (primaryRegion == null || secondaryRegion == null || boundaryRegime == null) {
            throw new IllegalArgumentException("structural identities/regime must not be null");
        }
        if (primaryRegion.equals(secondaryRegion)) {
            throw new IllegalArgumentException("primary and secondary structural regions must differ");
        }
        if (!Double.isFinite(boundaryNormalX) || !Double.isFinite(boundaryNormalY)) {
            throw new IllegalArgumentException("boundary normal must be finite");
        }
        double length = Math.hypot(boundaryNormalX, boundaryNormalY);
        if (Math.abs(length - 1.0d) > 1.0e-9d) {
            throw new IllegalArgumentException("boundary normal must be unit length");
        }
        if (boundaryRegime == MacroGeophysicalBoundaryRegime.INTERIOR
                && boundaryInfluence >= 0.05d) {
            throw new IllegalArgumentException("INTERIOR is reserved for negligible boundary influence");
        }
        if (boundaryRegime != MacroGeophysicalBoundaryRegime.INTERIOR
                && boundaryInfluence < 0.05d) {
            throw new IllegalArgumentException("active boundary regime requires meaningful influence");
        }
    }

    private static void requireUnitInterval(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be finite in [0, 1]");
        }
    }
}
