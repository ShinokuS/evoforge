package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.landscape.liquid.StandardLiquidTypes;
import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;

final class WaterLiquidFacadeTest {

    @Test
    void waterProjectionIgnoresOtherLiquidIdentityInSharedOwner() {
        LiquidSystem liquids = new LiquidSystem(
                new SparseLiquidStorage(),
                (x, y, z) -> null);
        WaterSystem water = new WaterSystem(liquids);
        LiquidTypeId blood = LiquidTypeId.of("blood");

        water.addAtMost(3, 4, 1, 100_000);
        liquids.addAtMost(blood, 3, 4, 5, 200_000);

        assertEquals(100_000, water.lookup().amount(3, 4, 1));
        assertEquals(0, water.lookup().amount(3, 4, 5));
        assertEquals(1, water.surfaces().topZ(3, 4));
        assertEquals(5, liquids.surfaces().topZ(3, 4));
        assertEquals(blood, liquids.surfaces().topType(3, 4));
        assertEquals(
                100_000,
                liquids.lookup().amountOf(StandardLiquidTypes.WATER, 3, 4, 1));
    }

    @Test
    void oneSharedSolverMovesSeveralTypesWhileWaterFacadeFiltersDiagnostics() {
        GeometryLookup geometry = (x, y, z) ->
                z == 0 && y == 0 && (x == 0 || x == 1 || x == 3 || x == 4)
                        ? null
                        : FullShape.INSTANCE;
        LiquidSystem liquids = new LiquidSystem(
                new SparseLiquidStorage(),
                geometry);
        WaterSystem water = new WaterSystem(liquids);
        LiquidTypeId blood = LiquidTypeId.of("blood");
        LiquidFlowSystem sharedFlow = new LiquidFlowSystem(liquids, geometry);
        WaterFlowSystem waterFlow = new WaterFlowSystem(sharedFlow);

        water.addAtMost(0, 0, 0, 400_000);
        liquids.addAtMost(blood, 3, 0, 0, 400_000);

        assertTrue(sharedFlow.update() > 0L);
        assertTrue(water.lookup().amount(1, 0, 0) > 0);
        assertTrue(liquids.lookup().amountOf(blood, 4, 0, 0) > 0);
        assertNotNull(waterFlow.flowLookup().find(1, 0, 0));
        assertNull(waterFlow.flowLookup().find(4, 0, 0));
    }
}
