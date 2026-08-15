package io.github.evoforge.visualizer.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.water.WaterLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

final class WaterSliceResolverTest {

    @Test
    void exactSelectedLayerWinsEvenWhenColumnAlsoContainsHigherWater() {
        Fixture fixture = new Fixture()
                .water(2, 3, 0, 300_000)
                .water(2, 3, 1, 700_000);

        assertEquals(
                0,
                fixture.resolver().resolve(2, 3, 0, 4));
        assertEquals(
                1,
                fixture.resolver().resolve(2, 3, 1, 4));
    }

    @Test
    void fullWaterCellOneLevelBelowSelectedCutRemainsVisible() {
        Fixture fixture = new Fixture()
                .water(0, 0, 0, 1_000_000);

        assertEquals(
                0,
                fixture.resolver().resolve(0, 0, 1, 4));
    }

    @Test
    void deeperWaterIsVisibleThroughOpenCutawayWithinConfiguredDepth() {
        Fixture fixture = new Fixture()
                .water(0, 0, -2, 400_000);

        assertEquals(
                -2,
                fixture.resolver().resolve(0, 0, 1, 3));
        assertEquals(
                WaterSliceResolver.NO_WATER,
                fixture.resolver().resolve(0, 0, 1, 2));
    }

    @Test
    void solidRoofBlocksWaterBelowIt() {
        Fixture fixture = new Fixture()
                .solid(0, 0, 0)
                .water(0, 0, -1, 600_000);

        assertEquals(
                WaterSliceResolver.NO_WATER,
                fixture.resolver().resolve(0, 0, 1, 4));
    }

    @Test
    void waterAboveSelectedCutDoesNotLeakIntoLowerSlice() {
        Fixture fixture = new Fixture()
                .water(0, 0, 2, 500_000);

        assertEquals(
                WaterSliceResolver.NO_WATER,
                fixture.resolver().resolve(0, 0, 1, 4));
    }

    @Test
    void partialRampCellCanCarryVisibleWaterAndDoesNotBehaveAsSolidRoof() {
        Fixture fixture = new Fixture()
                .shape(0, 0, 0, RampShape.POSITIVE_X)
                .water(0, 0, 0, 300_000);

        assertEquals(
                0,
                fixture.resolver().resolve(0, 0, 1, 4));
    }

    private record Cell(int x, int y, int z) {
    }

    private static final class Fixture {
        private final Map<Cell, Integer> water = new HashMap<>();
        private final Map<Cell, Shape> shapes = new HashMap<>();

        private Fixture water(int x, int y, int z, int amount) {
            water.put(new Cell(x, y, z), amount);
            return this;
        }

        private Fixture solid(int x, int y, int z) {
            return shape(x, y, z, FullShape.INSTANCE);
        }

        private Fixture shape(int x, int y, int z, Shape shape) {
            shapes.put(new Cell(x, y, z), shape);
            return this;
        }

        private WaterSliceResolver resolver() {
            WaterLookup waterLookup = (x, y, z) ->
                    water.getOrDefault(new Cell(x, y, z), 0);
            GeometryLookup geometryLookup = (x, y, z) ->
                    shapes.get(new Cell(x, y, z));
            return new WaterSliceResolver(waterLookup, geometryLookup);
        }
    }
}
