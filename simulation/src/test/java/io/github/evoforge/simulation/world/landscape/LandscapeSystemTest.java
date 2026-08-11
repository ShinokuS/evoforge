package io.github.evoforge.simulation.world.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.result.OperationResults;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainPlacementResult;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainRemovalResult;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainReplacementResult;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainStorage;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;

final class LandscapeSystemTest {

    private static final LandscapeDefinitionId GRANITE =
            LandscapeDefinitionId.of(0);
    private static final LandscapeDefinitionId SOIL =
            LandscapeDefinitionId.of(1);

    @Test
    void placeClearsAnyOrphanedGeometryOverride() {
        Fixture fixture = createFixture();

        OperationResults.requireAccepted(
                fixture.terrain.place(1, 2, 3, GRANITE));
        fixture.geometry.setShape(
                1,
                2,
                3,
                RampShape.POSITIVE_X);
        OperationResults.requireAccepted(
                fixture.terrain.remove(1, 2, 3));

        assertEquals(
                TerrainPlacementResult.PLACED,
                fixture.landscape.placeTerrain(
                        1,
                        2,
                        3,
                        SOIL));
        assertSame(
                FullShape.INSTANCE,
                fixture.geometry.lookup().find(1, 2, 3));
    }

    @Test
    void replacePreservesExistingGeometryOverride() {
        Fixture fixture = createFixture();

        OperationResults.requireAccepted(
                fixture.landscape.placeTerrain(
                        1,
                        2,
                        3,
                        GRANITE));
        fixture.geometry.setShape(
                1,
                2,
                3,
                RampShape.POSITIVE_Y);

        assertEquals(
                TerrainReplacementResult.REPLACED,
                fixture.landscape.replaceTerrain(
                        1,
                        2,
                        3,
                        SOIL));
        assertSame(
                RampShape.POSITIVE_Y,
                fixture.geometry.lookup().find(1, 2, 3));
    }

    @Test
    void removeClearsGeometryOverrideForLaterTerrain() {
        Fixture fixture = createFixture();

        OperationResults.requireAccepted(
                fixture.landscape.placeTerrain(
                        1,
                        2,
                        3,
                        GRANITE));
        fixture.geometry.setShape(
                1,
                2,
                3,
                RampShape.NEGATIVE_X);

        assertEquals(
                TerrainRemovalResult.REMOVED,
                fixture.landscape.removeTerrain(
                        1,
                        2,
                        3));

        OperationResults.requireAccepted(
                fixture.terrain.place(1, 2, 3, SOIL));
        assertSame(
                FullShape.INSTANCE,
                fixture.geometry.lookup().find(1, 2, 3));
    }

    @Test
    void rejectedPlacementDoesNotChangeTerrainOrGeometry() {
        Fixture fixture = createFixture();

        OperationResults.requireAccepted(
                fixture.landscape.placeTerrain(
                        1,
                        2,
                        3,
                        GRANITE));
        fixture.geometry.setShape(
                1,
                2,
                3,
                RampShape.NEGATIVE_Y);

        assertEquals(
                TerrainPlacementResult.POSITION_OCCUPIED,
                fixture.landscape.placeTerrain(
                        1,
                        2,
                        3,
                        SOIL));
        assertEquals(
                GRANITE,
                fixture.terrain.lookup().find(1, 2, 3));
        assertSame(
                RampShape.NEGATIVE_Y,
                fixture.geometry.lookup().find(1, 2, 3));
    }

    @Test
    void constructorRejectsNullDependencies() {
        Fixture fixture = createFixture();

        assertThrows(
                IllegalArgumentException.class,
                () -> new LandscapeSystem(
                        null,
                        fixture.geometry));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandscapeSystem(
                        fixture.terrain,
                        null));
    }

    private static Fixture createFixture() {
        TestDefinitionCatalog definitions =
                new TestDefinitionCatalog();
        definitions.add("core:granite", GRANITE);
        definitions.add("core:soil", SOIL);

        TerrainSystem terrain =
                new TerrainSystem(
                        new TestTerrainStorage(),
                        definitions);
        GeometrySystem geometry =
                new GeometrySystem(terrain.lookup());
        LandscapeSystem landscape =
                new LandscapeSystem(
                        terrain,
                        geometry);

        return new Fixture(
                terrain,
                geometry,
                landscape);
    }

    private record Fixture(
            TerrainSystem terrain,
            GeometrySystem geometry,
            LandscapeSystem landscape) {
    }

    private record Cell(int x, int y, int z) {
    }

    private static final class TestTerrainStorage implements TerrainStorage {
        private final Map<Cell, LandscapeDefinitionId> terrain =
                new HashMap<>();

        @Override
        public LandscapeDefinitionId find(
                int x,
                int y,
                int z) {
            return terrain.get(new Cell(x, y, z));
        }

        @Override
        public void put(
                int x,
                int y,
                int z,
                LandscapeDefinitionId definitionId) {
            terrain.put(
                    new Cell(x, y, z),
                    definitionId);
        }

        @Override
        public void remove(
                int x,
                int y,
                int z) {
            terrain.remove(new Cell(x, y, z));
        }
    }

    private static final class TestDefinitionCatalog
            implements DefinitionCatalog<LandscapeDefinitionId> {
        private final Map<String, LandscapeDefinitionId> byKey =
                new HashMap<>();
        private final Map<LandscapeDefinitionId, String> byId =
                new HashMap<>();

        void add(
                String key,
                LandscapeDefinitionId id) {
            byKey.put(key, id);
            byId.put(id, key);
        }

        @Override
        public LandscapeDefinitionId resolve(String key) {
            return byKey.get(key);
        }

        @Override
        public String keyOf(LandscapeDefinitionId id) {
            return byId.get(id);
        }

        @Override
        public boolean contains(LandscapeDefinitionId id) {
            return byId.containsKey(id);
        }
    }
}
