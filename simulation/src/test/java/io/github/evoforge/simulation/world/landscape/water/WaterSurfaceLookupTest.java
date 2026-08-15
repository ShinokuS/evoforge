package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
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
    void sharedLiquidFlowPublishesDestinationThroughWaterSurfaceProjection() {
        GeometryLookup geometry = (x, y, z) ->
                y == 0 && z == 0 && (x == 0 || x == 1)
                        ? null
                        : FullShape.INSTANCE;
        LiquidSystem liquids = new LiquidSystem(
                new SparseLiquidStorage(),
                geometry);
        WaterSystem water = new WaterSystem(liquids);
        LiquidFlowSystem flow = new LiquidFlowSystem(
                liquids,
                geometry,
                type -> LiquidTransportProperties.reference());
        WaterSurfaceLookup surfaces = water.surfaces();

        water.addAtMost(0, 0, 0, 400_000);
        assertFalse(surfaces.hasColumn(1, 0));

        assertTrue(flow.update() > 0L);

        assertTrue(water.lookup().amount(1, 0, 0) > 0);
        assertTrue(surfaces.hasColumn(1, 0));
        assertEquals(0, surfaces.topZ(1, 0));
    }

    private static WaterSystem water() {
        return new WaterSystem(new LiquidSystem(
                new SparseLiquidStorage(),
                (x, y, z) -> null));
    }
}
