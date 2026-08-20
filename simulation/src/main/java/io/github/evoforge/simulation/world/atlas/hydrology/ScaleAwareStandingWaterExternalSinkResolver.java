package io.github.evoforge.simulation.world.atlas.hydrology;

/**
 * Resolves external drainage sinks from a sufficiently broad opening onto the finite world edge.
 *
 * <p>Neither boundary contact nor water-body area alone is sufficient. A body must expose enough
 * distinct water cells on the world boundary for the calibrated world scale and retain a minimal
 * interior width. This keeps a large lake clipped by a short map edge from becoming a global
 * terminal merely because it happens to touch the finite domain boundary.</p>
 */
public final class ScaleAwareStandingWaterExternalSinkResolver
        implements StandingWaterExternalSinkResolver {
    static final ScaleAwareStandingWaterExternalSinkResolver INSTANCE =
            new ScaleAwareStandingWaterExternalSinkResolver();

    public ScaleAwareStandingWaterExternalSinkResolver() {
    }

    @Override
    public StandingWaterExternalSinkTopology resolve(
            StandingWaterTopology standingWater,
            StandingWaterMorphologyTopology morphology,
            StandingWaterExternalSinkCalibration calibration) {
        if (standingWater == null || morphology == null || calibration == null) {
            throw new IllegalArgumentException("external-sink resolver inputs must not be null");
        }
        if (!standingWater.bounds().equals(morphology.bounds())
                || standingWater.bodyCount() != morphology.bodyCount()) {
            throw new IllegalArgumentException("external-sink inputs must describe the same bodies");
        }

        boolean[] sinks = new boolean[standingWater.bodyCount()];
        for (int bodyId = 0; bodyId < standingWater.bodyCount(); bodyId++) {
            StandingWaterBody body = standingWater.body(bodyId);
            StandingWaterMorphology bodyMorphology = morphology.morphology(bodyId);
            sinks[bodyId] = body.touchesWorldBoundary()
                    && bodyMorphology.worldBoundaryCellCount()
                    >= calibration.minimumBoundaryContactCells()
                    && bodyMorphology.maximumInteriorClearanceCells()
                    >= calibration.minimumClearanceCells();
        }
        return new DenseStandingWaterExternalSinkTopology(standingWater.bounds(), sinks);
    }
}
