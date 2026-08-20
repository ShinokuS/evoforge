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
        assertEquals(9L, result.worldBoundaryCellCount(),
                "corner cells touch two boundary edges but must count as one boundary-contact cell");
    }

    @Test
    void allWaterBodyGetsFiniteFallbackClearanceAndDistinctBoundaryCells() {
        WorldBounds bounds = new WorldBounds(0, 4, 0, 2, -4, 4);
        int[] labels = new int[15];
        StandingWaterBody body = new StandingWaterBody(0, 15L, 0L, true, 0, 4, 0, 2);
        StandingWaterTopology water = new DenseStandingWaterTopology(bounds, labels, List.of(body));

        StandingWaterMorphology result = StandingWaterMorphologyAnalyzer.standard()
                .analyze(water)
                .morphology(0);

        assertEquals(2, result.maximumInteriorClearanceCells());
        assertEquals(12L, result.worldBoundaryCellCount());
    }

    @Test
    void balancedCalibrationUsesSublinearBoundaryOpeningScale() {
        StandingWaterExternalSinkCalibrator calibrator = StandingWaterExternalSinkCalibrator.standard();
        StandingWaterExternalSinkRecipe recipe = StandingWaterExternalSinkRecipe.balanced();

        StandingWaterExternalSinkCalibration small = calibrator.calibrate(
                new WorldBounds(0, 63, 0, 63, -8, 8), recipe);
        StandingWaterExternalSinkCalibration medium = calibrator.calibrate(
                new WorldBounds(0, 299, 0, 299, -8, 8), recipe);
        StandingWaterExternalSinkCalibration large = calibrator.calibrate(
                new WorldBounds(0, 499, 0, 499, -8, 8), recipe);
        StandingWaterExternalSinkCalibration huge = calibrator.calibrate(
                new WorldBounds(0, 9_999, 0, 9_999, -8, 8), recipe);

        assertEquals(36, small.minimumBoundaryContactCells());
        assertEquals(78, medium.minimumBoundaryContactCells());
        assertEquals(100, large.minimumBoundaryContactCells());
        assertEquals(448, huge.minimumBoundaryContactCells(),
                "a huge world must not require a linear thousands-of-cells opening");
        assertEquals(2, large.minimumClearanceCells());
    }

    @Test
    void externalRoleDependsOnBoundaryOpeningRatherThanWaterBodyArea() {
        WorldBounds bounds = new WorldBounds(0, 499, 0, 499, -8, 8);
        StandingWaterTopology water = bodiesOnlyTopology(bounds, List.of(
                body(0, 20_000L, true),
                body(1, 20_000L, true),
                body(2, 500L, true),
                body(3, 20_000L, false)));
        StandingWaterMorphologyTopology morphology = new DenseStandingWaterMorphologyTopology(
                bounds,
                List.of(
                        new StandingWaterMorphology(0, 10, 30),
                        new StandingWaterMorphology(1, 1, 150),
                        new StandingWaterMorphology(2, 8, 100),
                        new StandingWaterMorphology(3, 20, 200)));
        StandingWaterExternalSinkCalibration calibration =
                StandingWaterExternalSinkCalibrator.standard().calibrate(
                        bounds,
                        StandingWaterExternalSinkRecipe.balanced());

        StandingWaterExternalSinkTopology sinks = StandingWaterExternalSinkResolver.standard()
                .resolve(water, morphology, calibration);

        assertFalse(sinks.isExternalSink(0),
                "even a huge edge lake with only 30 boundary cells must not become external on 500x500");
        assertFalse(sinks.isExternalSink(1),
                "a long but one-cell-clearance boundary trace must not become external");
        assertTrue(sinks.isExternalSink(2),
                "100 boundary cells with real interior width is a broad enough 500x500 opening");
        assertFalse(sinks.isExternalSink(3),
                "internal water is never external merely because it is large and broad");
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
