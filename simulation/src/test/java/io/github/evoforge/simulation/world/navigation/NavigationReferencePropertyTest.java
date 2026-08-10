package io.github.evoforge.simulation.world.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionPorts;

final class NavigationReferencePropertyTest {

    private static final long SEED =
            0x5EED_EF0A_2026L;

    private static final int[][] SOURCES = {
            {0, 0, 0},
            {10, -7, 3},
            {-20, 5, -4},
            {31, 17, 9}
    };

    @Test
    void optimizedResolverMatchesSeededReferenceAcrossMutations() {
        SplittableRandom random =
                new SplittableRandom(SEED);

        TestGeometry geometry =
                new TestGeometry();

        NavigationLookup navigation =
                new NavigationSystem(geometry).lookup();

        for (int step = 0; step < 400; step++) {
            mutate(
                    random,
                    geometry);

            for (int[] source : SOURCES) {
                int expected =
                        resolveReference(
                                geometry,
                                source[0],
                                source[1],
                                source[2]);

                int actual =
                        navigation.transitions(
                                source[0],
                                source[1],
                                source[2]);

                assertEquals(
                        expected,
                        actual,
                        "seed="
                                + SEED
                                + ", step="
                                + step
                                + ", source=("
                                + source[0]
                                + ","
                                + source[1]
                                + ","
                                + source[2]
                                + ")");
            }
        }
    }

    private static void mutate(
            SplittableRandom random,
            TestGeometry geometry) {

        int[] source =
                SOURCES[random.nextInt(SOURCES.length)];

        int x =
                source[0]
                        + random.nextInt(-2, 3);
        int y =
                source[1]
                        + random.nextInt(-2, 3);
        int z =
                source[2]
                        + random.nextInt(-2, 3);

        if (random.nextInt(4) == 0) {
            geometry.remove(
                    x,
                    y,
                    z);
            return;
        }

        geometry.put(
                x,
                y,
                z,
                randomShape(random));
    }

    private static Shape randomShape(
            SplittableRandom random) {

        long[] ports =
                new long[27];
        int[] blocks =
                new int[27];

        for (int i = 0; i < 27; i++) {
            int departures =
                    random.nextInt()
                            & TransitionMask.ALL;

            int arrivals =
                    random.nextInt()
                            & TransitionMask.ALL;

            ports[i] =
                    TransitionPorts.of(
                            departures,
                            arrivals);

            blocks[i] =
                    random.nextInt()
                            & TransitionMask.ALL;
        }

        return new RelativeProbeShape(
                ports,
                blocks);
    }

    private static int resolveReference(
            TestGeometry geometry,
            int sourceX,
            int sourceY,
            int sourceZ) {

        int departures =
                TransitionMask.NONE;
        int arrivals =
                TransitionMask.NONE;
        int blocks =
                TransitionMask.NONE;

        for (Map.Entry<Cell, Shape> entry
                : geometry.shapes.entrySet()) {

            Cell cell =
                    entry.getKey();

            long offsetX =
                    (long) cell.x - sourceX;
            long offsetY =
                    (long) cell.y - sourceY;
            long offsetZ =
                    (long) cell.z - sourceZ;

            if (Math.abs(offsetX) > 1
                    || Math.abs(offsetY) > 1
                    || Math.abs(offsetZ) > 1) {
                continue;
            }

            Shape shape =
                    entry.getValue();

            long ports =
                    shape.transitionPorts(
                            sourceX - cell.x,
                            sourceY - cell.y,
                            sourceZ - cell.z);

            departures |=
                    TransitionPorts.departures(ports);

            arrivals |=
                    TransitionPorts.arrivals(ports);

            blocks |=
                    shape.transitionBlocks(
                            sourceX - cell.x,
                            sourceY - cell.y,
                            sourceZ - cell.z);
        }

        int resolved =
                TransitionMask.NONE;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0
                            && dy == 0
                            && dz == 0) {
                        continue;
                    }

                    int direction =
                            TransitionMask.of(
                                    dx,
                                    dy,
                                    dz);

                    if ((departures & direction) != 0
                            && (arrivals & direction) != 0
                            && (blocks & direction) == 0) {
                        resolved |= direction;
                    }
                }
            }
        }

        return resolved;
    }

    private record RelativeProbeShape(
            long[] ports,
            int[] blocks)
            implements Shape {

        @Override
        public long transitionPorts(
                int relativeX,
                int relativeY,
                int relativeZ) {

            int index =
                    localIndex(
                            relativeX,
                            relativeY,
                            relativeZ);

            if (index < 0) {
                return TransitionPorts.NONE;
            }

            return ports[index];
        }

        @Override
        public int transitionBlocks(
                int relativeX,
                int relativeY,
                int relativeZ) {

            int index =
                    localIndex(
                            relativeX,
                            relativeY,
                            relativeZ);

            if (index < 0) {
                return TransitionMask.NONE;
            }

            return blocks[index];
        }
    }

    private static int localIndex(
            int x,
            int y,
            int z) {

        if (x < -1 || x > 1
                || y < -1 || y > 1
                || z < -1 || z > 1) {
            return -1;
        }

        return (z + 1) * 9
                + (y + 1) * 3
                + x + 1;
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

        void remove(
                int x,
                int y,
                int z) {

            shapes.remove(
                    new Cell(x, y, z));
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
