package io.github.evoforge.simulation.world.terrain.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class SurfaceMorphologyGenerationStageTest {

    @Test
    void distinguishesConvexAndConcavePositionsFromPreciseElevation() {
        long cell = ElevationField.SUBUNITS_PER_CELL;
        WorldBounds bounds = new WorldBounds(0, 2, 0, 2, -2, 2);

        SurfaceMorphologyField bowl = new SurfaceMorphologyGenerationStage().generate(field(
                bounds,
                new long[] {
                        cell, cell, cell,
                        cell, 0L, cell,
                        cell, cell, cell
                }));
        assertEquals(cell, bowl.maximumNeighborSlopeSubunitsAt(1, 1));
        assertEquals(0L, bowl.convexitySubunitsAt(1, 1));
        assertEquals(cell, bowl.concavitySubunitsAt(1, 1));

        SurfaceMorphologyField ridge = new SurfaceMorphologyGenerationStage().generate(field(
                bounds,
                new long[] {
                        0L, 0L, 0L,
                        0L, cell, 0L,
                        0L, 0L, 0L
                }));
        assertEquals(cell, ridge.maximumNeighborSlopeSubunitsAt(1, 1));
        assertEquals(cell, ridge.convexitySubunitsAt(1, 1));
        assertEquals(0L, ridge.concavitySubunitsAt(1, 1));
    }

    @Test
    void verticalTranslationPreservesMorphology() {
        long cell = ElevationField.SUBUNITS_PER_CELL;
        WorldBounds bounds = new WorldBounds(0, 2, 0, 2, -10, 10);
        long[] base = {
                0L, 100_000L, 0L,
                100_000L, -200_000L, 100_000L,
                0L, 100_000L, 0L
        };
        long[] shifted = base.clone();
        for (int i = 0; i < shifted.length; i++) {
            shifted[i] = Math.addExact(shifted[i], 7L * cell);
        }

        SurfaceMorphologyField first =
                new SurfaceMorphologyGenerationStage().generate(field(bounds, base));
        SurfaceMorphologyField second =
                new SurfaceMorphologyGenerationStage().generate(field(bounds, shifted));

        for (int y = 0; y <= 2; y++) {
            for (int x = 0; x <= 2; x++) {
                assertEquals(
                        first.maximumNeighborSlopeSubunitsAt(x, y),
                        second.maximumNeighborSlopeSubunitsAt(x, y));
                assertEquals(
                        first.convexitySubunitsAt(x, y),
                        second.convexitySubunitsAt(x, y));
                assertEquals(
                        first.concavitySubunitsAt(x, y),
                        second.concavitySubunitsAt(x, y));
            }
        }
    }

    @Test
    void rejectsQueriesOutsideFieldBounds() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, 0, 0);
        SurfaceMorphologyField morphology = new SurfaceMorphologyGenerationStage().generate(
                field(bounds, new long[] {0L}));

        assertThrows(
                IllegalArgumentException.class,
                () -> morphology.maximumNeighborSlopeSubunitsAt(1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> morphology.convexitySubunitsAt(0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> morphology.concavitySubunitsAt(0, 1));
    }

    private static ElevationField field(WorldBounds bounds, long[] elevations) {
        int width = bounds.maxX() - bounds.minX() + 1;
        long[] copy = elevations.clone();
        return new ElevationField() {
            @Override public WorldBounds bounds() { return bounds; }

            @Override
            public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(
                        elevationSubunitsAt(x, y),
                        SUBUNITS_PER_CELL));
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                if (!contains(x, y)) {
                    throw new IllegalArgumentException("outside test elevation field");
                }
                return copy[(y - bounds.minY()) * width + (x - bounds.minX())];
            }
        };
    }
}
