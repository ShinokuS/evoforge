package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;

final class WaterSurfaceLookupTest {

    @Test
    void tracksTopWetCellThroughExternalMutations() {
        WaterSystem water = water();
        WaterSurfaceLookup surfaces = water.surfaces();

        water.addAtMost(2, 3, -1, 100_000);
        water.addAtMost(2, 3, 5, 200_000);
        water.addAtMost(2, 3, 2, 300_000);

        assertTrue(surfaces.hasColumn(2, 3));
        assertEquals(5, surfaces.topZ(2, 3));
        assertEquals(1, surfaces.columnCount());

        water.removeAtMost(2, 3, 5, 200_000);
        assertEquals(2, surfaces.topZ(2, 3));

        water.removeAtMost(2, 3, 2, 300_000);
        water.removeAtMost(2, 3, -1, 100_000);
        assertFalse(surfaces.hasColumn(2, 3));
        assertEquals(0, surfaces.columnCount());
        assertThrows(
                IllegalArgumentException.class,
                () -> surfaces.topZ(2, 3));
    }

    @Test
    void flowReplacementUsesSameSurfaceBoundary() {
        WaterSystem water = water();
        WaterSurfaceLookup surfaces = water.surfaces();

        water.replaceFromFlow(new WaterCell(7, 8, 4), 250_000);
        water.replaceFromFlow(new WaterCell(7, 8, 9), 125_000);
        assertEquals(9, surfaces.topZ(7, 8));

        water.replaceFromFlow(new WaterCell(7, 8, 9), 0);
        assertEquals(4, surfaces.topZ(7, 8));
    }

    private static WaterSystem water() {
        return new WaterSystem(
                new SparseWaterStorage(),
                (x, y, z) -> null);
    }
}
