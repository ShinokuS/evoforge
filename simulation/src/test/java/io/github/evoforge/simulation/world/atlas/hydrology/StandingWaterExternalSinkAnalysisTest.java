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
        assertEquals(5, result.maximumBoundaryRunCells(),
                "the longest uninterrupted opening is the five-cell left edge");
    }

    @Test
    void morphologyKeepsSeparatedEdgeContactsOutOfOneArtificialOpening() {
        WorldBounds bounds = new WorldBounds(0, 6, 0, 6, -4, 4);
        int[] labels = new int[49];
        java.util.Arrays.fill(labels, StandingWaterTopology.NO_BODY);
        labels[0] = 0;
        labels[1] = 0;
        labels[5] = 0;
        labels[6] = 0;
        for (int x = 0; x <= 6; x++) labels[7 + x] = 0;
        StandingWaterBody body = new StandingWaterBody(0, 11L, 12L, true, 0, 6, 0, 1);
        StandingWaterTopology water = new DenseStandingWaterTopology(bounds, labels, List.of(body));

        StandingWaterMorphology result = StandingWaterMorphologyAnalyzer.standard()
                .analyze(water)
                .morphology(0);

        assertEquals(8L, result.worldBoundaryEdgeCount());
        assertEquals(2, result.maximumBoundaryRunCells(),
                "two separated top-edge openings must not be summed into one external opening");
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
        assertEquals(5, result.maximumBoundaryRunCells());
    }

    @Test
    void balancedCalibrationUsesSublinearAreaAndBoundaryOpeningScale() {
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

        assertEquals(1_229, small.minimumAreaCells());
        assertEquals(2, small.minimumClearanceCells());
        assertEquals(36, small.minimumBoundaryRunCells());

        assertEquals(27_000, medium.minimumAreaCells());
        assertEquals(5, medium.minimumClearanceCells());
        assertEquals(78, medium.minimumBoundaryRunCells());

        assertEquals(61_492, large.minimumAreaCells());
        assertEquals(8, large.minimumClearanceCells());
        assertEquals(101, large.minimumBoundaryRunCells());

        assertEquals(5_500_000, huge.minimumAreaCells(),
                "huge worlds must not require a fixed thirty-percent ocean footprint");
        assertEquals(150, huge.minimumClearanceCells());
        assertEquals(450, huge.minimumBoundaryRunCells());
    }

    @Test
    void externalSinkRequiresAreaOpeningAndInteriorBreadthTogether() {
        WorldBounds bounds = new WorldBounds(0, 499, 0, 499, -8, 8);
        StandingWaterTopology water = bodiesOnlyTopology(bounds, List.of(
                body(0, 80_000L, true),
                body(1, 20_000L, true),
                body(2, 80_000L, true),
                body(3, 80_000L, true),
                body(4, 100_000L, false)));
        StandingWaterMorphologyTopology morphology = new DenseStandingWaterMorphologyTopology(
                bounds,
                List.of(
                        new StandingWaterMorphology(0, 20, 40L, 30),
                        new StandingWaterMorphology(1, 20, 140L, 120),
                        new StandingWaterMorphology(2, 20, 140L, 120),
                        new StandingWaterMorphology(3, 3, 140L, 120),
                        new StandingWaterMorphology(4, 20, 140L, 120)));
        StandingWaterExternalSinkCalibration calibration =
                StandingWaterExternalSinkCalibrator.standard().calibrate(
                        bounds,
                        StandingWaterExternalSinkRecipe.balanced());

        StandingWaterExternalSinkTopology sinks = StandingWaterExternalSinkResolver.standard()
                .resolve(water, morphology, calibration);

        assertFalse(sinks.isExternalSink(0),
                "large edge lake with only a short opening must not become external");
        assertFalse(sinks.isExternalSink(1),
                "wide edge water with too little total area must remain lake-scale");
        assertTrue(sinks.isExternalSink(2),
                "only broad, sufficiently large, broadly open edge water may be external");
        assertFalse(sinks.isExternalSink(3),
                "long narrow edge water must fail the interior-breadth guard");
        assertFalse(sinks.isExternalSink(4),
                "large internal water is not external merely by scale");
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
