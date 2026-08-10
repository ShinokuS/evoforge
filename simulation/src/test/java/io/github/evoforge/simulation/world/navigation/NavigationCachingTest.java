package io.github.evoforge.simulation.world.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionPorts;

final class NavigationCachingTest {

    @Test
    void repeatedQueryDoesNotReadGeometryAgain() {
        CountingGeometryLookup geometry =
                new CountingGeometryLookup();

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        navigation.lookup().transitions(
                10,
                20,
                30);

        assertEquals(
                27,
                geometry.calls);

        navigation.lookup().transitions(
                10,
                20,
                30);

        assertEquals(
                27,
                geometry.calls);
    }

    @Test
    void cachesZeroTransitionResult() {
        CountingGeometryLookup geometry =
                new CountingGeometryLookup();

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        assertEquals(
                TransitionMask.NONE,
                navigation.lookup().transitions(
                        0,
                        0,
                        0));

        assertEquals(
                TransitionMask.NONE,
                navigation.lookup().transitions(
                        0,
                        0,
                        0));

        assertEquals(
                27,
                geometry.calls);
    }

    @Test
    void geometryInvalidationRefreshesAffectedTransition() {
        CountingGeometryLookup geometry =
                new CountingGeometryLookup();

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

        geometry.put(
                0,
                0,
                1,
                new TestShape(
                        TransitionPorts.NONE,
                        east));

        assertEquals(
                east,
                navigation.lookup().transitions(
                        0,
                        0,
                        0));

        navigation.invalidateGeometry(
                0,
                0,
                1);

        assertEquals(
                TransitionMask.NONE,
                navigation.lookup().transitions(
                        0,
                        0,
                        0));
    }

    @Test
    void geometryInvalidationKeepsUnrelatedCachedSources() {
        CountingGeometryLookup geometry =
                new CountingGeometryLookup();

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        navigation.lookup().transitions(
                0,
                0,
                0);

        navigation.lookup().transitions(
                10,
                0,
                0);

        assertEquals(
                54,
                geometry.calls);

        navigation.invalidateGeometry(
                1,
                0,
                0);

        navigation.lookup().transitions(
                10,
                0,
                0);

        assertEquals(
                54,
                geometry.calls);

        navigation.lookup().transitions(
                0,
                0,
                0);

        assertEquals(
                81,
                geometry.calls);
    }

    @Test
    void geometryInvalidationDoesNotWrapAtIntegerBoundary() {
        CountingGeometryLookup geometry =
                new CountingGeometryLookup();

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        navigation.lookup().transitions(
                Integer.MAX_VALUE,
                0,
                0);

        navigation.lookup().transitions(
                Integer.MIN_VALUE,
                0,
                0);

        assertEquals(
                36,
                geometry.calls);

        navigation.invalidateGeometry(
                Integer.MAX_VALUE,
                0,
                0);

        navigation.lookup().transitions(
                Integer.MIN_VALUE,
                0,
                0);

        assertEquals(
                36,
                geometry.calls);

        navigation.lookup().transitions(
                Integer.MAX_VALUE,
                0,
                0);

        assertEquals(
                54,
                geometry.calls);
    }

    @Test
    void clearCacheForcesAllSourcesToResolveAgain() {
        CountingGeometryLookup geometry =
                new CountingGeometryLookup();

        NavigationSystem navigation =
                new NavigationSystem(geometry);

        navigation.lookup().transitions(
                0,
                0,
                0);

        assertEquals(
                27,
                geometry.calls);

        navigation.clearCache();

        navigation.lookup().transitions(
                0,
                0,
                0);

        assertEquals(
                54,
                geometry.calls);
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

    private static final class CountingGeometryLookup
            implements GeometryLookup {

        private final Map<Cell, Shape> shapes =
                new HashMap<>();

        private int calls;

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

            calls++;

            return shapes.get(
                    new Cell(x, y, z));
        }
    }
}
