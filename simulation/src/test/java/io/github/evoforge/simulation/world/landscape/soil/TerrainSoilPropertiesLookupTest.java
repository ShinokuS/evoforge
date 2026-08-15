package io.github.evoforge.simulation.world.landscape.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;

final class TerrainSoilPropertiesLookupTest {

    @Test
    void sameSeedAndCoordinatesResolveIdenticalCapacityWithoutChangingPermeability() {
        LandscapeDefinitionId id = LandscapeDefinitionId.of(0);
        SoilPropertiesDefinitions definitions = new SoilPropertiesDefinitions();
        definitions.put(id, new SoilProperties(120_000, 3_000));
        SoilPropertiesVariationDefinitions variations =
                new SoilPropertiesVariationDefinitions();
        variations.put(id, new SoilPropertiesVariation(42L, 20_000));
        TerrainLookup terrain = (x, y, z) -> id;

        TerrainSoilPropertiesLookup first =
                new TerrainSoilPropertiesLookup(
                        terrain,
                        definitions,
                        variations);
        TerrainSoilPropertiesLookup second =
                new TerrainSoilPropertiesLookup(
                        terrain,
                        definitions,
                        variations);

        Set<Integer> observed = new HashSet<>();
        for (int x = -8; x <= 8; x++) {
            SoilProperties a = first.find(x, 3, -1);
            SoilProperties b = second.find(x, 3, -1);
            assertEquals(a, b);
            assertTrue(a.capacity() >= 100_000);
            assertTrue(a.capacity() <= 140_000);
            assertEquals(3_000, a.permeability());
            observed.add(a.capacity());
        }

        assertTrue(
                observed.size() > 1,
                "coordinate-local pore capacity variation should not collapse to one value");
    }

    @Test
    void missingVariationKeepsMaterialPropertiesExact() {
        LandscapeDefinitionId id = LandscapeDefinitionId.of(0);
        SoilPropertiesDefinitions definitions = new SoilPropertiesDefinitions();
        SoilProperties base = new SoilProperties(80_000, 800);
        definitions.put(id, base);

        TerrainSoilPropertiesLookup lookup =
                new TerrainSoilPropertiesLookup(
                        (x, y, z) -> id,
                        definitions);

        assertEquals(base, lookup.find(17, -4, 2));
    }
}
