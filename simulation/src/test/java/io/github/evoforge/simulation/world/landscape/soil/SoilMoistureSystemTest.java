package io.github.evoforge.simulation.world.landscape.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilMoistureStorage;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;

final class SoilMoistureSystemTest {

    private static final LandscapeDefinitionId SOIL_ID =
            LandscapeDefinitionId.of(0);

    @Test
    void infiltrationIsBoundedByMaterialLimitAndCapacity() {
        LandscapeDefinitionId[] terrainId = {SOIL_ID};
        SoilMoistureSystem soil = system(
                (x, y, z) -> terrainId[0],
                new SoilHydrology(250_000, 150_000));

        assertEquals(150_000, soil.infiltrateAtMost(0, 0, 0, 200_000));
        assertEquals(100_000, soil.infiltrateAtMost(0, 0, 0, 200_000));
        assertEquals(0, soil.infiltrateAtMost(0, 0, 0, 1));
        assertEquals(250_000, soil.lookup().amount(0, 0, 0));
    }

    @Test
    void missingHydrologyDoesNotAbsorb() {
        SoilHydrologyDefinitions definitions = new SoilHydrologyDefinitions();
        definitions.freeze();
        SoilMoistureSystem soil = new SoilMoistureSystem(
                new SparseSoilMoistureStorage(),
                (x, y, z) -> SOIL_ID,
                definitions);

        assertEquals(0, soil.infiltrateAtMost(0, 0, 0, 100_000));
        assertEquals(0, soil.lookup().amount(0, 0, 0));
    }

    @Test
    void terrainChangeNeverSilentlyDeletesRetainedMoisture() {
        LandscapeDefinitionId[] terrainId = {SOIL_ID};
        TerrainLookup terrain = (x, y, z) -> terrainId[0];
        SoilMoistureSystem soil = system(
                terrain,
                new SoilHydrology(400_000, 400_000));

        soil.infiltrateAtMost(0, 0, 0, 300_000);
        terrainId[0] = null;

        assertEquals(300_000, soil.lookup().amount(0, 0, 0));
        assertEquals(0, soil.infiltrateAtMost(0, 0, 0, 1));
        assertEquals(300_000, soil.removeAtMost(0, 0, 0, 500_000));
        assertEquals(0, soil.lookup().amount(0, 0, 0));
    }

    @Test
    void negativeRequestsAreProgrammingErrors() {
        SoilMoistureSystem soil = system(
                (x, y, z) -> SOIL_ID,
                new SoilHydrology(400_000, 100_000));

        assertThrows(
                IllegalArgumentException.class,
                () -> soil.infiltrateAtMost(0, 0, 0, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> soil.removeAtMost(0, 0, 0, -1));
    }

    private static SoilMoistureSystem system(
            TerrainLookup terrain,
            SoilHydrology hydrology) {

        SoilHydrologyDefinitions definitions =
                new SoilHydrologyDefinitions();
        definitions.put(SOIL_ID, hydrology);
        definitions.freeze();

        return new SoilMoistureSystem(
                new SparseSoilMoistureStorage(),
                terrain,
                definitions);
    }
}
