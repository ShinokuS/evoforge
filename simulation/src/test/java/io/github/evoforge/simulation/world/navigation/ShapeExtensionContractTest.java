package io.github.evoforge.simulation.world.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionPorts;

final class ShapeExtensionContractTest {

    private static final LandscapeDefinitionId TERRAIN =
            LandscapeDefinitionId.of(0);

    @Test
    void rampProbeConnectsTwoLevelsThroughExistingNavigationContract() {
        TestTerrainLookup terrain =
                rampTerrain(true, true, false);

        NavigationSystem navigation =
                navigation(terrain);

        int lower =
                navigation.lookup().transitions(
                        -1,
                        0,
                        0);

        assertEquals(
                1,
                Integer.bitCount(lower));
        assertTrue(
                TransitionMask.contains(
                        lower,
                        1,
                        0,
                        0));

        int rampLow =
                navigation.lookup().transitions(
                        0,
                        0,
                        0);

        assertEquals(
                2,
                Integer.bitCount(rampLow));
        assertTrue(
                TransitionMask.contains(
                        rampLow,
                        -1,
                        0,
                        0));
        assertTrue(
                TransitionMask.contains(
                        rampLow,
                        0,
                        0,
                        1));

        int rampTop =
                navigation.lookup().transitions(
                        0,
                        0,
                        1);

        assertEquals(
                2,
                Integer.bitCount(rampTop));
        assertTrue(
                TransitionMask.contains(
                        rampTop,
                        0,
                        0,
                        -1));
        assertTrue(
                TransitionMask.contains(
                        rampTop,
                        1,
                        0,
                        0));

        int upper =
                navigation.lookup().transitions(
                        1,
                        0,
                        1);

        assertEquals(
                1,
                Integer.bitCount(upper));
        assertTrue(
                TransitionMask.contains(
                        upper,
                        -1,
                        0,
                        0));
    }

    @Test
    void rampProbeRequiresNeighborSupportForExternalConnections() {
        NavigationSystem withoutLower =
                navigation(
                        rampTerrain(
                                false,
                                true,
                                false));

        assertEquals(
                TransitionMask.NONE,
                withoutLower.lookup().transitions(
                        -1,
                        0,
                        0));

        int lowerInside =
                withoutLower.lookup().transitions(
                        0,
                        0,
                        0);

        assertFalse(
                TransitionMask.contains(
                        lowerInside,
                        -1,
                        0,
                        0));
        assertTrue(
                TransitionMask.contains(
                        lowerInside,
                        0,
                        0,
                        1));

        NavigationSystem withoutUpper =
                navigation(
                        rampTerrain(
                                true,
                                false,
                                false));

        int upperInside =
                withoutUpper.lookup().transitions(
                        0,
                        0,
                        1);

        assertFalse(
                TransitionMask.contains(
                        upperInside,
                        1,
                        0,
                        0));
        assertTrue(
                TransitionMask.contains(
                        upperInside,
                        0,
                        0,
                        -1));
    }

    @Test
    void fullCellAboveRampProbeBlocksInternalAscent() {
        NavigationSystem navigation =
                navigation(
                        rampTerrain(
                                true,
                                true,
                                true));

        int rampLow =
                navigation.lookup().transitions(
                        0,
                        0,
                        0);

        assertFalse(
                TransitionMask.contains(
                        rampLow,
                        0,
                        0,
                        1));
        assertTrue(
                TransitionMask.contains(
                        rampLow,
                        -1,
                        0,
                        0));
    }

    private static NavigationSystem navigation(
            TestTerrainLookup terrain) {

        GeometrySystem geometry =
                new GeometrySystem(terrain);

        geometry.setShape(
                0,
                0,
                0,
                EastRampProbe.INSTANCE);

        return new NavigationSystem(
                geometry.lookup());
    }

    private static TestTerrainLookup rampTerrain(
            boolean lowerSupport,
            boolean upperSupport,
            boolean ceiling) {

        TestTerrainLookup terrain =
                new TestTerrainLookup();

        terrain.add(
                0,
                0,
                0);

        if (lowerSupport) {
            terrain.add(
                    -1,
                    0,
                    -1);
        }

        if (upperSupport) {
            terrain.add(
                    1,
                    0,
                    0);
        }

        if (ceiling) {
            terrain.add(
                    0,
                    0,
                    1);
        }

        return terrain;
    }

    private enum EastRampProbe
            implements Shape {

        INSTANCE;

        private static final int EAST =
                TransitionMask.of(1, 0, 0);
        private static final int WEST =
                TransitionMask.of(-1, 0, 0);
        private static final int UP =
                TransitionMask.of(0, 0, 1);
        private static final int DOWN =
                TransitionMask.of(0, 0, -1);

        @Override
        public long transitionPorts(
                int relativeX,
                int relativeY,
                int relativeZ) {

            if (relativeY != 0) {
                return TransitionPorts.NONE;
            }

            if (relativeX == -1 && relativeZ == 0) {
                return TransitionPorts.arrivalsOnly(EAST);
            }

            if (relativeX == 0 && relativeZ == 0) {
                return TransitionPorts.of(
                        WEST | UP,
                        UP);
            }

            if (relativeX == 0 && relativeZ == 1) {
                return TransitionPorts.of(
                        DOWN | EAST,
                        DOWN);
            }

            if (relativeX == 1 && relativeZ == 1) {
                return TransitionPorts.arrivalsOnly(WEST);
            }

            return TransitionPorts.NONE;
        }
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
