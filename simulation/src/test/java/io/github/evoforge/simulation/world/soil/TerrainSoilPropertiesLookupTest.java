package io.github.evoforge.simulation.world.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import org.junit.jupiter.api.Test;

final class TerrainSoilPropertiesLookupTest {

    @Test
    void samePreparedMaterialKeepsExactPropertiesAcrossCoordinates() {
        LandscapeDefinitionId id = LandscapeDefinitionId.of(0);
        SoilPropertiesDefinitions definitions = new SoilPropertiesDefinitions();
        SoilProperties prepared = new SoilProperties(120_000, 3_000);
        definitions.put(id, prepared);

        TerrainSoilPropertiesLookup lookup =
                new TerrainSoilPropertiesLookup((x, y, z) -> id, definitions);

        assertEquals(prepared, lookup.find(-8, 3, -1));
        assertEquals(prepared, lookup.find(0, 3, -1));
        assertEquals(prepared, lookup.find(8, 3, -1));
    }

    @Test
    void nonSoilTerrainHasNoSoilProperties() {
        LandscapeDefinitionId soil = LandscapeDefinitionId.of(0);
        LandscapeDefinitionId rock = LandscapeDefinitionId.of(1);
        SoilPropertiesDefinitions definitions = new SoilPropertiesDefinitions();
        definitions.put(soil, new SoilProperties(80_000, 800));

        TerrainSoilPropertiesLookup lookup =
                new TerrainSoilPropertiesLookup((x, y, z) -> rock, definitions);

        assertNull(lookup.find(0, 0, 0));
    }
}
