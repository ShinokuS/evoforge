package io.github.evoforge.simulation.world.liquid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.geometry.RampShape;
import org.junit.jupiter.api.Test;

final class JoinedRampLiquidFlowTest {
    private static final LiquidTypeId WATER = LiquidTypeId.of("joined-ramp-water");

    @Test
    void parallelRampCellsExchangeLiquidThroughTheirSharedSideGeometry() {
        GeometryLookup geometry = (x, y, z) ->
                z == 0 && y == 0 && (x == 0 || x == 1)
                        ? RampShape.POSITIVE_Y
                        : FullShape.INSTANCE;
        LiquidSystem liquids = new LiquidSystem(new SparseLiquidStorage(), geometry);
        LiquidFlowSystem flow = new LiquidFlowSystem(
                liquids,
                geometry,
                type -> LiquidTransportProperties.reference());

        liquids.addAtMost(WATER, 0, 0, 0, 400_000);
        long moved = flow.update();

        assertTrue(moved > 0L);
        assertTrue(liquids.lookup().amountOf(WATER, 1, 0, 0) > 0);
        assertEquals(
                400_000,
                liquids.lookup().amountOf(WATER, 0, 0, 0)
                        + liquids.lookup().amountOf(WATER, 1, 0, 0));
    }
}
