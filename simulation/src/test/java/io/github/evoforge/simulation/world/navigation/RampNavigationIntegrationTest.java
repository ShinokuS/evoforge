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
import io.github.evoforge.simulation.world.geometry.RampShape;
import io.github.evoforge.simulation.world.geometry.TransitionMask;

final class RampNavigationIntegrationTest {
    private static final MaterialDefinitionId TERRAIN = MaterialDefinitionId.of(0);

    @Test
    void positiveYRampConnectsLowerAndUpperLevels() {
        TestTerrainLookup terrain = baseTerrain();
        NavigationLookup navigation = navigation(terrain, RampShape.POSITIVE_Y);

        int lower = navigation.transitions(0, 0, 0);
        assertEquals(1, Integer.bitCount(lower));
        assertTrue(TransitionMask.contains(lower, 0, 1, 1));

        int ramp = navigation.transitions(0, 1, 1);
        assertEquals(2, Integer.bitCount(ramp));
        assertTrue(TransitionMask.contains(ramp, 0, -1, -1));
        assertTrue(TransitionMask.contains(ramp, 0, 1, 0));

        int upper = navigation.transitions(0, 2, 1);
        assertEquals(1, Integer.bitCount(upper));
        assertTrue(TransitionMask.contains(upper, 0, -1, 0));
    }

    @Test
    void consecutiveRampsConnectThreeLevels() {
        TestTerrainLookup terrain = new TestTerrainLookup();
        terrain.add(0, 0, -1);
        terrain.add(0, 1, 0);
        terrain.add(0, 2, 1);
        terrain.add(0, 3, 1);

        GeometrySystem geometry = new GeometrySystem(terrain);
        geometry.setShape(0, 1, 0, RampShape.POSITIVE_Y);
        geometry.setShape(0, 2, 1, RampShape.POSITIVE_Y);
        NavigationLookup navigation = new NavigationSystem(geometry.lookup()).lookup();

        int firstRamp = navigation.transitions(0, 1, 1);
        assertEquals(2, Integer.bitCount(firstRamp));
        assertTrue(TransitionMask.contains(firstRamp, 0, -1, -1));
        assertTrue(TransitionMask.contains(firstRamp, 0, 1, 1));

        int secondRamp = navigation.transitions(0, 2, 2);
        assertEquals(2, Integer.bitCount(secondRamp));
        assertTrue(TransitionMask.contains(secondRamp, 0, -1, -1));
        assertTrue(TransitionMask.contains(secondRamp, 0, 1, 0));
    }

    @Test
    void parallelRampsJoinHorizontallyInBothDirections() {
        TestTerrainLookup terrain = baseTerrain();
        terrain.add(1, 1, 0);
        GeometrySystem geometry = new GeometrySystem(terrain);
        geometry.setShape(0, 1, 0, RampShape.POSITIVE_Y);
        geometry.setShape(1, 1, 0, RampShape.POSITIVE_Y);
        NavigationLookup navigation = new NavigationSystem(geometry.lookup()).lookup();

        assertTrue(TransitionMask.contains(navigation.transitions(0, 1, 1), 1, 0, 0));
        assertTrue(TransitionMask.contains(navigation.transitions(1, 1, 1), -1, 0, 0));
    }

    @Test
    void flatNeighbourDoesNotJoinASlopedRampSide() {
        TestTerrainLookup terrain = baseTerrain();
        terrain.add(1, 1, 0);
        NavigationLookup navigation = navigation(terrain, RampShape.POSITIVE_Y);

        int side = navigation.transitions(1, 1, 1);
        assertFalse(TransitionMask.contains(side, -1, 0, 0));
    }

    @Test
    void oppositeRampProfilesDoNotJoinLaterally() {
        TestTerrainLookup terrain = baseTerrain();
        terrain.add(1, 1, 0);
        GeometrySystem geometry = new GeometrySystem(terrain);
        geometry.setShape(0, 1, 0, RampShape.POSITIVE_Y);
        geometry.setShape(1, 1, 0, RampShape.NEGATIVE_Y);
        NavigationLookup navigation = new NavigationSystem(geometry.lookup()).lookup();

        assertFalse(TransitionMask.contains(navigation.transitions(0, 1, 1), 1, 0, 0));
        assertFalse(TransitionMask.contains(navigation.transitions(1, 1, 1), -1, 0, 0));
    }

    @Test
    void fullCellAtRampPositionBlocksDiagonalAscent() {
        TestTerrainLookup terrain = baseTerrain();
        terrain.add(0, 1, 1);
        NavigationLookup navigation = navigation(terrain, RampShape.POSITIVE_Y);
        assertFalse(TransitionMask.contains(navigation.transitions(0, 0, 0), 0, 1, 1));
    }

    private static NavigationLookup navigation(TestTerrainLookup terrain, RampShape ramp) {
        GeometrySystem geometry = new GeometrySystem(terrain);
        geometry.setShape(0, 1, 0, ramp);
        return new NavigationSystem(geometry.lookup()).lookup();
    }

    private static TestTerrainLookup baseTerrain() {
        TestTerrainLookup terrain = new TestTerrainLookup();
        terrain.add(0, 0, -1);
        terrain.add(0, 1, 0);
        terrain.add(0, 2, 0);
        return terrain;
    }

    private record Cell(int x, int y, int z) { }

    private static final class TestTerrainLookup implements TerrainLookup {
        private final Set<Cell> terrain = new HashSet<>();
        void add(int x, int y, int z) { terrain.add(new Cell(x, y, z)); }
        @Override public MaterialDefinitionId find(int x, int y, int z) {
            return terrain.contains(new Cell(x, y, z)) ? TERRAIN : null;
        }
    }
}
