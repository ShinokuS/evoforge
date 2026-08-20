package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StandingWaterExternalSinkAnalysisTest {

    @Test
    void morphologyMeasuresInteriorWidthWithoutTreatingWorldEdgeAsShoreline() {
        WorldBounds bounds = new WorldBounds(0, 4, 0, 4, -4, 4);
        int[] labels = new int[25];
        java.util.Arrays.fill(labels, StandingWaterTopology.NO_BODY);
        for (int y = 0; y <= 4; y++) {
            for (int x = 0; x <= 2; x++) {
                labels[y * 5 + x] = 0;
            }
        }
        StandingWaterBody body = new StandingWaterBody(0, 15L, 5L, true, 0, 2, 0, 4);
        StandingWaterTopology water = new DenseStandingWaterTopology(bounds, labels, List.of(body));

        StandingWaterMorphology result = StandingWaterMorphologyAnalyzer.standard()
                .analyze(water)
                .morphology(0);

        assertEquals(3, result.maximumInteriorClearanceCells(),
                "clearance must measure toward in-world dry terrain, not the finite map edge");
        assertEquals(11L, result.worldBoundaryEdgeCount());
    }

    @Test
    void allWaterBodyGetsFiniteFallbackClearance() {
        WorldBounds bounds = new WorldBounds(0, 4, 0, 2, -4, 4);
        int[] labels = new int[15];
        StandingWaterBody body = new StandingWaterBody(0, 15L, 0L, true, 0, 4, 0, 2);
        StandingWaterTopology water = new DenseStandingWaterTopology(bounds, labels, List.of(body));

        StandingWaterMorphology result = StandingWaterMorphologyAnalyzer.standard()
                .analyze(water)
                .morphology(0);

        assertEquals(2, result.maximumInteriorClearanceCells());
        assertEquals(16L, result.worldBoundaryEdgeCount());
    }

    @Test
    void balancedCalibrationScalesExternalRoleWithWorldSize() {
        StandingWaterExternalSinkCalibrator calibrator = StandingWaterExternalSinkCalibrator.standard();
        StandingWaterExternalSinkRecipe recipe = StandingWaterExternalSinkRecipe.balanced();

        StandingWaterExternalSinkCalibration small = calibrator.calibrate(
                new WorldBounds(0, 63, 0, 63, -8, 8), recipe);
        StandingWaterExternalSinkCalibration medium = calibrator.calibrate(
                new WorldBounds(0, 299, 0, 299, -8, 8), recipe);
        StandingWaterExternalSinkCalibration large = calibrator.calibrate(
                new WorldBounds(0, 499, 0, 499, -8, 8), recipe);

        assertEquals(21, small.minimumAreaCells());
        assertEquals(2, small.minimumClearanceCells());
        assertEquals(450, medium.minimumAreaCells());
        assertEquals(5, medium.minimumClearanceCells());
        assertEquals(1_250, large.minimumAreaCells());
        assertEquals(8, large.minimumClearanceCells());
    }

    @Test
    void boundaryContactAloneNeverMakesSmallOrNarrowWaterAnExternalSink() {
        WorldBounds bounds = new WorldBounds(0, 299, 0, 299, -8, 8);
        StandingWaterTopology water = bodiesOnlyTopology(bounds, List.of(
                body(0, 100L, true),
                body(1, 2_000L, true),
                body(2, 2_000L, true),
                body(3, 5_000L, false)));
        StandingWaterMorphologyTopology morphology = new DenseStandingWaterMorphologyTopology(
                bounds,
                List.of(
                        new StandingWaterMorphology(0, 10, 2),
                        new StandingWaterMorphology(1, 3, 20),
                        new StandingWaterMorphology(2, 8, 20),
                        new StandingWaterMorphology(3, 20, 0)));
        StandingWaterExternalSinkCalibration calibration =
                StandingWaterExternalSinkCalibrator.standard().calibrate(
                        bounds,
                        StandingWaterExternalSinkRecipe.balanced());

        StandingWaterExternalSinkTopology sinks = StandingWaterExternalSinkResolver.standard()
                .resolve(water, morphology, calibration);

        assertFalse(sinks.isExternalSink(0), "small edge lake must not become a global terminal");
        assertFalse(sinks.isExternalSink(1), "narrow edge water must not become a global terminal");
        assertTrue(sinks.isExternalSink(2), "broad large edge water may act as external drainage");
        assertFalse(sinks.isExternalSink(3), "large internal water is not external merely by scale");
        assertEquals(1, sinks.externalSinkCount());
    }

    private static StandingWaterBody body(int id, long cells, boolean boundary) {
        return new StandingWaterBody(id, cells, 4L, boundary, 0, 0, 0, 0);
    }

    private static StandingWaterTopology bodiesOnlyTopology(
            WorldBounds bounds,
            List<StandingWaterBody> bodies) {
        return new StandingWaterTopology() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int bodyCount() {
                return bodies.size();
            }

            @Override
            public int bodyIdAt(int x, int y) {
                throw new UnsupportedOperationException("external-sink resolver does not consume labels");
            }

            @Override
            public StandingWaterBody body(int id) {
                return bodies.get(id);
            }
        };
    }
}
