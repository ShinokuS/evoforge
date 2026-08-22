package io.github.evoforge.simulation.world.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.geometry.TransitionMask;
import io.github.evoforge.simulation.world.geometry.TransitionPorts;

final class NavigationSystemTest {

    @Test
    void rejectsNullGeometry() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NavigationSystem(null));
    }

    @Test
    void lookupIsStable() {
        NavigationSystem navigation =
                new NavigationSystem(
                        (x, y, z) -> null);

        assertSame(
                navigation.lookup(),
                navigation.lookup());
    }

    @Test
    void returnsNoTransitionsWithoutGeometry() {
        NavigationSystem navigation =
                new NavigationSystem(
                        (x, y, z) -> null);

        assertEquals(
                TransitionMask.NONE,
                navigation.lookup().transitions(
                        1,
                        2,
                        3));
    }

    @Test
    void composesArbitraryShapesWithoutKnowingTheirTypes() {
        TestGeometryLookup geometry =
                new TestGeometryLookup();

        int east =
                TransitionMask.of(
                        1,
                        0,
                        0);

        geometry.put(
                0,
                0,
                0,
                new TestShape(
                        TransitionPorts.departuresOnly(east),
                        TransitionMask.NONE));

        geometry.put(
                1,
                0,
                0,
                new TestShape(
                        TransitionPorts.arrivalsOnly(east),
                        TransitionMask.NONE));

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        assertEquals(
                east,
                navigation.lookup().transitions(
                        0,
                        0,
                        0));
    }

    @Test
    void resolvesEightTransitionsAcrossFlatFullNeighborhood() {
        TestGeometryLookup geometry =
                flatFullNeighborhood(
                        10,
                        20,
                        30,
                        Integer.MIN_VALUE,
                        Integer.MIN_VALUE);

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        int transitions =
                navigation.lookup().transitions(
                        10,
                        20,
                        30);

        assertEquals(
                8,
                Integer.bitCount(transitions));

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }

                assertTrue(
                        TransitionMask.contains(
                                transitions,
                                dx,
                                dy,
                                0));
            }
        }
    }

    @Test
    void missingSupportRemovesOnlyItsTransition() {
        TestGeometryLookup geometry =
                flatFullNeighborhood(
                        10,
                        20,
                        30,
                        1,
                        0);

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        int transitions =
                navigation.lookup().transitions(
                        10,
                        20,
                        30);

        assertEquals(
                7,
                Integer.bitCount(transitions));

        assertFalse(
                TransitionMask.contains(
                        transitions,
                        1,
                        0,
                        0));

        assertTrue(
                TransitionMask.contains(
                        transitions,
                        0,
                        1,
                        0));
    }

    @Test
    void occupiedSideBlocksDirectAndCornerCrossingTransitions() {
        TestGeometryLookup geometry =
                flatFullNeighborhood(
                        10,
                        20,
                        30,
                        Integer.MIN_VALUE,
                        Integer.MIN_VALUE);

        geometry.put(
                11,
                20,
                30,
                FullShape.INSTANCE);

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        int transitions =
                navigation.lookup().transitions(
                        10,
                        20,
                        30);

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
    }

    @Test
    void readsOnlyLocalTransitionAndLowerSupportNeighborhood() {
        TrackingGeometryLookup geometry =
                new TrackingGeometryLookup(
                        10,
                        20,
                        30);

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        navigation.lookup().transitions(
                10,
                20,
                30);

        assertEquals(
                36,
                geometry.calls);

        assertEquals(
                2,
                geometry.maxDistance);
    }

    @Test
    void doesNotWrapCoordinatesAtIntegerBoundary() {
        TestGeometryLookup geometry =
                new TestGeometryLookup();

        geometry.put(
                Integer.MAX_VALUE,
                0,
                -1,
                FullShape.INSTANCE);

        geometry.put(
                Integer.MAX_VALUE - 1,
                0,
                -1,
                FullShape.INSTANCE);

        geometry.put(
                Integer.MIN_VALUE,
                0,
                -1,
                FullShape.INSTANCE);

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        int transitions =
                navigation.lookup().transitions(
                        Integer.MAX_VALUE,
                        0,
                        0);

        assertEquals(
                1,
                Integer.bitCount(transitions));

        assertTrue(
                TransitionMask.contains(
                        transitions,
                        -1,
                        0,
                        0));

        assertFalse(
                TransitionMask.contains(
                        transitions,
                        1,
                        0,
                        0));
    }

    private static TestGeometryLookup flatFullNeighborhood(
            int x,
            int y,
            int z,
            int missingX,
            int missingY) {

        TestGeometryLookup geometry =
                new TestGeometryLookup();

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == missingX && dy == missingY) {
                    continue;
                }

                geometry.put(
                        x + dx,
                        y + dy,
                        z - 1,
                        FullShape.INSTANCE);
            }
        }

        return geometry;
    }

    private record TestShape(
            long ports,
            int blocks)
            implements Shape {

        @Override
        public long transitionPorts(
                int relativeX,
                int relativeY,
                int relativeZ) {

            return ports;
        }

        @Override
        public int transitionBlocks(
                int relativeX,
                int relativeY,
                int relativeZ) {

            return blocks;
        }
    }

    private record Cell(
            int x,
            int y,
            int z) {
    }

    private static final class TestGeometryLookup
            implements GeometryLookup {

        private final Map<Cell, Shape> shapes =
                new HashMap<>();

        void put(
                int x,
                int y,
                int z,
                Shape shape) {

            shapes.put(
                    new Cell(x, y, z),
                    shape);
        }

        @Override
        public Shape find(
                int x,
                int y,
                int z) {

            return shapes.get(
                    new Cell(x, y, z));
        }
    }

    private static final class TrackingGeometryLookup
            implements GeometryLookup {

        private final int originX;
        private final int originY;
        private final int originZ;

        private int calls;
        private int maxDistance;

        TrackingGeometryLookup(
                int originX,
                int originY,
                int originZ) {

            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
        }

        @Override
        public Shape find(
                int x,
                int y,
                int z) {

            calls++;

            int distance = Math.max(
                    Math.max(
                            Math.abs(x - originX),
                            Math.abs(y - originY)),
                    Math.abs(z - originZ));

            maxDistance = Math.max(
                    maxDistance,
                    distance);

            return null;
        }
    }
}
