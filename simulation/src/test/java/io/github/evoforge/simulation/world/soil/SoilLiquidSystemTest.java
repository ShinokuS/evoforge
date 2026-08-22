package io.github.evoforge.simulation.world.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.liquid.LiquidTransportDefinitions;
import io.github.evoforge.simulation.world.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.soil.storage.SparseSoilLiquidStorage;

final class SoilLiquidSystemTest {

    private static final LiquidTypeId WATER = LiquidTypeId.of("water");
    private static final LiquidTypeId BLOOD = LiquidTypeId.of("blood");

    @Test
    void severalRetainedLiquidsShareOneMaterialOwnedPoreCapacity() {
        SoilLiquidSystem soil = soil(10_000, 10_000, referenceTransport());

        assertEquals(6_000, soil.infiltrateAtMost(WATER, 1, 2, 3, 6_000));
        assertEquals(4_000, soil.infiltrateAtMost(BLOOD, 1, 2, 3, 6_000));

        assertEquals(6_000, soil.lookup().amountOf(WATER, 1, 2, 3));
        assertEquals(4_000, soil.lookup().amountOf(BLOOD, 1, 2, 3));
        assertEquals(10_000, soil.lookup().totalAmount(1, 2, 3));
        assertEquals(1, soil.cells().occupiedCellCount());
        assertEquals(1, soil.cells().cellCount(WATER));
        assertEquals(1, soil.cells().cellCount(BLOOD));
    }

    @Test
    void sameMaterialPermeabilityProducesSlowerUptakeForMoreViscousLiquid() {
        LiquidTransportDefinitions transport = new LiquidTransportDefinitions();
        transport.put(WATER, LiquidTransportProperties.reference());
        transport.put(
                BLOOD,
                LiquidTransportProperties.ofKinematicViscosity(
                        LiquidTransportProperties.REFERENCE_KINEMATIC_VISCOSITY * 2L));
        SoilLiquidSystem soil = soil(100_000, 8_000, transport);

        assertEquals(8_000, soil.infiltrateAtMost(WATER, 0, 0, 0, 20_000));
        assertEquals(4_000, soil.infiltrateAtMost(BLOOD, 1, 0, 0, 20_000));
    }

    @Test
    void permeabilityAloneChangesUptakeForSameLiquid() {
        LiquidTransportDefinitions transport = referenceTransport();
        SoilLiquidSystem fast = soil(100_000, 12_000, transport);
        SoilLiquidSystem slow = soil(100_000, 3_000, transport);

        assertEquals(12_000, fast.infiltrateAtMost(WATER, 0, 0, 0, 20_000));
        assertEquals(3_000, slow.infiltrateAtMost(WATER, 0, 0, 0, 20_000));
    }

    @Test
    void removingOneConstituentPreservesOtherRetainedComposition() {
        SoilLiquidSystem soil = soil(20_000, 20_000, referenceTransport());
        soil.infiltrateAtMost(WATER, 0, 0, 0, 7_000);
        soil.infiltrateAtMost(BLOOD, 0, 0, 0, 5_000);

        assertEquals(5_000, soil.removeAtMost(BLOOD, 0, 0, 0, 20_000));

        assertEquals(7_000, soil.lookup().amountOf(WATER, 0, 0, 0));
        assertEquals(0, soil.lookup().amountOf(BLOOD, 0, 0, 0));
        assertEquals(7_000, soil.lookup().totalAmount(0, 0, 0));
        assertEquals(1, soil.cells().occupiedCellCount());
        assertEquals(0, soil.cells().cellCount(BLOOD));
    }

    private static SoilLiquidSystem soil(
            int capacity,
            int permeability,
            LiquidTransportDefinitions transport) {
        return new SoilLiquidSystem(
                new SparseSoilLiquidStorage(),
                (x, y, z) -> new SoilProperties(capacity, permeability),
                transport);
    }

    private static LiquidTransportDefinitions referenceTransport() {
        LiquidTransportDefinitions transport = new LiquidTransportDefinitions();
        transport.put(WATER, LiquidTransportProperties.reference());
        transport.put(BLOOD, LiquidTransportProperties.reference());
        return transport;
    }
}
