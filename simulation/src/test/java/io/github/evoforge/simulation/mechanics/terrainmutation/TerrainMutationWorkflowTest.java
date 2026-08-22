package io.github.evoforge.simulation.mechanics.terrainmutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.kernel.operation.OperationResults;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainPlacementResult;
import io.github.evoforge.simulation.world.terrain.TerrainRemovalResult;
import io.github.evoforge.simulation.world.terrain.TerrainReplacementResult;
import io.github.evoforge.simulation.world.terrain.TerrainStorage;
import io.github.evoforge.simulation.world.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.geometry.RampShape;

final class TerrainMutationWorkflowTest {

    private static final MaterialDefinitionId GRANITE =
            MaterialDefinitionId.of(0);
    private static final MaterialDefinitionId SOIL =
            MaterialDefinitionId.of(1);

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
                () -> new TerrainMutationWorkflow(
                        null,
                        fixture.geometry));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainMutationWorkflow(
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
        TerrainMutationWorkflow landscape =
                new TerrainMutationWorkflow(
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
            TerrainMutationWorkflow landscape) {
    }

    private record Cell(int x, int y, int z) {
    }

    private static final class TestTerrainStorage implements TerrainStorage {
        private final Map<Cell, MaterialDefinitionId> terrain =
                new HashMap<>();

        @Override
        public MaterialDefinitionId find(
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
                MaterialDefinitionId definitionId) {
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
            implements DefinitionCatalog<MaterialDefinitionId> {
        private final Map<String, MaterialDefinitionId> byKey =
                new HashMap<>();
        private final Map<MaterialDefinitionId, String> byId =
                new HashMap<>();

        void add(
                String key,
                MaterialDefinitionId id) {
            byKey.put(key, id);
            byId.put(id, key);
        }

        @Override
        public MaterialDefinitionId resolve(String key) {
            return byKey.get(key);
        }

        @Override
        public String keyOf(MaterialDefinitionId id) {
            return byId.get(id);
        }

        @Override
        public boolean contains(MaterialDefinitionId id) {
            return byId.containsKey(id);
        }
    }
}
