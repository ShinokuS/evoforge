package io.github.evoforge.simulation.world.terrain.shape;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

/** V13 keeps coherent slope access distributed without turning coherent faces into solid Shape bands. */
final class V13SparseShapeGenerationTest {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;

    @Test
    void broadSlopeKeepsSmallEvenlyDistributedSubsetOfCoherentTransitionSites() {
        ElevationField elevation = broadCardinalSlope();
        TerrainShapeField v12 = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V12)
                .generate(elevation);
        TerrainShapeField v13 = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V13)
                .generate(elevation);

        long v12Overrides = 0L;
        long v13Overrides = 0L;
        long[] horizontalBands = new long[6];
        WorldBounds bounds = elevation.bounds();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (v12.shapeOverrideAt(x, y) != null) v12Overrides++;
                if (v13.shapeOverrideAt(x, y) == null) continue;

                v13Overrides++;
                horizontalBands[y / 5]++;
                assertNotNull(
                        v12.shapeOverrideAt(x, y),
                        "V13 sparse policy may only retain sites already accepted by coherent geometry");
            }
        }

        assertTrue(v12Overrides > 100L, "fixture must contain a broad coherent slope");
        assertTrue(v13Overrides > 0L, "V13 must keep some surface access on a broad slope");
        assertTrue(
                v13Overrides * 4L <= v12Overrides,
                "V13 must not convert most of a coherent face into surface Shapes");
        for (int band = 0; band < horizontalBands.length; band++) {
            assertTrue(
                    horizontalBands[band] > 0L,
                    "sparse access must be distributed along the full face; missing lateral band=" + band);
        }
    }

    private static ElevationField broadCardinalSlope() {
        WorldBounds bounds = new WorldBounds(0, 60, 0, 29, -2, 20);
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), CELL));
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                if (!contains(x, y)) {
                    throw new IllegalArgumentException("coordinate outside synthetic slope");
                }
                return CELL + (long) x * CELL / 5L;
            }
        };
    }
}
