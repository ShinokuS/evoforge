package io.github.evoforge.simulation.world.atlas.hydrology;

/**
 * Resolves external drainage sinks from broad edge openness plus water-body scale.
 *
 * <p>World-edge contact alone is deliberately insufficient. A body must satisfy three calibrated
 * conditions at once: enough total area, enough interior clearance, and one sufficiently long
 * contiguous opening along a single side of the finite world. This keeps both small edge lakes and
 * long narrow edge traces from becoming global drainage terminals.</p>
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
                    >= calibration.minimumClearanceCells()
                    && bodyMorphology.maximumBoundaryRunCells()
                    >= calibration.minimumBoundaryRunCells();
        }
        return new DenseStandingWaterExternalSinkTopology(standingWater.bounds(), sinks);
    }
}
