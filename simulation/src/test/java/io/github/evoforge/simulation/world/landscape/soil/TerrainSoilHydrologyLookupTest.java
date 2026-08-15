package io.github.evoforge.simulation.world.landscape.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;

final class TerrainSoilHydrologyLookupTest {

    @Test
    void sameSeedAndCoordinatesResolveIdenticalCapacity() {
        LandscapeDefinitionId id = LandscapeDefinitionId.of(0);
        SoilHydrologyDefinitions definitions = new SoilHydrologyDefinitions();
        definitions.put(id, new SoilHydrology(120_000, 3_000));
        SoilHydrologyVariationDefinitions variations =
                new SoilHydrologyVariationDefinitions();
        variations.put(id, new SoilHydrologyVariation(42L, 20_000));
        TerrainLookup terrain = (x, y, z) -> id;

        TerrainSoilHydrologyLookup first =
                new TerrainSoilHydrologyLookup(
                        terrain,
                        definitions,
                        variations);
        TerrainSoilHydrologyLookup second =
                new TerrainSoilHydrologyLookup(
                        terrain,
                        definitions,
                        variations);

        Set<Integer> observed = new HashSet<>();
        for (int x = -8; x <= 8; x++) {
            SoilHydrology a = first.find(x, 3, -1);
            SoilHydrology b = second.find(x, 3, -1);
            assertEquals(a, b);
            assertTrue(a.capacity() >= 100_000);
            assertTrue(a.capacity() <= 140_000);
            assertEquals(3_000, a.infiltrationLimit());
            observed.add(a.capacity());
        }

        assertTrue(
                observed.size() > 1,
                "coordinate-local capacity variation should not collapse to one value");
    }

    @Test
    void missingVariationKeepsMaterialDefinitionExact() {
        LandscapeDefinitionId id = LandscapeDefinitionId.of(0);
        SoilHydrologyDefinitions definitions = new SoilHydrologyDefinitions();
        SoilHydrology base = new SoilHydrology(80_000, 800);
        definitions.put(id, base);

        TerrainSoilHydrologyLookup lookup =
                new TerrainSoilHydrologyLookup(
                        (x, y, z) -> id,
                        definitions);

        assertEquals(base, lookup.find(17, -4, 2));
    }
}
