package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;

/**
 * Resolves runtime Soil properties from already prepared material definitions.
 *
 * <p>This lookup deliberately performs no coordinate noise, randomization or world generation.
 * Spatial differences in physical soil properties must be prepared upstream and supplied through a
 * spatial {@link SoilPropertiesLookup}; Simulation only consumes the resolved facts.</p>
 */
public final class TerrainSoilPropertiesLookup implements SoilPropertiesLookup {

    private final TerrainLookup terrain;
    private final SoilPropertiesDefinitions definitions;

    public TerrainSoilPropertiesLookup(
            TerrainLookup terrain,
            SoilPropertiesDefinitions definitions) {
        if (terrain == null || definitions == null) {
            throw new IllegalArgumentException(
                    "soil property lookup dependencies must not be null");
        }
        this.terrain = terrain;
        this.definitions = definitions;
    }

    @Override
    public SoilProperties find(int x, int y, int z) {
        LandscapeDefinitionId id = terrain.find(x, y, z);
        if (id == null || !definitions.has(id)) return null;
        return definitions.get(id);
    }
}
