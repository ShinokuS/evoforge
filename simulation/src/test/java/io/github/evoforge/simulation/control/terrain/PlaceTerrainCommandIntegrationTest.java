package io.github.evoforge.simulation.control.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.control.core.CommandDispatcher;
import io.github.evoforge.simulation.control.sync.SynchronousCommandGateway;
import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.result.ResultCode;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainStorage;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometrySystem;

final class PlaceTerrainCommandIntegrationTest {

    private static final LandscapeDefinitionId GRANITE =
            LandscapeDefinitionId.of(0);
    private static final LandscapeDefinitionId SOIL =
            LandscapeDefinitionId.of(1);
    private static final LandscapeDefinitionId UNKNOWN =
            LandscapeDefinitionId.of(2);

    @Test
    void synchronousGatewayPlacesTerrainAndReturnsStructuredRejection() {
        Fixture fixture = createFixture();

        PlaceTerrainResult first =
                fixture.gateway.submit(
                        new PlaceTerrainCommand(
                                4,
                                5,
                                6,
                                GRANITE));

        assertEquals(
                PlaceTerrainResult.PLACED,
                first);
        assertTrue(first.accepted());
        assertEquals(
                ResultCode.of("terrain", "placed"),
                first.code());
        assertEquals(
                GRANITE,
                fixture.terrain.lookup().find(4, 5, 6));

        PlaceTerrainResult second =
                fixture.gateway.submit(
                        new PlaceTerrainCommand(
                                4,
                                5,
                                6,
                                SOIL));

        assertEquals(
                PlaceTerrainResult.POSITION_OCCUPIED,
                second);
        assertFalse(second.accepted());
        assertEquals(
                ResultCode.of(
                        "terrain",
                        "position_occupied"),
                second.code());
        assertEquals(
                GRANITE,
                fixture.terrain.lookup().find(4, 5, 6));
    }

    @Test
    void invalidRuntimeDefinitionRemainsProgrammingError() {
        Fixture fixture = createFixture();

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.gateway.submit(
                        new PlaceTerrainCommand(
                                1,
                                2,
                                3,
                                UNKNOWN)));
    }

    @Test
    void commandAndHandlerRejectNullProgrammingInputs() {
        Fixture fixture = createFixture();

        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaceTerrainCommand(
                        1,
                        2,
                        3,
                        null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaceTerrainHandler(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SynchronousCommandGateway(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaceTerrainHandler(
                        fixture.landscape)
                        .handle(null));
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

        CommandDispatcher dispatcher =
                new CommandDispatcher();
        dispatcher.register(
                PlaceTerrainCommand.class,
                new PlaceTerrainHandler(landscape));

        return new Fixture(
                terrain,
                landscape,
                new SynchronousCommandGateway(dispatcher));
    }

    private record Fixture(
            TerrainSystem terrain,
            LandscapeSystem landscape,
            SynchronousCommandGateway gateway) {
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
