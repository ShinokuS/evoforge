package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class InlandBasinMorphologyCalibrationTest {

    @Test
    void basinRadiusTracksRealizedLandLinearScaleInsteadOfFixedRasterSize() {
        InlandBasinMorphologyCalibrator calibrator = InlandBasinMorphologyCalibrator.standard();
        InlandBasinMorphologyRecipe recipe = InlandBasinMorphologyRecipe.balanced();

        InlandBasinMorphologyCalibration small = calibrator.calibrate(field(150), recipe);
        InlandBasinMorphologyCalibration medium = calibrator.calibrate(field(500), recipe);
        InlandBasinMorphologyCalibration large = calibrator.calibrate(field(1500), recipe);

        assertTrue(medium.maximumRadiusCells() > small.maximumRadiusCells());
        assertTrue(large.maximumRadiusCells() > medium.maximumRadiusCells());
        assertTrue(large.maximumRadiusCells() >= 60,
                "1500-cell continental worlds need genuinely geographic basin scale");
        assertTrue(large.maximumDepthSubunits() > small.maximumDepthSubunits());
    }

    private static ElevationField field(int size) {
        int min = -size / 2;
        WorldBounds bounds = new WorldBounds(min, min + size - 1, min, min + size - 1, -32, 96);
        int oceanBand = Math.max(1, size / 12);
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return elevationSubunitsAt(x, y) < 0L ? -1 : 4;
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                int lx = x - bounds.minX();
                int ly = y - bounds.minY();
                return lx < oceanBand || ly < oceanBand
                        || lx >= size - oceanBand || ly >= size - oceanBand
                        ? -SUBUNITS_PER_CELL
                        : 4L * SUBUNITS_PER_CELL;
            }
        };
    }
}
