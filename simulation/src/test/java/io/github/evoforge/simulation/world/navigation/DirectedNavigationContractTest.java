package io.github.evoforge.simulation.world.navigation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.geometry.TransitionMask;
import io.github.evoforge.simulation.world.geometry.TransitionPorts;

final class DirectedNavigationContractTest {

    @Test
    void forwardTransitionDoesNotImplyReverseTransition() {
        TestGeometry geometry =
                new TestGeometry();

        int east =
                TransitionMask.of(
                        1,
                        0,
                        0);

        geometry.put(
                0,
                0,
                0,
                new SourceShape(east));

        geometry.put(
                1,
                0,
                0,
                new DestinationShape(east));

        NavigationLookup navigation =
                new NavigationSystem(geometry).lookup();

        assertTrue(
                TransitionMask.contains(
                        navigation.transitions(
                                0,
                                0,
                                0),
                        1,
                        0,
                        0));

        assertFalse(
                TransitionMask.contains(
                        navigation.transitions(
                                1,
                                0,
                                0),
                        -1,
                        0,
                        0));
    }

    private record SourceShape(
            int direction)
            implements Shape {

        @Override
        public long transitionPorts(
                int relativeX,
                int relativeY,
                int relativeZ) {

            if (relativeX == 0
                    && relativeY == 0
                    && relativeZ == 0) {
                return TransitionPorts.departuresOnly(
                        direction);
            }

            return TransitionPorts.NONE;
        }
    }

    private record DestinationShape(
            int direction)
            implements Shape {

        @Override
        public long transitionPorts(
                int relativeX,
                int relativeY,
                int relativeZ) {

            if (relativeX == -1
                    && relativeY == 0
                    && relativeZ == 0) {
                return TransitionPorts.arrivalsOnly(
                        direction);
            }

            return TransitionPorts.NONE;
        }
    }

    private record Cell(
            int x,
            int y,
            int z) {
    }

    private static final class TestGeometry
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
}
