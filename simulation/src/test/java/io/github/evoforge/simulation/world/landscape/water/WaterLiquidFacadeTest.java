package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.landscape.liquid.StandardLiquidTypes;
import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;

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
}
