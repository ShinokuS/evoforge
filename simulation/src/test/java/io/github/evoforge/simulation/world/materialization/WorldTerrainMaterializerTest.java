package io.github.evoforge.simulation.world.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.IntBinaryOperator;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class WorldTerrainMaterializerTest {

    @Test
    void materializesSolidColumnsFromWorldFloorThroughAtlasSurface() {
        Fixture fixture = new Fixture();
        WorldBounds bounds = new WorldBounds(0, 1, 0, 0, -2, 3);
        ElevationField elevation = elevation(
                bounds,
                (x, y) -> x == 0 ? 0 : 2);

        TerrainMaterializationResult result = fixture.materializer(
                elevation,
                TerrainMaterialResolver.uniform(fixture.ground))
                .materialize();

        assertEquals(2L, result.columns());
        assertEquals(8L, result.terrainCells());

        for (int z = -2; z <= 0; z++) {
            assertEquals(fixture.ground, fixture.landscape.terrain().find(0, 0, z));
        }
        assertNull(fixture.landscape.terrain().find(0, 0, 1));

        for (int z = -2; z <= 2; z++) {
            assertEquals(fixture.ground, fixture.landscape.terrain().find(1, 0, z));
        }
        assertNull(fixture.landscape.terrain().find(1, 0, 3));
    }

    @Test
    void materialResolverCanAuthorDifferentSubsurfaceMaterials() {
        Fixture fixture = new Fixture();
        WorldBounds bounds = new WorldBounds(4, 4, -3, -3, -2, 3);
        ElevationField elevation = elevation(bounds, (x, y) -> 1);

        TerrainMaterializationResult result = fixture.materializer(
                elevation,
                (x, y, z) -> z < 0 ? fixture.stone : fixture.ground)
                .materialize();

        assertEquals(1L, result.columns());
        assertEquals(4L, result.terrainCells());
        assertEquals(fixture.stone, fixture.landscape.terrain().find(4, -3, -2));
        assertEquals(fixture.stone, fixture.landscape.terrain().find(4, -3, -1));
        assertEquals(fixture.ground, fixture.landscape.terrain().find(4, -3, 0));
        assertEquals(fixture.ground, fixture.landscape.terrain().find(4, -3, 1));
    }

    @Test
    void refusesToMergeGeneratedTerrainIntoExistingRuntimeTerrain() {
        Fixture fixture = new Fixture();
        fixture.landscape.placeTerrain(10, 10, 10, fixture.ground);
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -1, 2);

        WorldTerrainMaterializer materializer = fixture.materializer(
                elevation(bounds, (x, y) -> 0),
                TerrainMaterialResolver.uniform(fixture.ground));

        assertThrows(IllegalStateException.class, materializer::materialize);
        assertTrue(fixture.landscape.terrain().contains(10, 10, 10));
        assertFalse(fixture.landscape.terrain().contains(0, 0, -1));
    }

    @Test
    void invalidSurfaceIsRejectedBeforeAnyTerrainMutation() {
        Fixture fixture = new Fixture();
        WorldBounds bounds = new WorldBounds(0, 1, 0, 0, -1, 2);
        ElevationField elevation = elevation(
                bounds,
                (x, y) -> x == 0 ? 0 : 3);

        WorldTerrainMaterializer materializer = fixture.materializer(
                elevation,
                TerrainMaterialResolver.uniform(fixture.ground));

        assertThrows(IllegalStateException.class, materializer::materialize);
        assertTrue(fixture.landscape.terrainExtents().empty());
    }

    @Test
    void unknownMaterialIsRejectedDuringPreflightBeforeMutation() {
        Fixture fixture = new Fixture();
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -1, 1);
        LandscapeDefinitionId unknown = LandscapeDefinitionId.of(999);

        WorldTerrainMaterializer materializer = fixture.materializer(
                elevation(bounds, (x, y) -> 0),
                (x, y, z) -> z == 0 ? unknown : fixture.ground);

        assertThrows(IllegalStateException.class, materializer::materialize);
        assertTrue(fixture.landscape.terrainExtents().empty());
    }

    @Test
    void uniformResolverRejectsMissingMaterialIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TerrainMaterialResolver.uniform(null));
    }

    private static ElevationField elevation(
            WorldBounds bounds,
            IntBinaryOperator surfaceZ) {

        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                return Math.multiplyExact(
                        (long) surfaceZ.applyAsInt(x, y),
                        SUBUNITS_PER_CELL);
            }
        };
    }

    private static final class Fixture {
        private final DefinitionRegistry<LandscapeDefinitionId> definitions =
                new DefinitionRegistry<>(
                        LandscapeDefinitionId::of,
                        LandscapeDefinitionId::asInt);
        private final LandscapeDefinitionId ground =
                definitions.register("test:ground");
        private final LandscapeDefinitionId stone =
                definitions.register("test:stone");
        private final LandscapeSystem landscape = LandscapeSystem.create(
                new SparseTerrainStorage(),
                definitions);

        private WorldTerrainMaterializer materializer(
                ElevationField elevation,
                TerrainMaterialResolver materials) {

            return new WorldTerrainMaterializer(
                    elevation,
                    materials,
                    definitions,
                    landscape.terrainExtents(),
                    landscape);
        }
    }
}
