package io.github.evoforge.simulation.world.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class GeneratedTerrainMaterializationIntegrationTest {

    @Test
    void productionAtlasElevationBecomesLandscapeSurfaceWithoutChangingAtlas() {
        WorldBounds bounds = new WorldBounds(-2, 2, -1, 1, -6, 6);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 2026L);
        WorldAtlas atlas = new WorldAtlasGenerator().generate(genesis);

        DefinitionRegistry<LandscapeDefinitionId> definitions =
                new DefinitionRegistry<>(
                        LandscapeDefinitionId::of,
                        LandscapeDefinitionId::asInt);
        LandscapeDefinitionId baseMaterial =
                definitions.register("test:generated-base");
        LandscapeSystem landscape = LandscapeSystem.create(
                new SparseTerrainStorage(),
                definitions);

        TerrainMaterializationResult result = new WorldTerrainMaterializer(
                atlas.elevation(),
                TerrainMaterialResolver.uniform(baseMaterial),
                definitions,
                landscape.terrainExtents(),
                landscape)
                .materialize();

        assertEquals(15L, result.columns());
        assertEquals(15, landscape.terrainSurfaces().columnCount());

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                int atlasSurface = atlas.elevation().elevationAt(x, y);
                assertEquals(atlasSurface, landscape.terrainSurfaces().topZ(x, y));
                assertEquals(
                        baseMaterial,
                        landscape.terrain().find(x, y, atlasSurface));
                if (atlasSurface < bounds.maxZ()) {
                    assertNull(landscape.terrain().find(x, y, atlasSurface + 1));
                }
                assertEquals(
                        atlasSurface,
                        atlas.elevation().elevationAt(x, y));
            }
        }
    }
}
