package io.github.evoforge.simulation.world.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionPorts;

final class NavigationMutationIntegrationTest {

    private static final LandscapeDefinitionId TERRAIN =
            LandscapeDefinitionId.of(0);

    @Test
    void terrainRemovalIsVisibleOnNextQuery() {
        TerrainSystem terrain = terrainSystem();

        placeFlatNeighborhood(terrain);

        GeometrySystem geometry =
                new GeometrySystem(
                        terrain.lookup());

        NavigationSystem navigation =
                new NavigationSystem(
                        geometry.lookup());

        assertEquals(
                8,
                Integer.bitCount(
                        navigation.lookup().transitions(
                                0,
                                0,
                                0)));

        terrain.remove(
                1,
                0,
                -1);

        int transitions =
                navigation.lookup().transitions(
                        0,
                        0,
                        0);

        assertEquals(
                7,
                Integer.bitCount(transitions));

        assertFalse(
                TransitionMask.contains(
                        transitions,
                        1,
                        0,
                        0));
    }

    @Test
    void geometryOverrideIsVisibleOnNextQuery() {
        TerrainSystem terrain = terrainSystem();

        placeFlatNeighborhood(terrain);

        GeometrySystem geometry =
                new GeometrySystem(
                        terrain.lookup());

        NavigationSystem navigation =
                new NavigationSystem(
                        geometry.lookup());

        assertEquals(
                8,
                Integer.bitCount(
                        navigation.lookup().transitions(
                                0,
                                0,
                                0)));

        geometry.setShape(
                1,
                0,
                -1,
                NoTransitionsShape.INSTANCE);

        int transitions =
                navigation.lookup().transitions(
                        0,
                        0,
                        0);

        assertEquals(
                7,
                Integer.bitCount(transitions));

        assertFalse(
                TransitionMask.contains(
                        transitions,
                        1,
                        0,
                        0));
    }

    private static TerrainSystem terrainSystem() {
        return new TerrainSystem(
                new SparseTerrainStorage(),
                new DefinitionCatalog<>() {

                    @Override
                    public LandscapeDefinitionId resolve(
                            String key) {

                        return "core:test".equals(key)
                                ? TERRAIN
                                : null;
                    }

                    @Override
                    public String keyOf(
                            LandscapeDefinitionId id) {

                        return TERRAIN.equals(id)
                                ? "core:test"
                                : null;
                    }

                    @Override
                    public boolean contains(
                            LandscapeDefinitionId id) {

                        return TERRAIN.equals(id);
                    }
                });
    }

    private static void placeFlatNeighborhood(
            TerrainSystem terrain) {

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                terrain.place(
                        x,
                        y,
                        -1,
                        TERRAIN);
            }
        }
    }

    private enum NoTransitionsShape
            implements Shape {

        INSTANCE;

        @Override
        public long transitionPorts(
                int relativeX,
                int relativeY,
                int relativeZ) {

            return TransitionPorts.NONE;
        }
    }
}
