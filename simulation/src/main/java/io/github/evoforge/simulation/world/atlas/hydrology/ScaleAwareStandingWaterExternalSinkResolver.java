package io.github.evoforge.simulation.world.atlas.hydrology;

/**
 * Resolves external drainage sinks from boundary contact plus broad water-body scale.
 *
 * <p>Boundary contact alone is deliberately insufficient. A body must also satisfy calibrated
 * minimum area and interior-clearance thresholds. This keeps a small edge lake or narrow water
 * trace from becoming a global terminal merely because the finite world cuts through it.</p>
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
                    && body.cellCount() >= calibration.minimumAreaCells()
                    && bodyMorphology.maximumInteriorClearanceCells()
                    >= calibration.minimumClearanceCells();
        }
        return new DenseStandingWaterExternalSinkTopology(standingWater.bounds(), sinks);
    }
}
