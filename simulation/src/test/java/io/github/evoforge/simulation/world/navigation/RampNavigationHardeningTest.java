package io.github.evoforge.simulation.world.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;

final class RampNavigationHardeningTest {

    private static final LandscapeDefinitionId TERRAIN =
            LandscapeDefinitionId.of(0);

    @Test
    void lowerAscentRequiresLowerShape() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        terrain.add(0, 1, 0);
        terrain.add(0, 2, 0);

        NavigationLookup navigation =
                navigation(terrain);

        int lower =
                navigation.transitions(0, 0, 0);

        assertFalse(
                TransitionMask.contains(
                        lower,
                        0,
                        1,
                        1));
    }

    @Test
    void lowerDescentRequiresLowerShape() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        terrain.add(0, 1, 0);
        terrain.add(0, 2, 0);

        NavigationLookup navigation =
                navigation(terrain);

        int ramp =
                navigation.transitions(0, 1, 1);

        assertFalse(
                TransitionMask.contains(
                        ramp,
                        0,
                        -1,
                        -1));
    }

    @Test
    void upperConnectionRequiresUpperShape() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        terrain.add(0, 0, -1);
        terrain.add(0, 1, 0);

        NavigationLookup navigation =
                navigation(terrain);

        int ramp =
                navigation.transitions(0, 1, 1);
        int upper =
                navigation.transitions(0, 2, 1);

        assertFalse(
                TransitionMask.contains(
                        ramp,
                        0,
                        1,
                        0));

        assertFalse(
                TransitionMask.contains(
                        upper,
                        0,
                        -1,
                        0));
    }

    @Test
    void rampTerrainCoordinateIsNotNavigable() {
        TestTerrainLookup terrain =
                solidFloor();

        terrain.add(0, 1, 0);

        NavigationLookup navigation =
                navigation(terrain);

        assertEquals(
                TransitionMask.NONE,
                navigation.transitions(0, 1, 0));
    }

    @Test
    void sideNeighborCannotEnterRampTerrainCoordinate() {
        TestTerrainLookup terrain =
                solidFloor();

        terrain.add(0, 1, 0);

        NavigationLookup navigation =
                navigation(terrain);

        int side =
                navigation.transitions(1, 1, 0);

        assertFalse(
                TransitionMask.contains(
                        side,
                        -1,
                        0,
                        0));
    }

    private static NavigationLookup navigation(
            TestTerrainLookup terrain) {

        GeometrySystem geometry =
                new GeometrySystem(terrain);

        geometry.setShape(
                0,
                1,
                0,
                RampShape.POSITIVE_Y);

        return new NavigationSystem(
                geometry.lookup()).lookup();
    }

    private static TestTerrainLookup solidFloor() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                terrain.add(x, y, -1);
            }
        }

        return terrain;
    }

    private record Cell(
            int x,
            int y,
            int z) {
    }

    private static final class TestTerrainLookup
            implements TerrainLookup {

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
        public LandscapeDefinitionId find(
                int x,
                int y,
                int z) {

            return terrain.contains(
                    new Cell(x, y, z))
                            ? TERRAIN
                            : null;
        }
    }
}
