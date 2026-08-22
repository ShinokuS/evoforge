package io.github.evoforge.simulation.mechanics.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.liquid.LiquidTransportDefinitions;
import io.github.evoforge.simulation.world.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.soil.storage.SparseSoilLiquidStorage;
import io.github.evoforge.simulation.world.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.soil.SoilProperties;

final class SoilLiquidInfiltrationSystemTest {

    private static final MaterialDefinitionId SOIL = MaterialDefinitionId.of(0);
    private static final LiquidTypeId WATER = LiquidTypeId.of("water");
    private static final LiquidTypeId BLOOD = LiquidTypeId.of("blood");

    @Test
    void nonWaterLiquidUsesTheSameSoilInfiltrationMechanic() {
        Fixture fixture = fixture(100_000, 30_000, referenceTransport());
        fixture.free.addAtMost(BLOOD, 0, 0, 0, 20_000);

        assertEquals(20_000L, fixture.infiltration.update());
        assertEquals(0, fixture.free.lookup().amountOf(BLOOD, 0, 0, 0));
        assertEquals(20_000, fixture.retained.lookup().amountOf(BLOOD, 0, 0, -1));
        assertEquals(20_000, fixture.retained.lookup().totalAmount(0, 0, -1));
    }

    @Test
    void retainedWaterAndBloodCompeteForOneSharedPoreCapacity() {
        Fixture fixture = fixture(10_000, 10_000, referenceTransport());
        fixture.free.addAtMost(WATER, 0, 0, 0, 6_000);
        assertEquals(6_000L, fixture.infiltration.update());

        fixture.free.addAtMost(BLOOD, 0, 0, 0, 8_000);
        assertEquals(4_000L, fixture.infiltration.update());

        assertEquals(6_000, fixture.retained.lookup().amountOf(WATER, 0, 0, -1));
        assertEquals(4_000, fixture.retained.lookup().amountOf(BLOOD, 0, 0, -1));
        assertEquals(10_000, fixture.retained.lookup().totalAmount(0, 0, -1));
        assertEquals(4_000, fixture.free.lookup().amountOf(BLOOD, 0, 0, 0));
    }

    @Test
    void excessViscousLiquidRemainsFreeAfterPermeabilityBoundedUptake() {
        LiquidTransportDefinitions transport = new LiquidTransportDefinitions();
        transport.put(WATER, LiquidTransportProperties.reference());
        transport.put(
                BLOOD,
                LiquidTransportProperties.ofKinematicViscosity(
                        LiquidTransportProperties.REFERENCE_KINEMATIC_VISCOSITY * 2L));
        Fixture fixture = fixture(100_000, 6_000, transport);
        fixture.free.addAtMost(BLOOD, 0, 0, 0, 10_000);

        assertEquals(3_000L, fixture.infiltration.update());
        assertEquals(3_000, fixture.retained.lookup().amountOf(BLOOD, 0, 0, -1));
        assertEquals(7_000, fixture.free.lookup().amountOf(BLOOD, 0, 0, 0));
    }

    @Test
    void onePassHandlesDifferentActiveLiquidTypesOnDifferentSoilCells() {
        Fixture fixture = fixture(100_000, 30_000, referenceTransport());
        fixture.free.addAtMost(WATER, 0, 0, 0, 7_000);
        fixture.free.addAtMost(BLOOD, 1, 0, 0, 9_000);

        assertEquals(16_000L, fixture.infiltration.update());
        assertEquals(7_000, fixture.retained.lookup().amountOf(WATER, 0, 0, -1));
        assertEquals(9_000, fixture.retained.lookup().amountOf(BLOOD, 1, 0, -1));
    }

    private static Fixture fixture(
            int capacity,
            int permeability,
            LiquidTransportDefinitions transport) {
        TerrainLookup terrain = terrain();
        SoilLiquidSystem retained = new SoilLiquidSystem(
                new SparseSoilLiquidStorage(),
                (x, y, z) -> terrain.contains(x, y, z)
                        ? new SoilProperties(capacity, permeability)
                        : null,
                transport);
        LiquidSystem free = new LiquidSystem(
                new SparseLiquidStorage(),
                (x, y, z) -> null);
        return new Fixture(
                free,
                retained,
                new SoilLiquidInfiltrationSystem(free, terrain, retained));
    }

    private static LiquidTransportDefinitions referenceTransport() {
        LiquidTransportDefinitions transport = new LiquidTransportDefinitions();
        transport.put(WATER, LiquidTransportProperties.reference());
        transport.put(BLOOD, LiquidTransportProperties.reference());
        return transport;
    }

    private static TerrainLookup terrain() {
        return (x, y, z) -> z == -1 ? SOIL : null;
    }

    private record Fixture(
            LiquidSystem free,
            SoilLiquidSystem retained,
            SoilLiquidInfiltrationSystem infiltration) {
    }
}
