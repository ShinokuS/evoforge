package io.github.evoforge.simulation.world.terrain.shape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class V13SparseShapeGenerationTest {

    @Test
    void v13SelectsIrregularSubsetOfOtherwiseCoherentSlopeGeometry() {
        ElevationField slope = new SyntheticSlope();
        TerrainShapeField dense = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V12)
                .generate(slope);
        TerrainShapeField sparse = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V13)
                .generate(slope);

        assertTrue(dense.overrideCount() > 100L,
                "synthetic surface must expose a meaningful coherent transition population");
        assertTrue(sparse.overrideCount() > 0L,
                "V13 must retain some material-agnostic transition geometry");
        assertTrue(sparse.overrideCount() * 2L < dense.overrideCount(),
                "V13 should not turn every geometrically eligible contour cell into an override");
    }

    private static final class SyntheticSlope implements ElevationField {
        private static final WorldBounds BOUNDS = new WorldBounds(0, 95, 0, 95, -12, 96);

        @Override
        public WorldBounds bounds() {
            return BOUNDS;
        }

        @Override
        public int elevationAt(int x, int y) {
            return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
        }

        @Override
        public long elevationSubunitsAt(int x, int y) {
            if (!contains(x, y)) throw new IllegalArgumentException("outside synthetic slope");
            return x * 250_000L + y * 12_000L;
        }
    }
}
