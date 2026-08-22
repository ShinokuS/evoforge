package io.github.evoforge.simulation.world.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.geometry.TransitionMask;

final class NavigationGeometryIntegrationTest {

    @Test
    void resolvesDefaultTerrainGeometryThroughGeometryLookup() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                terrain.add(
                        x,
                        y,
                        -1);
            }
        }

        GeometrySystem geometry =
                new GeometrySystem(terrain);

        NavigationSystem navigation =
                new NavigationSystem(
                        geometry.lookup());

        int transitions =
                navigation.lookup().transitions(
                        0,
                        0,
                        0);

        assertEquals(
                8,
                Integer.bitCount(transitions));
    }

    @Test
    void terrainAddedAtObjectLevelBlocksAffectedTransitions() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                terrain.add(
                        x,
                        y,
                        -1);
            }
        }

        terrain.add(
                1,
                0,
                0);

        GeometrySystem geometry =
                new GeometrySystem(terrain);

        NavigationSystem navigation =
                new NavigationSystem(
                        geometry.lookup());

        int transitions =
                navigation.lookup().transitions(
                        0,
                        0,
                        0);

        assertEquals(
                5,
                Integer.bitCount(transitions));

        assertFalse(
                TransitionMask.contains(
                        transitions,
                        1,
                        -1,
                        0));

        assertFalse(
                TransitionMask.contains(
                        transitions,
                        1,
                        0,
                        0));

        assertFalse(
                TransitionMask.contains(
                        transitions,
                        1,
                        1,
                        0));

        assertTrue(
                TransitionMask.contains(
                        transitions,
                        -1,
                        0,
                        0));
    }

    private record Cell(
            int x,
            int y,
            int z) {
    }

    private static final class TestTerrainLookup
            implements TerrainLookup {

        private static final MaterialDefinitionId TERRAIN =
                MaterialDefinitionId.of(0);

        private final Set<Cell> terrain =
                new HashSet<>();

        void add(
                int x,
                int y,
                int z) {

            terrain.add(
                    new Cell(x, y, z));
        }

        @Override
        public MaterialDefinitionId find(
                int x,
                int y,
                int z) {

            if (terrain.contains(
                    new Cell(x, y, z))) {
                return TERRAIN;
            }

            return null;
        }
    }
}
