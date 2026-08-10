package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;

final class GeometrySystemTest {

    @Test
    void returnsNullWithoutTerrain() {
        GeometrySystem geometry =
                new GeometrySystem(
                        new TestTerrainLookup());

        assertNull(
                geometry.lookup().find(
                        1,
                        2,
                        3));
    }

    @Test
    void returnsFullShapeForTerrainWithoutOverride() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        terrain.add(
                1,
                2,
                3);

        GeometrySystem geometry =
                new GeometrySystem(terrain);

        assertSame(
                FullShape.INSTANCE,
                geometry.lookup().find(
                        1,
                        2,
                        3));
    }

    @Test
    void returnsShapeOverride() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        terrain.add(
                1,
                2,
                3);

        GeometrySystem geometry =
                new GeometrySystem(terrain);

        Shape shape =
                new TestShape("test");

        geometry.setShape(
                1,
                2,
                3,
                shape);

        assertSame(
                shape,
                geometry.lookup().find(
                        1,
                        2,
                        3));
    }

    @Test
    void replacesShapeOverride() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        terrain.add(
                1,
                2,
                3);

        GeometrySystem geometry =
                new GeometrySystem(terrain);

        Shape first =
                new TestShape("first");

        Shape second =
                new TestShape("second");

        geometry.setShape(
                1,
                2,
                3,
                first);

        geometry.setShape(
                1,
                2,
                3,
                second);

        assertSame(
                second,
                geometry.lookup().find(
                        1,
                        2,
                        3));
    }

    @Test
    void settingFullShapeRemovesOverride() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        terrain.add(
                1,
                2,
                3);

        GeometrySystem geometry =
                new GeometrySystem(terrain);

        geometry.setShape(
                1,
                2,
                3,
                new TestShape("test"));

        geometry.setShape(
                1,
                2,
                3,
                FullShape.INSTANCE);

        assertSame(
                FullShape.INSTANCE,
                geometry.lookup().find(
                        1,
                        2,
                        3));
    }

    @Test
    void rejectsShapeWithoutTerrain() {
        GeometrySystem geometry =
                new GeometrySystem(
                        new TestTerrainLookup());

        assertThrows(
                IllegalStateException.class,
                () -> geometry.setShape(
                        1,
                        2,
                        3,
                        new TestShape("test")));
    }

    @Test
    void rejectsNullShape() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        terrain.add(
                1,
                2,
                3);

        GeometrySystem geometry =
                new GeometrySystem(terrain);

        assertThrows(
                IllegalArgumentException.class,
                () -> geometry.setShape(
                        1,
                        2,
                        3,
                        null));
    }

    @Test
    void lookupIsStable() {
        GeometrySystem geometry =
                new GeometrySystem(
                        new TestTerrainLookup());

        assertSame(
                geometry.lookup(),
                geometry.lookup());
    }

    @Test
    void supportsFullIntCoordinateRange() {
        TestTerrainLookup terrain =
                new TestTerrainLookup();

        terrain.add(
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE);

        GeometrySystem geometry =
                new GeometrySystem(terrain);

        Shape shape =
                new TestShape("test");

        geometry.setShape(
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                shape);

        assertSame(
                shape,
                geometry.lookup().find(
                        Integer.MIN_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MIN_VALUE));
    }

    @Test
    void rejectsNullTerrain() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeometrySystem(null));
    }

    private record TestShape(
            String name)
            implements Shape {

        @Override
        public long transitionPorts(
                int relativeX,
                int relativeY,
                int relativeZ) {

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

        private static final LandscapeDefinitionId TERRAIN =
                LandscapeDefinitionId.of(0);

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

            if (terrain.contains(
                    new Cell(x, y, z))) {
                return TERRAIN;
            }

            return null;
        }
    }
}
