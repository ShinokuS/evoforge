package io.github.evoforge.simulation.world.liquid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;

final class LiquidSystemTest {

    private static final GeometryLookup OPEN_GEOMETRY = (x, y, z) -> null;
    private static final LiquidTypeId WINE = LiquidTypeId.of("wine");
    private static final LiquidTypeId BLOOD = LiquidTypeId.of("blood");

    @Test
    void arbitraryLiquidUsesSameFiniteGeometryBoundedStorage() {
        LiquidSystem liquids = liquids();

        assertEquals(700_000, liquids.addAtMost(WINE, 1, 2, 3, 700_000));
        assertEquals(300_000, liquids.addAtMost(WINE, 1, 2, 3, 700_000));
        assertEquals(CellVolume.FULL, liquids.lookup().amount(1, 2, 3));
        assertEquals(WINE, liquids.lookup().typeAt(1, 2, 3));

        assertEquals(250_000, liquids.removeAtMost(WINE, 1, 2, 3, 250_000));
        assertEquals(750_000, liquids.lookup().amountOf(WINE, 1, 2, 3));
    }

    @Test
    void unlikeLiquidCannotImplicitlyOverwriteOrMixOccupiedCell() {
        LiquidSystem liquids = liquids();
        liquids.addAtMost(WINE, 0, 0, 0, 400_000);

        assertEquals(0, liquids.addAtMost(BLOOD, 0, 0, 0, 200_000));
        assertEquals(WINE, liquids.lookup().typeAt(0, 0, 0));
        assertEquals(400_000, liquids.lookup().amount(0, 0, 0));
        assertEquals(0, liquids.lookup().amountOf(BLOOD, 0, 0, 0));
    }

    @Test
    void removingLastVolumeRestoresDryUntypedCell() {
        LiquidSystem liquids = liquids();
        liquids.addAtMost(BLOOD, 4, 5, 6, 10_000);

        assertEquals(10_000, liquids.removeAtMost(BLOOD, 4, 5, 6, CellVolume.FULL));
        assertEquals(0, liquids.lookup().amount(4, 5, 6));
        assertNull(liquids.lookup().typeAt(4, 5, 6));
    }

    private static LiquidSystem liquids() {
        return new LiquidSystem(new SparseLiquidStorage(), OPEN_GEOMETRY);
    }
}
