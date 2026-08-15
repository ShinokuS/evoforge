package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;

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
    void iteratesWetColumnsInStableCoordinateOrder() {
        WaterSystem water = water();
        WaterSurfaceLookup surfaces = water.surfaces();

        water.addAtMost(2, 4, -3, 100_000);
        water.addAtMost(-5, 7, 8, 100_000);
        water.addAtMost(2, -1, 6, 100_000);
        water.addAtMost(2, -1, 9, 100_000);

        List<String> visited = new ArrayList<>();
        surfaces.forEach((x, y, z) ->
                visited.add(x + ":" + y + ":" + z));

        assertEquals(
                List.of(
                        "-5:7:8",
                        "2:-1:9",
                        "2:4:-3"),
                visited);
    }

    @Test
    void sharedLiquidFlowUpdatesTheSameWaterSurfaceProjection() {
        GeometryLookup geometry = (x, y, z) ->
                x == 7 && y == 8 && (z == 4 || z == 5)
                        ? null
                        : FullShape.INSTANCE;
        WaterSystem water = new WaterSystem(new SparseWaterStorage(), geometry);
        WaterFlowSystem flow = new WaterFlowSystem(water, geometry);
        WaterSurfaceLookup surfaces = water.surfaces();

        water.addAtMost(7, 8, 5, 250_000);
        assertEquals(5, surfaces.topZ(7, 8));

        for (int step = 0; step < 100 && flow.activeCellCount() > 0; step++) {
            flow.update();
        }

        assertEquals(4, surfaces.topZ(7, 8));
        assertEquals(250_000, water.lookup().amount(7, 8, 4));
        assertEquals(0, water.lookup().amount(7, 8, 5));
    }

    private static WaterSystem water() {
        return new WaterSystem(
                new SparseWaterStorage(),
                (x, y, z) -> null);
    }
}
